package io.github.xavitje.signcoach.sync

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.remote.BadgeApi
import io.github.xavitje.signcoach.data.remote.ProfileApi
import io.github.xavitje.signcoach.data.remote.ProgressApi
import io.github.xavitje.signcoach.data.remote.SupabaseAuthApi
import io.github.xavitje.signcoach.data.repository.CrashReportRepository
import io.github.xavitje.signcoach.data.repository.OfflineProgressRepository
import io.github.xavitje.signcoach.domain.AppError
import io.github.xavitje.signcoach.platform.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Zero Trust / offline-first: neemt nooit aan dat een sync lukt. Speelt de lokale wachtrij van
 * nog-niet-bevestigde oefeningen (zie [OfflineProgressRepository]) in volgorde af naar Supabase
 * zodra er verbinding is, en cachet daarna de authoritatieve serverstatus lokaal terug. Bij elke
 * mislukking (geen netwerk, server down, verlopen sessie) blijft de wachtrij gewoon intact voor
 * een volgende poging — er gaat nooit data verloren, alleen uitgesteld.
 */
class SyncManager(
    private val offlineRepo: OfflineProgressRepository = OfflineProgressRepository(),
    private val progressApi: ProgressApi = ProgressApi(),
    private val profileApi: ProfileApi = ProfileApi(),
    private val badgeApi: BadgeApi = BadgeApi(),
    private val authApi: SupabaseAuthApi = SupabaseAuthApi(),
    private val networkMonitor: NetworkMonitor = NetworkMonitor(),
    private val crashReportRepo: CrashReportRepository = CrashReportRepository(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val mutex = Mutex()
    private var started = false

    /** Eén keer aanroepen bij app-start (zie App.kt/MainActivity). Start de netwerkmonitor en
     * probeert automatisch te synchroniseren zodra de verbinding terugkomt. */
    fun start() {
        if (started) return
        started = true
        networkMonitor.start()
        scope.launch {
            networkMonitor.isOnline.collect { online ->
                _syncState.update { it.copy(isOnline = online) }
                if (online) syncNow()
            }
        }
        // Ook meteen een eerste poging bij opstarten (voor het geval de status al "online" is
        // en er nog dingen in de wachtrij staan van een vorige sessie).
        scope.launch { syncNow() }
    }

    /** Fire-and-forget-variant voor aanroeppunten die niet zelf al in een coroutine zitten
     * (bv. meteen na het lokaal opslaan van een oefening — "misschien is er toch al verbinding"). */
    fun triggerSyncNow() {
        scope.launch { syncNow() }
    }

    /**
     * Verwerkt de wachtrij en haalt daarna de serverstatus op. Veilig om vaak/overlappend aan te
     * roepen: een mutex zorgt dat er nooit twee syncs tegelijk lopen.
     */
    suspend fun syncNow() {
        if (mutex.isLocked) return
        mutex.withLock {
            // Crashrapporten mogen ook zonder ingelogde sessie verstuurd worden (anonieme
            // crash vóór inloggen) — dus dit gebeurt vóór de sessie-check hieronder.
            runCatching { crashReportRepo.flushPending(SessionStore.load()?.accessToken) }

            val session = SessionStore.load() ?: return@withLock
            var accessToken = session.accessToken
            _syncState.update { it.copy(isSyncing = true) }

            val pending = offlineRepo.pendingForUser(session.userId)
            var alreadyRefreshedToken = false

            for (item in pending) {
                var attempt = progressApi.recordPractice(accessToken, item.gestureId, item.score.toFloat())

                if (attempt.isFailure && attempt.exceptionOrNull() is AppError.Unauthorized && !alreadyRefreshedToken) {
                    alreadyRefreshedToken = true
                    authApi.refresh(session.refreshToken).onSuccess { refreshed ->
                        SessionStore.save(refreshed.accessToken, refreshed.refreshToken, refreshed.user.id, refreshed.user.email)
                        accessToken = refreshed.accessToken
                    }
                    attempt = progressApi.recordPractice(accessToken, item.gestureId, item.score.toFloat())
                }

                if (attempt.isSuccess) {
                    offlineRepo.removePending(item.id)
                } else {
                    // Stop hier: bewaar de rest van de wachtrij (in volgorde!) voor een volgende
                    // poging. Nooit data weggooien omdat één poging mislukte.
                    _syncState.update { it.copy(isSyncing = false, lastError = attempt.exceptionOrNull()) }
                    return@withLock
                }
            }

            // Wachtrij leeg (of was al leeg): haal de authoritatieve staat op en cache die lokaal.
            var profileResult = profileApi.getProfile(accessToken)
            if (profileResult.isFailure && profileResult.exceptionOrNull() is AppError.NotFound) {
                // Nieuw account zonder profielrij (Fase A transitie) — zelfde auto-create als
                // voorheen in ProgressViewModel.
                profileApi.createProfile(accessToken, session.userId, null)
                profileResult = profileApi.getProfile(accessToken)
            }
            badgeApi.checkAndAwardBadges(accessToken)

            val todayXp = progressApi.getTodayXp(accessToken).getOrDefault(0)
            profileResult.onSuccess { profile ->
                offlineRepo.cacheServerProfile(session.userId, profile, todayXp)
            }

            _syncState.update {
                it.copy(
                    isSyncing = false,
                    lastSyncedAtMillis = if (profileResult.isSuccess) Clock.System.now().toEpochMilliseconds() else it.lastSyncedAtMillis,
                    lastError = profileResult.exceptionOrNull(),
                )
            }
        }
    }
}

data class SyncState(
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
    val lastError: Throwable? = null,
)

/** Eén gedeelde SyncManager-instantie voor de hele app, zodat er maar één netwerkmonitor en
 * één wachtrij-verwerker actief is (zie AppSync.manager.start() in MainActivity/App.kt). */
object AppSync {
    val manager: SyncManager by lazy { SyncManager() }
}
