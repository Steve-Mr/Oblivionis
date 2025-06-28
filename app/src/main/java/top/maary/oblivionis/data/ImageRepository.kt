package top.maary.oblivionis.data

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow


class ImageRepository(private val imageDao: ImageDao) {

    @WorkerThread
    suspend fun mark(image: MediaStoreImage) {
        imageDao.mark(image)
    }

    suspend fun unmark(image: MediaStoreImage) {
        if (image.isMarked and image.isExcluded) {
            imageDao.updateIsMarked(image.id, false)
            return
        }

        imageDao.unmark(image)
    }

    suspend fun removeAll() {
        imageDao.removeAll()
    }

    suspend fun getLastMarkedInAlbum(album: String): MediaStoreImage {
        return imageDao.getLastMarkedInAlbum(album)
    }

    fun getMarkedInAlbum(album: String): Flow<List<MediaStoreImage>>? {
        return imageDao.getMarkedInAlbum(album)
    }

    fun getExcludedInAlbum(album: String): Flow<List<MediaStoreImage>>? {
        return imageDao.getExcludedInAlbum(album)
    }

    suspend fun removeId(id: Long) {
        imageDao.removeId(id)
    }
}