package com.example.cameraflow.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import com.example.cameraflow.model.MediaModel
import com.example.cameraflow.utils.FormatUtils
import java.util.concurrent.Executor

class MediaRepository(private val context: Context) {

    fun loadMediaFromStore(isVideo: Boolean): List<MediaModel> {
        val uri = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val baseProjection = if (isVideo) {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            )
        } else {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )
        }

        val selection = if (isVideo) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        }
        val selectionArgs = arrayOf("%CameraFlow%")
        val sortOrder = if (isVideo) {
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        } else {
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        }

        val mediaItems = mutableListOf<MediaModel>()

        context.contentResolver.query(
            uri,
            baseProjection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(
                if (isVideo) MediaStore.Video.Media._ID else MediaStore.Images.Media._ID
            )
            val dateColumn = cursor.getColumnIndexOrThrow(
                if (isVideo) MediaStore.Video.Media.DATE_ADDED else MediaStore.Images.Media.DATE_ADDED
            )
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(
                if (isVideo) MediaStore.Video.Media.MIME_TYPE else MediaStore.Images.Media.MIME_TYPE
            )
            val displayNameColumn = cursor.getColumnIndexOrThrow(
                if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Images.Media.DISPLAY_NAME
            )
            val sizeColumn = cursor.getColumnIndexOrThrow(
                if (isVideo) MediaStore.Video.Media.SIZE else MediaStore.Images.Media.SIZE
            )
            val widthColumn = cursor.getColumnIndexOrThrow(
                if (isVideo) MediaStore.Video.Media.WIDTH else MediaStore.Images.Media.WIDTH
            )
            val heightColumn = cursor.getColumnIndexOrThrow(
                if (isVideo) MediaStore.Video.Media.HEIGHT else MediaStore.Images.Media.HEIGHT
            )

            val durationColumn = if (isVideo) {
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateColumn) * 1000 // Convert to milliseconds
                val mimeType = cursor.getString(mimeTypeColumn)
                val displayName = cursor.getString(displayNameColumn) ?: ""
                val size = cursor.getLong(sizeColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val duration = if (isVideo && durationColumn >= 0) {
                    cursor.getLong(durationColumn)
                } else {
                    0L
                }

                val mediaUri = if (isVideo) {
                    ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                } else {
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                }

                mediaItems.add(
                    MediaModel(
                        id = id,
                        uri = mediaUri,
                        dateAdded = dateAdded,
                        mimeType = mimeType,
                        isVideo = isVideo,
                        duration = duration,
                        displayName = displayName,
                        size = size,
                        width = width,
                        height = height
                    )
                )
            }
        }

        return mediaItems
    }

    fun getAll(): List<MediaModel> {
        val images = loadMediaFromStore(isVideo = false)
        val videos = loadMediaFromStore(isVideo = true)
        return images + videos
    }

    fun preparePhoto(): ContentValues {
        val fileName = FormatUtils.generateFileName("photo")
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraFlow")
            }
        }
    }

    fun prepareVideo(): ContentValues {
        val fileName = FormatUtils.generateFileName("video")
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraFlow")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun delete(uri: Uri): Pair<Boolean, Boolean> {
        return try {
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            Pair(rowsDeleted > 0, false)
        } catch (e: RecoverableSecurityException) {
            Pair(false, true)
        } catch (e: Exception) {
            Pair(false, false)
        }
    }

    fun savePhoto(
        imageCapture: ImageCapture,
        executor: Executor,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val contentValues = preparePhoto()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onSuccess()
                }
                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Unknown error")
                }
            }
        )
    }

    @androidx.camera.video.ExperimentalPersistentRecording
    fun createVideoOutputOptions(): MediaStoreOutputOptions {
        val contentValues = prepareVideo()
        return MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()
    }
}