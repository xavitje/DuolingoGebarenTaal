package io.github.xavitje.signcoach.presentation

import androidx.compose.ui.graphics.ImageBitmap
import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.remote.AdminApi
import io.github.xavitje.signcoach.data.remote.CrashAdminApi
import io.github.xavitje.signcoach.data.remote.SupabaseAuthApi
import io.github.xavitje.signcoach.platform.decodeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminStep { LOGIN, LIST, DETAIL, CRASH_LIST, CRASH_DETAIL }

data class AdminUiState(
    val step: AdminStep = AdminStep.LOGIN,
    val loginEmail: String = "",
    val loginPassword: String = "",
    val isLoggingIn: Boolean = false,
    val loginError: io.github.xavitje.signcoach.domain.AppError? = null,
    val adminEmail: String? = null,
    val isLoadingList: Boolean = false,
    val pendingSamples: List<AdminApi.AdminSampleDto> = emptyList(),
    val listError: io.github.xavitje.signcoach.domain.AppError? = null,
    val selectedSample: AdminApi.AdminSampleDto? = null,
    val isLoadingImages: Boolean = false,
    val selectedImages: List<ImageBitmap> = emptyList(),
    val isSubmittingDecision: Boolean = false,
    val rejectNote: String = "",
    val actionError: io.github.xavitje.signcoach.domain.AppError? = null,
    val isLoadingCrashes: Boolean = false,
    val crashReports: List<CrashAdminApi.CrashReportDto> = emptyList(),
    val crashListError: io.github.xavitje.signcoach.domain.AppError? = null,
    val selectedCrash: CrashAdminApi.CrashReportDto? = null,
)

/**
 * Beheert het admin-goedkeurscherm: inloggen, de wachtrij van pending bijdrages tonen, per
 * bijdrage de review-beelden ophalen en goed- of afkeuren. Een sessie leeft alleen in-memory
 * (geen lokale opslag van het access-token) — bewuste, eenvoudige keuze voor een scherm dat maar
 * door één persoon wordt gebruikt; opnieuw inloggen bij elke app-start is geen probleem.
 */
