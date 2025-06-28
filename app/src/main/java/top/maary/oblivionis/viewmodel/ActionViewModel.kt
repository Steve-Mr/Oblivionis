package top.maary.oblivionis.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    // --- 状态管理 ---
    private val _uiState = MutableStateFlow(ActionUiState())
    val uiState: StateFlow<ActionUiState> = _uiState.asStateFlow()

    // 这个Flow现在专门为RecycleScreen服务，因为它需要一个完整的、非分页的列表
    lateinit var markedImagesFlow: Flow<List<MediaStoreImage>>
        private set

    // 新增：持有PagingData流的属性，这是ActionScreen的主要数据源
    var imagePagingDataFlow: Flow<PagingData<MediaStoreImage>> = emptyFlow()
        private set

    // --- 业务逻辑状态 ---
    private val undoManager = UndoManager()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> get() = _albums

    private var currentAlbumPath: String? = null

    // --- 删除流程状态 ---
    private var pendingDeleteImage: MediaStoreImage? = null
    private val _permissionNeededForDelete = MutableLiveData<IntentSender?>()
    private val _pendingDeleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val pendingDeleteIntentSender: Flow<IntentSender?> = _pendingDeleteIntentSender

    init {
        loadAlbums()

        undoManager.lastMarkedImage
            .onEach { newLastMarked ->
                _uiState.update { it.copy(lastMarkedImage = newLastMarked) }
            }
            .launchIn(viewModelScope) // 在 viewModelScope 中启动这个监听
    }

    /**
     * 加载所有相册的列表。
     */
    fun loadAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            _albums.value = getAlbumsFromMediaStore(getApplication<Application>().contentResolver)
        }
    }

    private var countJob: Job? = null

    /**
     * 为指定的相册加载图片数据。
     * 这个方法会初始化两个数据流：一个用于ActionScreen的分页流，一个用于RecycleScreen的完整标记列表流。
     * @param albumPath 要加载的相册路径。
     */
    fun loadImages(albumPath: String) {
        this.currentAlbumPath = albumPath
        undoManager.syncStateForAlbum(albumPath)
        // 1. 更新UI状态中的相册标题
        _uiState.update {
            it.copy(
                albumTitle = albumPath.substringAfterLast("/"),
                totalImageCount = 0) }

        // 2. 为RecycleScreen准备数据流
        markedImagesFlow = imageRepository.getMarkedImagesStream(albumPath)

        // 3. 创建并缓存PagingData流，供ActionScreen使用
        imagePagingDataFlow = imageRepository.getImagePagingData(albumPath)
            .cachedIn(viewModelScope) // 关键！缓存数据以支持屏幕旋转等场景

        countJob?.cancel()

        countJob = viewModelScope.launch {
            val totalCount = imageRepository.getAlbumTotalCount(albumPath)
            // 获取到总数后，更新 UI 状态
            _uiState.update { it.copy(totalImageCount = totalCount) }
        }
    }

    // --- 图片操作 ---

    fun markImage(image: MediaStoreImage) {
        viewModelScope.launch {
            val imageToMark = image.copy(isMarked = true)
            imageRepository.mark(imageToMark)

            // 将标记的图片压入对应相册的“历史记录”栈
            undoManager.push(imageToMark)

            // 更新UI状态，让“撤销”按钮可以立即显示
            _uiState.update { it.copy(lastMarkedImage = imageToMark) }
        }
    }

    fun markAllImages() {
        // 1. 确保我们知道当前是哪个相册，如果不知道则不执行任何操作。
        val albumToMark = currentAlbumPath ?: return

        viewModelScope.launch {
            // 2. 调用 Repository 的批量更新方法。
            imageRepository.markAllInAlbum(albumToMark)

            // 3. 这是一个不可撤销的大规模操作，因此需要清空当前相册的“撤销”历史。
            undoManager.clearHistoryForCurrentAlbum()

            // 4. 更新UI状态，清除 lastMarkedImage，让“撤销”按钮消失。
            _uiState.update { it.copy(lastMarkedImage = null) }
        }
    }

    fun unMarkImage(target: MediaStoreImage) {
        viewModelScope.launch {
            // 直接调用 repository 的 unmark 方法，它会处理数据库的更新。
            // 由于 markedImagesFlow 是一个响应式数据流，
            // 数据库更新后，UI 会自动收到新的列表并刷新。
            imageRepository.unmark(target)
        }
    }

    fun unMarkLastImage() {
        // 从 UndoManager 获取要撤销的图片
        val imageToUnmark = undoManager.pop() ?: return

        viewModelScope.launch {
            // 用获取到的图片更新数据库
            imageRepository.unmark(imageToUnmark)
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

    // --- 删除逻辑 ---

    fun deleteMarkedImagesAndRescheduleNotification(
        dataStore: PreferenceRepository,
        notificationViewModel: NotificationViewModel
    ) {
        viewModelScope.launch {
            // 从专用的数据流中获取当前的标记列表
            val imagesToDelete = markedImagesFlow.first()
            if (imagesToDelete.isEmpty()) return@launch

            // 执行删除
            performDeleteImageList(imagesToDelete)

            // 检查是否需要重新调度通知
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
        // 由于无法直接从PagingData获取列表，我们在删除后不直接操作状态，
        // 而是依赖PagingSource的刷新机制。
        // 主要任务是清理待处理的删除请求和更新相册计数。
        viewModelScope.launch {
            _pendingDeleteIntentSender.value = null
            loadAlbums() // 重新加载相册以更新计数
        }
    }

    private suspend fun performDeleteImageList(images: List<MediaStoreImage>) {
        if (images.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                val deleteRequest = MediaStore.createDeleteRequest(
                    getApplication<Application>().contentResolver,
                    images.map { it.contentUri }
                )
                _pendingDeleteIntentSender.value = deleteRequest.intentSender
            } catch (securityException: SecurityException) {
                val recoverableSecurityException =
                    securityException as? RecoverableSecurityException
                        ?: throw securityException
                pendingDeleteImage = images.first()
                _permissionNeededForDelete.postValue(
                    recoverableSecurityException.userAction.actionIntent.intentSender
                )
            }
        }
    }

    // --- 辅助方法 ---

    private fun getAlbumsFromMediaStore(contentResolver: ContentResolver): List<Album> {
        val albumMap = mutableMapOf<String, Album>()
        val uriList = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        for (uri in uriList) {
            contentResolver.query(uri, projection, null, null, sortOrder)
                ?.use { cursor ->
                    val nameColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn) ?: ""
                        val path = File(cursor.getString(pathColumn)).parent?.replace(
                            "/storage/emulated/0/", ""
                        )?.replace("/storage/emulated/0", "") ?: continue

                        val album = albumMap[path]
                        if (album == null) {
                            albumMap[path] = Album(name, path, 1)
                        } else {
                            albumMap[path] = album.copy(mediaCount = album.mediaCount + 1)
                        }
                    }
                }
        }
        return albumMap.values.toList()
    }

    // --- ViewModel Factory ---
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) as OblivionisApplication
                return ActionViewModel(
                    application,
                    application.repository
                ) as T
            }
        }
    }
}