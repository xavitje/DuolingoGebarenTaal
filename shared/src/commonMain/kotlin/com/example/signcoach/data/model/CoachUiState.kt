package com.example.signcoach.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class TrackerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    NO_PERMISSION,
    UNSUPPORTED,
    ERROR,
}

@Serializable
data class CoachUiState(
    val status: TrackerStatus = TrackerStatus.STOPPED,
    val templates: List<SignTemplate> = emptyList(),
    val selectedTemplateId: String? = null,
    val recentPoses: List<HandPose> = emptyList(),
    val feedback: FeedbackResult? = null,
    val errorMessage: String? = null,
)
