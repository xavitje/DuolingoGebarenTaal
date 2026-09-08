package com.example.signcoach.presentation

import com.example.signcoach.data.model.CoachUiState
import com.example.signcoach.data.model.HandPose
import com.example.signcoach.data.model.RuleType
import com.example.signcoach.data.model.TrackerStatus
import com.example.signcoach.data.repository.TemplateRepository
import com.example.signcoach.domain.coach.CoachIntent
import com.example.signcoach.domain.coach.CoachReducer
import com.example.signcoach.domain.scoring.DynamicGestureScorer
import com.example.signcoach.domain.scoring.GestureScorer
import com.example.signcoach.domain.scoring.StaticGestureScorer
import com.example.signcoach.platform.CameraController
import com.example.signcoach.platform.HandTrackerBridge
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

class CoachViewModel(
    private val repository: TemplateRepository,
    private val cameraController: CameraController,
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

    fun dispatch(intent: CoachIntent) {
        println("SignCoach: Dispatching intent $intent (current status=${state.value.status})")
        _state.update { CoachReducer.reduce(it, intent) }
        when (intent) {
            CoachIntent.LoadTemplates -> loadTemplates()
            CoachIntent.StartPractice, CoachIntent.Retry -> startPractice()
            CoachIntent.StopPractice -> stopPractice()
            is CoachIntent.SelectTemplate -> {
                println("SignCoach: Selected template ${intent.id}")
            }
        }
    }

    private fun loadTemplates() {
        repository.load()
            .onSuccess { templates -> _state.update { CoachReducer.templatesLoaded(it, templates) } }
            .onFailure { _state.update(CoachReducer::templateLoadFailed) }
    }

    private fun startPractice() {
        val selected = selectedTemplate() ?: return
        println("SignCoach: startPractice called for template ${selected.id}")
        poseJob?.cancel()
        statusJob?.cancel()
        practiceJob?.cancel()
        practiceJob = scope.launch {
            try {
            println("SignCoach: about to call cameraController.requestPermission()")
            if (!cameraController.requestPermission()) {
                println("SignCoach: Camera permission NOT granted")
                _state.update { CoachReducer.trackerStatusChanged(it, TrackerStatus.NO_PERMISSION) }
                return@launch
            }

            println("SignCoach: Camera permission granted, starting hand tracker...")
            val activeTracker = tracker ?: cameraController.createHandTracker().also { tracker = it }
            statusJob = launch {
                activeTracker.status.collectLatest { status ->
                    println("SignCoach: Tracker status changed to $status")
                    _state.update { CoachReducer.trackerStatusChanged(it, status) }
                }
            }
            poseJob = launch {
                activeTracker.start()
                    .catch { error ->
                        println("SignCoach: Tracker error: ${error.message}")
                        _state.update {
                            CoachReducer.trackerStatusChanged(
                                it,
                                TrackerStatus.ERROR,
                                error.message ?: "Handtracking kon niet worden gestart.",
                            )
                        }
                    }
                    .collect { pose ->
                        if (!pose.isValid()) return@collect
                        val currentSelected = selectedTemplate() ?: return@collect
                        _state.update { current ->
                            val withPose = CoachReducer.poseReceived(current, pose)
                            val scorer = if (currentSelected.rules.any { it.type == RuleType.MOTION_DIRECTION }) {
                                dynamicScorer
                            } else {
                                staticScorer
                            }
                            CoachReducer.feedbackReceived(
                                withPose,
                                scorer.score(withPose.recentPoses, currentSelected),
                            )
                        }
                    }
            }
            } catch (e: Throwable) {
                println("SignCoach: startPractice coroutine crashed: $e")
                e.printStackTrace()
            }
        }
    }

    private fun stopPractice() {
        println("SignCoach: stopPractice called")
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

    fun clear() {
        println("SignCoach: ViewModel clear called")
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
