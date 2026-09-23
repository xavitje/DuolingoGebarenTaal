package io.github.xavitje.signcoach.platform

import android.graphics.Bitmap
import io.github.xavitje.signcoach.data.model.HandPose
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

actual class PlatformContributionCapture actual constructor() : ContributionCapture {
    actual override suspend fun record(
        tracker: HandTrackerBridge,
        durationMs: Long,
        onProgress: (Float) -> Unit,
    ): CapturedClip {
        val androidTracker = tracker as? AndroidHandTrackerBridge
            ?: return CapturedClip(emptyList(), emptyList())

        val poses = mutableListOf<HandPose>()
        val frames = mutableListOf<ByteArray>()
        val startTime = System.currentTimeMillis()
        var lastFrameCapturedAt = 0L

        androidTracker.setFrameSink { bitmap ->
            val now = System.currentTimeMillis()
            if (now - lastFrameCapturedAt >= FRAME_INTERVAL_MS && frames.size < MAX_FRAMES) {
                lastFrameCapturedAt = now
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                frames.add(stream.toByteArray())
            }
        }
        try {
            coroutineScope {
                val poseJob = launch {
                    tracker.start().collect { pose ->
                        if (pose.landmarks.size == 21) poses.add(pose)
                    }
                }
                val startedAt = System.currentTimeMillis()
                while (System.currentTimeMillis() - startedAt < durationMs) {
                    onProgress(((System.currentTimeMillis() - startedAt).toFloat() / durationMs).coerceIn(0f, 1f))
                    delay(PROGRESS_TICK_MS)
                }
                onProgress(1f)
                poseJob.cancel()
            }
        } finally {
            androidTracker.setFrameSink(null)
        }
        return CapturedClip(poses.toList(), frames.toList())
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 200L
        const val PROGRESS_TICK_MS = 100L
        const val MAX_FRAMES = 15
        const val JPEG_QUALITY = 70
    }
}
