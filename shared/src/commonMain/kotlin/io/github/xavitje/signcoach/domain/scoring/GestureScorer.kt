package io.github.xavitje.signcoach.domain.scoring

import io.github.xavitje.signcoach.data.model.FeedbackResult
import io.github.xavitje.signcoach.data.model.HandPose
import io.github.xavitje.signcoach.data.model.SignTemplate

interface GestureScorer {
    fun score(input: List<HandPose>, template: SignTemplate): FeedbackResult
}

internal fun scoreFromDistance(meanDistance: Float, maxDistance: Float): Float =
    if (!meanDistance.isFinite() || maxDistance <= 0f) 0f
    else (1f - meanDistance / maxDistance).coerceIn(0f, 1f)
