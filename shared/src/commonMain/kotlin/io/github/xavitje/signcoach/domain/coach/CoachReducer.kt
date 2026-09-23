package io.github.xavitje.signcoach.domain.coach

import io.github.xavitje.signcoach.data.model.CoachUiState
import io.github.xavitje.signcoach.data.model.FeedbackResult
import io.github.xavitje.signcoach.data.model.HandPose
import io.github.xavitje.signcoach.data.model.SignTemplate
import io.github.xavitje.signcoach.data.model.TrackerStatus

object CoachReducer {
    private const val MAX_RECENT_POSES = 60

    fun reduce(state: CoachUiState, intent: CoachIntent): CoachUiState = when (intent) {
        CoachIntent.LoadTemplates -> state.copy(error = null)
        is CoachIntent.SelectTemplate -> {
            if (state.templates.any { it.id == intent.id }) {
                state.copy(
                    selectedTemplateId = intent.id,
                    recentPoses = emptyList(),
                    feedback = null,
                    error = null,
                    isCompleted = false,
                    earnedXp = null,
                )
            } else {
                state.copy(error = io.github.xavitje.signcoach.domain.AppError.GestureNotAvailable)
            }
        }
        CoachIntent.StartPractice -> {
            if (state.selectedTemplateId == null) {
                state.copy(error = io.github.xavitje.signcoach.domain.AppError.NoGestureSelected)
            } else {
                state.copy(
                    status = TrackerStatus.STARTING,
                    recentPoses = emptyList(),
                    feedback = null,
                    error = null,
                    isCompleted = false,
                    earnedXp = null,
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
            error = if (state.selectedTemplateId == null) io.github.xavitje.signcoach.domain.AppError.NoGestureSelected else null,
            isCompleted = false,
            earnedXp = null,
        )
        is CoachIntent.CompletePractice -> state.copy(
            status = TrackerStatus.STOPPED, // We stop the tracker when practice is completed
            error = null,
            isCompleted = true,
        )
    }

    fun templatesLoaded(state: CoachUiState, templates: List<SignTemplate>): CoachUiState {
        val autoSelectedId = if (state.selectedTemplateId == null && templates.size == 1) templates.first().id else state.selectedTemplateId
        return state.copy(templates = templates, selectedTemplateId = autoSelectedId, error = null)
    }

    fun templateLoadFailed(state: CoachUiState): CoachUiState = state.copy(
        status = TrackerStatus.ERROR,
        error = io.github.xavitje.signcoach.domain.AppError.TemplateLoadFailed,
    )

    fun trackerStatusChanged(
        state: CoachUiState,
        status: TrackerStatus,
        error: io.github.xavitje.signcoach.domain.AppError? = null,
    ): CoachUiState = state.copy(status = status, error = error)

    fun poseReceived(state: CoachUiState, pose: HandPose): CoachUiState = state.copy(
        recentPoses = (state.recentPoses + pose).takeLast(MAX_RECENT_POSES),
    )

    fun feedbackReceived(state: CoachUiState, feedback: FeedbackResult): CoachUiState =
        state.copy(feedback = feedback)
}
