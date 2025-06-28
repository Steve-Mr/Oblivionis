package top.maary.oblivionis.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import top.maary.oblivionis.OblivionisApplication
import top.maary.oblivionis.data.Album
import top.maary.oblivionis.data.ImageRepository
import top.maary.oblivionis.data.MediaStoreImage
import top.maary.oblivionis.data.PreferenceRepository
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

data class ActionUiState(
    val allImages: List<MediaStoreImage> = emptyList(), // 单一数据源
    val allLastMarked: List<MediaStoreImage?> = emptyList(),
    val albumTitle: String = "",
    val isLoading: Boolean = true,
) {
    // 将 unmarkedImages 和 markedImages 作为计算属性
    val unmarkedImages: List<MediaStoreImage>
        get() = allImages.filter { !it.isMarked }

    val markedImages: List<MediaStoreImage>
        get() = allImages.filter { it.isMarked }

    val lastMarked: List<MediaStoreImage?>
        get() = allLastMarked.filter {it?.album == allImages.firstOrNull()?.album}
}

class ActionViewModel(
    application: Application,
    private val imageRepository: ImageRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ActionUiState())
    val uiState: StateFlow<ActionUiState> = _uiState.asStateFlow()

    var albumPath: String? = null

    private var contentObserver: ContentObserver? = null
    private var videoContentObserver: ContentObserver? = null


    private var pendingDeleteImage: MediaStoreImage? = null
    private val _permissionNeededForDelete = MutableLiveData<IntentSender?>()

    private val _pendingDeleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val pendingDeleteIntentSender: Flow<IntentSender?> = _pendingDeleteIntentSender


    /**
     * Performs a one shot load of images from [MediaStore.Images.Media.EXTERNAL_CONTENT_URI] into
     * the [_images] [LiveData] above.
     */
    fun loadImages(album: String) {
        viewModelScope.launch {
            // 设置 albumPath
            _uiState.update { it.copy(isLoading = true, albumTitle = album.substringAfterLast("/")) }
            albumPath = album

            val allImages = queryImages() // 从 MediaStore 加载
            val markedFromDb = imageRepository.getMarkedInAlbum(album)?.firstOrNull() ?: emptyList()
            val excludedFromDb = imageRepository.getExcludedInAlbum(album)?.firstOrNull() ?: emptyList()

            val finalAllImages = allImages.map { image ->
                image.copy(
                    isMarked = markedFromDb.any { it.id == image.id },
                    isExcluded = excludedFromDb.any { it.id == image.id }
                )
            }.sortedByDescending { it.dateAdded } // 确保初始加载时是排序好的

            _uiState.update {
                it.copy(
                    allImages = finalAllImages,
                    isLoading = false
                )
            }
            // 5. 注册内容观察者
            registerContentObserverIfNeeded()
            registerVideoContentObserverIfNeeded()
        }
    }

    private fun restoreData(databaseMarks: List<MediaStoreImage>?, databaseExclusions: List<MediaStoreImage>?) {
        databaseMarks?.let { restoreMarkList(it) }
        databaseExclusions?.let { restoreExcluded(it) }
    }

    private fun registerContentObserverIfNeeded() {
        if (contentObserver == null) {
            contentObserver = getApplication<Application>().contentResolver.registerObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ) {
                reloadContentIfNeeded()
            }
        }
    }

    private fun registerVideoContentObserverIfNeeded() {
        if (videoContentObserver == null) {
            videoContentObserver = getApplication<Application>().contentResolver.registerObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ) {
                reloadContentIfNeeded()
            }
        }
    }

    private val reloadMutex = Mutex()

    private fun reloadContentIfNeeded() {
        viewModelScope.launch {
            // 尝试获取锁，如果已经被锁定，则直接返回
            if (reloadMutex.tryLock()) {
                try {
                    reloadContent()
                } finally {
                    // 确保在任务完成后释放锁
                    reloadMutex.unlock()
                }
            }
        }
    }

    private fun reloadContent() {
        loadImages(albumPath ?: return)
        loadAlbums()
        viewModelScope.launch {
            val databaseMarks = imageRepository.getMarkedInAlbum(albumPath!!)?.firstOrNull()
            val databaseExclusions = imageRepository.getExcludedInAlbum(albumPath!!)?.firstOrNull()
            restoreData(databaseMarks, databaseExclusions)
        }
    }

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> get() = _albums

    fun loadAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            val albumList = getAlbumsFromMediaStore(getApplication<Application>().contentResolver)
            _albums.value = albumList
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

    private fun deleteImage(image: MediaStoreImage) {
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

    fun deleteMarkedImagesAndRescheduleNotification(
        images: List<MediaStoreImage>,
        dataStore: PreferenceRepository,
        notificationViewModel: NotificationViewModel
    ) {
        viewModelScope.launch {
            // 先执行删除
            performDeleteImageList(images)
            clearImages()
            loadAlbums()

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

    fun markImage(index: Int) {

        val target = _uiState.value.unmarkedImages.getOrNull(index) ?: return

        databaseMark(target.copy(isMarked = true))

        _uiState.update { currentState ->
            val newAllImages = currentState.allImages.map {
                if (it.id == target.id) {
                    it.copy(isMarked = true)
                } else {
                    it
                }
            }
            val newLastMarked = currentState.allLastMarked.toMutableList().apply { add(target.copy(isMarked = true)) }

            currentState.copy(
                allImages = newAllImages,
                allLastMarked = newLastMarked
            )
        }
    }

    fun excludeMedia(index: Int) {

        val target = _uiState.value.unmarkedImages.getOrNull(index) ?: return

        // 获取要标记的图片
        databaseMark(target.copy(isExcluded = true))

        _uiState.update { currentState ->
            // 创建一个新的未标记图片列表，更改目标图片的 isExcluded 状态
            val newAllImages = currentState.allImages.map {
                if (it.id == target.id) {
                    it.copy(isExcluded = true)
                } else {
                    it
                }
            }
            currentState.copy(
                allImages = newAllImages,
            )
        }
    }
    fun unMarkLastImage(): Long? {
        // 创建一个更新后的 _images 列表
        val img = _uiState.value.allLastMarked.lastOrNull() ?: return null
        databaseUnmark(img)

        _uiState.update { currentState ->
            val newAllImages = currentState.allImages.map {
                if (it.id == img.id) it.copy(isMarked = false) else it
            }
            val newLastMarked = currentState.allLastMarked.dropLast(1)

            currentState.copy(
                allImages = newAllImages,
                allLastMarked = newLastMarked
            )
        }
        return img.id
    }

    fun unMarkImage(target: MediaStoreImage) {
        _uiState.update { currentState ->
            databaseUnmark(target.copy(isMarked = true))
            val newAllImages = currentState.allImages.map {
                if (it.id == target.id) {
                    it.copy(isMarked = false)
                } else {
                    it
                }
            }
            currentState.copy(allImages = newAllImages)
        }
    }

    fun includeMedia(target: MediaStoreImage) {
        databaseUnmark(target.copy(isExcluded = true))
        _uiState.update { currentState ->
            val newAllImages = currentState.allImages.map {
                if (it.id == target.id) {
                    it.copy(isExcluded = false)
                } else {
                    it
                }
            }
            currentState.copy(allImages = newAllImages)
        }
    }

    private fun clearImages() {
        _uiState.update { currentState ->
            databaseRemoveAll(currentState.markedImages) // Remove all marked images from DB
            val newAllImages = currentState.allImages.map {
                if (it.isMarked) {
                    it.copy(isMarked = false)
                } else {
                    it
                }
            }
            currentState.copy(
                allImages = newAllImages,
                allLastMarked = emptyList()
            )
        }
    }
    private fun restoreMarkList(listToMark: List<MediaStoreImage>) {
        _uiState.update { currentState ->
            val newAllImages = currentState.allImages.map { image ->
                if (listToMark.any { markedItem -> markedItem.id == image.id }) {
                    image.copy(isMarked = true)
                } else {
                    image
                }
            }
            currentState.copy(allImages = newAllImages)
        }
    }

    private fun restoreExcluded(list: List<MediaStoreImage>) {
        _uiState.update { currentState ->
            val newAllImages = currentState.allImages.map { image ->
                if (list.any { excludedItem -> excludedItem.id == image.id }) {
                    image.copy(isExcluded = true)
                } else {
                    image
                }
            }
            // Ensure that items in listToMark are also updated in newAllImages
            // This handles cases where an item might be in both listToMark and list (restoreExcluded)
            val finalAllImages = newAllImages.map { image ->
                if (currentState.allLastMarked.any { it?.id == image.id }) { // Assuming lastMarked holds items that should be marked
                    image.copy(isMarked = true)
                } else {
                    image
                }
            }
            currentState.copy(allImages = finalAllImages)
        }
    }

    private suspend fun queryImages(): List<MediaStoreImage> {
        val images = mutableListOf<MediaStoreImage>()

        withContext(Dispatchers.IO) {

            val uriList = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, // Images URI
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI   // Videos URI
            )

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )

            val selection = "${MediaStore.Images.ImageColumns.RELATIVE_PATH} like ?"

            val selectionArgs = arrayOf(
                "${albumPath}/"
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            for (uri in uriList) {
                getApplication<Application>().contentResolver.query(
                    uri,
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

                    while (cursor.moveToNext()) {

                        // Here we'll use the column index that we found above.
                        val id = cursor.getLong(idColumn)
                        val dateModified =
                            Date(TimeUnit.SECONDS.toMillis(cursor.getLong(dateModifiedColumn)))
                        val displayName = cursor.getString(displayNameColumn)

                        val contentUri = ContentUris.withAppendedId(
                            uri,
                            id
                        )

                        val image = MediaStoreImage(id, displayName, albumPath!!,dateModified, contentUri)

                        images += image
                    }
                }
            }
        }

        return images
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

    /**
     * Since we register a [ContentObserver], we want to unregister this when the `ViewModel`
     * is being released.
     */
    override fun onCleared() {
        contentObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
        videoContentObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
    }

    init {
        loadAlbums()
    }

    private fun databaseMark(image: MediaStoreImage) = viewModelScope.launch {
        imageRepository.mark(image)
    }

    private fun databaseUnmark(image: MediaStoreImage) = viewModelScope.launch {
        imageRepository.unmark(image)
    }

    private fun databaseMarkAll(images: List<MediaStoreImage>) = viewModelScope.launch {
        images.forEach {
            if (it.isExcluded) return@forEach
            imageRepository.mark(it.copy(isMarked = true))
        }
    }

    private fun databaseRemoveAll(images: List<MediaStoreImage>) = databaseUnmarkAll(images)
    fun markAllImages() {
        _uiState.update { currentState ->
            val allImagesToMark = currentState.unmarkedImages.filterNot { it.isExcluded }
            databaseMarkAll(images = allImagesToMark) // Database operation

            // Update the allImages list in the UI state
            val newAllImages = currentState.allImages.map { image ->
                if (allImagesToMark.any { it.id == image.id }) {
                    image.copy(isMarked = true)
                } else {
                    image
                }
            }

            currentState.copy(
                allImages = newAllImages
            )
        }
    }

    private fun databaseUnmarkAll(images: List<MediaStoreImage>) = viewModelScope.launch {
        images.forEach {
            imageRepository.unmark(it.copy(isMarked = false))
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