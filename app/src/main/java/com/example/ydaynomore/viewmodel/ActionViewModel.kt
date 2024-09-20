package com.example.ydaynomore.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.ydaynomore.data.MediaStoreImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit

class ActionViewModel(application: Application): AndroidViewModel(application) {
    private val _images = MutableStateFlow<List<MediaStoreImage>>(emptyList())

    val unmarkedImages : Flow<List<MediaStoreImage>> = _images.map {
        images -> images.filter { !it.isMarked }
    }

    private val _lastMarked = MutableStateFlow<MediaStoreImage?>(null)
    val lastMarked = _lastMarked.asStateFlow()

    private var contentObserver: ContentObserver? = null

    private var pendingDeleteImage: MediaStoreImage? = null
    private val _permissionNeededForDelete = MutableLiveData<IntentSender?>()
    val permissionNeededForDelete: LiveData<IntentSender?> = _permissionNeededForDelete

    /**
     * Performs a one shot load of images from [MediaStore.Images.Media.EXTERNAL_CONTENT_URI] into
     * the [_images] [LiveData] above.
     */
    fun loadImages() {
        viewModelScope.launch {
            val imageList = queryImages()
            _images.value = imageList

            if (contentObserver == null) {
                contentObserver = getApplication<Application>().contentResolver.registerObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                ) {
                    loadImages()
                }
            }
        }
    }

    fun deleteImages(images: List<MediaStoreImage>) {
        viewModelScope.launch {
            images.forEach {
                performDeleteImage(it)
            }
        }
    }

    fun deleteImage(image: MediaStoreImage) {
        viewModelScope.launch {
            performDeleteImage(image)
        }
    }

    fun deletePendingImage() {
        pendingDeleteImage?.let { image ->
            pendingDeleteImage = null
            deleteImage(image)
        }
    }

    fun markImage(index: Int) {

        val unmarkedImageList = _images.value.filter {
            !it.isMarked
        }

        // 获取要标记的图片
        val target = unmarkedImageList[index]
        _lastMarked.value = target.copy(isMarked = true)

        // 复制原始图片列表并找到要标记的图片的索引
        val updatedList = _images.value.toMutableList()
        val newIndex = updatedList.indexOf(target)

        // 确保找到图片并且不为空
        if (newIndex != -1) {
            // 标记图片
            updatedList[newIndex] = target.copy(isMarked = true)

            // 更新 _images 列表，Flow 会自动更新
            _images.value = updatedList
        }

    }

    fun unMarkLastImage(): Long? {
        // 检查 lastMarkedImage 是否为空
        _lastMarked.value?.let { img ->
            // 创建一个更新后的 _images 列表
            val updatedList = _images.value.toMutableList()

            // 找到 lastMarkedImage 的索引
            val index = updatedList.indexOf(img)

            // 确保图片在列表中存在
            if (index != -1) {
                // 更新 isMarked 状态为 false
                updatedList[index] = img.copy(isMarked = false)

                // 更新 _images 的值
                _images.value = updatedList

                // 清空 lastMarkedImage
                _lastMarked.value = null

                // 返回图片的 id
                return img.id
            }
        }
        return null
    }

    fun markList(listToMark: List<MediaStoreImage>) {
        val updatedList = _images.value.toMutableList()

        listToMark.forEach { target ->
            updatedList[updatedList.indexOf(target.copy(isMarked = false))] = target.copy(isMarked = true)
        }
        _images.value = updatedList

    }


    suspend fun queryImages(): List<MediaStoreImage> {
        val images = mutableListOf<MediaStoreImage>()

        withContext(Dispatchers.IO) {

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )

            val selection = "${MediaStore.Images.ImageColumns.RELATIVE_PATH} like ?"

            val selectionArgs = arrayOf(
                "Pictures/Screenshots/%"
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            getApplication<Application>().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->

                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateModifiedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val widthColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                Log.i(TAG, "Found ${cursor.count} images")
                while (cursor.moveToNext()) {

                    // Here we'll use the column index that we found above.
                    val id = cursor.getLong(idColumn)
                    val dateModified =
                        Date(TimeUnit.SECONDS.toMillis(cursor.getLong(dateModifiedColumn)))
                    val displayName = cursor.getString(displayNameColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val image = MediaStoreImage(id, displayName, dateModified, contentUri)

                    images += image
                }
            }
        }

        Log.v(TAG, "Found ${images.size} images")
        return images
    }

    private suspend fun performDeleteImage(image: MediaStoreImage) {
        withContext(Dispatchers.IO) {
            try {

                MediaStore.createDeleteRequest(getApplication<Application>().contentResolver, arrayListOf(image.contentUri))

            } catch (securityException: SecurityException) {
                val recoverableSecurityException =
                    securityException as? RecoverableSecurityException
                        ?: throw securityException

                // Signal to the Activity that it needs to request permission and
                // try the delete again if it succeeds.
                pendingDeleteImage = image
                _permissionNeededForDelete.postValue(
                    recoverableSecurityException.userAction.actionIntent.intentSender
                )
            }
        }
    }

    /**
     * Since we register a [ContentObserver], we want to unregister this when the `ViewModel`
     * is being released.
     */
    override fun onCleared() {
        contentObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
    }

    init {
        loadImages()
    }
}

/**
 * Convenience extension method to register a [ContentObserver] given a lambda.
 */
private fun ContentResolver.registerObserver(
    uri: Uri,
    observer: (selfChange: Boolean) -> Unit
): ContentObserver {
    val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            observer(selfChange)
        }
    }
    registerContentObserver(uri, true, contentObserver)
    return contentObserver
}


private const val TAG = "HistoryActivityVM"