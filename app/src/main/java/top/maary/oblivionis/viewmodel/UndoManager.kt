package top.maary.oblivionis.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.maary.oblivionis.data.MediaStoreImage
import java.util.Stack

/**
 * 封装所有与“撤销标记”相关的状态和逻辑。
 * 这个类是线程不安全的，应仅在 ViewModel 的协程作用域内使用。
 */
class UndoManager {
    // 内部私有的状态：存储每个相册的撤销栈
    private val stacksByAlbum = mutableMapOf<String, Stack<MediaStoreImage>>()

    // 内部私有的、可变的 StateFlow，用于驱动UI
    private val _lastMarkedImage = MutableStateFlow<MediaStoreImage?>(null)

    // 对外只暴露不可变的 StateFlow，供 ViewModel 监听
    val lastMarkedImage: StateFlow<MediaStoreImage?> = _lastMarkedImage.asStateFlow()

    // 当前正在操作的相册路径
    private var currentAlbumPath: String? = null

    /**
     * 当用户标记一张图片时调用。
     * @param image 被标记的图片。
     */
    fun push(image: MediaStoreImage) {
        val stack = stacksByAlbum.getOrPut(image.album) { Stack() }
        stack.push(image)
        // 只有当操作发生在当前相册时，才更新UI状态
        if (image.album == currentAlbumPath) {
            _lastMarkedImage.value = image
        }
    }

    /**
     * 当用户点击“撤销”时调用。
     * @return 返回被撤销的图片，ViewModel 需要用它来更新数据库。
     */
    fun pop(): MediaStoreImage? {
        val stack = stacksByAlbum[currentAlbumPath]
        if (stack == null || stack.isEmpty()) {
            return null
        }
        val poppedImage = stack.pop()
        // 更新UI状态为新的栈顶元素
        _lastMarkedImage.value = if (stack.isNotEmpty()) stack.peek() else null
        return poppedImage
    }

    /**
     * 当用户切换相册时调用。
     * @param albumPath 新的相册路径。
     */
    fun syncStateForAlbum(albumPath: String) {
        currentAlbumPath = albumPath
        // 更新UI状态以反映新相册的撤销栈状态
        _lastMarkedImage.value = stacksByAlbum[albumPath]?.peek()
    }

    /**
     * 当执行“全部标记”这种不可撤销的操作时调用。
     */
    fun clearHistoryForCurrentAlbum() {
        currentAlbumPath?.let {
            stacksByAlbum.remove(it)
            _lastMarkedImage.value = null
        }
    }
}