package com.example.cameraflow.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cameraflow.data.MediaRepository
import com.example.cameraflow.model.MediaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val mediaRepository = MediaRepository(application.applicationContext)
    
    private val _mediaList = MutableLiveData<List<MediaModel>>(emptyList())
    val mediaList: LiveData<List<MediaModel>> = _mediaList
    
    private val _currentPosition = MutableLiveData(0)
    val currentPosition: LiveData<Int> = _currentPosition
    
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadMediaFiles(sortDescending: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val media = withContext(Dispatchers.IO) {
                    mediaRepository.getAll()
                }
                
                val sorted = if (sortDescending) {
                    media.sortedByDescending { it.dateAdded }
                } else {
                    media.sortedBy { it.dateAdded }
                }
                
                _mediaList.value = sorted
                
                if (sorted.isEmpty()) {
                    _currentPosition.value = 0
                } else {
                    _currentPosition.value = _currentPosition.value?.coerceIn(0, sorted.lastIndex) ?: 0
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setMediaList(list: List<MediaModel>) {
        _mediaList.value = list
        if (list.isEmpty()) {
            _currentPosition.value = 0
        } else {
            _currentPosition.value = _currentPosition.value?.coerceIn(0, list.lastIndex) ?: 0
        }
    }

    fun setCurrentPosition(position: Int) {
        val list = _mediaList.value ?: return
        if (position in list.indices) {
            _currentPosition.value = position
        }
    }

    fun deleteMedia(uri: android.net.Uri): Pair<Boolean, Boolean> {
        val (success, isRecoverableSecurityException) = mediaRepository.delete(uri)
        if (success) {
            val list = _mediaList.value?.toMutableList() ?: return Pair(false, false)
            val itemToRemove = list.find { it.uri == uri }
            itemToRemove?.let {
                val deletedPosition = list.indexOf(it)
                val currentPos = _currentPosition.value ?: 0
                list.removeAt(deletedPosition)
                _mediaList.value = list

                val newPosition = when {
                    list.isEmpty() -> 0
                    deletedPosition < currentPos -> currentPos - 1
                    deletedPosition == currentPos -> {
                        if (currentPos >= list.size) list.lastIndex else currentPos
                    }
                    else -> currentPos
                }
                _currentPosition.value = newPosition
            }
        }
        return Pair(success, isRecoverableSecurityException)
    }

    fun removeItem(position: Int) {
        val list = _mediaList.value?.toMutableList() ?: return
        if (position !in list.indices) return

        list.removeAt(position)
        _mediaList.value = list

        val newPosition = when {
            list.isEmpty() -> 0
            position >= list.size -> list.lastIndex
            else -> position
        }
        _currentPosition.value = newPosition
    }
}