package io.github.xavitje.signcoach.domain.normalize

import io.github.xavitje.signcoach.data.model.LandmarkPoint

data class CanonicalPose(
    val landmarks: List<LandmarkPoint>,
    val timestampMs: Long,
)
