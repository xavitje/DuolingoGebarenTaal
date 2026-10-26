package io.github.xavitje.signcoach.presentation

import io.github.xavitje.signcoach.data.model.CoachUiState
import io.github.xavitje.signcoach.log.AppLogger
import io.github.xavitje.signcoach.data.model.HandPose
import io.github.xavitje.signcoach.data.model.RuleType
import io.github.xavitje.signcoach.data.model.TrackerStatus
import io.github.xavitje.signcoach.data.repository.TemplateRepository
import io.github.xavitje.signcoach.domain.coach.CoachIntent
import io.github.xavitje.signcoach.domain.coach.CoachReducer
import io.github.xavitje.signcoach.domain.coach.RepCounter
import io.github.xavitje.signcoach.domain.scoring.DynamicGestureScorer
import io.github.xavitje.signcoach.domain.scoring.GestureScorer
import io.github.xavitje.signcoach.domain.scoring.StaticGestureScorer
import io.github.xavitje.signcoach.platform.CameraController
import io.github.xavitje.signcoach.platform.HandTrackerBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.repository.OfflineProgressRepository
import io.github.xavitje.signcoach.sync.AppSync
import io.github.xavitje.signcoach.sync.SyncManager

class CoachViewModel(
    private val repository: TemplateRepository,
    private val cameraController: CameraController,
    // Zero Trust / offline-first: een afgeronde oefening wordt ALTIJD eerst lokaal opgeslagen
    // (werkt zonder netwerk); syncManager stuurt de wachtrij door zodra er verbinding is.
    private val offlineProgressRepository: OfflineProgressRepository = OfflineProgressRepository(),
    private val syncManager: SyncManager = AppSync.manager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val staticScorer: GestureScorer = StaticGestureScorer(),
    private val dynamicScorer: GestureScorer = DynamicGestureScorer(),
) {
    private val _state = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    private var tracker: HandTrackerBridge? = null
    private var poseJob: Job? = null
    private var statusJob: Job? = null
    private var practiceJob: Job? = null
    private var highScoreSinceMs: Long? = null
    private val pilotApi = io.github.xavitje.signcoach.data.remote.PilotApi()
    /** Pilotmeting: er is een hand gezien in deze poging, en is de uitkomst al gemeld? */
    private var attemptStarted = false
    private var attemptLogged = false

    fun dispatch(intent: CoachIntent) {
        AppLogger.d("SignCoach", "Dispatching intent $intent (current status=${state.value.status})")
        _state.update { CoachReducer.reduce(it, intent) }
        when (intent) {
            CoachIntent.LoadTemplates -> loadTemplates()
            CoachIntent.StartPractice, CoachIntent.Retry -> startPractice()
            CoachIntent.StopPractice -> stopPractice()
            is CoachIntent.SelectTemplate -> {
                AppLogger.d("SignCoach", "Selected template ${intent.id}")
            }
            is CoachIntent.CompletePractice -> completePractice(intent.score)
        }
    }

    private fun loadTemplates() {
        repository.load()
            .onSuccess { templates -> _state.update { CoachReducer.templatesLoaded(it, templates) } }
            .onFailure { _state.update(CoachReducer::templateLoadFailed) }
    }

    private fun startPractice() {
        val selected = selectedTemplate() ?: return
        AppLogger.d("SignCoach", "startPractice called for template ${selected.id}")
        highScoreSinceMs = null
        attemptStarted = false
        attemptLogged = false
        poseJob?.cancel()
        statusJob?.cancel()
        practiceJob?.cancel()
        practiceJob = scope.launch {
            try {
            AppLogger.d("SignCoach", "about to call cameraController.requestPermission()")
            if (!cameraController.requestPermission()) {
                AppLogger.e("SignCoach", "Camera permission NOT granted")
                _state.update { CoachReducer.trackerStatusChanged(it, TrackerStatus.NO_PERMISSION) }
                return@launch
            }

            AppLogger.d("SignCoach", "Camera permission granted, starting hand tracker...")
            val activeTracker = tracker ?: cameraController.createHandTracker().also { tracker = it }
            statusJob = launch {
                activeTracker.status.collectLatest { status ->
                    AppLogger.d("SignCoach", "Tracker status changed to $status")
                    _state.update { CoachReducer.trackerStatusChanged(it, status) }
                }
            }
            poseJob = launch {
                activeTracker.start()
                    .catch { error ->
                        AppLogger.e("SignCoach", "Tracker error: ${error.message}")
                        _state.update {
                            CoachReducer.trackerStatusChanged(
                                it,
                                TrackerStatus.ERROR,
                                io.github.xavitje.signcoach.domain.AppError.TrackerError(error.message),
                            )
                        }
                    }
                    .collect { pose ->
                        if (!pose.isValid()) return@collect
                        attemptStarted = true
                        val currentSelected = selectedTemplate() ?: return@collect
                        _state.update { current ->
                            val withPose = CoachReducer.poseReceived(current, pose)
                            val scorer = if (currentSelected.rules.any { it.type == RuleType.MOTION_DIRECTION }) {
                                dynamicScorer
                            } else {
                                staticScorer
                            }
                            val scoreResult = scorer.score(withPose.recentPoses, currentSelected)
                            
                            // Herhalingen: een herhaling telt als het gebaar minstens 90% correct is
                            // gedurende HOLD_MS. Daarna moet de leerling eerst loslaten (score onder
                            // RELEASE_SCORE) voordat de volgende telt. Na repsRequired keer is de les
                            // gehaald. Pure telling staat in RepCounter (los getest).
                            val step = RepCounter.step(
                                score = scoreResult.score,
                                running = current.status == TrackerStatus.RUNNING,
                                timestampMs = pose.timestampMs,
                                highScoreSinceMs = highScoreSinceMs,
                                waitingForRelease = current.waitingForRelease,
                                repsDone = current.repsDone,
                                repsRequired = current.repsRequired,
                            )
                            highScoreSinceMs = step.highScoreSinceMs
                            if (step.completed) {
                                scope.launch { dispatch(CoachIntent.CompletePractice(scoreResult.score)) }
                            }

                            CoachReducer.feedbackReceived(
                                withPose.copy(repsDone = step.repsDone, waitingForRelease = step.waitingForRelease),
                                scoreResult,
                            )
                        }
                    }
            }
            } catch (e: Throwable) {
                AppLogger.e("SignCoach", "startPractice coroutine crashed: $e")
                e.printStackTrace()
            }
        }
    }

    private fun completePractice(score: Float) {
        AppLogger.d("SignCoach", "completePractice called with score $score")
        highScoreSinceMs = null
        val selected = selectedTemplate() ?: return
        logAttempt(selected.id, success = true)

        // Stop de tracker (wordt al deels afgehandeld door Reducer, maar voor de zekerheid)
        poseJob?.cancel()
        statusJob?.cancel()
        scope.launch {
            tracker?.stop()
            // Zero Trust: neem nooit aan dat er een (geldige) sessie of netwerk is. Een sessie
            // moet lokaal bekend zijn (ingelogd); of die verder werkt, weet pas de sync-poging.
            val session = SessionStore.load()
            if (session == null) {
                AppLogger.e("SignCoach", "Geen sessie bekend, oefening kan niet worden opgeslagen")
                return@launch
            }
            val xpEarned = runCatching {
                offlineProgressRepository.recordPracticeOffline(session.userId, selected.id, score, SessionStore.getSignLanguage())
            }.onFailure {
                AppLogger.e("SignCoach", "Lokaal opslaan van oefening mislukt: ${it.message}")
            }.getOrNull()
            if (xpEarned != null) {
                // Korte "+X XP"-melding op het successcherm — puur cosmetisch, de opslag zelf
                // is hierboven al gebeurd (dit mag dus nooit de opslag blokkeren of laten falen).
                _state.update { it.copy(earnedXp = xpEarned) }
            }
            AppLogger.d("SignCoach", "Progress lokaal opgeslagen, wordt gesynchroniseerd zodra er verbinding is")
            // Meteen proberen te synchroniseren (kan best al verbinding zijn); als dit faalt is
            // dat geen probleem, de data staat al veilig lokaal en blijft in de wachtrij staan.
            syncManager.triggerSyncNow()
        }
    }

    private fun stopPractice() {
        AppLogger.d("SignCoach", "stopPractice called")
        highScoreSinceMs = null
        poseJob?.cancel()
        statusJob?.cancel()
        practiceJob?.cancel()
        scope.launch {
            tracker?.stop()
            _state.update { CoachReducer.trackerStatusChanged(it, TrackerStatus.STOPPED) }
        }
    }

    private fun selectedTemplate() = state.value.templates.firstOrNull {
        it.id == state.value.selectedTemplateId
    }

    /** Anonieme pilotmeting (fase 5): één keer per poging, gelukt of opgegeven. Best-effort. */
    private fun logAttempt(gestureId: String, success: Boolean) {
        if (attemptLogged || (!success && !attemptStarted)) return
        attemptLogged = true
        val token = SessionStore.load()?.accessToken ?: return
        scope.launch { runCatching { pilotApi.logAttempt(token, gestureId, success) } }
    }

    fun clear() {
        AppLogger.d("SignCoach", "ViewModel clear called")
        selectedTemplate()?.let { logAttempt(it.id, success = false) }
        highScoreSinceMs = null
        poseJob?.cancel()
        statusJob?.cancel()
        practiceJob?.cancel()
        scope.launch {
            tracker?.stop()
        }
    }

    private fun HandPose.isValid(): Boolean =
        landmarks.size == 21 && timestampMs >= 0 &&
            landmarks.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
}
