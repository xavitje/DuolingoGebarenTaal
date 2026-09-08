package com.example.signcoach.platform

/** TODO(iOS): net als IosCameraPlatform is de iOS-trackingpijplijn nog UNSUPPORTED, dus deze
 * geeft nu altijd een lege opname terug in plaats van echt op te nemen. */
actual class PlatformContributionCapture actual constructor() : ContributionCapture {
    actual override suspend fun record(
        tracker: HandTrackerBridge,
        durationMs: Long,
        onProgress: (Float) -> Unit,
    ): CapturedClip {
        onProgress(1f)
        return CapturedClip(emptyList(), emptyList())
    }
}
