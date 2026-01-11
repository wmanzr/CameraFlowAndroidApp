package com.example.cameraflow.camera

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IndicatorController(
    private val context: Context,
    private val focusIndicator: View,
    private val previewView: PreviewView,
    private val rootView: View,
    private val flashOverlay: View?,
    private val recordingIndicator: View?,
    private val recordingTime: TextView?,
    private val freezeFrame: ImageView?,
    private val lifecycleOwner: LifecycleOwner
) {
    private val indicatorSize: Int by lazy {
        (80 * context.resources.displayMetrics.density).toInt()
    }
    private var focusDisappearJob: Job? = null

    fun showFocusIndicator(x: Float, y: Float) {
        previewView.post {
            positionIndicator(x, y)
            animateAppearance()
            scheduleDisappearance()
        }
    }

    private fun positionIndicator(x: Float, y: Float) {
        val previewLocation = IntArray(2)
        previewView.getLocationInWindow(previewLocation)

        val rootLocation = IntArray(2)
        rootView.getLocationInWindow(rootLocation)

        val relativeX = previewLocation[0] - rootLocation[0] + x
        val relativeY = previewLocation[1] - rootLocation[1] + y

        val left = (relativeX - indicatorSize / 2).toInt()
        val top = (relativeY - indicatorSize / 2).toInt()

        val maxLeft = rootView.width - indicatorSize
        val maxTop = rootView.height - indicatorSize

        val layoutParams = (focusIndicator.layoutParams as? ViewGroup.MarginLayoutParams)
            ?: ViewGroup.MarginLayoutParams(indicatorSize, indicatorSize)

        layoutParams.apply {
            width = indicatorSize
            height = indicatorSize
            leftMargin = left.coerceIn(0, maxLeft)
            topMargin = top.coerceIn(0, maxTop)
        }

        focusIndicator.layoutParams = layoutParams
    }

    private fun animateAppearance() {
        focusDisappearJob?.cancel()
        
        focusIndicator.apply {
            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 1.5f
            scaleY = 1.5f

            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .withEndAction {
                    scheduleDisappearance()
                }
                .start()
        }
    }

    private fun scheduleDisappearance() {
        focusDisappearJob = lifecycleOwner.lifecycleScope.launch {
            delay(2000)
            if (focusIndicator.isVisible) {
                focusIndicator.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(200)
                    .withEndAction {
                        focusIndicator.visibility = View.GONE
                    }
                    .start()
            }
        }
    }

    fun animateFlash() {
        val overlay = flashOverlay ?: return
        
        overlay.visibility = View.VISIBLE
        overlay.alpha = 1.0f

        val animation = AlphaAnimation(1.0f, 0.0f).apply {
            duration = 150
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    overlay.visibility = View.GONE
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }
        overlay.startAnimation(animation)
    }

    fun updateRecordingUI(recording: Boolean) {
        recordingIndicator?.visibility = if (recording) View.VISIBLE else View.GONE
        if (!recording) {
            recordingTime?.text = "00:00:00"
        }
    }

    fun updateRecordingTime(timeString: String) {
        recordingTime?.text = timeString
    }

    fun resetRecordingTime() {
        recordingTime?.text = "00:00:00"
    }

    fun captureFreezeFrame() {
        val bitmap = previewView.bitmap ?: return
        showFreezeFrame(bitmap)
    }

    fun showFreezeFrame(bitmap: Bitmap) {
        val frame = freezeFrame ?: return
        frame.setImageBitmap(bitmap)
        frame.alpha = 1f
        frame.visibility = View.VISIBLE
    }

    fun fadeOutFreezeFrame() {
        val frame = freezeFrame ?: return
        if (frame.visibility != View.VISIBLE) return
        
        frame.animate()
            .alpha(0f)
            .setDuration(300L)
            .withEndAction {
                frame.visibility = View.GONE
            }
            .start()
    }

    fun animateRotation(view: View, rotation: Float) {
        view.animate()
            .rotation(rotation)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }
}