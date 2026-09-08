package com.example.signcoach.platform

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

actual class PlatformCameraController actual constructor() : CameraController {
    actual override suspend fun requestPermission(): Boolean {
        val requester = IosPlatformHooks.permissionRequester ?: return false
        return suspendCancellableCoroutine { continuation ->
            requester { granted ->
                if (continuation.isActive) continuation.resume(granted)
            }
        }
    }

    actual override fun createHandTracker(): HandTrackerBridge =
        IosHandTrackerBridge(IosPlatformHooks.nativeTrackerFactory?.invoke())
}
