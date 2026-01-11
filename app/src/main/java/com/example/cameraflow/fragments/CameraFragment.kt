package com.example.cameraflow.fragments

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.cameraflow.R
import com.example.cameraflow.databinding.FragmentCameraBinding
import com.example.cameraflow.utils.applySystemBarsPadding
import com.example.cameraflow.viewmodel.CameraViewModel
import com.example.cameraflow.camera.CameraController
import com.example.cameraflow.camera.IndicatorController
import com.example.cameraflow.camera.GestureHandler
import com.example.cameraflow.camera.TimerController
import com.example.cameraflow.utils.FormatUtils
import com.google.android.material.button.MaterialButton
import androidx.core.graphics.toColorInt
import androidx.core.view.doOnLayout
import android.view.LayoutInflater
import android.view.ViewGroup
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.cameraflow.utils.PermissionHelper

abstract class CameraFragment : Fragment() {
    protected val cameraViewModel: CameraViewModel by activityViewModels()
    protected lateinit var cameraController: CameraController
    protected lateinit var gestureHandler: GestureHandler
    protected lateinit var timerController: TimerController
    protected lateinit var indicatorController: IndicatorController

    private var isCameraInitialized = false
    private var isUpdatingAspectRatio = false
    private var orientationEventListener: OrientationEventListener? = null
    private var currentDeviceRotation: Int = Surface.ROTATION_0
    
    protected fun getDeviceRotation(): Int {
        return currentDeviceRotation
    }

    protected val isFlashEnabled: Boolean
        get() = cameraViewModel.isFlashEnabled.value ?: false
    protected val timerDelaySeconds: Int
        get() = cameraViewModel.timerDelaySeconds.value ?: 0

    protected abstract fun getCameraBinding(): FragmentCameraBinding
    protected abstract fun getPreviewView(): PreviewView
    protected abstract fun getRootView(): View
    protected abstract fun getFlashButton(): MaterialButton
    protected abstract fun getTimerButton(): MaterialButton
    protected abstract fun getAspectRatio43Button(): MaterialButton
    protected abstract fun getAspectRatio169Button(): MaterialButton
    protected abstract fun getFocusIndicator(): View
    protected abstract fun getCountdownTimer(): android.widget.TextView
    protected open fun getFlashOverlay(): View? = getCameraBinding().flashOverlay
    protected abstract fun onCameraReady(camera: Camera)
    protected abstract fun createUseCases(preview: Preview): List<UseCase>

    protected open fun onPermissionsGranted() {
        startCamera()
    }

    protected abstract fun setupModeSpecificButtons()
    protected open fun updateCaptureRotation() {}
    protected abstract fun observeModeSpecificViewModel()

