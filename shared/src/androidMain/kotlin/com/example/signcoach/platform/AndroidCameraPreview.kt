package com.example.signcoach.platform

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

actual class CameraPreview

@Composable
actual fun CameraPreview(modifier: Modifier, isFrontFacing: Boolean) {
    val previewView = remember { arrayOfNulls<PreviewView>(1) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).also { view ->
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                previewView[0] = view
                AndroidCameraPlatform.attachPreview(view, isFrontFacing)
            }
        },
        update = { view -> AndroidCameraPlatform.attachPreview(view, isFrontFacing) },
    )
    DisposableEffect(Unit) {
        onDispose { previewView[0]?.let(AndroidCameraPlatform::detachPreview) }
    }
}
