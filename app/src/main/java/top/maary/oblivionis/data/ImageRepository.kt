package top.maary.oblivionis.data

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow


class ImageRepository(private val imageDao: ImageDao) {
    val allMarks: Flow<List<MediaStoreImage>>? = imageDao.getAllMarks()
    val allExcludes: Flow<List<MediaStoreImage>>? = imageDao.getAllExcludes()

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

    suspend fun getLastMarked(): MediaStoreImage {
        return imageDao.getLastMarked()
    }

    suspend fun removeId(id: Long) {
        imageDao.removeId(id)
    }
}