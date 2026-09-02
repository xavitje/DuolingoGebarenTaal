package com.example.signcoach.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GestureRule(
    val type: RuleType,
    val landmarkIndices: List<Int> = emptyList(),
    val targetValue: Float? = null,
    val tolerance: Float? = null,
    val minDurationMs: Long? = null,
)

@Serializable
enum class RuleType {
    LANDMARK_DISTANCE,
    LANDMARK_ANGLE,
    MOTION_DIRECTION,
    MIN_STABLE_FRAMES,
}
