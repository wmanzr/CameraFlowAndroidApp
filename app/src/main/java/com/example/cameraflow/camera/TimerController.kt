package com.example.cameraflow.camera

import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimerController(
    private val lifecycleOwner: LifecycleOwner,
    private val countdownView: TextView
) {
    private var timerJob: Job? = null
    private var recordingTimerJob: Job? = null

    fun startCountdown(seconds: Int, onComplete: () -> Unit) {
        timerJob?.cancel()

        countdownView.visibility = View.VISIBLE
        countdownView.text = seconds.toString()
        countdownView.alpha = 1f
        countdownView.scaleX = 1.5f
        countdownView.scaleY = 1.5f

        countdownView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .start()

        timerJob = lifecycleOwner.lifecycleScope.launch {
            var countdown = seconds

            while (countdown > 0) {
                delay(1000)
                countdown--

                if (countdown > 0) {
                    withContext(Dispatchers.Main) {
                        countdownView.text = countdown.toString()
                        countdownView.alpha = 1f
                        countdownView.scaleX = 1.5f
                        countdownView.scaleY = 1.5f

                        countdownView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                countdownView.animate()
                    .alpha(0f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setDuration(200)
                    .withEndAction {
                        countdownView.visibility = View.GONE
                        onComplete()
                    }
                    .start()
            }
        }
    }

    fun cancel() {
        timerJob?.cancel()
        countdownView.visibility = View.GONE
    }

    /**
     * Запуск таймера записи
     */
    fun startRecordingTimer(
        onTimeUpdate: (String) -> Unit
    ) {
        stopRecordingTimer()
        
        val startTime = System.currentTimeMillis()
        recordingTimerJob = lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val totalSeconds = elapsed / 1000
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60
                val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                onTimeUpdate(timeString)
                delay(1000)
            }
        }
    }

    /**
     * Остановка таймера записи
     */
    fun stopRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }
}