package com.example.ydaynomore.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.ydaynomore.YNMApplication
import com.example.ydaynomore.data.ImageRepository
import com.example.ydaynomore.data.MediaStoreImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit

class ActionViewModel(
    application: Application,
    private val imageRepository: ImageRepository): AndroidViewModel(application) {
    private val _images = MutableStateFlow<List<MediaStoreImage>>(emptyList())

    val markedImages : Flow<List<MediaStoreImage>> = _images.map { images ->
        images.filter { it.isMarked }
    }
    val unmarkedImages : Flow<List<MediaStoreImage>> = _images.map { images ->
        images.filter { !it.isMarked }
    }

    private val _lastMarked = MutableStateFlow<MediaStoreImage?>(null)
    val lastMarked = _lastMarked.asStateFlow()

    private var contentObserver: ContentObserver? = null

    private var pendingDeleteImage: MediaStoreImage? = null
    private val _permissionNeededForDelete = MutableLiveData<IntentSender?>()
    val permissionNeededForDelete: LiveData<IntentSender?> = _permissionNeededForDelete

    private val _pendingDeleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val pendingDeleteIntentSender: Flow<IntentSender?> = _pendingDeleteIntentSender


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
            performDeleteImageList(images)
            clearImages()
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
        databaseMark(target)

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

        val markedImageList = _images.value.filter {
            it.isMarked
        }

        Log.v("YDNM", "MARKED ${markedImageList.size}")

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
                databaseUnmark(img)

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

    fun unMarkImage(target: MediaStoreImage) {
        val updatedList = _images.value.toMutableList()
        updatedList[updatedList.indexOf(target.copy(isMarked = true))] = target.copy(isMarked = false)
        databaseUnmark(target)
        if (target == _lastMarked.value) {
            _lastMarked.value = null
        }
        _images.value = updatedList
    }

    fun clearImages() {
        _images.update { currentList ->
            currentList.filter { !it.isMarked }
        }
        databaseRemoveAll()
    }

    fun restoreMarkList(listToMark: List<MediaStoreImage>) {
        val updatedList = _images.value.toMutableList()

        updatedList.forEach { item ->
            updatedList[updatedList.indexOf(item)] = item.copy(isMarked = item in listToMark)
            Log.v("YDNM", "ITEM IS ${item in listToMark}")
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
        Log.v("YDNM", "PERFORM DELETION")

        withContext(Dispatchers.IO) {
            try {
                val deleteRequest = MediaStore.createDeleteRequest(getApplication<Application>().contentResolver, arrayListOf(image.contentUri))
                _pendingDeleteIntentSender.value = deleteRequest.intentSender
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
    private suspend fun performDeleteImageList(images: List<MediaStoreImage>) {
        Log.v("YDNM", "PERFORM DELETION")

        withContext(Dispatchers.IO) {
            try {
                val deleteRequest = MediaStore.createDeleteRequest(
                    getApplication<Application>().contentResolver,
                    images.map { it.contentUri })
                _pendingDeleteIntentSender.value = deleteRequest.intentSender
            } catch (securityException: SecurityException) {
                val recoverableSecurityException =
                    securityException as? RecoverableSecurityException
                        ?: throw securityException

                // Signal to the Activity that it needs to request permission and
                // try the delete again if it succeeds.
                pendingDeleteImage = images.first()
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
        viewModelScope.launch {
            val databaseMarks = imageRepository.allMarks?.firstOrNull()
            if (!databaseMarks.isNullOrEmpty()) {
                restoreMarkList(databaseMarks)
            }
        }
        Log.v("YDNM", "VIEWMODEL INIT")
    }

    fun databaseMark(image: MediaStoreImage) = viewModelScope.launch {
        imageRepository.mark(image.copy(isMarked = false))
    }

    fun databaseUnmark(image: MediaStoreImage) = viewModelScope.launch {
        imageRepository.unmark(image.copy(isMarked = false))
    }

    fun databaseRemoveAll() = viewModelScope.launch {
        imageRepository.removeAll()
    }

    fun removeId(id: Long) = viewModelScope.launch {
        imageRepository.removeId(id)
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        val Factory : ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[APPLICATION_KEY])

                return ActionViewModel(
                    application = application,
                    imageRepository = (application as YNMApplication).repository
                ) as T
            }
        }
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