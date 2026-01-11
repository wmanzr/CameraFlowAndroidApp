package com.example.cameraflow.viewmodel

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.camera.video.MediaStoreOutputOptions
import androidx.lifecycle.AndroidViewModel
import androidx.camera.core.CameraSelector
import androidx.camera.core.AspectRatio
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import com.example.cameraflow.data.MediaRepository
import com.example.cameraflow.utils.FormatUtils
import java.util.concurrent.Executor

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val mediaRepository = MediaRepository(application.applicationContext)
    private val _selectedCamera = MutableLiveData<CameraSelector>(CameraSelector.DEFAULT_BACK_CAMERA)
    val selectedCamera: LiveData<CameraSelector> = _selectedCamera
    private val _zoomRatio = MutableLiveData<Float>(1.0f)
    val zoomRatio: LiveData<Float> = _zoomRatio
    private val _isFlashEnabled = MutableLiveData<Boolean>(false)
    val isFlashEnabled: LiveData<Boolean> = _isFlashEnabled
    private val _timerDelaySeconds = MutableLiveData<Int>(0)
    val timerDelaySeconds: LiveData<Int> = _timerDelaySeconds

    private val _aspectRatio = MutableLiveData<Int>(AspectRatio.RATIO_4_3)
    val aspectRatio: LiveData<Int> = _aspectRatio

    private var freezeFrameBitmap: Bitmap? = null

    val currentCameraSelector: CameraSelector
        get() = _selectedCamera.value ?: CameraSelector.DEFAULT_BACK_CAMERA

    fun setCamera(cameraSelector: CameraSelector) {
        _selectedCamera.value = cameraSelector
    }

    fun toggleCamera() {
        val current = _selectedCamera.value ?: CameraSelector.DEFAULT_BACK_CAMERA
        _selectedCamera.value = if (current == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    fun setZoomRatio(ratio: Float) {
        _zoomRatio.value = ratio
    }

    fun setFlashEnabled(enabled: Boolean) {
        _isFlashEnabled.value = enabled
    }

    fun toggleFlash() {
        _isFlashEnabled.value = !(_isFlashEnabled.value ?: false)
    }

    fun setTimerDelay(seconds: Int) {
        _timerDelaySeconds.value = seconds
    }

    fun toggleTimer() {
        val current = _timerDelaySeconds.value ?: 0
        _timerDelaySeconds.value = when (current) {
            0 -> FormatUtils.TIMER_2_SEC
            FormatUtils.TIMER_2_SEC -> FormatUtils.TIMER_5_SEC
            FormatUtils.TIMER_5_SEC -> FormatUtils.TIMER_10_SEC
            FormatUtils.TIMER_10_SEC -> 0
            else -> 0
        }
    }

    fun setAspectRatio(ratio: Int) {
        if (ratio == AspectRatio.RATIO_4_3 || ratio == AspectRatio.RATIO_16_9) {
            _aspectRatio.value = ratio
        }
    }

    fun getAspectRatioValue(): Int {
        return _aspectRatio.value ?: AspectRatio.RATIO_4_3
    }

    fun isFrontCamera(): Boolean {
        return _selectedCamera.value == CameraSelector.DEFAULT_FRONT_CAMERA
    }

    fun preparePhoto(): ContentValues {
        return mediaRepository.preparePhoto()
    }

    fun prepareVideo(): ContentValues {
        return mediaRepository.prepareVideo()
    }

    fun setFreezeFrameBitmap(bitmap: Bitmap?) {
        freezeFrameBitmap = bitmap
    }

    fun getFreezeFrameBitmap(): Bitmap? {
        val bitmap = freezeFrameBitmap
        freezeFrameBitmap = null // Очищаем после использования
        return bitmap
    }

    fun savePhoto(
        imageCapture: ImageCapture,
        executor: Executor,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        mediaRepository.savePhoto(imageCapture, executor, onSuccess, onError)
    }

    @androidx.camera.video.ExperimentalPersistentRecording
    fun createVideoOutputOptions(): MediaStoreOutputOptions {
        return mediaRepository.createVideoOutputOptions()
    }
}