package io.github.xavitje.signcoach

import io.github.xavitje.signcoach.data.model.CurriculumGesture
import io.github.xavitje.signcoach.data.model.CurriculumUnit
import io.github.xavitje.signcoach.data.model.LessonStatus
import io.github.xavitje.signcoach.data.model.LessonStatus.AVAILABLE
import io.github.xavitje.signcoach.data.model.LessonStatus.COMING_SOON
import io.github.xavitje.signcoach.data.model.LessonStatus.COMPLETED
import io.github.xavitje.signcoach.data.model.LessonStatus.LOCKED
import io.github.xavitje.signcoach.domain.progress.LessonUnlocker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LessonUnlockerTest {

    private fun g(id: String, template: Boolean = true, video: String? = null) = CurriculumGesture(
        id = id, name = id, category = "letter", orderIndex = 0, unitId = "u",
        hasTemplate = template, demoVideoUrl = video,
    )

    private fun statuses(list: List<CurriculumGesture>, done: Set<String> = emptySet()): List<LessonStatus> =
        LessonUnlocker.applyToUnit(list, done).map { it.status }

    @Test
    fun firstLessonIsAlwaysOpenForNewStudent() {
        assertEquals(listOf(AVAILABLE, LOCKED, LOCKED), statuses(listOf(g("a"), g("b"), g("c"))))
    }

    @Test
    fun completingALessonOpensTheNextOne() {
        assertEquals(listOf(COMPLETED, AVAILABLE, LOCKED), statuses(listOf(g("a"), g("b"), g("c")), setOf("a")))
    }

    @Test
    fun lessonWithOnlyAVideoIsStillOpen() {
        assertEquals(listOf(AVAILABLE, LOCKED), statuses(listOf(g("a", template = false, video = "https://x/a.mp4"), g("b"))))
    }

    @Test
    fun lessonWithoutContentDoesNotBlockThePath() {
        assertEquals(
            listOf(COMPLETED, COMING_SOON, AVAILABLE),
            statuses(listOf(g("a"), g("b", template = false), g("c")), setOf("a")),
        )
    }

    @Test
    fun comingSoonAfterALockedLessonDoesNotOpenTheRest() {
        assertEquals(
            listOf(AVAILABLE, LOCKED, COMING_SOON, LOCKED),
            statuses(listOf(g("a"), g("b"), g("c", template = false), g("d"))),
        )
    }

    @Test
    fun everyUnitStartsOpen() {
        val units = listOf(
            CurriculumUnit("alphabet", "Alfabet", "", listOf(g("a"), g("b"))),
            CurriculumUnit("numbers", "Cijfers", "", listOf(g("1"), g("2"))),
        )
        val result = LessonUnlocker.apply(units, emptySet())
        assertEquals(AVAILABLE, result[1].gestures[0].status)
        assertEquals("a", LessonUnlocker.nextLessonId(result))
    }

    @Test
    fun noNextLessonWhenNothingHasContent() {
        val units = listOf(CurriculumUnit("core", "Basis", "", listOf(g("x", template = false))))
        assertNull(LessonUnlocker.nextLessonId(LessonUnlocker.apply(units, emptySet())))
    }
}
