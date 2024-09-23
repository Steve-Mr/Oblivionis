package top.maary.oblivionis.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import top.maary.oblivionis.OblivionisApplication
import top.maary.oblivionis.data.ImageRepository
import top.maary.oblivionis.data.MediaStoreImage
import kotlinx.coroutines.launch


class RecycleViewModel(private val imageRepository: ImageRepository): ViewModel() {
//    val allMarked: List<MediaStoreImage> = imageRepository.allMarks

    fun mark(image: MediaStoreImage) = viewModelScope.launch {
        imageRepository.mark(image)
    }

    fun unMark(image: MediaStoreImage) = viewModelScope.launch {
        imageRepository.unmark(image)
    }

    fun removeAll() = viewModelScope.launch {
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
                extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])

                return RecycleViewModel(
                    (application as OblivionisApplication).repository
                ) as T
            }
        }
    }
}
