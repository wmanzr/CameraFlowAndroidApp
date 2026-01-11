package com.example.cameraflow.fragments

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import com.example.cameraflow.R
import com.example.cameraflow.databinding.FragmentCameraBinding
import com.example.cameraflow.utils.PermissionHelper

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

    protected val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            PermissionHelper.handlePermissionResult(
                fragment = this,
                permission = Manifest.permission.CAMERA,
                isGranted = isGranted,
                permissionTitleRes = R.string.camera_permission_required,
                onGranted = { onPermissionsGranted() }
            )
        }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateModeSelectorButtons(isPhotoMode = true)
    }

    override fun onPermissionsGranted() {
        requestCameraPermission()
    }

    private fun requestCameraPermission() {
        when {
            PermissionHelper.hasCameraPermission(requireContext()) -> {
                startCamera()
            }
            else -> {
                PermissionHelper.requestPermission(
                    fragment = this,
                    permission = Manifest.permission.CAMERA,
                    launcher = cameraPermissionLauncher,
                    rationaleTitleRes = R.string.camera_permission_required
                )
            }
        }
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

        imageCapture.targetRotation = getDeviceRotation()

        showSavingProgress(true)

        val mainExecutor = ContextCompat.getMainExecutor(requireContext())
        
        cameraViewModel.savePhoto(
            imageCapture = imageCapture,
            executor = mainExecutor,
            onSuccess = {
                if (isAdded) {
                    showSavingProgress(false)
                }
            },
            onError = { _ ->
                if (isAdded) {
                    showSavingProgress(false)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_photo_save),
                        Toast.LENGTH_LONG
                    ).show()
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