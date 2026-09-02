package com.example.signcoach.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackResult(
    val score: Float,
    val isCorrect: Boolean,
    val feedbackMessages: List<String>,
)
