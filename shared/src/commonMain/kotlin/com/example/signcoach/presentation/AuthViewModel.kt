package com.example.signcoach.presentation

import com.example.signcoach.data.local.SessionStore
import com.example.signcoach.data.local.StoredSession
import com.example.signcoach.data.remote.ProfileApi
import com.example.signcoach.data.remote.SupabaseAuthApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStatus {
    /** App start, bezig met sessie-herstel. */
    LOADING,
    /** Geen actieve sessie — toon auth-flow. */
    LOGGED_OUT,
    /** Actieve sessie aanwezig — toon hoofd-app. */
    LOGGED_IN,
}

data class AuthUiState(
    val status: AuthStatus = AuthStatus.LOADING,
    val userId: String? = null,
    val email: String? = null,
    val isAdmin: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Bevestigingsmelding (bijv. na wachtwoord-reset-mail verstuurd). */
    val infoMessage: String? = null,
)

/**
 * Beheert de auth-staat van de app: sessie-herstel bij opstarten, inloggen (e-mail/wachtwoord
 * en Google OAuth), registreren, uitloggen en admin-check. Alle schermen die weten of de
 * gebruiker ingelogd is observeren deze ViewModel.
 */
class AuthViewModel(
    private val authApi: SupabaseAuthApi = SupabaseAuthApi(),
    private val profileApi: ProfileApi = ProfileApi(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        restoreSession()
    }

    // ─── Sessie herstel bij app-start ────────────────────────────────────────

    private fun restoreSession() {
        val stored = SessionStore.load()
        if (stored == null) {
            _state.update { it.copy(status = AuthStatus.LOGGED_OUT) }
            return
        }
        // Probeer de opgeslagen sessie te vernieuwen (access token kan verlopen zijn).
        scope.launch {
            authApi.refresh(stored.refreshToken)
                .onSuccess { session ->
                    SessionStore.save(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        userId = session.user.id,
                        email = session.user.email,
                    )
                    checkAdminAndSetLoggedIn(session.accessToken, session.user.id, session.user.email)
                }
                .onFailure {
                    // Refresh mislukt (verlopen of ingetrokken) — sessie wissen en uitloggen.
                    SessionStore.clear()
                    _state.update { it.copy(status = AuthStatus.LOGGED_OUT) }
                }
        }
    }

    // ─── Inloggen ────────────────────────────────────────────────────────────

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(errorMessage = "Vul e-mailadres en wachtwoord in.") }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        scope.launch {
            authApi.signInWithPassword(email.trim(), password)
                .onSuccess { session ->
                    SessionStore.save(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        userId = session.user.id,
                        email = session.user.email,
                    )
                    checkAdminAndSetLoggedIn(session.accessToken, session.user.id, session.user.email)
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Inloggen mislukt. Controleer e-mail en wachtwoord.",
                        )
                    }
                }
        }
    }

    // ─── Registreren ─────────────────────────────────────────────────────────

    fun signUp(email: String, password: String, confirmPassword: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(errorMessage = "Vul alle velden in.") }
            return
        }
        if (password != confirmPassword) {
            _state.update { it.copy(errorMessage = "Wachtwoorden komen niet overeen.") }
            return
        }
        if (password.length < 6) {
            _state.update { it.copy(errorMessage = "Wachtwoord moet minimaal 6 tekens zijn.") }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        scope.launch {
            authApi.signUp(email.trim(), password)
                .onSuccess { session ->
                    // Na registratie meteen een profielrij aanmaken.
                    profileApi.createProfile(
                        accessToken = session.accessToken,
                        userId = session.user.id,
                        displayName = null,
                    )
                    SessionStore.save(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        userId = session.user.id,
                        email = session.user.email,
                    )
                    // Nieuwe gebruikers zijn nooit admin.
                    _state.update {
                        it.copy(
                            status = AuthStatus.LOGGED_IN,
                            isLoading = false,
                            userId = session.user.id,
                            email = session.user.email,
                            isAdmin = false,
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Registreren mislukt. Probeer een ander e-mailadres.",
                        )
                    }
                }
        }
    }

    // ─── Uitloggen ───────────────────────────────────────────────────────────

    fun signOut() {
        val stored: StoredSession = SessionStore.load() ?: run {
            SessionStore.clear()
            _state.update { AuthUiState(status = AuthStatus.LOGGED_OUT) }
            return
        }
        scope.launch {
            // Best-effort: als het API-verzoek faalt wissen we de lokale sessie toch.
            authApi.signOut(stored.accessToken)
            SessionStore.clear()
            _state.update { AuthUiState(status = AuthStatus.LOGGED_OUT) }
        }
    }

    // ─── Wachtwoord vergeten ─────────────────────────────────────────────────

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _state.update { it.copy(errorMessage = "Vul je e-mailadres in.") }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        scope.launch {
            authApi.resetPassword(email.trim())
                .onSuccess {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            infoMessage = "Als dit e-mailadres bij ons bekend is, ontvang je een herstel-mail.",
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            infoMessage = "Als dit e-mailadres bij ons bekend is, ontvang je een herstel-mail.",
                        )
                    }
                }
        }
    }

    // ─── Google OAuth (web flow) ──────────────────────────────────────────────

    /**
     * Geeft de Google OAuth-URL terug die geopend moet worden in een browser.
     * De app-specifieke deep-link moet als redirect_to worden meegegeven.
     */
    fun getGoogleSignInUrl(redirectTo: String): String = authApi.googleOAuthUrl(redirectTo)

    /**
     * Verwerkt de OAuth-callback nadat de browser de code teruggeeft via deep-link.
     * Roep dit aan vanuit het platform-specifieke deep-link-afhandeling.
     */
    fun handleOAuthCallback(code: String) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            authApi.exchangeOAuthCode(code)
                .onSuccess { session ->
                    // Probeer een profiel aan te maken (is geen probleem als het al bestaat).
                    profileApi.createProfile(
                        accessToken = session.accessToken,
                        userId = session.user.id,
                        displayName = null,
                    )
                    SessionStore.save(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        userId = session.user.id,
                        email = session.user.email,
                    )
                    checkAdminAndSetLoggedIn(session.accessToken, session.user.id, session.user.email)
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Google-inloggen mislukt. Probeer opnieuw.",
                        )
                    }
                }
        }
    }

    // ─── Hulpfuncties ────────────────────────────────────────────────────────

    fun clearError() {
        _state.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private suspend fun checkAdminAndSetLoggedIn(
        accessToken: String,
        userId: String,
        email: String?,
    ) {
        val isAdmin = authApi.amIAdmin(accessToken).getOrDefault(false)
        _state.update {
            it.copy(
                status = AuthStatus.LOGGED_IN,
                isLoading = false,
                userId = userId,
                email = email,
                isAdmin = isAdmin,
            )
        }
    }
}
