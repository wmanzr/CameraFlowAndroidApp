package com.example.cameraflow.fragments

import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import com.example.cameraflow.R
import com.example.cameraflow.databinding.FragmentCameraBinding

class PhotoFragment : CameraFragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding ?: throw RuntimeException("Non-zero value was expected")

    private var imageCapture: ImageCapture? = null

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateModeSelectorButtons(isPhotoMode = true)
    }

    override fun setupModeSpecificButtons() {
        binding.captureButtonContainer.setOnClickListener {
            if (timerDelaySeconds > 0) {
                binding.captureButtonContainer.isEnabled = false
                startCountdownTimer {
                    takePhoto()
                    animateFlash()
                    binding.captureButtonContainer.isEnabled = true
                }
            } else {
                takePhoto()
                animateFlash()
            }
        }

        binding.videoModeButton.setOnClickListener {
            if (isRecording()) return@setOnClickListener
            navigateToMode(toPhotoMode = false)
        }

        binding.photoModeButton.isEnabled = false
    }

    override fun observeModeSpecificViewModel() {
        cameraViewModel.isFlashEnabled.observe(viewLifecycleOwner) {
            updateImageCaptureFlashMode()
        }
    }

    override fun createUseCases(preview: Preview): List<UseCase> {
        val aspectRatio = cameraViewModel.getAspectRatioValue()
        
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    aspectRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .build()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(if (isFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(getDeviceRotation())
            .build()

        return listOf(imageCapture!!)
    }

    override fun onCameraReady(camera: Camera) {
    }

    private fun updateImageCaptureFlashMode() {
        val imageCapture = imageCapture ?: return
        imageCapture.flashMode = if (isFlashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        imageCapture.targetRotation = getDeviceRotation()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Обновляем ориентацию непосредственно перед съемкой
        imageCapture.targetRotation = getDeviceRotation()

        showSavingProgress(true)

        val contentValues = cameraViewModel.preparePhoto()

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        val mainExecutor = ContextCompat.getMainExecutor(requireContext())
        imageCapture.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    if (isAdded) {
                        showSavingProgress(false)
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.error_photo_save),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (isAdded) {
                        showSavingProgress(false)
                    }
                }
            }
        )
    }

    override fun updateCaptureRotation() {
        imageCapture?.targetRotation = getDeviceRotation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}