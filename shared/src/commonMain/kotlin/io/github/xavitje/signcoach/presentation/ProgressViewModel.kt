package io.github.xavitje.signcoach.presentation

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.log.AppLogger
import io.github.xavitje.signcoach.data.remote.BadgeApi
import io.github.xavitje.signcoach.data.remote.ProfileApi
import io.github.xavitje.signcoach.data.repository.OfflineProgressRepository
import io.github.xavitje.signcoach.domain.AppError
import io.github.xavitje.signcoach.sync.AppSync
import io.github.xavitje.signcoach.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgressUiState(
    val isLoading: Boolean = true,
    val profile: ProfileApi.UserProfile? = null,
    /** Vandaag verdiende XP — komt uit de lokale cache (zie [OfflineProgressRepository]), niet
     * rechtstreeks van het netwerk, zodat dit ook zonder verbinding altijd klopt. */
    val todayXp: Int = 0,
    val error: AppError? = null,
    /** Alle mogelijke badges (Fase D), gesorteerd op weergavevolgorde. */
    val allBadges: List<BadgeApi.Badge> = emptyList(),
    /** IDs van de badges die de gebruiker al behaald heeft. */
    val earnedBadgeIds: Set<String> = emptySet(),
    /** Zero Trust / offline-first: aantal oefeningen dat nog wacht op synchronisatie met de
     * server. >0 betekent "je ziet voorlopig lokale cijfers, dit klopt zodra je weer online bent". */
    val pendingSyncCount: Int = 0,
    val isSyncing: Boolean = false,
)

/**
 * Beheert de staat voor de Voortgang-tab. Zero Trust / offline-first: leest XP/streak altijd
 * eerst uit de lokale database ([OfflineProgressRepository]) — dit werkt gegarandeerd, ook zonder
 * netwerk — en probeert daarna op de achtergrond te synchroniseren via [SyncManager]. Badges
 * blijven (zoals voorheen) losse, best-effort netwerkaanroepen die het scherm niet blokkeren.
 */
class ProgressViewModel(
    private val profileApi: ProfileApi = ProfileApi(),
    private val badgeApi: BadgeApi = BadgeApi(),
    private val offlineRepo: OfflineProgressRepository = OfflineProgressRepository(),
    private val syncManager: SyncManager = AppSync.manager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    init {
        loadProgress()
    }

    /** Fase D: avatar-emoji bijwerken vanuit de avatar-picker in ProfileScreen. Dit valt buiten
     * de offline-scope (alleen XP/streak zijn offline-first) — een best-effort netwerkaanroep die
     * bij falen de UI niet verandert (geen valse belofte doen dat het al is opgeslagen). */
    fun updateAvatarEmoji(emoji: String) {
        val session = SessionStore.load() ?: return
        val current = _state.value.profile ?: return
        scope.launch {
            profileApi.updateAvatarEmoji(session.accessToken, session.userId, emoji)
                .onSuccess {
                    _state.update { it.copy(profile = current.copy(avatarEmoji = emoji)) }
                }
                .onFailure {
                    AppLogger.e("SignCoach", "Avatar bijwerken mislukt: ${it.message}")
                }
        }
    }

    fun loadProgress() {
        val session = SessionStore.load()
        if (session == null) {
            _state.update { it.copy(isLoading = false, error = AppError.Unauthorized) }
            return
        }

        // Zero Trust: toon eerst wat we ZEKER lokaal weten — dit werkt altijd, ook zonder
        // netwerk. Alleen als er nog nooit iets lokaal is opgeslagen tonen we de spinner.
        val cachedProfile = offlineRepo.localProfileOrNull(session.userId)
        _state.update {
            it.copy(
                isLoading = cachedProfile == null,
                profile = cachedProfile ?: it.profile,
                todayXp = offlineRepo.localTodayXp(session.userId),
                pendingSyncCount = offlineRepo.pendingCountForUser(session.userId),
                error = if (cachedProfile != null) null else it.error,
            )
        }

        scope.launch {
            // Badges: onafhankelijk, best-effort — een badge-fout mag Voortgang niet blokkeren.
            badgeApi.fetchAllBadges()
                .onSuccess { badges -> _state.update { it.copy(allBadges = badges) } }
                .onFailure { AppLogger.e("SignCoach", "Badges ophalen mislukt: ${it.message}") }
            badgeApi.fetchEarnedBadges(session.accessToken)
                .onSuccess { earned -> _state.update { it.copy(earnedBadgeIds = earned.map { b -> b.badgeId }.toSet()) } }
                .onFailure { AppLogger.e("SignCoach", "Behaalde badges ophalen mislukt: ${it.message}") }

            // Probeer op de achtergrond te synchroniseren. Dit kan altijd falen (geen netwerk,
            // server down, verlopen sessie) — de al-getoonde lokale cijfers staan daar los van.
            syncManager.syncNow()

            val freshProfile = offlineRepo.localProfileOrNull(session.userId)
            _state.update {
                it.copy(
                    isLoading = false,
                    profile = freshProfile ?: it.profile,
                    todayXp = offlineRepo.localTodayXp(session.userId),
                    pendingSyncCount = offlineRepo.pendingCountForUser(session.userId),
                    // Alleen een harde fout tonen als we ECHT niets hebben om te tonen (bv.
                    // allereerste keer openen zonder internet) — anders blijven gewoon de
                    // lokale cijfers staan, eventueel met een "nog niet gesynchroniseerd"-hint.
                    error = if (freshProfile == null && cachedProfile == null) {
                        syncManager.syncState.value.lastError as? AppError ?: AppError.NetworkUnavailable
                    } else null,
                )
            }
        }
    }
}
