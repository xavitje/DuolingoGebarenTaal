package com.example.signcoach.domain.normalize

import com.example.signcoach.data.model.LandmarkPoint

data class CanonicalPose(
    val landmarks: List<LandmarkPoint>,
    val timestampMs: Long,
)
