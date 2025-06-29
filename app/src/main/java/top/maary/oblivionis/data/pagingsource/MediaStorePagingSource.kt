package top.maary.oblivionis.data.pagingsource

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.maary.oblivionis.data.ImageDatabase
import top.maary.oblivionis.data.MediaEntity
import java.util.Date
import java.util.concurrent.TimeUnit

class MediaStorePagingSource(
    private val contentResolver: ContentResolver,
    private val database: ImageDatabase,
    private val albumPath: String
) : PagingSource<Int, MediaEntity>() {

    private val imageDao = database.imageDao()

    private var markedIdsSnapshot: Set<Long>? = null
    private var excludedIdsSnapshot: Set<Long>? = null

    private val observer = object : InvalidationTracker.Observer(arrayOf("images")) {
        override fun onInvalidated(tables: Set<String>) {
            Log.d("PAGING_DEBUG", "mediaObserver triggered. Invalidating PagingSource for album: $albumPath")
            invalidate()
        }
    }

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            // 当外部媒体文件发生变化时，让 PagingSource 失效
            invalidate()
        }
    }

    init {
        Log.d("PAGING_DEBUG", "PagingSource CREATED for album: '$albumPath'")
        // 在 PagingSource 初始化时，注册观察者
        database.invalidationTracker.addObserver(observer)
        contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, mediaObserver)
        // 当 PagingSource 失效时（无论是手动还是自动），确保注销观察者，防止内存泄漏
        registerInvalidatedCallback {
            Log.d("PAGING_DEBUG", "PagingSource INVALIDATED for album: '$albumPath'")
            database.invalidationTracker.removeObserver(observer)
            contentResolver.unregisterContentObserver(mediaObserver)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaEntity>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaEntity> {
        return try {
            val page = params.key ?: 0
            val offset = page * params.loadSize
            Log.i("PAGING_DEBUG", "--> load() called: page=$page, offset=$offset, loadSize=${params.loadSize}")

            if (markedIdsSnapshot == null || excludedIdsSnapshot == null) {
                markedIdsSnapshot = imageDao.getMarkedIdsInAlbum(albumPath).toSet()
                excludedIdsSnapshot = imageDao.getExcludedIdsInAlbum(albumPath).toSet()
            }

            val currentMarkedIds = markedIdsSnapshot!!
            val currentExcludedIds = excludedIdsSnapshot!!

            val media = withContext(Dispatchers.IO) {
                queryMediaStore(limit = params.loadSize, offset = offset)
            }
            Log.i("PAGING_DEBUG", "queryMediaStore returned ${media.size} items.")

//            val markedIds = imageDao.getMarkedIdsInAlbum(albumPath)
//            val excludedIds = imageDao.getExcludedIdsInAlbum(albumPath)

            val processedMedia = media
                .filterNot { currentMarkedIds.contains(it.id) }
                .map { it.copy(isExcluded = currentExcludedIds.contains(it.id)) }

            Log.i("PAGING_DEBUG", "<-- load() FINISHED. Returning ${processedMedia.size} items. IDs: ${processedMedia.map { it.id }}")

            LoadResult.Page(
                data = processedMedia,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (media.size < params.loadSize) null else page + 1
            )
        } catch (e: Exception) {
            Log.e("PAGING_DEBUG", "Error during load()", e)
            LoadResult.Error(e)
        }
    }

    private fun queryMediaStore(limit: Int, offset: Int): List<MediaEntity> {
        val images = mutableListOf<MediaEntity>()

        // 【核心修改】使用统一的URI，不再需要循环
        val collection = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE // 需要这个来区分图片和视频，并构建正确的 content URI
        )

        // 【核心修改】在 selection 中同时指定图片和视频两种媒体类型
        val selection = """
            (${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)
            AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} like ?
        """.trimIndent()

        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            "$albumPath/"
        )

        val sortColumns = arrayOf(
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns._ID
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC, ${MediaStore.Files.FileColumns._ID} DESC"

        val queryArgs = Bundle().apply {
            // Selection and Args
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)

            // 【核心修改】使用新的、结构化的方式来指定排序
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)

            // Limit and Offset
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }

        contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(displayNameColumn)
                val dateAdded = Date(TimeUnit.SECONDS.toMillis(cursor.getLong(dateAddedColumn)))
                val mediaType = cursor.getInt(mediaTypeColumn)

                // 【核心修改】根据媒体类型构建正确的 Content URI
                val contentUri = when (mediaType) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    else -> continue // 忽略其他类型
                }

                val image = MediaEntity(id, displayName, albumPath, dateAdded, contentUri)
                Log.e("MediaStorePagingSource", "Loaded image: ${image.dateAdded}")
                images += image
            }
        }
        return images
    }
}