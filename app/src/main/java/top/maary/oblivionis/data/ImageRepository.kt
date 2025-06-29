package top.maary.oblivionis.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
 * @param imageDao Room 数据库的访问接口.
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

    /**
     * RecycleScreen 仍然需要一个非分页的标记列表。
     */
    fun getMarkedImagesStream(albumPath: String): Flow<List<MediaEntity>> {
        return imageDao.getMarkedInAlbum(albumPath) ?: flow { emit(emptyList()) }
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
     * 【新增】调用 DAO 来通过ID列表删除数据库记录。
     */
    suspend fun deleteImagesByIds(ids: Set<Long>) {
        withContext(Dispatchers.IO) {
            imageDao.deleteImagesByIds(ids)
        }
    }

    /**
     * 【补充】调用 DAO 来通过ID列表获取图片对象。
     */
    suspend fun getImagesByIds(ids: Set<Long>): List<MediaEntity> {
        return withContext(Dispatchers.IO) {
            imageDao.getImagesByIds(ids)
        }
    }

    /**
     * 【新增】创建一个响应式的 Flow，用于监听并提供相册列表。
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
     * 【新增】从 ViewModel 移入的私有方法，专门用于查询相册列表。
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
     * 获取指定相册的图片流。
     * 这个 Flow 会结合 MediaStore 的数据和 Room 中标记/排除的数据。
     * 当数据库变化时，Flow 会自动发出最新的数据列表。
     * @param albumPath 要加载的相册路径.
     */
    fun getImagesStream(albumPath: String): Flow<List<MediaEntity>> {

        // 1. 创建一个 ContentObserver Flow 作为“触发器”
        //    它会在外部文件系统变化时发出一个信号。
        val contentObserverFlow = callbackFlow {
            val observer = object : android.database.ContentObserver(android.os.Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit) // 文件变化时，发送一个信号
                }
            }
            // 注册观察者
            val urisToObserve = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
            urisToObserve.forEach { uri ->
                contentResolver.registerContentObserver(uri, true, observer)
            }

            // 当 Flow 被取消时，注销观察者
            awaitClose {
                contentResolver.unregisterContentObserver(observer)
            }
        }.onStart { emit(Unit) } // onStart 会在 Flow 第一次被收集时立即发送一个初始信号，用于加载初始数据

        // 2. 结合触发器来驱动 MediaStore 查询
        //    每当 contentObserverFlow 发出信号，我们都重新查询 MediaStore
        val imagesFromMediaFlow = contentObserverFlow.map {
            queryImagesFromMediaStore(albumPath)
        }.distinctUntilChanged() // 如果查询结果没有变化，则不发射，避免不必要的重组

        Log.v("IMAGE_REPO", "getImagesStream: Querying images from MediaStore for album: $albumPath")

        // 3. 从 Room 获取标记和排除的图片流 (这部分逻辑不变)
        val markedImagesFlow: Flow<List<MediaEntity>> = imageDao.getMarkedInAlbum(albumPath) ?: flow { emit(emptyList()) }
        val excludedImagesFlow: Flow<List<MediaEntity>> = imageDao.getExcludedInAlbum(albumPath) ?: flow { emit(emptyList()) }

        // 4. 将所有数据源合并，生成最终的 UI 状态
        return combine(imagesFromMediaFlow, markedImagesFlow, excludedImagesFlow) { mediaImages, markedImages, excludedImages ->
            mediaImages.map { mediaImage ->
                mediaImage.copy(
                    isMarked = markedImages.any { it.id == mediaImage.id },
                    isExcluded = excludedImages.any { it.id == mediaImage.id }
                )
            }.sortedByDescending { it.dateAdded }
        }.flowOn(Dispatchers.IO) // 确保所有操作都在 IO 线程
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
        val images = mutableListOf<MediaEntity>()
        val uriList = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.ImageColumns.RELATIVE_PATH} like ?"
        val selectionArgs = arrayOf("$albumPath/")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        for (uri in uriList) {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val dateAdded = Date(TimeUnit.SECONDS.toMillis(cursor.getLong(dateAddedColumn)))
                        val displayName = cursor.getString(displayNameColumn)
                        val contentUri = ContentUris.withAppendedId(uri, id)
                        val image = MediaEntity(id, displayName, albumPath, dateAdded, contentUri)
                        images += image
                    }
                }
        }
        return images
    }
}