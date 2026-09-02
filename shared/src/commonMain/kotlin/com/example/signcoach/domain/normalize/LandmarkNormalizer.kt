package com.example.signcoach.domain.normalize

import com.example.signcoach.data.model.HandPose
import com.example.signcoach.data.model.LandmarkPoint
import kotlin.math.sqrt

object LandmarkNormalizer {
    private const val LANDMARK_COUNT = 21
    private const val WRIST = 0
    private const val MIDDLE_FINGER_MCP = 9
    private const val MIN_SCALE = 0.0001f

    fun normalize(pose: HandPose, mirrored: Boolean = false): CanonicalPose? {
        if (pose.landmarks.size != LANDMARK_COUNT || pose.timestampMs < 0) return null
        if (pose.landmarks.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) return null

        val oriented = if (mirrored) {
            pose.landmarks.map { it.copy(x = 1f - it.x) }
        } else {
            pose.landmarks
        }
        val wrist = oriented[WRIST]
        val middleMcp = oriented[MIDDLE_FINGER_MCP]
        val dx = middleMcp.x - wrist.x
        val dy = middleMcp.y - wrist.y
        val scale = sqrt(dx * dx + dy * dy)
        if (!scale.isFinite() || scale < MIN_SCALE) return null

        return CanonicalPose(
            landmarks = oriented.map { point ->
                LandmarkPoint(
                    x = (point.x - wrist.x) / scale,
                    y = (point.y - wrist.y) / scale,
                    z = (point.z - wrist.z) / scale,
                )
            },
            timestampMs = pose.timestampMs,
        )
    }
}
