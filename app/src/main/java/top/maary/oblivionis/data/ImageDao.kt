package top.maary.oblivionis.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun mark(image: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAll(images: List<MediaEntity>)

    @Delete
    suspend fun unmark(image: MediaEntity)

    @Query("DELETE FROM images")
    suspend fun removeAll()

    @Query("SELECT * FROM images WHERE album = :album AND is_marked = true")
    fun getMarkedInAlbum(album: String): Flow<List<MediaEntity>>?

    @Query("SELECT * FROM images WHERE album = :album AND is_excluded = true")
    fun getExcludedInAlbum(album: String): Flow<List<MediaEntity>>?

    @Query("SELECT id FROM images WHERE album = :album AND is_marked = 1")
    suspend fun getMarkedIdsInAlbum(album: String): List<Long>

    @Query("SELECT id FROM images WHERE album = :album AND is_excluded = 1")
    suspend fun getExcludedIdsInAlbum(album: String): List<Long>

    @Query("SELECT * FROM images WHERE album = :album ORDER BY date_added DESC LIMIT 1")
    suspend fun getLastMarkedInAlbum(album: String): MediaEntity

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun removeId(id: Long)

    @Query("UPDATE images SET is_marked = :isMarked WHERE id = :id")
    suspend fun updateIsMarked(id: Long, isMarked: Boolean)

    // 查询所有 album 字段是空字符串的条目
    @Query("SELECT * FROM images WHERE album = ''")
    suspend fun getImagesWithoutAlbumPath(): List<MediaEntity>

    @Query("SELECT * FROM images WHERE album = :albumPath AND is_marked = 1 ORDER BY date_added DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getMarkedInAlbumPaged(albumPath: String, limit: Int, offset: Int): List<MediaEntity>

    @Query("SELECT * FROM images WHERE album = :albumPath AND is_marked = 1")
    suspend fun getMarkedInAlbumOnce(albumPath: String): List<MediaEntity>

    @Query("UPDATE images SET is_marked = 0 WHERE album = :albumPath AND is_marked = 1 AND id NOT IN (:excludeIds)")
    suspend fun unmarkAllInAlbum(albumPath: String, excludeIds: Set<Long>)

    @Query("SELECT COUNT(*) FROM images WHERE album = :albumPath AND is_marked = 1")
    fun getMarkedCountStream(albumPath: String): Flow<Int>

    /**
     * 通过ID列表删除数据库中的图片记录。
     * 这个方法将在用户确认删除后，用于清理数据库。
     * @param ids 要从数据库中删除的图片记录的 ID 集合。
     */
    @Query("DELETE FROM images WHERE id IN (:ids)")
    suspend fun deleteImagesByIds(ids: Set<Long>)

    /**
     * 为了让 ViewModel 中的 'deleteSelection' 能工作，我们还需要这个方法。
     * 它根据ID列表获取完整的 MediaStoreImage 对象。
     */
    @Query("SELECT * FROM images WHERE id IN (:ids)")
    suspend fun getImagesByIds(ids: Set<Long>): List<MediaEntity>

    @Query("SELECT id FROM images WHERE album = :albumPath AND (is_marked = 1 OR is_excluded = 1)")
    fun getFilteredIdsStream(albumPath: String): Flow<List<Long>>

    // 提供一个更新方法
    @Update
    suspend fun updateImage(image: MediaEntity)

}