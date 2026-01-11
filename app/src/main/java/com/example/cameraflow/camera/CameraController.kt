package com.example.cameraflow.camera

import android.content.Context
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.cameraflow.viewmodel.CameraViewModel
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: CameraViewModel
) {
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var previewView: PreviewView? = null
    private var captureUseCasesProvider: ((Preview) -> List<UseCase>)? = null

    fun initialize() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    }

    private fun createResolutionSelector(): ResolutionSelector {
        val aspectRatio = viewModel.getAspectRatioValue()
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    aspectRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .build()
    }

    fun getOrCreatePreview(previewView: PreviewView): Preview {
        this.previewView = previewView
        
        if (preview == null) {
            preview = Preview.Builder()
                .setResolutionSelector(createResolutionSelector())
                .build()
        }

        preview?.setSurfaceProvider(previewView.surfaceProvider)
        
        return preview!!
    }

    fun startCamera(
        useCasesProvider: (Preview) -> List<UseCase>,
        onReady: (Camera) -> Unit,
        onError: (String) -> Unit
    ) {
        this.captureUseCasesProvider = useCasesProvider
        
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                
                if (provider.availableCameraInfos.isEmpty()) {
                    onError("No cameras available")
                    return@addListener
                }

                provider.unbindAll()

                val currentPreview = preview ?: run {
                    onError("Preview not initialized")
                    return@addListener
                }

                val captureUseCases = useCasesProvider(currentPreview)

                val allUseCases = listOf(currentPreview) + captureUseCases

                try {
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        viewModel.currentCameraSelector,
                        *allUseCases.toTypedArray()
                    )
                } catch (e: CameraInfoUnavailableException) {
                    onError("Camera unavailable: ${e.message}")
                    return@addListener
                }

                camera?.cameraControl?.setZoomRatio(viewModel.zoomRatio.value ?: 1f)

                onReady(camera!!)
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun switchCamera(onSwitched: () -> Unit) {
        viewModel.toggleCamera()
        viewModel.setZoomRatio(1f)
        
        if (viewModel.isFrontCamera()) {
            viewModel.setFlashEnabled(false)
        }

        onSwitched()
    }

    fun updateAspectRatio(
        onReady: (Camera) -> Unit,
        onError: (String) -> Unit
    ) {
        val provider = cameraProvider ?: run {
            onError("Camera provider not initialized")
            return
        }
        
        val pv = previewView ?: run {
            onError("PreviewView not initialized")
            return
        }
        
        val useCasesProvider = captureUseCasesProvider ?: run {
            onError("Capture use cases provider not initialized")
            return
        }

        try {
            provider.unbindAll()
            preview = Preview.Builder()
                .setResolutionSelector(createResolutionSelector())
                .build()
                .also {
                    it.setSurfaceProvider(pv.surfaceProvider)
                }

            val captureUseCases = useCasesProvider(preview!!)
            val allUseCases = listOf(preview!!) + captureUseCases
            
            try {
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    viewModel.currentCameraSelector,
                    *allUseCases.toTypedArray()
                )
            } catch (e: CameraInfoUnavailableException) {
                onError("Camera unavailable: ${e.message}")
                return
            }

            camera?.cameraControl?.setZoomRatio(viewModel.zoomRatio.value ?: 1f)
            
            onReady(camera!!)
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }

    fun setZoom(ratio: Float) {
        val camera = camera ?: return
        val maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
        val clampedZoom = ratio.coerceIn(1f, maxZoom)
        camera.cameraControl.setZoomRatio(clampedZoom)
        viewModel.setZoomRatio(clampedZoom)
    }

    fun focusAt(x: Float, y: Float, previewWidth: Float, previewHeight: Float) {
        val camera = camera ?: return
        val meteringPointFactory = SurfaceOrientedMeteringPointFactory(
            previewWidth,
            previewHeight
        )
        val point = meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()

        camera.cameraControl.startFocusAndMetering(action)
    }

    fun getCamera(): Camera? = camera
    
    fun getPreview(): Preview? = preview

    fun shutdown() {
        camera = null
        preview = null
        cameraProvider = null
        previewView = null
        captureUseCasesProvider = null
    }
}