class AdminViewModel(
    private val authApi: SupabaseAuthApi = SupabaseAuthApi(),
    private val adminApi: AdminApi = AdminApi(),
    private val crashAdminApi: CrashAdminApi = CrashAdminApi(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private var accessToken: String? = null

    init {
        initFromActiveSession()
    }

    fun initFromActiveSession() {
        val session = SessionStore.load()
        if (session != null) {
            accessToken = session.accessToken
            _state.update {
                it.copy(
                    step = if (it.step == AdminStep.LOGIN) AdminStep.LIST else it.step,
                    adminEmail = session.email,
                )
            }
            loadPending()
        }
    }

    fun setLoginEmail(value: String) {
        _state.update { it.copy(loginEmail = value, loginError = null) }
    }

    fun setLoginPassword(value: String) {
        _state.update { it.copy(loginPassword = value, loginError = null) }
    }

    fun login() {
        val email = state.value.loginEmail.trim()
        val password = state.value.loginPassword
        if (email.isEmpty() || password.isEmpty()) return
        _state.update { it.copy(isLoggingIn = true, loginError = null) }
        scope.launch {
            authApi.signInWithPassword(email, password)
                .onSuccess { session ->
                    accessToken = session.accessToken
                    _state.update {
                        it.copy(
                            step = AdminStep.LIST,
                            isLoggingIn = false,
                            loginPassword = "",
                            adminEmail = session.user.email ?: email,
                        )
                    }
                    loadPending()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoggingIn = false,
                            loginError = (e as? io.github.xavitje.signcoach.domain.AppError) ?: io.github.xavitje.signcoach.domain.AppError.InvalidCredentials,
                        )
                    }
                }
        }
    }

    fun loadPending() {
        val token = accessToken ?: return
        _state.update { it.copy(isLoadingList = true, listError = null) }
        scope.launch {
            adminApi.fetchPendingSamples(token)
                .onSuccess { samples ->
                    _state.update { it.copy(isLoadingList = false, pendingSamples = samples) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoadingList = false, listError = (e as? io.github.xavitje.signcoach.domain.AppError) ?: io.github.xavitje.signcoach.domain.AppError.Unknown(e))
                    }
                }
        }
    }

    fun selectSample(sample: AdminApi.AdminSampleDto) {
        val token = accessToken ?: return
        _state.update {
            it.copy(
                step = AdminStep.DETAIL,
                selectedSample = sample,
                selectedImages = emptyList(),
                isLoadingImages = true,
                rejectNote = "",
                actionError = null,
            )
        }
        val videoPath = sample.videoPath
        if (videoPath == null) {
            _state.update { it.copy(isLoadingImages = false) }
            return
        }
        scope.launch {
            adminApi.listReviewImages(token, videoPath)
                .onSuccess { paths ->
                    val images = paths.mapNotNull { path ->
                        adminApi.fetchImageBytes(token, path).getOrNull()?.let(::decodeImageBitmap)
                    }
                    _state.update { it.copy(isLoadingImages = false, selectedImages = images) }
                }
                .onFailure {
                    _state.update { it.copy(isLoadingImages = false) }
                }
        }
    }

    fun setRejectNote(value: String) {
        _state.update { it.copy(rejectNote = value) }
    }

    fun approve() = decide(status = "approved", note = null)

    fun reject() = decide(status = "rejected", note = state.value.rejectNote.trim().ifEmpty { null })

    private fun decide(status: String, note: String?) {
        val token = accessToken ?: return
        val sample = state.value.selectedSample ?: return
        val reviewer = state.value.adminEmail ?: return
        _state.update { it.copy(isSubmittingDecision = true, actionError = null) }
        scope.launch {
            adminApi.updateSampleStatus(token, sample.id, status, note, reviewer)
                .onSuccess {
                    _state.update {
                        it.copy(
                            step = AdminStep.LIST,
                            isSubmittingDecision = false,
                            selectedSample = null,
                            selectedImages = emptyList(),
                            pendingSamples = it.pendingSamples.filterNot { s -> s.id == sample.id },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isSubmittingDecision = false,
                            actionError = (e as? io.github.xavitje.signcoach.domain.AppError) ?: io.github.xavitje.signcoach.domain.AppError.Unknown(e),
                        )
                    }
                }
        }
    }

    fun backToList() {
        _state.update {
            it.copy(step = AdminStep.LIST, selectedSample = null, selectedImages = emptyList(), actionError = null)
        }
    }

    /** Navigeert vanaf het samples-overzicht naar het crashoverzicht (zie AdminScreen). */
    fun openCrashes() {
        _state.update { it.copy(step = AdminStep.CRASH_LIST) }
        loadCrashes()
    }

    fun loadCrashes() {
        val token = accessToken ?: return
        _state.update { it.copy(isLoadingCrashes = true, crashListError = null) }
        scope.launch {
            crashAdminApi.fetchCrashReports(token)
                .onSuccess { reports ->
                    _state.update { it.copy(isLoadingCrashes = false, crashReports = reports) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoadingCrashes = false,
                            crashListError = (e as? io.github.xavitje.signcoach.domain.AppError) ?: io.github.xavitje.signcoach.domain.AppError.Unknown(e),
                        )
                    }
                }
        }
    }

    fun selectCrash(report: CrashAdminApi.CrashReportDto) {
        _state.update { it.copy(step = AdminStep.CRASH_DETAIL, selectedCrash = report) }
    }

    fun backToCrashList() {
        _state.update { it.copy(step = AdminStep.CRASH_LIST, selectedCrash = null) }
    }

    fun backToSamplesFromCrashes() {
        _state.update { it.copy(step = AdminStep.LIST, selectedCrash = null, crashReports = emptyList()) }
    }

    fun logout() {
        accessToken = null
        _state.update { AdminUiState() }
    }
}
