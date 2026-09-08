package com.example.signcoach.platform

import com.example.signcoach.data.model.HandPose

/** Resultaat van een korte opname voor een community-bijdrage: de landmarks (voor de score-
 * referentie) én een reeks losse voorbeeldbeelden (voor de menselijke review — zie
 * GestureContributionApi.uploadReviewClip). */
data class CapturedClip(
    val poses: List<HandPose>,
    /** JPEG-gecodeerde losse frames, in volgorde, ~1 per 200ms. Bewust geen video/GIF-encoding
     * (extra afhankelijkheid/risico); een reviewer bekijkt ze als een mapje losse beelden in
     * Supabase Storage, wat voor het beoordelen van een gebaar net zo goed werkt. */
    val previewFrames: List<ByteArray>,
)

/** Neemt gedurende [durationMs] zowel landmarks als voorbeeldbeelden op via de al lopende
 * [tracker]. [onProgress] loopt van 0f naar 1f. */
interface ContributionCapture {
    suspend fun record(tracker: HandTrackerBridge, durationMs: Long, onProgress: (Float) -> Unit): CapturedClip
}

expect class PlatformContributionCapture() : ContributionCapture {
    override suspend fun record(tracker: HandTrackerBridge, durationMs: Long, onProgress: (Float) -> Unit): CapturedClip
}
