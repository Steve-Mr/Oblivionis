package com.example.ydaynomore.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.ydaynomore.YNMApplication
import com.example.ydaynomore.data.ImageRepository
import com.example.ydaynomore.data.MediaStoreImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch


/* TODO: database only use for persistence not compose */
class RecycleViewModel(private val imageRepository: ImageRepository): ViewModel() {
    val allMarked: Flow<List<MediaStoreImage>> = imageRepository.allMarks

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
                    (application as YNMApplication).repository
                ) as T
            }
        }
    }
}
