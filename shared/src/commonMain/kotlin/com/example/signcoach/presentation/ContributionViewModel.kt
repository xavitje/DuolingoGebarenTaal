package com.example.signcoach.presentation

import com.example.signcoach.data.model.Handedness
import com.example.signcoach.data.model.HandPose
import com.example.signcoach.data.model.LandmarkPoint
import com.example.signcoach.data.model.SignTemplate
import com.example.signcoach.data.remote.GestureContributionApi
import com.example.signcoach.platform.CameraController
import com.example.signcoach.platform.CapturedClip
import com.example.signcoach.platform.ContributionCapture
import com.example.signcoach.platform.PlatformContributionCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ContributionStep { PICK_GESTURE, CONSENT, COUNTDOWN, RECORDING, UPLOADING, SUCCESS, ERROR }

data class ContributionUiState(
    val step: ContributionStep = ContributionStep.PICK_GESTURE,
    val availableGestures: List<SignTemplate> = emptyList(),
    val selectedGestureId: String? = null,
    val consentGiven: Boolean = false,
    /** Alleen relevant tijdens [ContributionStep.COUNTDOWN]: seconden tot de opname echt begint. */
    val countdownSeconds: Int = 0,
    val recordingProgress: Float = 0f,
    val errorMessage: String? = null,
)

class ContributionViewModel(
    private val cameraController: CameraController,
    private val capture: ContributionCapture = PlatformContributionCapture(),
    private val api: GestureContributionApi = GestureContributionApi(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(ContributionUiState())
    val state: StateFlow<ContributionUiState> = _state.asStateFlow()

    fun setAvailableGestures(gestures: List<SignTemplate>) {
        _state.update { it.copy(availableGestures = gestures) }
    }

    fun selectGesture(id: String) {
        _state.update {
            it.copy(selectedGestureId = id, step = ContributionStep.CONSENT, consentGiven = false, errorMessage = null)
        }
    }

    fun setConsent(given: Boolean) {
        _state.update { it.copy(consentGiven = given) }
    }

    fun cancelToPicker() {
        _state.update { it.copy(step = ContributionStep.PICK_GESTURE, selectedGestureId = null, errorMessage = null) }
    }

    fun startRecording() {
        val gestureId = state.value.selectedGestureId ?: return
        if (!state.value.consentGiven) return
        _state.update {
            it.copy(step = ContributionStep.COUNTDOWN, countdownSeconds = COUNTDOWN_SECONDS, errorMessage = null)
        }
        scope.launch {
            val granted = cameraController.requestPermission()
            if (!granted) {
                _state.update { it.copy(step = ContributionStep.ERROR, errorMessage = "Camera-toestemming nodig om bij te dragen.") }
                return@launch
            }
            val tracker = cameraController.createHandTracker()

            // Aftellen terwijl de camerapreview al zichtbaar is (zie CountdownStep): geeft de
            // gebruiker tijd om de handvorm alvast neer te zetten en de camera de kans om scherp
            // te stellen, in plaats van meteen — zonder waarschuwing — te beginnen met opnemen.
            for (remaining in COUNTDOWN_SECONDS downTo 1) {
                _state.update { it.copy(step = ContributionStep.COUNTDOWN, countdownSeconds = remaining) }
                delay(1000)
            }

            _state.update { it.copy(step = ContributionStep.RECORDING, recordingProgress = 0f) }
            val clip = capture.record(tracker, RECORD_DURATION_MS) { progress ->
                _state.update { it.copy(recordingProgress = progress) }
            }
            if (clip.poses.isEmpty()) {
                _state.update {
                    it.copy(step = ContributionStep.ERROR, errorMessage = "Geen hand gedetecteerd tijdens de opname. Probeer opnieuw met je hand goed in beeld.")
                }
                return@launch
            }
            uploadClip(gestureId, clip)
        }
    }

    private suspend fun uploadClip(gestureId: String, clip: CapturedClip) {
        _state.update { it.copy(step = ContributionStep.UPLOADING) }
        val landmarks = averageLandmarks(clip.poses)
        val handedness = majorityHandedness(clip.poses)
        val folder = "$gestureId/${randomId()}"

        // BELANGRIJK: elke frame-upload moet daadwerkelijk lukken voordat we de bijdrage
        // indienen. Een reviewer beoordeelt uitsluitend op basis van deze beelden (zie de
        // toestemmingstekst) — een gesubmitte rij zonder video is voor de reviewer waardeloos
        // en zou eerder onterecht goedgekeurd of voor altijd blijven hangen. Eerder gaf de app
        // hier "Bedankt voor je bijdrage!" ook als alle frame-uploads mislukten (RLS-fout), wat
        // een lege 'gesture-videos'-bucket opleverde ondanks meerdere "succesvolle" opnames.
        for ((index, bytes) in clip.previewFrames.withIndex()) {
            val uploadResult = api.uploadReviewClip(
                "$folder/frame_${index.toString().padStart(2, '0')}.jpg",
                bytes,
                "image/jpeg",
            )
            if (uploadResult.isFailure) {
                _state.update {
                    it.copy(
                        step = ContributionStep.ERROR,
                        errorMessage = "Het opsturen van de beelden is niet gelukt. Controleer je internetverbinding en probeer opnieuw.",
                    )
                }
                return
            }
        }

        val result = api.submitSample(
            GestureContributionApi.NewSampleDto(
                gestureId = gestureId,
                landmarks = landmarks,
                handedness = handedness.name,
                videoPath = folder,
            ),
        )
        result.onSuccess {
            _state.update { it.copy(step = ContributionStep.SUCCESS) }
        }.onFailure {
            _state.update {
                it.copy(step = ContributionStep.ERROR, errorMessage = "Versturen is niet gelukt. Controleer je internetverbinding en probeer opnieuw.")
            }
        }
    }

    fun reset() {
        _state.update { ContributionUiState(availableGestures = it.availableGestures) }
    }

    private fun averageLandmarks(poses: List<HandPose>): List<LandmarkPoint> =
        (0 until 21).map { i ->
            val xs = poses.map { pose -> pose.landmarks[i].x }
            val ys = poses.map { pose -> pose.landmarks[i].y }
            val zs = poses.map { pose -> pose.landmarks[i].z }
            LandmarkPoint(x = xs.average().toFloat(), y = ys.average().toFloat(), z = zs.average().toFloat())
        }

    private fun majorityHandedness(poses: List<HandPose>): Handedness =
        poses.map { it.handedness }
            .filter { it != Handedness.UNKNOWN }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: Handedness.UNKNOWN

    private fun randomId(length: Int = 12): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 3
        const val RECORD_DURATION_MS = 2500L
    }
}
