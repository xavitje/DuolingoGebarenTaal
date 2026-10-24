package io.github.xavitje.signcoach.domain.coach

/**
 * Telt herhalingen binnen één les. Pure functie zodat de regels los te testen zijn (zie
 * RepCounterTest); [io.github.xavitje.signcoach.presentation.CoachViewModel] houdt de staat bij.
 *
 * Een herhaling telt als de score minstens [HOLD_SCORE] is gedurende [HOLD_MS]. Daarna moet de
 * score eerst onder [RELEASE_SCORE] zakken (hand laten zakken) voordat de volgende kan tellen.
 */
object RepCounter {
    const val HOLD_SCORE = 0.90f
    const val RELEASE_SCORE = 0.60f
    const val HOLD_MS = 1500L

    data class Step(
        val repsDone: Int,
        val waitingForRelease: Boolean,
        val highScoreSinceMs: Long?,
        /** True precies op het moment dat de laatste herhaling gehaald is. */
        val completed: Boolean,
    )

    fun step(
        score: Float,
        running: Boolean,
        timestampMs: Long,
        highScoreSinceMs: Long?,
        waitingForRelease: Boolean,
        repsDone: Int,
        repsRequired: Int,
    ): Step {
        if (!running || repsDone >= repsRequired) {
            return Step(repsDone, waitingForRelease, null, completed = false)
        }
        if (waitingForRelease) {
            return Step(repsDone, waitingForRelease = score >= RELEASE_SCORE, highScoreSinceMs = null, completed = false)
        }
        if (score < HOLD_SCORE) {
            return Step(repsDone, false, null, completed = false)
        }
        val since = highScoreSinceMs ?: timestampMs
        if (timestampMs - since < HOLD_MS) {
            return Step(repsDone, false, since, completed = false)
        }
        val reps = repsDone + 1
        return if (reps >= repsRequired) {
            Step(reps, waitingForRelease = false, highScoreSinceMs = null, completed = true)
        } else {
            Step(reps, waitingForRelease = true, highScoreSinceMs = null, completed = false)
        }
    }
}
