package com.example.signcoach.platform

actual class PlatformCameraController actual constructor() : CameraController {
    actual override suspend fun requestPermission(): Boolean = true
    actual override fun createHandTracker(): HandTrackerBridge = IosHandTrackerBridge()
}
