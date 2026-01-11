package com.example.cameraflow.model

import android.net.Uri

data class MediaModel(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long,
    val mimeType: String,
    val isVideo: Boolean,
    val duration: Long = 0,
    val displayName: String = "",
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0
)