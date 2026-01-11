package com.example.cameraflow.utils

import android.content.Context
import com.example.cameraflow.R
import java.text.SimpleDateFormat
import java.util.Locale

object FormatUtils {
    const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    
    // Timer constants
    const val TIMER_2_SEC = 2
    const val TIMER_5_SEC = 5
    const val TIMER_10_SEC = 10

    fun generateFileName(fileType: String): String {
        val dateTime = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        return "${fileType}_${dateTime}"
    }

    fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%d:%02d", minutes, seconds)
            else -> String.format("0:%02d", seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.2f КБ", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f МБ", mb)
        val gb = mb / 1024.0
        return String.format("%.2f ГБ", gb)
    }

    fun formatItemsCount(count: Int, context: Context): String {
        return when {
            count == 0 -> context.getString(R.string.items_count_zero)
            count % 10 == 1 && count % 100 != 11 -> context.getString(R.string.items_count_one, count)
            count % 10 in 2..4 && count % 100 !in 12..14 -> context.getString(R.string.items_count_few, count)
            else -> context.getString(R.string.items_count_many, count)
        }
    }
}