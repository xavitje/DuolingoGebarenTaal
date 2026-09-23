package io.github.xavitje.signcoach.data.model

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

data class CoachUiState(
    val status: TrackerStatus = TrackerStatus.STOPPED,
    val templates: List<SignTemplate> = emptyList(),
    val selectedTemplateId: String? = null,
    val recentPoses: List<HandPose> = emptyList(),
    val feedback: FeedbackResult? = null,
    val error: io.github.xavitje.signcoach.domain.AppError? = null,
    val isCompleted: Boolean = false,
    /** Aantal XP verdiend met de zojuist afgeronde oefening — voor de "+X XP"-melding op het
     * successcherm. Null zolang er nog geen (nieuwe) oefening is afgerond. */
    val earnedXp: Int? = null,
)
