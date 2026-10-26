package io.github.xavitje.signcoach

import io.github.xavitje.signcoach.presentation.StudentIdleGuard
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudentIdleGuardTest {
    private val limit = StudentIdleGuard.IDLE_LIMIT_MS

    @Test
    fun studentAwayTooLongIsLoggedOut() {
        assertTrue(StudentIdleGuard.shouldLogout(isStudent = true, backgroundedAt = 1_000L, now = 1_000L + limit + 1))
    }

    @Test
    fun studentBackQuicklyStaysLoggedIn() {
        assertFalse(StudentIdleGuard.shouldLogout(isStudent = true, backgroundedAt = 1_000L, now = 1_000L + limit - 1))
    }

    @Test
    fun teacherIsNeverLoggedOut() {
        assertFalse(StudentIdleGuard.shouldLogout(isStudent = false, backgroundedAt = 1_000L, now = 1_000L + limit * 10))
    }

    @Test
    fun appInForegroundIsNotIdle() {
        assertFalse(StudentIdleGuard.shouldLogout(isStudent = true, backgroundedAt = 0L, now = limit * 10))
    }
}
