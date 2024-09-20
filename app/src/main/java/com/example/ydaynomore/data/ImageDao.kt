package com.example.ydaynomore.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun mark(image: MediaStoreImage)

    @Delete
    suspend fun unmark(image: MediaStoreImage)

    @Query("DELETE FROM images")
    suspend fun removeAll()

    @Query("SELECT * FROM images")
    fun getAllMarks(): Flow<List<MediaStoreImage>>

    @Query("SELECT * FROM images ORDER BY date_added DESC LIMIT 1")
    suspend fun getLastMarked(): MediaStoreImage

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun removeId(id: Long)

}