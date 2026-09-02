package com.example.signcoach.domain.coach

import com.example.signcoach.data.model.CoachUiState
import com.example.signcoach.data.model.FeedbackResult
import com.example.signcoach.data.model.HandPose
import com.example.signcoach.data.model.SignTemplate
import com.example.signcoach.data.model.TrackerStatus

object CoachReducer {
    private const val MAX_RECENT_POSES = 60

    fun reduce(state: CoachUiState, intent: CoachIntent): CoachUiState = when (intent) {
        CoachIntent.LoadTemplates -> state.copy(errorMessage = null)
        is CoachIntent.SelectTemplate -> {
            if (state.templates.any { it.id == intent.id }) {
                state.copy(
                    selectedTemplateId = intent.id,
                    recentPoses = emptyList(),
                    feedback = null,
                    errorMessage = null,
                )
            } else {
                state.copy(errorMessage = "Dit gebaar is niet beschikbaar.")
            }
        }
        CoachIntent.StartPractice -> {
            if (state.selectedTemplateId == null) {
                state.copy(errorMessage = "Kies eerst een gebaar.")
            } else {
                state.copy(
                    status = TrackerStatus.STARTING,
                    recentPoses = emptyList(),
                    feedback = null,
                    errorMessage = null,
                )
            }
        }
        CoachIntent.StopPractice -> state.copy(
            status = TrackerStatus.STOPPED,
            recentPoses = emptyList(),
        )
        CoachIntent.Retry -> state.copy(
            status = if (state.selectedTemplateId == null) TrackerStatus.STOPPED else TrackerStatus.STARTING,
            recentPoses = emptyList(),
            feedback = null,
            errorMessage = if (state.selectedTemplateId == null) "Kies eerst een gebaar." else null,
        )
    }

    fun templatesLoaded(state: CoachUiState, templates: List<SignTemplate>): CoachUiState =
        state.copy(templates = templates, errorMessage = null)

    fun templateLoadFailed(state: CoachUiState): CoachUiState = state.copy(
        status = TrackerStatus.ERROR,
        errorMessage = "De gebaren konden niet worden geladen.",
    )

    fun trackerStatusChanged(
        state: CoachUiState,
        status: TrackerStatus,
        errorMessage: String? = null,
    ): CoachUiState = state.copy(status = status, errorMessage = errorMessage)

    fun poseReceived(state: CoachUiState, pose: HandPose): CoachUiState = state.copy(
        recentPoses = (state.recentPoses + pose).takeLast(MAX_RECENT_POSES),
    )

    fun feedbackReceived(state: CoachUiState, feedback: FeedbackResult): CoachUiState =
        state.copy(feedback = feedback)
}
