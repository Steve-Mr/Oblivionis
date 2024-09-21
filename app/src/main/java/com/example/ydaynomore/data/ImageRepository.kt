package com.example.ydaynomore.data

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow


class ImageRepository(private val imageDao: ImageDao) {
    val allMarks: Flow<List<MediaStoreImage>>? = imageDao.getAllMarks()

//    suspend fun getAllMarks(): List<MediaStoreImage>? {
//        return imageDao.getAllMarks()
//    }

    @WorkerThread
    suspend fun mark(image: MediaStoreImage) {
        imageDao.mark(image)
    }

    suspend fun unmark(image: MediaStoreImage) {
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