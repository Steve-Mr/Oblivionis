package top.maary.oblivionis.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.InvalidationTracker
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun mark(image: MediaStoreImage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAll(images: List<MediaStoreImage>)

    @Delete
    suspend fun unmark(image: MediaStoreImage)

    @Query("DELETE FROM images")
    suspend fun removeAll()

    @Query("SELECT * FROM images WHERE album = :album AND is_marked = true")
    fun getMarkedInAlbum(album: String): Flow<List<MediaStoreImage>>?

    @Query("SELECT * FROM images WHERE album = :album AND is_excluded = true")
    fun getExcludedInAlbum(album: String): Flow<List<MediaStoreImage>>?

    @Query("SELECT id FROM images WHERE album = :album AND is_marked = 1")
    suspend fun getMarkedIdsInAlbum(album: String): List<Long>

    @Query("SELECT id FROM images WHERE album = :album AND is_excluded = 1")
    suspend fun getExcludedIdsInAlbum(album: String): List<Long>

    @Query("SELECT * FROM images WHERE album = :album ORDER BY date_added DESC LIMIT 1")
    suspend fun getLastMarkedInAlbum(album: String): MediaStoreImage

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun removeId(id: Long)

    @Query("UPDATE images SET is_marked = :isMarked WHERE id = :id")
    suspend fun updateIsMarked(id: Long, isMarked: Boolean)

    // 查询所有 album 字段是空字符串的条目
    @Query("SELECT * FROM images WHERE album = ''")
    suspend fun getImagesWithoutAlbumPath(): List<MediaStoreImage>

    // 提供一个更新方法
    @Update
    suspend fun updateImage(image: MediaStoreImage)

}