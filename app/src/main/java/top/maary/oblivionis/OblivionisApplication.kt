// main/java/top/maary/oblivionis/OblivionisApplication.kt

package top.maary.oblivionis

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.maary.oblivionis.data.ImageDatabase
import top.maary.oblivionis.data.ImageRepository
import top.maary.oblivionis.data.PreferenceRepository

class OblivionisApplication: Application() {

    private val database by lazy { ImageDatabase.getDataBase(this) }
    val repository by lazy { ImageRepository(database, applicationContext.contentResolver) }
    // 将 preferenceRepository 也作为 Application 的属性，方便访问
    private val preferenceRepository by lazy { PreferenceRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // 在应用启动时，检查是否需要执行数据回填
        backfillAlbumData()
    }

    private fun backfillAlbumData() {
        // 使用协程在后台执行
        GlobalScope.launch(Dispatchers.IO) {
            // 使用 .first() 从 DataStore Flow 中读取一次当前值
            val needsBackfill = preferenceRepository.v3AlbumBackfillNeeded.first()

            if (needsBackfill) {
                val imagesToUpdate = database.imageDao().getImagesWithoutAlbumPath()
                if (imagesToUpdate.isEmpty()) {
                    // 如果没有需要更新的，也标记为完成
                    preferenceRepository.setV3AlbumBackfillNeeded(false)
                    return@launch
                }

                imagesToUpdate.forEach { image ->
                    try {
                        // 使用 ContentResolver 查询相册路径
                        val albumPath = getAlbumPathFromUri(applicationContext, image.contentUri)
                        if (albumPath != null) {
                            val updatedImage = image.copy(album = albumPath)
                            database.imageDao().updateImage(updatedImage)
                        }
                    } catch (e: Exception) {
                        // 处理可能发生的异常，例如 URI 无效
                        Log.e("DB_BACKFILL", "Failed to backfill for URI: ${image.contentUri}", e)
                    }
                }

                // 所有数据更新完毕后，修改标记，防止重复执行
                preferenceRepository.setV3AlbumBackfillNeeded(false)
                Log.i("DB_BACKFILL", "Album path backfill completed.")
            }
        }
    }

    // 这是一个辅助函数，用于从 URI 查询相册路径
    private fun getAlbumPathFromUri(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.RELATIVE_PATH)
        var path: String? = null
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                    // 路径类似于 "Pictures/MyAlbum/"，我们需要移除末尾的斜杠
                    path = cursor.getString(pathColumn)?.removeSuffix("/")
                }
            }
        } catch (e: Exception) {
            Log.e("DB_BACKFILL", "ContentResolver query failed for URI: $uri", e)
        }
        return path
    }
}