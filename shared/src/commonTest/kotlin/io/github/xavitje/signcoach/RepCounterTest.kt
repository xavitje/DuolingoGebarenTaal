package io.github.xavitje.signcoach

import io.github.xavitje.signcoach.domain.coach.RepCounter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepCounterTest {

    /** Speelt een reeks (score, tijd)-frames af en geeft de laatste stap terug + of er voltooid is. */
    private fun play(frames: List<Pair<Float, Long>>, required: Int = 3): Pair<RepCounter.Step, Boolean> {
        var reps = 0
        var waiting = false
        var since: Long? = null
        var completedAny = false
        var last = RepCounter.Step(0, false, null, false)
        for ((score, t) in frames) {
            last = RepCounter.step(score, true, t, since, waiting, reps, required)
            reps = last.repsDone; waiting = last.waitingForRelease; since = last.highScoreSinceMs
            if (last.completed) completedAny = true
        }
        return last to completedAny
    }

    private fun hold(from: Long) = listOf(0.95f to from, 0.95f to from + 800, 0.95f to from + 1600)
    private fun release(at: Long) = listOf(0.3f to at)

    @Test
    fun oneHoldCountsOneRep() {
        val (step, done) = play(hold(0))
        assertEquals(1, step.repsDone)
        assertTrue(step.waitingForRelease)
        assertFalse(done)
    }

    @Test
    fun keepingTheHandUpDoesNotCountTwice() {
        val (step, _) = play(hold(0) + hold(2000))
        assertEquals(1, step.repsDone)
    }

    @Test
    fun threeHoldsWithReleaseCompleteTheLesson() {
        val (step, done) = play(hold(0) + release(2000) + hold(3000) + release(5000) + hold(6000))
        assertEquals(3, step.repsDone)
        assertTrue(done)
    }

    @Test
    fun shortHoldDoesNotCount() {
        val (step, _) = play(listOf(0.95f to 0L, 0.95f to 1000L, 0.5f to 1200L))
        assertEquals(0, step.repsDone)
    }
}
