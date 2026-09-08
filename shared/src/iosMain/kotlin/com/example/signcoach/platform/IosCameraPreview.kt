package com.example.signcoach.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController

actual class CameraPreview

// LET OP: UIKitViewController (package androidx.compose.ui.viewinterop) is de Compose Multiplatform
// 1.8.x-tegenhanger van Android's AndroidView. Kon niet gecompileerd worden in deze omgeving (geen
// Xcode/macOS beschikbaar) - klopt het package/de parameternaam niet meer met je Compose-versie,
// kijk dan in de release notes van org.jetbrains.compose voor de actuele UIKit-interop-API.
@Composable
actual fun CameraPreview(modifier: Modifier, isFrontFacing: Boolean) {
    val factory = IosPlatformHooks.previewControllerFactory ?: return
    val controller = remember(factory) { factory() }
    UIKitViewController(
        factory = { controller },
        modifier = modifier,
    )
}
