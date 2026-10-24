package io.github.xavitje.signcoach.domain.progress

import io.github.xavitje.signcoach.data.model.CurriculumGesture
import io.github.xavitje.signcoach.data.model.CurriculumUnit
import io.github.xavitje.signcoach.data.model.LessonStatus

/**
 * Bepaalt per les of die op slot zit, open staat, al gehaald is of nog geen content heeft.
 * Pure functie (geen IO) zodat de regels los te testen zijn — zie LessonUnlockerTest.
 *
 * Regels:
 * 1. De eerste les van elke unit is altijd open (anders kan een leerling nooit beginnen).
 * 2. Een les gaat open zodra de vorige les "gepasseerd" is: gehaald, of een les zonder content
 *    die zelf ook bereikbaar was. Een les zonder content blokkeert het pad dus niet.
 * 3. Een gehaalde les blijft altijd gehaald (en dus opnieuw te oefenen).
 */
object LessonUnlocker {

    fun apply(units: List<CurriculumUnit>, completedIds: Set<String>): List<CurriculumUnit> =
        units.map { unit -> unit.copy(gestures = applyToUnit(unit.gestures, completedIds)) }

    fun applyToUnit(gestures: List<CurriculumGesture>, completedIds: Set<String>): List<CurriculumGesture> {
        var previousPassed = true // regel 1
        return gestures.map { gesture ->
            val status = when {
                gesture.id in completedIds -> LessonStatus.COMPLETED
                !gesture.hasContent -> LessonStatus.COMING_SOON
                previousPassed -> LessonStatus.AVAILABLE
                else -> LessonStatus.LOCKED
            }
            previousPassed = when (status) {
                LessonStatus.COMPLETED -> true
                LessonStatus.COMING_SOON -> previousPassed
                LessonStatus.AVAILABLE, LessonStatus.LOCKED -> false
            }
            gesture.copy(status = status)
        }
    }

    /** De les die de leerling nu moet doen: de eerste open, nog niet gehaalde les. */
    fun nextLessonId(units: List<CurriculumUnit>): String? =
        units.asSequence().flatMap { it.gestures.asSequence() }
            .firstOrNull { it.status == LessonStatus.AVAILABLE }?.id
}