    protected open fun isRecording(): Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentCameraBinding.inflate(inflater, container, false)
        initializeBinding(binding)
        return binding.root
    }

    protected abstract fun initializeBinding(binding: FragmentCameraBinding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeControllers()
        setupObservers()
        observeModeSpecificViewModel()
        setupCommonButtons()
        getRootView().applySystemBarsPadding(applyTop = true, applyBottom = true)

        initializeDeviceRotation()
        setupOrientationListener()
        onPermissionsGranted()
    }
    
    private fun initializeDeviceRotation() {
        val context = requireContext()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val displayRotation = windowManager.defaultDisplay.rotation

        currentDeviceRotation = displayRotation
    }

    private fun setupOrientationListener() {
        val context = requireContext()
        orientationEventListener = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                
                val rotation = when {
                    orientation in 45..134 -> -90f
                    orientation in 135..224 -> 180f
                    orientation in 225..314 -> 90f
                    else -> 0f
                }

                currentDeviceRotation = when {
                    orientation in 45..134 -> Surface.ROTATION_270
                    orientation in 135..224 -> Surface.ROTATION_180
                    orientation in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                
                val binding = getCameraBinding()
                indicatorController.animateRotation(binding.galleryButton, rotation)
                indicatorController.animateRotation(binding.switchCameraButton, rotation)
                indicatorController.animateRotation(binding.flashButton, rotation)
                indicatorController.animateRotation(binding.timerButton, rotation)
                indicatorController.animateRotation(binding.countdownTimer, rotation)
                
                updateCaptureRotation()
            }
        }
        orientationEventListener?.enable()
    }

    private fun initializeControllers() {
        val binding = getCameraBinding()

        cameraController = CameraController(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            viewModel = cameraViewModel
        )
        cameraController.initialize()

        timerController = TimerController(
            lifecycleOwner = viewLifecycleOwner,
            countdownView = binding.countdownTimer
        )

        indicatorController = IndicatorController(
            context = requireContext(),
            focusIndicator = binding.focusIndicator,
            previewView = getPreviewView(),
            rootView = getRootView(),
            flashOverlay = getFlashOverlay(),
            recordingIndicator = binding.recordingIndicator,
            recordingTime = binding.recordingTime,
            freezeFrame = binding.freezeFrame,
            lifecycleOwner = viewLifecycleOwner
        )

        gestureHandler = GestureHandler(
            context = requireContext(),
            cameraController = cameraController,
            onFocusIndicator = { x, y ->
                indicatorController.showFocusIndicator(x, y)
            }
        )
        gestureHandler.setupGestures(getPreviewView())
    }

    protected fun setupObservers() {
        cameraViewModel.isFlashEnabled.observe(viewLifecycleOwner) { isEnabled ->
            updateFlashButton(isEnabled)
        }

        cameraViewModel.timerDelaySeconds.observe(viewLifecycleOwner) { seconds ->
            updateTimerButton(seconds)
        }

        cameraViewModel.aspectRatio.observe(viewLifecycleOwner) { ratio ->
            updateAspectRatioButtons(ratio)
            if (isCameraInitialized && cameraController.getPreview() != null && cameraController.getCamera() != null) {
                updateAspectRatio(ratio)
            }
        }

        cameraViewModel.selectedCamera.observe(viewLifecycleOwner) { _ ->
            updateFlashButton(cameraViewModel.isFlashEnabled.value ?: false)
        }
    }

    protected fun setupCommonButtons() {
        val binding = getCameraBinding()

        binding.flashButton.setOnClickListener {
            toggleFlash()
        }

        binding.timerButton.setOnClickListener {
            toggleTimer()
        }

        binding.aspectRatio43Button.setOnClickListener {
            if (isRecording()) return@setOnClickListener
            if (cameraViewModel.getAspectRatioValue() != AspectRatio.RATIO_4_3) {
                cameraViewModel.setAspectRatio(AspectRatio.RATIO_4_3)
            }
        }

        binding.aspectRatio169Button.setOnClickListener {
            if (isRecording()) return@setOnClickListener
            if (cameraViewModel.getAspectRatioValue() != AspectRatio.RATIO_16_9) {
                cameraViewModel.setAspectRatio(AspectRatio.RATIO_16_9)
            }
        }

        binding.switchCameraButton.setOnClickListener {
            switchCamera()
        }

        binding.galleryButton.setOnClickListener {
            if (isRecording()) return@setOnClickListener
            navigateToGallery()
        }

        updateFlashButton(cameraViewModel.isFlashEnabled.value ?: false)
        updateTimerButton(cameraViewModel.timerDelaySeconds.value ?: 0)
        updateAspectRatioButtons(cameraViewModel.getAspectRatioValue())

        setupModeSpecificButtons()
    }

    private fun toggleFlash() {
        if (cameraViewModel.isFrontCamera()) {
            return
        }
        cameraViewModel.toggleFlash()
    }

    private fun updateFlashButton(isEnabled: Boolean) {
        val activeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        val inactiveColor = android.content.res.ColorStateList.valueOf(
            "#666666".toColorInt()
        )

        val flashButton = getFlashButton()
        val isFrontCamera = cameraViewModel.isFrontCamera()

        flashButton.isEnabled = !isFrontCamera

        if (isEnabled && !isFrontCamera) {
            flashButton.setIconResource(R.drawable.ic_flash)
            flashButton.iconTint = activeColor
            flashButton.strokeColor = activeColor
        } else {
            flashButton.setIconResource(R.drawable.ic_no_flash)
            flashButton.iconTint = inactiveColor
            flashButton.strokeColor = inactiveColor
        }
    }

    protected fun toggleTimer() {
        cameraViewModel.toggleTimer()
    }

    private fun updateTimerButton(seconds: Int) {
        val activeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        val inactiveColor = android.content.res.ColorStateList.valueOf(
            "#666666".toColorInt()
        )

        val timerButton = getTimerButton()

        if (seconds > 0) {
            timerButton.text = when (seconds) {
                FormatUtils.TIMER_2_SEC -> "2с"
                FormatUtils.TIMER_5_SEC -> "5с"
                FormatUtils.TIMER_10_SEC -> "10с"
                else -> ""
            }
            timerButton.icon = null
            timerButton.setTextColor(android.graphics.Color.WHITE)
            timerButton.strokeColor = activeColor
        } else {
            timerButton.text = ""
            timerButton.setIconResource(R.drawable.timer)
            timerButton.iconTint = inactiveColor
            timerButton.strokeColor = inactiveColor
        }
    }

    protected fun startCountdownTimer(onComplete: () -> Unit) {
        val timerSeconds = cameraViewModel.timerDelaySeconds.value ?: 0
        if (timerSeconds > 0) {
            timerController.startCountdown(timerSeconds, onComplete)
        } else {
            onComplete()
        }
    }

    private fun updateAspectRatioButtons(currentRatio: Int) {
        val activeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        val inactiveColor = android.content.res.ColorStateList.valueOf(
            "#666666".toColorInt()
        )

        val button43 = getAspectRatio43Button()
        val button169 = getAspectRatio169Button()

        if (currentRatio == AspectRatio.RATIO_4_3) {
            button43.setTextColor(android.graphics.Color.WHITE)
            button43.strokeColor = activeColor
        } else {
            button43.setTextColor("#666666".toColorInt())
            button43.strokeColor = inactiveColor
        }

        if (currentRatio == AspectRatio.RATIO_16_9) {
            button169.setTextColor(android.graphics.Color.WHITE)
            button169.strokeColor = activeColor
        } else {
            button169.setTextColor("#666666".toColorInt())
            button169.strokeColor = inactiveColor
        }
    }

    protected fun updateModeSelectorButtons(isPhotoMode: Boolean) {
        val binding = getCameraBinding()
        val activeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        val inactiveColor = android.content.res.ColorStateList.valueOf(
            "#666666".toColorInt()
        )

        if (isPhotoMode) {
            binding.photoModeButton.apply {
                setTextColor(android.graphics.Color.WHITE)
                strokeColor = activeColor
            }
            binding.videoModeButton.apply {
                setTextColor("#666666".toColorInt())
                strokeColor = inactiveColor
            }
        } else {
            binding.videoModeButton.apply {
                setTextColor(android.graphics.Color.WHITE)
                strokeColor = activeColor
            }
            binding.photoModeButton.apply {
                setTextColor("#666666".toColorInt())
                strokeColor = inactiveColor
            }
        }
    }

    protected fun showSavingProgress(show: Boolean) {
        val binding = getCameraBinding()
        binding.savingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.captureButtonContainer.apply {
            isClickable = !show
            alpha = if (show) 0.5f else 1.0f
        }
    }

    protected fun startCamera() {
        val savedBitmap = cameraViewModel.getFreezeFrameBitmap()
        if (savedBitmap != null) {
            indicatorController.showFreezeFrame(savedBitmap)
        }

        val preview = cameraController.getOrCreatePreview(getPreviewView())

        cameraController.startCamera(
            useCasesProvider = { preview ->
                createUseCases(preview)
            },
            onReady = { camera ->
                isCameraInitialized = true
                indicatorController.fadeOutFreezeFrame()
                onCameraReady(camera)
            },
            onError = { _ ->
                indicatorController.fadeOutFreezeFrame()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_camera_start),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun updateAspectRatio(aspectRatio: Int) {
        if (isUpdatingAspectRatio) return

        isUpdatingAspectRatio = true
        indicatorController.captureFreezeFrame()

        getPreviewView().doOnLayout {
            cameraController.updateAspectRatio(
                onReady = { camera: Camera ->
                    isUpdatingAspectRatio = false
                    indicatorController.fadeOutFreezeFrame()
                    onCameraReady(camera)
                },
                onError = { _: String ->
                    isUpdatingAspectRatio = false
                    indicatorController.fadeOutFreezeFrame()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_camera_start),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    protected fun switchCamera() {
        indicatorController.captureFreezeFrame()
        cameraController.switchCamera {
        startCamera()
        }
    }

    protected fun navigateToGallery() {
        try {
            val actionId = when (this) {
                is PhotoFragment -> R.id.action_photoFragment_to_galleryFragment
                is VideoFragment -> R.id.action_videoFragment_to_galleryFragment
                else -> return
            }
            findNavController().navigate(actionId)
        } catch (e: Exception) {
        }
    }

    protected fun animateFlash() {
        indicatorController.animateFlash()
    }


    protected fun navigateToMode(toPhotoMode: Boolean) {
        if (isRecording()) return
        try {
            val actionId = when {
                this is PhotoFragment && !toPhotoMode -> R.id.action_photoFragment_to_videoFragment
                this is VideoFragment && toPhotoMode -> R.id.action_videoFragment_to_photoFragment
                else -> return
            }
            val bitmap = getPreviewView().bitmap
            if (bitmap != null) {
                cameraViewModel.setFreezeFrameBitmap(bitmap)
            }
            findNavController().navigate(actionId)
        } catch (e: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerController.cancel()
        orientationEventListener?.disable()
        orientationEventListener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController.shutdown()
    }
}