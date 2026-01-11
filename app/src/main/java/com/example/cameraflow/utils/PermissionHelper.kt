package com.example.cameraflow.utils

import android.Manifest
import com.example.cameraflow.R
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object PermissionHelper {

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission(
        fragment: Fragment,
        permission: String,
        launcher: androidx.activity.result.ActivityResultLauncher<String>,
        rationaleTitleRes: Int
    ) {
        if (fragment.shouldShowRequestPermissionRationale(permission)) {
            showRationaleDialog(
                fragment = fragment,
                titleRes = rationaleTitleRes,
                onConfirm = { launcher.launch(permission) }
            )
        } else {
            launcher.launch(permission)
        }
    }


    fun handlePermissionResult(
        fragment: Fragment,
        permission: String,
        isGranted: Boolean,
        permissionTitleRes: Int,
        onGranted: () -> Unit
    ) {
        if (isGranted) {
            onGranted()
            return
        }

        val shouldShowRationale =
            fragment.shouldShowRequestPermissionRationale(permission)

        if (!shouldShowRationale) {
            showPermissionDeniedForeverDialog(
                fragment = fragment,
                titleRes = permissionTitleRes
            )
        }
    }

    /**
     * Обработать результат запроса множественных разрешений
     * @param permissions Map разрешений и их статусов
     * @param permissionTitleRes ID строкового ресурса для заголовка диалога
     * @param onAllGranted действие при предоставлении всех разрешений
     */
    fun handleMultiplePermissionsResult(
        fragment: Fragment,
        permissions: Map<String, Boolean>,
        permissionTitleRes: Int,
        onAllGranted: () -> Unit
    ) {
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onAllGranted()
            return
        }

        // Проверяем, есть ли хотя бы одно разрешение, которое было отклонено навсегда
        val deniedForever = permissions.entries.any { (permission, isGranted) ->
            !isGranted && !fragment.shouldShowRequestPermissionRationale(permission)
        }

        if (deniedForever) {
            showPermissionDeniedForeverDialog(
                fragment = fragment,
                titleRes = permissionTitleRes
            )
        }
    }

    /**
     * Проверка разрешения на камеру
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Проверка разрешения на аудио
     */
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Проверка разрешений на хранилище (для чтения медиа)
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun showRationaleDialog(
        fragment: Fragment,
        titleRes: Int,
        onConfirm: () -> Unit
    ) {
        // Определяем сообщение в зависимости от типа разрешения
        val messageRes = when (titleRes) {
            R.string.camera_permission_required -> R.string.camera_permission_rationale
            R.string.audio_permission_required -> R.string.audio_permission_rationale
            R.string.storage_permission_required -> R.string.storage_permission_rationale
            else -> R.string.camera_permission_rationale
        }
        
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(fragment.getString(titleRes))
            .setMessage(fragment.getString(messageRes))
            .setPositiveButton(fragment.getString(R.string.ok)) { _, _ ->
                onConfirm()
            }
            .setNegativeButton(fragment.getString(R.string.cancel), null)
            .show()
    }

    fun showPermissionDeniedForeverDialog(
        fragment: Fragment,
        titleRes: Int
    ) {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(fragment.getString(titleRes))
            .setMessage(fragment.getString(R.string.permission_denied_forever))
            .setPositiveButton(fragment.getString(R.string.open_settings)) { _, _ ->
                fragment.openAppSettings()
            }
            .setNegativeButton(fragment.getString(R.string.cancel), null)
            .show()
    }
    
    /**
     * Получить массив разрешений для хранилища в зависимости от версии Android
     */
    fun getStoragePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

/**
 * Проверить, нужно ли показать объяснение перед запросом разрешения
 */
fun Fragment.shouldShowRationale(permission: String): Boolean {
    return shouldShowRequestPermissionRationale(permission)
}

/**
 * Открыть настройки приложения для предоставления разрешения
 */
fun Fragment.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", requireContext().packageName, null)
    }
    startActivity(intent)
}

