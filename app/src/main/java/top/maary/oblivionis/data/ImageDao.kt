package top.maary.oblivionis.data

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

    @Query("SELECT * FROM images WHERE is_marked = true")
    fun getAllMarks(): Flow<List<MediaStoreImage>>?

    @Query("SELECT * FROM images WHERE is_excluded = true")
    fun getAllExcludes(): Flow<List<MediaStoreImage>>?

    @Query("SELECT * FROM images ORDER BY date_added DESC LIMIT 1")
    suspend fun getLastMarked(): MediaStoreImage

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun removeId(id: Long)

    @Query("UPDATE images SET is_marked = :isMarked WHERE id = :id")
    suspend fun updateIsMarked(id: Long, isMarked: Boolean)

}