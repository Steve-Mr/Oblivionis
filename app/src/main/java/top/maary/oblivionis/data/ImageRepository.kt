package top.maary.oblivionis.data

import android.util.Log
import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow


class ImageRepository(private val imageDao: ImageDao) {
    val allMarks: Flow<List<MediaStoreImage>>? = imageDao.getAllMarks()
    val allExcludes: Flow<List<MediaStoreImage>>? = imageDao.getAllExcludes()

//    suspend fun getAllMarks(): List<MediaStoreImage>? {
//        return imageDao.getAllMarks()
//    }

    @WorkerThread
    suspend fun mark(image: MediaStoreImage) {
//        if (image.isMarked and image.isExcluded) image.isMarked = false
        Log.v("OBLIVIONIS", "MARK ${image.isMarked} ${image.isExcluded}")
        imageDao.mark(image)
    }

    suspend fun unmark(image: MediaStoreImage) {
        Log.v("OBLIVIONIS", "UNMARK ${image.isMarked} ${image.isExcluded}")
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