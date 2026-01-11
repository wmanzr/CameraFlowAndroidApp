package com.example.cameraflow.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.cameraflow.R
import com.example.cameraflow.databinding.FragmentCameraBinding
import com.example.cameraflow.utils.PermissionHelper
import com.google.android.material.button.MaterialButton

class VideoFragment : CameraFragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding ?: throw RuntimeException("Non-zero value was expected")
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isRecording = false

    override fun isRecording(): Boolean = isRecording

    override fun initializeBinding(binding: FragmentCameraBinding) {
        _binding = binding
    }
    
    override fun getCameraBinding(): FragmentCameraBinding = binding
    override fun getPreviewView(): PreviewView = binding.preview
    override fun getRootView(): View = binding.root
    override fun getFlashButton(): MaterialButton = binding.flashButton
    override fun getTimerButton(): MaterialButton = binding.timerButton
    override fun getAspectRatio43Button(): MaterialButton = binding.aspectRatio43Button
    override fun getAspectRatio169Button(): MaterialButton = binding.aspectRatio169Button
    override fun getFocusIndicator(): View = binding.focusIndicator
    override fun getCountdownTimer(): android.widget.TextView = binding.countdownTimer

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            PermissionHelper.handlePermissionResult(
                fragment = this,
                permission = Manifest.permission.RECORD_AUDIO,
                isGranted = isGranted,
                permissionTitleRes = R.string.audio_permission_required,
                onGranted = { startCamera() }
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recordingTime.visibility = View.VISIBLE
        indicatorController.resetRecordingTime()

        updateModeSelectorButtons(isPhotoMode = false)
    }
    
    override fun onPermissionsGranted() {
        requestAudioPermission()
    }

    private fun requestAudioPermission() {
        when {
            PermissionHelper.hasAudioPermission(requireContext()) -> {
                startCamera()
            }
            else -> {
                PermissionHelper.requestPermission(
                    fragment = this,
                    permission = Manifest.permission.RECORD_AUDIO,
                    launcher = audioPermissionLauncher,
                    rationaleTitleRes = R.string.audio_permission_required
                )
            }
        }
    }

    override fun setupModeSpecificButtons() {
        binding.captureButtonContainer.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                if (timerDelaySeconds > 0) {
                    binding.captureButtonContainer.isEnabled = false
                    startCountdownTimer {
                        startRecording()
                        binding.captureButtonContainer.isEnabled = true
                    }
                } else {
                    startRecording()
                }
            }
        }

        binding.photoModeButton.setOnClickListener {
            if (isRecording) return@setOnClickListener
            navigateToMode(toPhotoMode = true)
        }

        binding.videoModeButton.isEnabled = false
    }

    override fun observeModeSpecificViewModel() {
        cameraViewModel.isFlashEnabled.observe(viewLifecycleOwner) {
            updateTorch()
        }
    }

    override fun createUseCases(preview: Preview): List<UseCase> {
        val aspectRatio = cameraViewModel.getAspectRatioValue()

        if (videoCapture == null || !isRecording) {
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .setAspectRatio(aspectRatio)
                .build()

            videoCapture = VideoCapture.Builder(recorder)
                .setTargetRotation(getDeviceRotation())
                .build()
        }

        return listOf(videoCapture!!)
    }

    override fun onCameraReady(camera: Camera) {
        updateTorch()
    }

    private fun updateTorch() {
        val camera = cameraController.getCamera() ?: return
        camera.cameraControl.enableTorch(isFlashEnabled)
    }

    @OptIn(ExperimentalPersistentRecording::class)
    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val videoCapture = videoCapture ?: return

        // Обновляем ориентацию непосредственно перед началом записи
        videoCapture.targetRotation = getDeviceRotation()

        val mediaStoreOutputOptions = cameraViewModel.createVideoOutputOptions()

        val pendingRecording = videoCapture.output
            .prepareRecording(requireContext(), mediaStoreOutputOptions)
            .apply {
                if (PermissionHelper.hasAudioPermission(requireContext())) {
                    withAudioEnabled()
                }
            }
            .asPersistentRecording()

        val mainExecutor = ContextCompat.getMainExecutor(requireContext())
        recording = pendingRecording.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecording = true
                    indicatorController.updateRecordingUI(true)
                    timerController.startRecordingTimer { timeString ->
                        indicatorController.updateRecordingTime(timeString)
                    }
                }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    timerController.stopRecordingTimer()
                    indicatorController.updateRecordingUI(false)
                    showSavingProgress(false)
                    if (event.hasError() && isAdded) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.error_video_save),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    recording?.close()
                    recording = null
                }
                else -> {}
            }
        }
    }

    private fun stopRecording() {
        timerController.stopRecordingTimer()
        indicatorController.updateRecordingUI(false)
        showSavingProgress(true)
        
        recording?.stop()
        recording = null
    }

    override fun updateCaptureRotation() {
        videoCapture?.targetRotation = getDeviceRotation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerController.stopRecordingTimer()
        _binding = null
    }
}