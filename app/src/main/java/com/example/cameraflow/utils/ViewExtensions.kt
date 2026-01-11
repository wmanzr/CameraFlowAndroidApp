package com.example.cameraflow.utils

import android.content.res.Resources
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.cameraflow.R

fun View.applySystemBarsPadding(
    applyTop: Boolean = false,
    applyBottom: Boolean = false,
    applyLeft: Boolean = false,
    applyRight: Boolean = false,
    additionalTop: Int = 0,
    additionalBottom: Int = 0
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        
        view.updatePadding(
            top = if (applyTop) systemBars.top + additionalTop.dpToPx() else view.paddingTop,
            bottom = if (applyBottom) systemBars.bottom + additionalBottom.dpToPx() else view.paddingBottom,
            left = if (applyLeft) systemBars.left else view.paddingLeft,
            right = if (applyRight) systemBars.right else view.paddingRight
        )
        insets
    }
}

fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

fun Fragment.showDeleteDialog(onConfirm: () -> Unit) {
    AlertDialog.Builder(requireContext())
        .setTitle(getString(R.string.delete))
        .setMessage(getString(R.string.delete_confirmation))
        .setPositiveButton(getString(R.string.delete)) { _, _ ->
            onConfirm()
        }
        .setNegativeButton(getString(R.string.cancel), null)
        .show()
}