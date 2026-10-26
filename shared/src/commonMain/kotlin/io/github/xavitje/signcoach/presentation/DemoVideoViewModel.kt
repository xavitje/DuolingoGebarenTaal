package io.github.xavitje.signcoach.presentation

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.remote.DemoVideoApi
import io.github.xavitje.signcoach.data.remote.SupabaseAuthApi
import io.github.xavitje.signcoach.platform.CameraController
import io.github.xavitje.signcoach.platform.DemoVideoRecorder
import io.github.xavitje.signcoach.platform.PlatformDemoVideoRecorder
import io.github.xavitje.signcoach.platform.RecordedVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DemoStep {
    /** Opnemen: toestemming geven (de video wordt aan alle leerlingen getoond). */
    CONSENT, COUNTDOWN, RECORDING, PREVIEW, UPLOADING, SENT,
    /** Beoordelen: lijst met video's van anderen, en één video bekijken. */
    REVIEW_LIST, REVIEWING, REVIEW_DONE,
    ERROR,
}

enum class DemoError { NOT_SUPPORTED, NO_PERMISSION, RECORDING_FAILED, NETWORK, OWN_VIDEO }

data class DemoUiState(
    val step: DemoStep = DemoStep.CONSENT,
    val gestureId: String? = null,
    val gestureName: String = "",
    val consentGiven: Boolean = false,
    val countdown: Int = 0,
    val progress: Float = 0f,
    /** Lokale video om terug te kijken voor het versturen. */
    val localVideoUri: String? = null,
    /** Video's van anderen die op beoordeling wachten. */
    val queue: List<DemoVideoApi.DemoVideo> = emptyList(),
    /** Eigen video's die op een collega wachten. */
    val ownPending: Int = 0,
    val reviewing: DemoVideoApi.DemoVideo? = null,
    val reviewUrl: String? = null,
    val lastApproved: Boolean = false,
    val isBusy: Boolean = false,
    val error: DemoError? = null,
)

/**
 * Demovideo's (fase 5+): een vakman neemt het voorbeeld van een gebaar op; een ANDERE vakman
 * keurt het goed of af. Pas na goedkeuring zien leerlingen de video (zie review-demo-video).
 */
