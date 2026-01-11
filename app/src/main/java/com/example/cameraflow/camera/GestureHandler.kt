package com.example.cameraflow.camera

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.view.PreviewView

class GestureHandler(
    private val context: Context,
    private val cameraController: CameraController,
    private val onFocusIndicator: (x: Float, y: Float) -> Unit
) {
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    fun setupGestures(previewView: PreviewView) {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTapToFocus(e.x, e.y, previewView)
                return true
            }
        })

        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                handleZoom(detector.scaleFactor)
                return true
            }
        })

        previewView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun handleTapToFocus(x: Float, y: Float, previewView: PreviewView) {
        cameraController.focusAt(
            x, y,
            previewView.width.toFloat(),
            previewView.height.toFloat()
        )
        onFocusIndicator(x, y)
    }

    private fun handleZoom(scaleFactor: Float) {
        val camera = cameraController.getCamera() ?: return
        val currentZoom = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
        val newZoom = currentZoom * scaleFactor
        cameraController.setZoom(newZoom)
    }
}