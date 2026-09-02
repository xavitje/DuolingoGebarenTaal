package com.example.signcoach

import com.example.signcoach.data.model.HandPose
import com.example.signcoach.domain.scoring.DynamicGestureScorer
import com.example.signcoach.domain.scoring.StaticGestureScorer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GestureScorerTest {
    private val staticScorer = StaticGestureScorer()

    @Test
    fun perfectPoseScoresOne() {
        val frame = pose()
        val result = staticScorer.score(listOf(frame), staticTemplate(frame = frame))
        assertEquals(1f, result.score, 0.0001f)
        assertTrue(result.isCorrect)
    }

    @Test
    fun smallAndLargeDeviationsProduceOrderedScores() {
        val template = staticTemplate()
        val small = pose(transform = { index, point -> if (index == 8) point.copy(x = point.x + 0.02f) else point })
        val large = pose(transform = { index, point -> if (index > 4) point.copy(x = point.x + 0.4f) else point })
        val smallResult = staticScorer.score(listOf(small), template)
        val largeResult = staticScorer.score(listOf(large), template)
        assertTrue(smallResult.score > largeResult.score)
        assertFalse(largeResult.isCorrect)
    }

    @Test
    fun missingLandmarksReturnSafeFeedback() {
        val result = staticScorer.score(listOf(HandPose(emptyList(), 0)), staticTemplate())
        assertEquals(0f, result.score)
        assertFalse(result.isCorrect)
    }

    @Test
    fun dynamicDirectionDistinguishesRightFromLeft() {
        val scorer = DynamicGestureScorer()
        val right = listOf(pose(0.3f, 0), pose(0.45f, 200), pose(0.6f, 400))
        val left = right.reversed().mapIndexed { index, frame -> frame.copy(timestampMs = index * 200L) }
        assertTrue(scorer.score(right, dynamicTemplate()).isCorrect)
        assertFalse(scorer.score(left, dynamicTemplate()).isCorrect)
    }
}
