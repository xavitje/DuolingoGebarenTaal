package com.example.signcoach.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float,
)
