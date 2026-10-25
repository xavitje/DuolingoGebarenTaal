package io.github.xavitje.signcoach.presentation

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.local.StoredSession
import io.github.xavitje.signcoach.data.remote.ProfileApi
import io.github.xavitje.signcoach.data.remote.SupabaseAuthApi
import io.github.xavitje.signcoach.push.PushNotificationRegistrar
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

/**
 * Zero Trust: vertaalt een mislukte auth-aanroep naar een betekenisvolle fout voor de UI in
 * plaats van blind aan te nemen dat elke fout "verkeerd wachtwoord" betekent. Supabase Auth
 * antwoordt op ongeldige inloggegevens met HTTP 400/422 — dat wordt hier expliciet herkend;
 * echte netwerk-/serverfouten (timeout, 5xx, geen internet) blijven wél als zodanig zichtbaar.
 */
private fun Throwable.toAuthError(): io.github.xavitje.signcoach.domain.AppError = when (this) {
    is io.github.xavitje.signcoach.domain.AppError.ServerError ->
        if (statusCode == 400 || statusCode == 422) io.github.xavitje.signcoach.domain.AppError.InvalidCredentials else this
    is io.github.xavitje.signcoach.domain.AppError -> this
    else -> io.github.xavitje.signcoach.domain.AppError.InvalidCredentials
}

/** Onderscheidt een echte afwijzing door de server (refresh token ongeldig/ingetrokken) van
 * een netwerk-/verbindingsfout. Alleen bij een echte afwijzing mag [AuthViewModel.restoreSession]
 * uitloggen (Zero Trust: een netwerkfout mag nooit als "sessie ongeldig" behandeld worden).
 * Bewust een top-level `internal` functie (geen instance-methode) zodat dit rechtstreeks te
 * unit-testen is zonder een AuthViewModel (met zijn echte SessionStore/netwerk-afhankelijkheden)
 * te moeten instantiëren — zie AuthFailureClassifierTest. */
internal fun isDefinitiveAuthFailure(e: Throwable): Boolean = when (e) {
    is io.github.xavitje.signcoach.domain.AppError.Unauthorized -> true
    is io.github.xavitje.signcoach.domain.AppError.ServerError -> e.statusCode == 400 || e.statusCode == 422
    else -> false
}

data class AuthUiState(
    val status: AuthStatus = AuthStatus.LOADING,
    val userId: String? = null,
    val email: String? = null,
    val isAdmin: Boolean = false,
    /** Rol van de gebruiker; bepaalt o.a. of "Draag een gebaar bij" zichtbaar is. */
    val role: io.github.xavitje.signcoach.data.model.UserRole = io.github.xavitje.signcoach.data.model.UserRole.STUDENT,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val error: io.github.xavitje.signcoach.domain.AppError? = null,
    /** Bevestigingsmelding (bijv. na wachtwoord-reset-mail verstuurd). */
    val infoMessage: String? = null,
    /** "email" of "google" — bepaalt of wachtwoord-wijzigen zinvol is (Fase D, ProfileScreen). */
    val authProvider: String? = null,
    val isAccountDeleted: Boolean = false,
    /** Providers die aan dit account gekoppeld zijn (bv. {"email", "google"} na het koppelen
     *  van een Google-account aan een bestaand e-mail-account) — zie ProfileScreen. Leeg zolang
     *  dit nog niet is opgehaald of als het ophalen mislukte (bv. geen netwerk); toont dan simpelweg
     *  geen "gekoppeld"-indicator, in plaats van iets te verzinnen. */
    val linkedProviders: Set<String> = emptySet(),
    /** Na registreren: e-mailadres waar de bevestigingslink naartoe is gestuurd (null = n.v.t.). */
    val confirmationSentTo: String? = null,
) {
    /** Bijdragen (gebaren opnemen) alleen voor vakmensen en admins — nooit voor leerlingen. */
    val canContribute: Boolean get() = role.canContribute

    /** Leerling van een school (ingelogd met klascode + pin, geen eigen e-mailadres). */
    val isSchoolStudent: Boolean
        get() = io.github.xavitje.signcoach.data.remote.SchoolApi.isSchoolStudentEmail(email)

    /** Docent of schoolbeheerder: krijgt het tabblad "Klas". */
    val isSchoolStaff: Boolean
        get() = role == io.github.xavitje.signcoach.data.model.UserRole.TEACHER ||
            role == io.github.xavitje.signcoach.data.model.UserRole.SCHOOL_ADMIN
}

