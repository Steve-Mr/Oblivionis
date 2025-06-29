package top.maary.oblivionis.ui

import top.maary.oblivionis.data.MediaStoreImage

// 状态类现在变得非常简单
data class ActionUiState(
    val isLoading: Boolean = true,
    val albumTitle: String = "",
    val lastMarkedImage: MediaStoreImage? = null, // 仍然需要这个来驱动“撤销”按钮的UI
    val totalImageCount: Int = 0,
    val markedImageCount: Int = 0,
    val displayableImageCount: Int = 0
)