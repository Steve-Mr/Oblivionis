package top.maary.oblivionis.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import top.maary.oblivionis.data.pagingsource.MarkedItemsPagingSource
import top.maary.oblivionis.data.pagingsource.MediaStorePagingSource
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * 重构后的 ImageRepository.
 *
 * 职责:
 * 1. 作为图片数据的唯一真实来源 (Single Source of Truth).
 * 2. 封装所有与图片相关的业务逻辑，包括从 MediaStore 加载和从 Room 数据库读写.
 * 3. 向 ViewModel 提供数据流 (Flows of data).
 *
 * @param database Room 数据库的访问接口.
 * @param contentResolver 用于查询 MediaStore.
 */
class ImageRepository(
    private val database: ImageDatabase,
    private val contentResolver: ContentResolver
) {
    private val imageDao = database.imageDao()

    fun getImagePagingData(albumPath: String): Flow<PagingData<MediaEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20, // 每次加载20个
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                MediaStorePagingSource(contentResolver, database, albumPath)
            }
        ).flow
    }

    fun getMarkedImagePagingData(albumPath: String): Flow<PagingData<MediaEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30, // 回收站每页可以加载更多，比如30个
                enablePlaceholders = false
            ),
            // 使用我们新创建的 PagingSource
            pagingSourceFactory = { MarkedItemsPagingSource(database, albumPath) }
        ).flow
    }

    suspend fun unmarkAllInAlbum(albumPath: String, excludeIds: Set<Long>) {
        withContext(Dispatchers.IO) {
            imageDao.unmarkAllInAlbum(albumPath, excludeIds)
        }
    }

    fun getMarkedCountStream(albumPath: String): Flow<Int> {
        return imageDao.getMarkedCountStream(albumPath)
    }
    
    suspend fun getMarkedInAlbumOnce(albumPath: String): List<MediaEntity> {
        return imageDao.getMarkedInAlbumOnce(albumPath)
    }

    /**
     * 标记指定相册中的所有图片。
     * @param albumPath 要批量标记的相册路径。
     */
    suspend fun markAllInAlbum(albumPath: String) {
        withContext(Dispatchers.IO) {
            // 1. 从 MediaStore 查询出该相册的所有图片信息
            val allImagesInAlbum = queryImagesFromMediaStore(albumPath)

            // 2. 准备要插入/更新到数据库的实体列表
            //    过滤掉已经被排除的图片，其他的全部设置为 isMarked = true
            val imagesToMark = allImagesInAlbum
                .filterNot { it.isExcluded } // 假设我们不标记已排除的
                .map { it.copy(isMarked = true) }

            // 3. 调用 DAO 进行批量插入/替换操作
            if (imagesToMark.isNotEmpty()) {
                imageDao.markAll(imagesToMark)
            }
        }
    }

    /**
     * 响应式地获取指定相册中的媒体总数。
     * 这个 Flow 由 ContentObserver 驱动，当外部存储变化时会自动发出新的计数值。
     * @param albumPath 要查询的相册路径。
     */
    fun getAlbumTotalCountStream(albumPath: String): Flow<Int> = callbackFlow {
        // 1. 定义 ContentObserver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // 当媒体库变化时，重新查询总数并发送
                trySend(getAlbumTotalCount(albumPath))
            }
        }

        // 2. 注册观察者
        contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"),
            true,
            observer
        )

        // 3. 在 Flow 第一次被收集时，立即发送一个初始值
        trySend(getAlbumTotalCount(albumPath))

        // 4. 当 Flow 关闭时，注销观察者
        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    /**
     * Log.e调用 DAO 来通过ID列表删除数据库记录。
     */
    suspend fun deleteImagesByIds(ids: Set<Long>) {
        withContext(Dispatchers.IO) {
            imageDao.deleteImagesByIds(ids)
        }
    }

    /**
     * Log.e创建一个响应式的 Flow，用于监听并提供相册列表。
     * 当外部媒体库发生变化时，它会自动发出一个新的相册列表。
     */
    fun getAlbumListStream(): Flow<List<Album>> = callbackFlow {
        // 1. 定义 ContentObserver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // 当媒体库变化时，重新查询相册列表并发送
                trySend(queryAlbumsFromMediaStore())
            }
        }

        // 2. 注册观察者来监听所有外部媒体文件的变化
        contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"),
            true,
            observer
        )

        // 3. 在 Flow 第一次被收集时，立即发送一个初始的相册列表
        trySend(queryAlbumsFromMediaStore())

        // 4. 当 Flow 关闭时（例如 ViewModel 被销毁），注销观察者以防内存泄漏
        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    /**
     * Log.e从 ViewModel 移入的私有方法，专门用于查询相册列表。
     */
    private fun queryAlbumsFromMediaStore(): List<Album> {
        val albumMap = mutableMapOf<String, Album>()
        // 【优化】直接查询外部文件 URI，覆盖图片和视频
        val collection = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns._ID // 只需要一个ID列来计数
        )

        // 【优化】通过 selection 过滤掉非图片和视频的文件
        val selection = """
            ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR 
            ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?
        """.trimIndent()
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val nameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                val pathColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn) ?: "Unknown"
                    // 【优化】RELATIVE_PATH 更可靠，且无需处理 File 对象
                    val path = cursor.getString(pathColumn)?.removeSuffix("/") ?: continue

                    // 如果路径为空（例如在存储根目录下的图片），则跳过
                    if (path.isEmpty()) continue

                    val album = albumMap[path]
                    if (album == null) {
                        albumMap[path] = Album(name, path, 1)
                    } else {
                        albumMap[path] = album.copy(mediaCount = album.mediaCount + 1)
                    }
                }
            }
        return albumMap.values.toList().sortedByDescending { it.mediaCount }
    }

    /**
     * 获取指定相册中的媒体总数。
     * 这是一个轻量级操作，只用于显示计数。
     * @param albumPath 要查询的相册路径。
     */
    private fun getAlbumTotalCount(albumPath: String): Int {
            var count = 0
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns._ID) // 只需要查询 ID 列即可
            val selection = """
                (${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)
                AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} like ?
            """.trimIndent()
            val selectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                "$albumPath/"
            )

            // 对于只获取总数，我们不需要 Bundle 查询，一个简单的 query 即可
            contentResolver.query(collection, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    count = cursor.count
                }
            return count
    }

    /**
     * 标记一张图片 (包括标记为待删除或排除).
     * @param image 要标记的图片.
     */
    @WorkerThread
    suspend fun mark(image: MediaEntity) {
        withContext(Dispatchers.IO) {
            imageDao.mark(image)
        }
    }

    /**
     * 取消标记一张图片 (包括恢复或取消排除).
     * @param image 要取消标记的图片.
     */
    @WorkerThread
    suspend fun unmark(image: MediaEntity) {
        withContext(Dispatchers.IO) {
            // 如果图片同时是 marked 和 excluded，unmark 只应该移除 marked 状态
            if (image.isMarked && image.isExcluded) {
                imageDao.updateIsMarked(image.id, false)
            } else {
                imageDao.unmark(image)
            }
        }
    }

    /**
     * 从 MediaStore 查询指定相册的所有图片和视频.
     */
    private fun queryImagesFromMediaStore(albumPath: String): List<MediaEntity> {
        val mediaList = mutableListOf<MediaEntity>()
        // 1. 使用统一的 URI
        val collection = MediaStore.Files.getContentUri("external")

        // 2. 投影中增加 MEDIA_TYPE，用于区分图片和视频
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE, // 新增
            MediaStore.Files.FileColumns.RELATIVE_PATH // 使用 RELATIVE_PATH 进行筛选
        )

        // 3. 在 selection 中同时筛选媒体类型和路径
        val selection = """
        (${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)
        AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} like ?
    """.trimIndent()

        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            "$albumPath/"
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val dateAdded = Date(TimeUnit.SECONDS.toMillis(cursor.getLong(dateAddedColumn)))
                    val mediaType = cursor.getInt(mediaTypeColumn)

                    // 4. 根据媒体类型，构建正确的 Content URI
                    val contentUri = when (mediaType) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        else -> continue // 忽略其他类型的文件
                    }

                    val media = MediaEntity(id, displayName, albumPath, dateAdded, contentUri)
                    mediaList += media
                }
            }
        // 注意这里返回的是一个未排序的列表，因为在外部调用它的 getImagesStream 中会进行排序。
        // 如果需要，也可以在这里直接返回排序后的列表。
        return mediaList
    }
}