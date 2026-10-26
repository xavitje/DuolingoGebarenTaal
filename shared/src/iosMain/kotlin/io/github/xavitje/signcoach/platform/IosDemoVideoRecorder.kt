package io.github.xavitje.signcoach.platform

/** Demovideo's opnemen kan voorlopig alleen op Android. */
actual class PlatformDemoVideoRecorder actual constructor() : DemoVideoRecorder {
    actual override val isSupported: Boolean = false
    actual override fun enable() {}
    actual override fun disable() {}
    actual override suspend fun record(durationMs: Long, onProgress: (Float) -> Unit): RecordedVideo? = null
}
