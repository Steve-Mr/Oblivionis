package top.maary.oblivionis.data.pagingsource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import top.maary.oblivionis.data.ImageDatabase
import top.maary.oblivionis.data.MediaEntity

/**
 * 一个专门为 RecycleScreen 设计的 PagingSource。
 * 它直接从 Room 数据库中按页加载被标记为删除的图片。
 * 【新】它现在可以监听数据库变化并自动刷新。
 */
class MarkedItemsPagingSource(
    private val database: ImageDatabase, // 【修改】依赖整个 database
    private val albumPath: String
) : PagingSource<Int, MediaEntity>() {

    private val imageDao = database.imageDao()

    // 【新增】创建数据库表变化的观察者
    private val observer = object : InvalidationTracker.Observer(arrayOf("images")) {
        override fun onInvalidated(tables: Set<String>) {
            invalidate()
        }
    }

    init {
        // 【新增】注册观察者
        database.invalidationTracker.addObserver(observer)

        // 【新增】在 PagingSource 失效时注销观察者
        registerInvalidatedCallback {
            database.invalidationTracker.removeObserver(observer)
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
            val pageSize = params.loadSize
            val markedImages = imageDao.getMarkedInAlbumPaged(albumPath, pageSize, page * pageSize)

            LoadResult.Page(
                data = markedImages,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (markedImages.size < params.loadSize) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}