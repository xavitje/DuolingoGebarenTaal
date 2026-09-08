package com.example.signcoach.platform

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

object AndroidCameraPlatform {
    private const val TAG = "SignCoach"
    private var activityReference: WeakReference<ComponentActivity>? = null
    private var previewReference: WeakReference<PreviewView>? = null
    private var permissionContinuation: ((Boolean) -> Unit)? = null
    private var frontFacing = true
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var tracker: AndroidHandTrackerBridge? = null

    fun initialize(activity: ComponentActivity) {
        Log.d(TAG, "AndroidCameraPlatform.initialize called with Activity: ${activity.javaClass.simpleName}")
        activityReference = WeakReference(activity)
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "ActivityResultCallback: CAMERA permission granted=$granted")
            permissionContinuation?.invoke(granted)
            permissionContinuation = null
            if (granted) bindCamera()
        }.also { launcher ->
            permissionLauncher = { launcher.launch(Manifest.permission.CAMERA) }
        }
    }

    internal fun attachPreview(previewView: PreviewView, isFrontFacing: Boolean) {
        // BELANGRIJK: Compose's AndroidView roept de `update`-lambda op bij ELKE recompositie,
        // en zodra de tracker RUNNING is triggert elke ontvangen hand-pose een state-update dus
        // een recompositie. Zonder deze guard werd bindCamera() (met unbindAll() + opnieuw
        // binden) dus tientallen keren per seconde aangeroepen zodra de tracker liep - de camera
        // kreeg zo nooit de kans om stabiel beeld te leveren (en liep uiteindelijk vast op
        // CameraId-x: onError 2, te veel gelijktijdige open-pogingen). Alleen echt opnieuw binden
        // als de preview of de gewenste richting daadwerkelijk is veranderd.
        if (boundPreviewView === previewView && boundFrontFacing == isFrontFacing) {
            return
        }
        Log.d(TAG, "attachPreview called (isFrontFacing=$isFrontFacing)")
        boundPreviewView = previewView
        boundFrontFacing = isFrontFacing
        previewReference = WeakReference(previewView)
        frontFacing = isFrontFacing
        bindCamera()
    }

    internal fun detachPreview(previewView: PreviewView) {
        Log.d(TAG, "detachPreview called")
        if (previewReference?.get() === previewView) previewReference = null
        if (boundPreviewView === previewView) {
            boundPreviewView = null
            boundFrontFacing = null
        }
    }

    private var boundPreviewView: PreviewView? = null
    private var boundFrontFacing: Boolean? = null

    internal fun getOrCreateTracker(): AndroidHandTrackerBridge {
        val activity = requireNotNull(activityReference?.get()) {
            "AndroidCameraPlatform.initialize(activity) must be called before creating the tracker"
        }
        return tracker ?: AndroidHandTrackerBridge(activity).also { tracker = it }
    }

    internal suspend fun requestPermission(): Boolean {
        Log.d(TAG, "requestPermission() called, about to withContext(Dispatchers.Main)")
        return withContext(Dispatchers.Main) {
        Log.d(TAG, "requestPermission(): inside withContext(Dispatchers.Main) now")
        val activity = activityReference?.get() ?: run {
            Log.e(TAG, "requestPermission failed: activityReference is null")
            return@withContext false
        }
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "requestPermission: checkSelfPermission=$granted")
        if (granted) {
            bindCamera()
            return@withContext true
        }
        suspendCancellableCoroutine { continuation ->
            permissionContinuation = { result ->
                Log.d(TAG, "permissionContinuation resumed with result=$result")
                if (continuation.isActive) continuation.resume(result)
            }
            continuation.invokeOnCancellation { permissionContinuation = null }
            Log.d(TAG, "Launching CAMERA permission request dialog...")
            permissionLauncher?.invoke() ?: run {
                Log.e(TAG, "permissionLauncher was null!")
                continuation.resume(false)
            }
        }
        }
    }

    private fun bindCamera() {
        val activity = activityReference?.get() ?: return
        val previewView = previewReference?.get() ?: run {
            Log.d(TAG, "bindCamera skipped: previewView is not attached yet")
            return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "bindCamera skipped: CAMERA permission not granted")
            return
        }
        // bindCamera() wordt vanuit twee plekken aangeroepen die kort na elkaar (of zelfs
        // tegelijk) kunnen vuren: attachPreview() (zodra de preview-view klaar is) en de
        // permissie-flow (zodra de gebruiker toestemming geeft). ProcessCameraProvider.getInstance()
        // is async, dus als beide oproepen elk hun eigen listener registreren, kan de tweede net
        // klaar zijn nadat de eerste al succesvol gebonden heeft - en dan breekt zijn unbindAll()
        // de zojuist gestarte preview weer af (zichtbaar in de logs als "Detaching [Preview,
        // ImageAnalysis]" direct na "bound successfully"). Daarom hier maar één bind-poging
        // tegelijk toestaan.
        if (bindInFlight) {
            Log.d(TAG, "bindCamera skipped: a bind is already in flight")
            return
        }
        bindInFlight = true
        Log.d(TAG, "bindCamera: binding CameraX to lifecycle...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            try {
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
                val preferredSelector = if (frontFacing) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                try {
                    cameraProvider.bindToLifecycle(activity, preferredSelector, preview, imageAnalysis)
                    Log.d(TAG, "bindCamera: CameraX bound successfully (preferred facing)")
                } catch (e: IllegalArgumentException) {
                    // Sommige emulators/AVD's registreren hun webcam-passthrough camera niet als
                    // front-facing (of niet als back-facing), waardoor DEFAULT_FRONT_CAMERA /
                    // DEFAULT_BACK_CAMERA geen match vindt. Val terug op de eerste camera die er
                    // daadwerkelijk is, in plaats van helemaal niets te binden.
                    Log.w(TAG, "bindCamera: preferred facing not available, falling back to any available camera", e)
                    val fallbackInfo = cameraProvider.availableCameraInfos.firstOrNull()
                    if (fallbackInfo == null) {
                        Log.e(TAG, "bindCamera: no cameras available at all")
                    } else {
                        val fallbackSelector = fallbackInfo.cameraSelector
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(activity, fallbackSelector, preview, imageAnalysis)
                        Log.d(TAG, "bindCamera: CameraX bound successfully (fallback camera)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "bindCamera failed", e)
            } finally {
                bindInFlight = false
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    private var bindInFlight = false
    private var permissionLauncher: (() -> Unit)? = null
}

actual class PlatformCameraController actual constructor() : CameraController {
    actual override suspend fun requestPermission(): Boolean = AndroidCameraPlatform.requestPermission()
    actual override fun createHandTracker(): HandTrackerBridge = AndroidCameraPlatform.getOrCreateTracker()
}
