package com.example.signcoach.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.example.signcoach.data.model.HandPose
import com.example.signcoach.data.model.LandmarkPoint
import com.example.signcoach.data.model.TrackerStatus
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

class AndroidHandTrackerBridge internal constructor(
    context: Context,
) : HandTrackerBridge {
    private val applicationContext = context.applicationContext
    private val mutableStatus = MutableStateFlow(TrackerStatus.STOPPED)
    private val poses = MutableSharedFlow<HandPose>(extraBufferCapacity = 1)
    private var handLandmarker: HandLandmarker? = null
    @Volatile private var active = false

    override val status: StateFlow<TrackerStatus> = mutableStatus

    override fun start(): Flow<HandPose> = flow {
        startTracker()
        emitAll(poses.asSharedFlow())
    }

    @Synchronized
    private fun startTracker() {
        if (active && handLandmarker != null) return
        mutableStatus.value = TrackerStatus.STARTING
        try {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .build(),
                )
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumHands(1)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener(::onResult)
                .setErrorListener(::onError)
                .build()
            handLandmarker = HandLandmarker.createFromOptions(applicationContext, options)
            active = true
            mutableStatus.value = TrackerStatus.RUNNING
        } catch (_: Throwable) {
            active = false
            handLandmarker = null
            mutableStatus.value = TrackerStatus.ERROR
        }
    }

    internal fun analyze(imageProxy: ImageProxy, isFrontFacing: Boolean) {
        val detector = handLandmarker
        if (!active || detector == null) {
            imageProxy.close()
            return
        }

        val timestamp = SystemClock.uptimeMillis()
        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
        val width = imageProxy.width
        val height = imageProxy.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        } catch (_: Throwable) {
            return
        } finally {
            imageProxy.close()
        }

        val matrix = Matrix().apply {
            postRotate(rotation)
            if (isFrontFacing) postScale(-1f, 1f, width.toFloat(), height.toFloat())
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        try {
            detector.detectAsync(BitmapImageBuilder(oriented).build(), timestamp)
        } catch (_: Throwable) {
            mutableStatus.value = TrackerStatus.ERROR
        }
    }

    private fun onResult(result: HandLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage) {
        val landmarks = result.landmarks().firstOrNull() ?: return
        if (landmarks.size != LANDMARK_COUNT) return
        val pose = HandPose(
            landmarks = landmarks.map { point ->
                LandmarkPoint(point.x(), point.y(), point.z())
            },
            timestampMs = result.timestampMs(),
        )
        if (pose.landmarks.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }) {
            poses.tryEmit(pose)
        }
    }

    private fun onError(@Suppress("UNUSED_PARAMETER") error: RuntimeException) {
        mutableStatus.value = TrackerStatus.ERROR
    }

    @Synchronized
    override suspend fun stop() {
        active = false
        handLandmarker?.close()
        handLandmarker = null
        mutableStatus.value = TrackerStatus.STOPPED
    }

    private companion object {
        const val MODEL_ASSET = "hand_landmarker.task"
        const val LANDMARK_COUNT = 21
    }
}
