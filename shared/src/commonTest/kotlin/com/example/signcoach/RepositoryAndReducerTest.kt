package com.example.signcoach

import com.example.signcoach.data.model.CoachUiState
import com.example.signcoach.data.model.SignTemplate
import com.example.signcoach.data.model.TrackerStatus
import com.example.signcoach.data.repository.AssetTemplateRepository
import com.example.signcoach.domain.coach.CoachIntent
import com.example.signcoach.domain.coach.CoachReducer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryAndReducerTest {
    @Test
    fun validJsonLoadsAllRequiredTemplates() {
        val templates = requiredTemplates()
        val result = AssetTemplateRepository(Json.encodeToString(templates)).load()
        assertTrue(result.isSuccess)
        assertEquals(4, result.getOrThrow().size)
    }

    @Test
    fun malformedOrIncompleteJsonFailsSafely() {
        assertTrue(AssetTemplateRepository("not json").load().isFailure)
        assertTrue(AssetTemplateRepository("[]").load().isFailure)
    }

    @Test
    fun reducerTransitionsThroughSelectionPracticeAndStop() {
        val loaded = CoachReducer.templatesLoaded(CoachUiState(), requiredTemplates())
        val selected = CoachReducer.reduce(loaded, CoachIntent.SelectTemplate("letter_a"))
        val started = CoachReducer.reduce(selected, CoachIntent.StartPractice)
        val stopped = CoachReducer.reduce(started, CoachIntent.StopPractice)
        assertEquals("letter_a", selected.selectedTemplateId)
        assertEquals(TrackerStatus.STARTING, started.status)
        assertEquals(TrackerStatus.STOPPED, stopped.status)
    }

    @Test
    fun recentPoseWindowIsBounded() {
        val state = (0L..70L).fold(CoachUiState()) { current, timestamp ->
            CoachReducer.poseReceived(current, pose(timestampMs = timestamp))
        }
        assertEquals(60, state.recentPoses.size)
        assertEquals(11L, state.recentPoses.first().timestampMs)
    }

    private fun requiredTemplates(): List<SignTemplate> = listOf(
        staticTemplate("letter_a"),
        staticTemplate("letter_b"),
        staticTemplate("letter_l"),
        dynamicTemplate(),
    )
}
