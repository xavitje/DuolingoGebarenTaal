package com.example.signcoach.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SignTemplate(
    val id: String,
    val name: String,
    val frames: List<HandPose>,
    val rules: List<GestureRule>,
)
