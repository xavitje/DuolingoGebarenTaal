package com.example.signcoach.platform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

object AndroidCameraPlatform {
    private var activityReference: WeakReference<ComponentActivity>? = null
    private var previewReference: WeakReference<PreviewView>? = null
    private var permissionContinuation: ((Boolean) -> Unit)? = null
    private var frontFacing = true
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var tracker: AndroidHandTrackerBridge? = null

    fun initialize(activity: ComponentActivity) {
        activityReference = WeakReference(activity)
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionContinuation?.invoke(granted)
            permissionContinuation = null
            if (granted) bindCamera()
        }.also { launcher ->
            permissionLauncher = { launcher.launch(Manifest.permission.CAMERA) }
        }
    }

    internal fun attachPreview(previewView: PreviewView, isFrontFacing: Boolean) {
        previewReference = WeakReference(previewView)
        frontFacing = isFrontFacing
        bindCamera()
    }

    internal fun detachPreview(previewView: PreviewView) {
        if (previewReference?.get() === previewView) previewReference = null
    }

    internal fun getOrCreateTracker(): AndroidHandTrackerBridge {
        val activity = requireNotNull(activityReference?.get()) {
            "AndroidCameraPlatform.initialize(activity) must be called before creating the tracker"
        }
        return tracker ?: AndroidHandTrackerBridge(activity).also { tracker = it }
    }

    internal suspend fun requestPermission(): Boolean {
        val activity = activityReference?.get() ?: return false
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera()
            return true
        }
        return suspendCancellableCoroutine { continuation ->
            permissionContinuation = { granted ->
                if (continuation.isActive) continuation.resume(granted)
            }
            continuation.invokeOnCancellation { permissionContinuation = null }
            permissionLauncher?.invoke() ?: continuation.resume(false)
        }
    }

    private fun bindCamera() {
        val activity = activityReference?.get() ?: return
        val previewView = previewReference?.get() ?: return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { image ->
                        val activeTracker = tracker
                        if (activeTracker == null) image.close()
                        else activeTracker.analyze(image, frontFacing)
                    }
                }
            val selector = if (frontFacing) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(activity, selector, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(activity))
    }

    private var permissionLauncher: (() -> Unit)? = null
}

actual class PlatformCameraController actual constructor() : CameraController {
    actual override suspend fun requestPermission(): Boolean = AndroidCameraPlatform.requestPermission()
    actual override fun createHandTracker(): HandTrackerBridge = AndroidCameraPlatform.getOrCreateTracker()
}
