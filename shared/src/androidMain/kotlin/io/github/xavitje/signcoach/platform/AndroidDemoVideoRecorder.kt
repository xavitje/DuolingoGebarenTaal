package io.github.xavitje.signcoach.platform

import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

actual class PlatformDemoVideoRecorder actual constructor() : DemoVideoRecorder {
    actual override val isSupported: Boolean = true

    actual override fun enable() = AndroidCameraPlatform.setVideoMode(true)

    actual override fun disable() = AndroidCameraPlatform.setVideoMode(false)

    actual override suspend fun record(durationMs: Long, onProgress: (Float) -> Unit): RecordedVideo? {
        val activity = AndroidCameraPlatform.currentActivity ?: return null
        // De camera bindt asynchroon: even wachten tot VideoCapture klaarstaat.
        var capture = AndroidCameraPlatform.videoCapture
        var waited = 0
        while (capture == null && waited < 3000) {
            delay(100); waited += 100
            capture = AndroidCameraPlatform.videoCapture
        }
        if (capture == null) return null

        val file = File(activity.cacheDir, "demo_${System.currentTimeMillis()}.mp4")
        val finished = CompletableDeferred<Boolean>()
        val recording = withContext(Dispatchers.Main) {
            capture.output
                .prepareRecording(activity, FileOutputOptions.Builder(file).build())
                .start(ContextCompat.getMainExecutor(activity)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        if (event.hasError()) Log.e("SignCoach", "Demo recording error ${event.error}", event.cause)
                        finished.complete(!event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED)
                    }
                }
        }
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < durationMs) {
            onProgress(((System.currentTimeMillis() - start).toFloat() / durationMs).coerceIn(0f, 1f))
            delay(100)
        }
        onProgress(1f)
        withContext(Dispatchers.Main) { recording.stop() }
        val ok = finished.await()
        if (!ok || !file.exists() || file.length() == 0L) return null
        return RecordedVideo(localUri = file.toURI().toString(), bytes = withContext(Dispatchers.IO) { file.readBytes() })
    }
}
