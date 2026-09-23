package io.github.xavitje.signcoach.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GestureRule(
    val type: RuleType,
    val landmarkIndices: List<Int> = emptyList(),
    val targetValue: Float? = null,
    val tolerance: Float? = null,
    val minDurationMs: Long? = null,
    /** Verplicht voor [RuleType.FACE_BLENDSHAPE]: de naam van de MediaPipe-blendshape, bv. "mouthPucker". */
    val blendshapeName: String? = null,
)

@Serializable
enum class RuleType {
    LANDMARK_DISTANCE,
    LANDMARK_ANGLE,
    MOTION_DIRECTION,
    MIN_STABLE_FRAMES,
    /** Vereist dat een gemeten blendshape-score dicht bij [GestureRule.targetValue] ligt (binnen [GestureRule.tolerance]). */
    FACE_BLENDSHAPE,
    /** Vereist dat er een tweede hand gedetecteerd is (tweehandig gebaar). */
    TWO_HAND_PRESENCE,
}
