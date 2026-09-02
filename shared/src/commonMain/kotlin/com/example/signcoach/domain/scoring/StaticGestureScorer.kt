package com.example.signcoach.domain.scoring

import com.example.signcoach.data.model.FeedbackResult
import com.example.signcoach.data.model.HandPose
import com.example.signcoach.data.model.LandmarkPoint
import com.example.signcoach.data.model.RuleType
import com.example.signcoach.data.model.SignTemplate
import com.example.signcoach.domain.normalize.LandmarkNormalizer
import kotlin.math.sqrt

class StaticGestureScorer(
    private val maxDistance: Float = DEFAULT_MAX_DISTANCE,
    private val correctThreshold: Float = DEFAULT_CORRECT_THRESHOLD,
) : GestureScorer {
    override fun score(input: List<HandPose>, template: SignTemplate): FeedbackResult {
        val requiredFrames = template.rules
            .firstOrNull { it.type == RuleType.MIN_STABLE_FRAMES }
            ?.targetValue
            ?.toInt()
            ?.coerceAtLeast(1)
            ?: 1
        if (input.size < requiredFrames) {
            return FeedbackResult(
                score = 0f,
                isCorrect = false,
                feedbackMessages = listOf("Houd de handvorm nog even stabiel."),
            )
        }

        val actualFrames = input.takeLast(requiredFrames).mapNotNull { LandmarkNormalizer.normalize(it) }
        val expected = template.frames.firstOrNull()?.let { LandmarkNormalizer.normalize(it) }
        if (actualFrames.size != requiredFrames || expected == null) return invalidPoseFeedback()

        val meanDistance = actualFrames
            .flatMap { actual ->
                actual.landmarks.zip(expected.landmarks)
                    .map { (first, second) -> euclideanDistance(first, second) }
            }
            .average()
            .toFloat()
        val score = scoreFromDistance(meanDistance, maxDistance)
        val isCorrect = score >= correctThreshold
        return FeedbackResult(
            score = score,
            isCorrect = isCorrect,
            feedbackMessages = when {
                isCorrect -> listOf("Goed gedaan! Houd deze positie even vast.")
                score >= 0.5f -> listOf("Bijna goed. Kijk nog eens naar de stand van je vingers.")
                else -> listOf("Breng je hele hand in beeld en vorm het gebaar opnieuw.")
            },
        )
    }

    private fun euclideanDistance(first: LandmarkPoint, second: LandmarkPoint): Float {
        val dx = first.x - second.x
        val dy = first.y - second.y
        val dz = first.z - second.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun invalidPoseFeedback() = FeedbackResult(
        score = 0f,
        isCorrect = false,
        feedbackMessages = listOf("Houd één hand volledig in beeld."),
    )

    private companion object {
        const val DEFAULT_MAX_DISTANCE = 1.25f
        const val DEFAULT_CORRECT_THRESHOLD = 0.75f
    }
}
