package io.github.xavitje.signcoach.presentation

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.remote.SchoolApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Clock

/**
 * Logt een leerling automatisch uit als de app langer dan [IDLE_LIMIT_MS] op de achtergrond
 * stond (of afgesloten was). Op school delen kinderen vaak tablets: zo komt de volgende leerling
 * niet in het account van de vorige terecht (DPIA, fase 4). Docenten en losse gebruikers blijven
 * gewoon ingelogd.
 *
 * Het platform meldt de wissels: Android in MainActivity.onStop/onStart, iOS via de
 * UIApplication-notificaties in MainViewController.
 */
object StudentIdleGuard {
    const val IDLE_LIMIT_MS: Long = 10 * 60 * 1000L

    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Vuurt als een lopende leerlingsessie net is verlopen; AuthViewModel zet dan de UI op uitgelogd. */
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    fun onAppBackgrounded(now: Long = Clock.System.now().toEpochMilliseconds()) {
        SessionStore.setBackgroundedAt(now)
    }

    fun onAppForegrounded(now: Long = Clock.System.now().toEpochMilliseconds()) {
        if (expireIfIdle(now)) _expired.tryEmit(Unit)
        SessionStore.setBackgroundedAt(0L)
    }

    /** Wist de lokale sessie als die van een leerling is en te lang stillag. True = uitgelogd. */
    fun expireIfIdle(now: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        val session = SessionStore.load() ?: return false
        val isStudent = SchoolApi.isSchoolStudentEmail(session.email)
        if (!shouldLogout(isStudent, SessionStore.getBackgroundedAt(), now)) return false
        SessionStore.clear()
        return true
    }

    fun shouldLogout(isStudent: Boolean, backgroundedAt: Long, now: Long): Boolean =
        isStudent && backgroundedAt > 0L && now - backgroundedAt > IDLE_LIMIT_MS
}
