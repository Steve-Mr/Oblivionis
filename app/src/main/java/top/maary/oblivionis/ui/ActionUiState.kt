package top.maary.oblivionis.ui

import top.maary.oblivionis.data.MediaStoreImage

data class ActionUiState(
    val isLoading: Boolean = true,
    val albumTitle: String = "",
    val allImages: List<MediaStoreImage> = emptyList(), // 单一数据源，来自 Repository
    val lastMarkedImage: MediaStoreImage? = null // 用于恢复操作
) {
    // 计算属性，方便 UI 直接使用
    val unmarkedImages: List<MediaStoreImage>
        get() = allImages.filter { !it.isMarked }

    val markedImages: List<MediaStoreImage>
        get() = allImages.filter { it.isMarked }
}