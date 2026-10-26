package io.github.xavitje.signcoach.platform

/** Een opgenomen demovideo: lokaal bestand (voor terugkijken) en de bytes (voor uploaden). */
class RecordedVideo(val localUri: String, val bytes: ByteArray)

/**
 * Neemt een korte demovideo op met de camera (zonder geluid), voor vakmensen die het voorbeeld
 * van een gebaar opnemen. De camerapreview ([CameraPreview]) moet in beeld zijn tijdens het opnemen.
 */
interface DemoVideoRecorder {
    /** True als opnemen op dit platform kan. */
    val isSupported: Boolean

    /** Zet de camera in videomodus (aanroepen vóór de preview getoond wordt). */
    fun enable()

    /** Terug naar gewone handtracking-modus. */
    fun disable()

    /** Neemt [durationMs] op. Null bij een fout. */
    suspend fun record(durationMs: Long, onProgress: (Float) -> Unit): RecordedVideo?
}

expect class PlatformDemoVideoRecorder() : DemoVideoRecorder {
    override val isSupported: Boolean
    override fun enable()
    override fun disable()
    override suspend fun record(durationMs: Long, onProgress: (Float) -> Unit): RecordedVideo?
}
