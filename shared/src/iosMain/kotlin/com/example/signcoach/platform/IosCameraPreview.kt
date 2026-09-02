package com.example.signcoach.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual class CameraPreview

@Composable
actual fun CameraPreview(modifier: Modifier, isFrontFacing: Boolean) {
    // The common UI surfaces the explicit UNSUPPORTED tracker state on iOS.
}