class DemoVideoViewModel(
    private val cameraController: CameraController,
    private val signLanguage: String = SessionStore.getSignLanguage(),
    private val recorder: DemoVideoRecorder = PlatformDemoVideoRecorder(),
    private val api: DemoVideoApi = DemoVideoApi(),
    private val authApi: SupabaseAuthApi = SupabaseAuthApi(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(DemoUiState())
    val state: StateFlow<DemoUiState> = _state.asStateFlow()
    private var recorded: RecordedVideo? = null

    val isSupported: Boolean get() = recorder.isSupported

    // ─── Opnemen ──────────────────────────────────────────────────────────

    fun startForGesture(gestureId: String, gestureName: String) {
        recorded = null
        _state.value = DemoUiState(step = DemoStep.CONSENT, gestureId = gestureId, gestureName = gestureName)
        if (!recorder.isSupported) _state.update { it.copy(step = DemoStep.ERROR, error = DemoError.NOT_SUPPORTED) }
    }

    fun setConsent(given: Boolean) = _state.update { it.copy(consentGiven = given) }

    fun record() {
        if (!_state.value.consentGiven || _state.value.gestureId == null) return
        scope.launch {
            if (!cameraController.requestPermission()) {
                _state.update { it.copy(step = DemoStep.ERROR, error = DemoError.NO_PERMISSION) }
                return@launch
            }
            recorder.enable()
            for (n in COUNTDOWN downTo 1) {
                _state.update { it.copy(step = DemoStep.COUNTDOWN, countdown = n) }
                delay(1000)
            }
            _state.update { it.copy(step = DemoStep.RECORDING, progress = 0f) }
            val video = recorder.record(DURATION_MS) { p -> _state.update { it.copy(progress = p) } }
            if (video == null) {
                _state.update { it.copy(step = DemoStep.ERROR, error = DemoError.RECORDING_FAILED) }
                return@launch
            }
            recorded = video
            _state.update { it.copy(step = DemoStep.PREVIEW, localVideoUri = video.localUri) }
        }
    }

    fun retake() {
        recorded = null
        _state.update { it.copy(localVideoUri = null) }
        record()
    }

    fun send() {
        val video = recorded ?: return
        val gestureId = _state.value.gestureId ?: return
        _state.update { it.copy(step = DemoStep.UPLOADING) }
        scope.launch {
            val token = freshAccessToken()
            val userId = SessionStore.load()?.userId
            if (token == null || userId == null) {
                _state.update { it.copy(step = DemoStep.ERROR, error = DemoError.NETWORK) }
                return@launch
            }
            val path = "$signLanguage/$gestureId/${randomId()}.mp4"
            api.submit(token, userId, gestureId, signLanguage, path, video.bytes)
                .onSuccess { recorded = null; _state.update { it.copy(step = DemoStep.SENT) } }
                .onFailure { _state.update { it.copy(step = DemoStep.ERROR, error = DemoError.NETWORK) } }
        }
    }

    // ─── Beoordelen ───────────────────────────────────────────────────────

    fun openQueue() {
        _state.value = DemoUiState(step = DemoStep.REVIEW_LIST, isBusy = true)
        scope.launch {
            val token = freshAccessToken()
            val me = SessionStore.load()?.userId
            if (token == null) {
                _state.update { it.copy(isBusy = false, step = DemoStep.ERROR, error = DemoError.NETWORK) }
                return@launch
            }
            api.pending(token, signLanguage)
                .onSuccess { all ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            queue = all.filter { v -> v.recordedBy != me },
                            ownPending = all.count { v -> v.recordedBy == me },
                        )
                    }
                }
                .onFailure { _state.update { it.copy(isBusy = false, step = DemoStep.ERROR, error = DemoError.NETWORK) } }
        }
    }

    fun openVideo(video: DemoVideoApi.DemoVideo) {
        _state.update { it.copy(step = DemoStep.REVIEWING, reviewing = video, reviewUrl = null, isBusy = true) }
        scope.launch {
            val token = freshAccessToken() ?: return@launch
            api.signedUrl(token, video.storagePath)
                .onSuccess { url -> _state.update { it.copy(reviewUrl = url, isBusy = false) } }
                .onFailure { _state.update { it.copy(isBusy = false, step = DemoStep.ERROR, error = DemoError.NETWORK) } }
        }
    }

    fun decide(approve: Boolean, note: String?) {
        val video = _state.value.reviewing ?: return
        _state.update { it.copy(isBusy = true) }
        scope.launch {
            val token = freshAccessToken() ?: return@launch
            api.review(token, video.id, approve, note)
                .onSuccess { _state.update { it.copy(isBusy = false, step = DemoStep.REVIEW_DONE, lastApproved = approve) } }
                .onFailure { e ->
                    val own = (e as? io.github.xavitje.signcoach.domain.AppError.ServerError)?.details?.contains("own_video") == true
                    _state.update { it.copy(isBusy = false, step = DemoStep.ERROR, error = if (own) DemoError.OWN_VIDEO else DemoError.NETWORK) }
                }
        }
    }

    /** Camera terug naar gewone handtracking; aanroepen bij het verlaten van het scherm. */
    fun close() {
        recorder.disable()
    }

    private suspend fun freshAccessToken(): String? {
        val stored = SessionStore.load() ?: return null
        return authApi.refresh(stored.refreshToken).fold(
            onSuccess = { s ->
                SessionStore.save(s.accessToken, s.refreshToken, s.user.id, s.user.email)
                s.accessToken
            },
            onFailure = { stored.accessToken },
        )
    }

    private fun randomId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }

    companion object {
        const val COUNTDOWN = 3
        const val DURATION_MS = 5000L
    }
}
