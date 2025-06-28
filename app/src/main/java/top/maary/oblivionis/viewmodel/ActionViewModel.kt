package top.maary.oblivionis.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.IntentSender
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import top.maary.oblivionis.OblivionisApplication
import top.maary.oblivionis.data.Album
import top.maary.oblivionis.data.ImageRepository
import top.maary.oblivionis.data.MediaStoreImage
import top.maary.oblivionis.data.PreferenceRepository
import top.maary.oblivionis.ui.ActionUiState
import java.io.File
import java.util.Calendar
import java.util.Stack

class ActionViewModel(
    application: Application,
    private val imageRepository: ImageRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ActionUiState())
    val uiState: StateFlow<ActionUiState> = _uiState.asStateFlow()

    private val lastMarkedStacksByAlbum = mutableMapOf<String, Stack<MediaStoreImage>>()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> get() = _albums

    private var pendingDeleteImage: MediaStoreImage? = null
    private val _permissionNeededForDelete = MutableLiveData<IntentSender?>()

    private val _pendingDeleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val pendingDeleteIntentSender: Flow<IntentSender?> = _pendingDeleteIntentSender

    init {
        loadAlbums()
    }

    fun loadAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            val albumList = getAlbumsFromMediaStore(getApplication<Application>().contentResolver)
            _albums.value = albumList
        }
    }

    private var imageLoadingJob: Job? = null
    fun loadImages(albumPath: String) {
        imageLoadingJob?.cancel()
        // 在开始加载时立即更新UI状态为加载中
        _uiState.update {
            it.copy(
                isLoading = true,
                albumTitle = albumPath.substringAfterLast("/")
            )
        }

        imageLoadingJob = viewModelScope.launch {
            imageRepository.getImagesStream(albumPath)
                .catch {
                    _uiState.update { it.copy(isLoading = false) }
                }
                .collect { images ->
                    // 当新相册的图片列表加载完成后...
                    _uiState.update { currentState ->
                        // ...检查这个新相册是否有自己的标记历史
                        val stack = lastMarkedStacksByAlbum[albumPath]
                        val lastMarkedInThisAlbum = if (!stack.isNullOrEmpty()) stack.peek() else null

                        // 更新UI状态，同时传入正确的 lastMarkedImage
                        currentState.copy(
                            isLoading = false,
                            allImages = images,
                            lastMarkedImage = lastMarkedInThisAlbum
                        )
                    }
                }
        }
    }

    fun deleteMarkedImagesAndRescheduleNotification(
        dataStore: PreferenceRepository,
        notificationViewModel: NotificationViewModel
    ) {
        val imagesToDelete = _uiState.value.markedImages
        viewModelScope.launch {
            // 先执行删除
            performDeleteImageList(imagesToDelete)

            // 然后检查是否需要重新调度通知（这里是异步挂起，不会阻塞）
            if (!dataStore.intervalStartFixed.first()) {
                val calendar = Calendar.getInstance()
                val notificationTime = dataStore.notificationTime.first()
                val timeParts = notificationTime.split(":").map { it.toInt() }
                dataStore.setIntervalStart(calendar.get(Calendar.DAY_OF_MONTH))
                notificationViewModel.scheduleNotification(
                    date = calendar.get(Calendar.DAY_OF_MONTH),
                    hour = timeParts[0],
                    minute = timeParts[1],
                    interval = dataStore.notificationInterval.first().toLong()
                )
            }
        }
    }

    fun onDeletionCompleted() {
        val deletedImages = _uiState.value.markedImages
        // 如果没有删除任何图片，则直接返回
        if (deletedImages.isEmpty()) {
            _pendingDeleteIntentSender.value = null
            return
        }

        // 从被删除的图片中获取相册路径
        val albumPath = deletedImages.first().album

        viewModelScope.launch(Dispatchers.IO) {
            // 1. 从数据库中移除这些图片的记录
            deletedImages.forEach { image ->
                imageRepository.unmark(image)
            }

            // 2. 清空对应相册的标记历史栈
            lastMarkedStacksByAlbum.remove(albumPath)

            // 3. 清理待处理的删除请求
            _pendingDeleteIntentSender.value = null

            // 4. 重新加载相册列表以更新计数
            loadAlbums()

            // 5. 更新UI状态，清除 lastMarkedImage
            _uiState.update { it.copy(lastMarkedImage = null) }
        }
    }

    fun markImage(image: MediaStoreImage) {
        viewModelScope.launch {
            val imageToMark = image.copy(isMarked = true)
            imageRepository.mark(imageToMark)
            // 从 Map 中获取或创建当前相册的 Stack，然后将图片压栈
            val stack = lastMarkedStacksByAlbum.getOrPut(image.album) { Stack() }
            stack.push(imageToMark)

            // 更新 UI state，让恢复按钮可以立即显示
            _uiState.update { it.copy(lastMarkedImage = imageToMark) }
        }
    }

    fun markAllImages() {
        viewModelScope.launch {
            val imagesToMark = _uiState.value.unmarkedImages.filterNot { it.isExcluded }
            if (imagesToMark.isEmpty()) return@launch

            // 获取当前相册路径
            val albumPath = imagesToMark.first().album

            // 获取或创建当前相册的标记历史栈
            val stack = lastMarkedStacksByAlbum.getOrPut(albumPath) { Stack() }

            // 批量标记并压入历史栈
            imagesToMark.forEach { image ->
                val imageToMark = image.copy(isMarked = true)
                imageRepository.mark(imageToMark)
                stack.push(imageToMark)
            }

            // 更新UI状态，lastMarkedImage 为这批操作的最后一张
            _uiState.update { it.copy(lastMarkedImage = imagesToMark.lastOrNull()) }
        }
    }

    fun unMarkImage(target: MediaStoreImage) {
        viewModelScope.launch {
            // 在数据库中，它已经是isMarked=true，我们只需更新它
            imageRepository.unmark(target)
        }
    }

    fun unMarkLastImage() {
        // 从当前的 uiState 中获取最后标记的图片，它的相册路径告诉我们应该操作哪个 Stack
        val lastImage = _uiState.value.lastMarkedImage ?: return
        val albumPath = lastImage.album

        val stack = lastMarkedStacksByAlbum[albumPath]
        if (!stack.isNullOrEmpty()) {
            val imageToUnmark = stack.pop() // 从正确的栈中弹出
            viewModelScope.launch {
                imageRepository.unmark(imageToUnmark)

                // 更新 uiState，其 lastMarkedImage 应为当前相册栈的新栈顶元素
                val newLastMarked = if (stack.isNotEmpty()) stack.peek() else null

                _uiState.update {
                    it.copy(lastMarkedImage = newLastMarked)
                }
            }
        }
    }

    fun excludeMedia(image: MediaStoreImage) {
        viewModelScope.launch {
            imageRepository.mark(image.copy(isExcluded = true))
        }
    }

    fun includeMedia(image: MediaStoreImage) {
        viewModelScope.launch {
            imageRepository.unmark(image.copy(isExcluded = true))
        }
    }

    private fun deleteImage(image: MediaStoreImage) {
        viewModelScope.launch {
            performDeleteImage(image)
        }
    }

    // Function to fetch album names and paths from MediaStore for both images and videos
    private fun getAlbumsFromMediaStore(contentResolver: ContentResolver): List<Album> {
        val albumMap = mutableMapOf<String, Album>()

        // Query both Images and Videos
        val uriList = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, // Images URI
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI   // Videos URI
        )

        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        for (uri in uriList) {
            val cursor: Cursor? = contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val nameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn) ?: ""
                    val path = File(cursor.getString(pathColumn)).parent!!.replace(
                        "/storage/emulated/0/",
                        ""
                    ).replace("/storage/emulated/0", "")
                    // Check if this album (BUCKET_ID) is already in the map
                    val album = albumMap[path]
                    if (album == null) {
                        // Add a new album with an initial count of 1
                        albumMap[path] = Album(name, path, 1)
                    } else {
                        // Increment the media count for this album
                        albumMap[path] = album.copy(mediaCount = album.mediaCount + 1)
                    }
                }
            }
        }

        return albumMap.values.toList()
    }

    private suspend fun performDeleteImage(image: MediaStoreImage) {

        withContext(Dispatchers.IO) {
            try {
                val deleteRequest = MediaStore.createDeleteRequest(
                    getApplication<Application>().contentResolver,
                    arrayListOf(image.contentUri)
                )
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

    @Suppress("UNCHECKED_CAST")
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[APPLICATION_KEY])

                return ActionViewModel(
                    application = application,
                    imageRepository = (application as OblivionisApplication).repository
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