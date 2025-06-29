package top.maary.oblivionis.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.maary.oblivionis.OblivionisApplication
import top.maary.oblivionis.data.Album
import top.maary.oblivionis.data.ImageRepository
import top.maary.oblivionis.data.MediaEntity
import top.maary.oblivionis.data.PreferenceRepository
import top.maary.oblivionis.ui.ActionUiState
import java.util.Calendar

class ActionViewModel(
    application: Application,
    private val imageRepository: ImageRepository
) : AndroidViewModel(application) {

    // --- 状态管理 ---
    private val _uiState = MutableStateFlow(ActionUiState())
    val uiState: StateFlow<ActionUiState> = _uiState.asStateFlow()

    // 新增：持有PagingData流的属性，这是ActionScreen的主要数据源
    var imagePagingDataFlow: Flow<PagingData<MediaEntity>> = emptyFlow()
        private set

    // 这个Flow现在专门为RecycleScreen服务，因为它需要一个完整的、非分页的列表
    var markedImagePagingDataFlow: Flow<PagingData<MediaEntity>> = emptyFlow()
        private set

    private var imagesPendingDeletion: List<MediaEntity>? = null

    // --- 业务逻辑状态 ---
    private val undoManager = UndoManager()

    val albums: StateFlow<List<Album>> = imageRepository.getAlbumListStream()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // 5秒后停止共享，节省资源
            initialValue = emptyList() // 提供一个初始空列表
        )

    private var currentAlbumPath: String? = null

    // --- 删除流程状态 ---
    private var pendingDeleteImage: MediaEntity? = null
    private val _permissionNeededForDelete = MutableLiveData<IntentSender?>()
    private val _pendingDeleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val pendingDeleteIntentSender: Flow<IntentSender?> = _pendingDeleteIntentSender

    init {
        undoManager.lastMarkedImage
            .onEach { newLastMarked ->
                _uiState.update { it.copy(lastMarkedImage = newLastMarked) }
            }
            .launchIn(viewModelScope) // 在 viewModelScope 中启动这个监听
    }
    
    private var aggregateDataJob: Job? = null
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
            it.copy(albumTitle = albumPath.substringAfterLast("/"))
        }

        // 3. 创建并缓存PagingData流，供ActionScreen使用
        imagePagingDataFlow = imageRepository.getImagePagingData(albumPath)
            .cachedIn(viewModelScope) // 关键！缓存数据以支持屏幕旋转等场景

        markedImagePagingDataFlow = imageRepository.getMarkedImagePagingData(albumPath)
            .cachedIn(viewModelScope) // 同样进行缓存

        aggregateDataJob?.cancel()
        aggregateDataJob = viewModelScope.launch {
            // 1. 定义两个上游数据流
            val totalCountFlow = imageRepository.getAlbumTotalCountStream(albumPath)
            val markedCountFlow = imageRepository.getMarkedCountStream(albumPath)

            // 2. 使用 combine 将它们合并
            combine(totalCountFlow, markedCountFlow) { totalCount, markedCount ->
                // 3. 在 combine 块内部，计算出所有需要的状态值
                val displayableCount = totalCount - markedCount
                // 4. 将计算结果包装成一个 Pair 或自定义数据类，以便一次性发送
                Pair(markedCount, displayableCount)
            }.onEach { (markedCount, displayableCount) ->
                // 5. 在最终的收集器中，用一次 update 更新所有相关的UI状态
                _uiState.update { currentState ->
                    currentState.copy(
                        markedImageCount = markedCount,
                        displayableImageCount = displayableCount
                    )
                }
            }.launchIn(this) // 在当前协程作用域内启动
        }
    }

    // --- 图片操作 ---

    fun markImage(image: MediaEntity) {
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
            undoManager.clearHistoryForCurrentAlbum(albumToMark)

            // 4. 更新UI状态，清除 lastMarkedImage，让“撤销”按钮消失。
            _uiState.update { it.copy(lastMarkedImage = null) }
        }
    }

    fun unmarkAllImages(excludeIds: Set<Long>) {
        val albumToUnmark = currentAlbumPath ?: return
        viewModelScope.launch {
            imageRepository.unmarkAllInAlbum(albumToUnmark, excludeIds)
            // 这是一个大规模恢复操作，也应该清空当前相册的“撤销”历史
            undoManager.clearHistoryForCurrentAlbum(albumToUnmark)
        }
    }

    fun unMarkImage(target: MediaEntity) {
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

    fun excludeMedia(image: MediaEntity) {
        viewModelScope.launch {
            imageRepository.mark(image.copy(isExcluded = true))
        }
    }

    fun includeMedia(image: MediaEntity) {
        viewModelScope.launch {
            imageRepository.unmark(image.copy(isExcluded = true))
        }
    }

    // --- 删除逻辑 ---

    fun deleteMarkedImagesAndRescheduleNotification(
        dataStore: PreferenceRepository,
        notificationViewModel: NotificationViewModel
    ) {
        // 1. 确保我们知道当前是哪个相册
        val albumToDeleteFrom = currentAlbumPath ?: return

        viewModelScope.launch {
            // 2. 调用专用的 Repository 方法，一次性获取所有待删除的图片
            val imagesToDelete = imageRepository.getMarkedInAlbumOnce(albumToDeleteFrom)
            if (imagesToDelete.isEmpty()) return@launch

            imagesPendingDeletion = imagesToDelete

            // 3. 执行删除（这部分逻辑不变）
            performDeleteImageList(imagesToDelete)

            // 4. 检查并重新调度通知（这部分逻辑不变）
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
        // 1. 获取刚刚被删除的图片列表
        val deletedImages = imagesPendingDeletion ?: return
        if (deletedImages.isEmpty()) return

        viewModelScope.launch {
            // 2. 从 Room 数据库中移除这些图片的记录
            imageRepository.deleteImagesByIds(deletedImages.map { it.id }.toSet())

            // 3. 清理暂存的列表和待处理的删除请求
            imagesPendingDeletion = null
            _pendingDeleteIntentSender.value = null

            // 5. 这是一个大规模操作，也应该清空当前相册的“撤销”历史
            deletedImages.firstOrNull()?.album?.let { albumPath ->
                undoManager.clearHistoryForCurrentAlbum(albumPath)
            }
        }
    }

    private suspend fun performDeleteImageList(images: List<MediaEntity>) {
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