package com.example.signcoach.domain.scoring

import com.example.signcoach.data.model.FeedbackResult
import com.example.signcoach.data.model.HandPose
import com.example.signcoach.data.model.RuleType
import com.example.signcoach.data.model.SignTemplate

class DynamicGestureScorer(
    private val defaultMinDisplacement: Float = 0.12f,
    private val defaultMinDurationMs: Long = 150L,
    private val defaultMaxDurationMs: Long = 2_500L,
) : GestureScorer {
    override fun score(input: List<HandPose>, template: SignTemplate): FeedbackResult {
        val valid = input.filter(::isValid).sortedBy { it.timestampMs }
        if (valid.size < 3) return feedback(0f, false, "Maak de beweging iets langer.")

        val first = valid.first()
        val last = valid.last()
        val duration = last.timestampMs - first.timestampMs
        val motionRule = template.rules.firstOrNull { it.type == RuleType.MOTION_DIRECTION }
        val minDuration = motionRule?.minDurationMs ?: defaultMinDurationMs
        if (duration < minDuration) return feedback(0.15f, false, "Beweeg iets rustiger van links naar rechts.")
        if (duration > defaultMaxDurationMs) return feedback(0.35f, false, "Probeer de beweging vloeiender en iets sneller.")

        val displacement = last.landmarks[WRIST].x - first.landmarks[WRIST].x
        val minDisplacement = motionRule?.targetValue?.let { kotlin.math.abs(it) }
            ?: motionRule?.tolerance
            ?: defaultMinDisplacement
        val directionScore = (displacement / minDisplacement).coerceIn(0f, 1f)
        val shapeScore = StaticGestureScorer().score(valid, template).score
        val score = (DIRECTION_WEIGHT * directionScore + SHAPE_WEIGHT * shapeScore).coerceIn(0f, 1f)
        val isCorrect = displacement >= minDisplacement && score >= CORRECT_THRESHOLD

        return when {
            isCorrect -> feedback(score, true, "Mooie beweging naar rechts!")
            displacement <= 0f -> feedback(score, false, "Beweeg je hand van links naar rechts.")
            displacement < minDisplacement -> feedback(score, false, "Maak de beweging wat groter naar rechts.")
            else -> feedback(score, false, "Houd de handvorm stabiel tijdens de beweging.")
        }
    }

    private fun isValid(pose: HandPose): Boolean =
        pose.landmarks.size == LANDMARK_COUNT &&
            pose.timestampMs >= 0 &&
            pose.landmarks.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }

    private fun feedback(score: Float, correct: Boolean, message: String) =
        FeedbackResult(score.coerceIn(0f, 1f), correct, listOf(message))

    private companion object {
        const val LANDMARK_COUNT = 21
        const val WRIST = 0
        const val DIRECTION_WEIGHT = 0.75f
        const val SHAPE_WEIGHT = 0.25f
        const val CORRECT_THRESHOLD = 0.75f
    }
}
