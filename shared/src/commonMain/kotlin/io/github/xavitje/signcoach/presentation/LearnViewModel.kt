package io.github.xavitje.signcoach.presentation

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.model.CurriculumGesture
import io.github.xavitje.signcoach.data.model.CurriculumUnit
import io.github.xavitje.signcoach.data.model.LessonStatus
import io.github.xavitje.signcoach.data.model.SignLanguage
import io.github.xavitje.signcoach.data.model.SignTemplate
import io.github.xavitje.signcoach.data.remote.ProgressApi
import io.github.xavitje.signcoach.data.repository.CurriculumRepository
import io.github.xavitje.signcoach.data.repository.OfflineProgressRepository
import io.github.xavitje.signcoach.domain.progress.LessonUnlocker
import io.github.xavitje.signcoach.log.AppLogger
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

data class LearnUiState(
    val units: List<CurriculumUnit> = emptyList(),
    val isLoading: Boolean = true,
    /** Het geselecteerde gebaar waarvoor het oefen-scherm geopend moet worden. Null = geen. */
    val selectedGesture: CurriculumGesture? = null,
    /** De les die de leerling nu moet doen (krijgt het START-label). */
    val nextLessonId: String? = null,
)

/**
 * ViewModel voor de Leren-tab. Laadt het curriculum, combineert dat met de voortgang van de
 * leerling (welke lessen gehaald zijn) en beheert de geselecteerde les.
 *
 * Ontgrendelen gebeurt op VOORTGANG via [LessonUnlocker] — niet meer op "heeft dit gebaar een
 * herkenningstemplate". Voorheen zat elke les zonder template op slot, waardoor bv. bij ASL de
 * eerste les nooit open ging.
 */
class LearnViewModel(
    private val curriculumRepository: CurriculumRepository = CurriculumRepository(),
    private val offlineProgress: OfflineProgressRepository = OfflineProgressRepository(),
    private val progressApi: ProgressApi = ProgressApi(),
    private val syncManager: SyncManager = AppSync.manager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(LearnUiState())
    val state: StateFlow<LearnUiState> = _state.asStateFlow()

    /** Curriculum zoals geladen, zonder voortgangsstatus — basis voor elke herberekening. */
    private var rawUnits: List<CurriculumUnit> = emptyList()

    private fun signLanguage(): String = SignLanguage.fromCode(SessionStore.getSignLanguage()).code

    fun loadCurriculum(availableTemplates: List<SignTemplate>) {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            val language = SignLanguage.fromCode(SessionStore.getSignLanguage())
            rawUnits = curriculumRepository.load(availableTemplates, language)
            applyProgress()
            _state.update { it.copy(isLoading = false) }
            refreshFromServer()
        }
    }

    /** Herberekent de status van elke les op basis van de lokaal bekende gehaalde lessen. */
    private fun applyProgress() {
        val userId = SessionStore.load()?.userId
        val completed = if (userId != null) offlineProgress.completedGestureIds(userId, signLanguage()) else emptySet()
        val units = LessonUnlocker.apply(rawUnits, completed)
        _state.update { it.copy(units = units, nextLessonId = LessonUnlocker.nextLessonId(units)) }
    }

    /** Vult de lokale voortgang aan met wat de server weet (bv. gehaald op een ander apparaat).
     * Best-effort: zonder netwerk blijft de lokale waarheid staan. */
    private suspend fun refreshFromServer() {
        val session = SessionStore.load() ?: return
        val language = signLanguage()
        progressApi.fetchCompletedGestureIds(session.accessToken, language)
            .onSuccess { ids ->
                offlineProgress.mergeServerCompleted(session.userId, language, ids)
                applyProgress()
            }
            .onFailure { AppLogger.d("SignCoach", "Voortgang van server ophalen mislukt, lokale voortgang blijft: ${it.message}") }
    }

    fun selectGesture(gesture: CurriculumGesture) {
        if (gesture.status != LessonStatus.AVAILABLE && gesture.status != LessonStatus.COMPLETED) return
        _state.update { it.copy(selectedGesture = gesture) }
    }

    /** Sluit de les en werkt het leerpad bij (een net gehaalde les opent de volgende). */
    fun clearSelection() {
        _state.update { it.copy(selectedGesture = null) }
        scope.launch { applyProgress() }
    }

    /**
     * Opent direct de volgende les na een gehaalde les ("Volgend gebaar"). [justCompleted] telt
     * als gehaald, ook als het lokaal opslaan nog loopt. Is er geen volgende les, dan sluit de les.
     */
    fun openNextLesson(justCompleted: CurriculumGesture) {
        scope.launch {
            val userId = SessionStore.load()?.userId
            val completed = (if (userId != null) offlineProgress.completedGestureIds(userId, signLanguage()) else emptySet()) +
                justCompleted.id
            val units = LessonUnlocker.apply(rawUnits, completed)
            val nextId = LessonUnlocker.nextLessonId(units)
            val next = units.asSequence().flatMap { it.gestures.asSequence() }.firstOrNull { it.id == nextId }
            _state.update { it.copy(units = units, nextLessonId = nextId, selectedGesture = next) }
        }
    }

    /** Is er na [current] nog een les om te openen? (voor het tonen van "Volgend gebaar") */
    fun hasNextLesson(current: CurriculumGesture): Boolean {
        val userId = SessionStore.load()?.userId
        val completed = (if (userId != null) offlineProgress.completedGestureIds(userId, signLanguage()) else emptySet()) + current.id
        return LessonUnlocker.nextLessonId(LessonUnlocker.apply(rawUnits, completed)) != null
    }

    /**
     * Rondt een les af zonder camera-herkenning (gebaar heeft alleen een demovideo): de leerling
     * heeft het gebaar nagedaan en geeft zelf aan dat het lukte. Telt als gehaald en levert XP op
     * via dezelfde offline-first route als een camera-oefening. Geeft de verdiende XP terug.
     */
    suspend fun completeWithSelfCheck(gesture: CurriculumGesture): Int? {
        val session = SessionStore.load() ?: return null
        val xp = runCatching {
            offlineProgress.recordPracticeOffline(session.userId, gesture.id, 1f, signLanguage())
        }.getOrNull()
        syncManager.triggerSyncNow()
        applyProgress()
        return xp
    }
}
