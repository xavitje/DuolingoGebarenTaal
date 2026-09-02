package com.example.signcoach.data.model

import kotlinx.serialization.Serializable

@Serializable
data class HandPose(
    val landmarks: List<LandmarkPoint>,
    val timestampMs: Long,
)