/**
 * Beheert de auth-staat van de app: sessie-herstel bij opstarten, inloggen (e-mail/wachtwoord
 * en Google OAuth), registreren, uitloggen en admin-check. Alle schermen die weten of de
 * gebruiker ingelogd is observeren deze ViewModel.
 */
class AuthViewModel(
    private val authApi: SupabaseAuthApi = SupabaseAuthApi(),
    private val profileApi: ProfileApi = ProfileApi(),
    private val pushRegistrar: PushNotificationRegistrar = PushNotificationRegistrar(),
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
        // Optimistisch/offline-first: toon de app DIRECT met de lokaal opgeslagen sessie, zonder
        // te wachten op een netwerk-rondrit. Voorheen bleef SplashGate op LOADING staan tot
        // authApi.refresh() klaar was — dat gebeurt bij ELKE herstart van deze ViewModel (dus ook
        // bij een Android Activity-recreate, bv. na het wisselen van de app-taal), en voelde bij
        // een trage verbinding aan als "de hele app start steeds opnieuw op". De achtergrond-
        // refresh hieronder corrigeert dit alsnog met de server-waarheid zodra dat lukt, en logt
        // pas uit bij een ECHTE (niet-netwerk) afwijzing — zie isDefinitiveAuthFailure.
        _state.update {
            it.copy(
                status = AuthStatus.LOGGED_IN,
                userId = stored.userId,
                email = stored.email,
            )
        }
        // Probeer de opgeslagen sessie op de achtergrond te vernieuwen (access token kan verlopen
        // zijn) — de UI staat hier niet meer op te wachten (zie hierboven).
        scope.launch {
            authApi.refresh(stored.refreshToken)
                .onSuccess { session ->
                    SessionStore.save(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        userId = session.user.id,
                        email = session.user.email,
                    )
                    checkAdminAndSetLoggedIn(session.accessToken, session.user.id, session.user.email, session.user.appMetadata?.provider)
                }
                .onFailure { e ->
                    if (isDefinitiveAuthFailure(e)) {
                        // De refresh token zelf is echt ongeldig/ingetrokken (server heeft dit
                        // expliciet afgewezen) — dan is uitloggen terecht.
                        SessionStore.clear()
                        _state.update { it.copy(status = AuthStatus.LOGGED_OUT) }
                    }
                    // Anders (netwerkfout, timeout, server onbereikbaar): niets doen — we blijven
                    // gewoon ingelogd met de lokale sessie die hierboven al is getoond. Zodra er
                    // weer verbinding is, corrigeert de volgende succesvolle refresh/sync dit.
                }
        }
    }


    // ─── Inloggen ────────────────────────────────────────────────────────────

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = io.github.xavitje.signcoach.domain.AppError.FieldEmpty) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, errorMessage = null, infoMessage = null) }
        scope.launch {
            authApi.signInWithPassword(email.trim(), password)
                .onSuccess { session ->
                    SessionStore.save(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        userId = session.user.id,
                        email = session.user.email,
                    )
                    checkAdminAndSetLoggedIn(session.accessToken, session.user.id, session.user.email, session.user.appMetadata?.provider)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.toAuthError(),
                        )
                    }
                }
        }
    }

    /**
     * Neemt een sessie over die buiten het e-mail/wachtwoord-formulier is verkregen — de
     * leerling-login (klascode + naam + pin, zie StudentLoginViewModel).
     */
    fun completeExternalLogin(session: io.github.xavitje.signcoach.data.remote.SupabaseAuthApi.AuthSession) {
        _state.update { it.copy(isLoading = true, error = null, errorMessage = null) }
        scope.launch {
            SessionStore.save(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                userId = session.user.id,
                email = session.user.email,
            )
            checkAdminAndSetLoggedIn(session.accessToken, session.user.id, session.user.email, "student")
        }
    }

    // ─── Registreren ─────────────────────────────────────────────────────────

    fun signUp(email: String, password: String, confirmPassword: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = io.github.xavitje.signcoach.domain.AppError.FieldEmpty) }
            return
        }
        if (password != confirmPassword) {
            _state.update { it.copy(error = io.github.xavitje.signcoach.domain.AppError.PasswordsDoNotMatch) }
            return
        }
        if (password.length < 6) {
            _state.update { it.copy(error = io.github.xavitje.signcoach.domain.AppError.PasswordTooShort) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, errorMessage = null, infoMessage = null) }
        scope.launch {
            authApi.signUp(email.trim(), password)
                .onSuccess { result ->
                    if (result is io.github.xavitje.signcoach.data.remote.SupabaseAuthApi.SignUpResult.ConfirmationRequired) {
                        _state.update { it.copy(isLoading = false, confirmationSentTo = email.trim()) }
                        return@onSuccess
                    }
                    val session = (result as io.github.xavitje.signcoach.data.remote.SupabaseAuthApi.SignUpResult.SignedIn).session
                    // Na registratie meteen een profielrij aanmaken, met de gebarentaal die
                    // lokaal is gekozen tijdens de onboarding (default "ngt" als er nog geen
                    // keuze is, bv. bij een bestaande sessie zonder onboarding-stap).
                    profileApi.createProfile(
                        accessToken = session.accessToken,
                        userId = session.user.id,
                        displayName = null,
                        signLanguage = SessionStore.getSignLanguage(),
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
                            role = io.github.xavitje.signcoach.data.model.UserRole.STUDENT,
                            authProvider = "email",
                        )
                    }
                    runCatching { pushRegistrar.registerIfPossible() }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.toAuthError(),
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
            _state.update { it.copy(error = io.github.xavitje.signcoach.domain.AppError.FieldEmpty) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, errorMessage = null, infoMessage = null) }
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

    // ─── Google OAuth (PKCE) ────────────────────────────────────────────────

    /**
     * De code_verifier van de lopende Google-inlogpoging. In-memory is voldoende: de Custom Tab
     * draait in hetzelfde app-proces, dus dit overleeft de hele flow (openen browser -> Google
     * inloggen -> terug-redirect naar de app).
     */
    private var pendingCodeVerifier: String? = null

    /**
     * Start de Google-inlogflow: genereert een nieuw PKCE-paar, onthoudt de code_verifier, en
     * geeft de autorisatie-URL terug die de aanroeper in een browser moet openen
     * (zie [io.github.xavitje.signcoach.platform.BrowserLauncher]).
     */
    fun beginGoogleSignIn(): String {
        val verifier = io.github.xavitje.signcoach.util.Pkce.generateCodeVerifier()
        pendingCodeVerifier = verifier
        val challenge = io.github.xavitje.signcoach.util.Pkce.codeChallenge(verifier)
        return authApi.googleOAuthUrl(challenge)
    }

    /**
     * Verwerkt de OAuth-callback nadat de browser de code teruggeeft via deep-link.
     * Roep dit aan vanuit het platform-specifieke deep-link-afhandeling (zie
     * [io.github.xavitje.signcoach.data.local.OAuthCallbackBus]).
     */
    fun handleOAuthCallback(code: String) {
        val verifier = pendingCodeVerifier
        if (verifier == null) {
            // Geen lopende poging bekend (bijv. proces herstart tijdens de browser-stap) —
            // vraag de gebruiker opnieuw te proberen in plaats van een cryptische serverfout te tonen.
            _state.update { it.copy(error = io.github.xavitje.signcoach.domain.AppError.InvalidCredentials) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, errorMessage = null) }
        scope.launch {
            authApi.exchangeOAuthCode(code, verifier)
                .onSuccess { session ->
                    pendingCodeVerifier = null
                    // Probeer een profiel aan te maken (is geen probleem als het al bestaat).
                    profileApi.createProfile(
                        accessToken = session.accessToken,
                        userId = session.user.id,
                        displayName = null,
                        signLanguage = SessionStore.getSignLanguage(),
                    )
                    SessionStore.save(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        userId = session.user.id,
                        email = session.user.email,
                    )
                    checkAdminAndSetLoggedIn(session.accessToken, session.user.id, session.user.email, session.user.appMetadata?.provider)
                }
                .onFailure { e ->
                    pendingCodeVerifier = null
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.toAuthError(),
                        )
                    }
                }
        }
    }

    // ─── Hulpfuncties ────────────────────────────────────────────────────────

    fun clearError() {
        _state.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun clearConfirmation() {
        _state.update { it.copy(confirmationSentTo = null) }
    }

    private suspend fun checkAdminAndSetLoggedIn(
        accessToken: String,
        userId: String,
        email: String?,
        authProvider: String? = null,
    ) {
        // Best-effort: profielrij aanmaken als die ontbreekt (eerste login na e-mailbevestiging).
        runCatching { profileApi.ensureProfile(accessToken, userId, SessionStore.getSignLanguage()) }
        val legacyAdmin = authApi.amIAdmin(accessToken).getOrDefault(false)
        // Rol uit de database; zolang de rollen-migratie er niet is (of bij een netwerkfout)
        // valt dit terug op "admin als am_i_admin() dat zegt, anders leerling" — nooit op meer
        // rechten dan zeker is.
        val role = authApi.myRole(accessToken).getOrNull()
            ?.let { io.github.xavitje.signcoach.data.model.UserRole.fromCode(it) }
            ?: if (legacyAdmin) io.github.xavitje.signcoach.data.model.UserRole.ADMIN
               else io.github.xavitje.signcoach.data.model.UserRole.STUDENT
        val isAdmin = legacyAdmin || role == io.github.xavitje.signcoach.data.model.UserRole.ADMIN
        // Zero Trust: dit mag nooit de login zelf blokkeren of laten falen — bij geen netwerk of
        // een fout blijft linkedProviders gewoon leeg, dan toont de UI simpelweg geen "gekoppeld"-
        // indicator in plaats van te crashen of foutief "niet gekoppeld" te tonen.
        val linkedProviders = authApi.getUser(accessToken).getOrNull()
            ?.identities?.map { it.provider }?.toSet()
        _state.update {
            it.copy(
                status = AuthStatus.LOGGED_IN,
                isLoading = false,
                userId = userId,
                email = email,
                isAdmin = isAdmin,
                role = role,
                authProvider = authProvider ?: it.authProvider,
                linkedProviders = linkedProviders ?: it.linkedProviders,
            )
        }
        // Best-effort devicetoken-registratie voor push-notificaties. Volledig fail-safe (zie
        // PushNotificationRegistrar) — geen Firebase-config, geen netwerk of geen toestemming
        // betekent gewoon "nog niets geregistreerd", nooit een fout die de login blokkeert.
        runCatching { pushRegistrar.registerIfPossible() }
    }

    // ─── Wachtwoord wijzigen (Fase D, alleen voor e-mail-accounts) ─────────────

    fun changePassword(newPassword: String, confirmPassword: String) {
        if (newPassword.isBlank()) {
            _state.update { it.copy(error = io.github.xavitje.signcoach.domain.AppError.FieldEmpty) }
            return
        }
        if (newPassword.length < 6) {
            _state.update { it.copy(error = io.github.xavitje.signcoach.domain.AppError.PasswordTooShort) }
            return
        }
        if (newPassword != confirmPassword) {
            _state.update { it.copy(error = io.github.xavitje.signcoach.domain.AppError.PasswordsDoNotMatch) }
            return
        }
        val stored = SessionStore.load() ?: return
        _state.update { it.copy(isLoading = true, error = null, errorMessage = null, infoMessage = null) }
        scope.launch {
            authApi.updatePassword(stored.accessToken, newPassword)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, infoMessage = "Wachtwoord bijgewerkt.") }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = (e as? io.github.xavitje.signcoach.domain.AppError) ?: io.github.xavitje.signcoach.domain.AppError.Unknown(e),
                        )
                    }
                }
        }
    }

    // ─── Account verwijderen (Fase D) ───────────────────────────────────────

    fun deleteAccount() {
        val stored = SessionStore.load() ?: return
        _state.update { it.copy(isLoading = true, error = null, errorMessage = null, infoMessage = null) }
        scope.launch {
            authApi.deleteAccount(stored.accessToken)
                .onSuccess {
                    SessionStore.clear()
                    _state.update { AuthUiState(status = AuthStatus.LOGGED_OUT, isAccountDeleted = true) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = (e as? io.github.xavitje.signcoach.domain.AppError) ?: io.github.xavitje.signcoach.domain.AppError.Unknown(e),
                        )
                    }
                }
        }
    }
}
