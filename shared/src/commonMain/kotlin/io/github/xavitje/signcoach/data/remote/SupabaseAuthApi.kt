package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

/**
 * Auth-API voor Supabase: login (admin en gewone gebruikers), registratie, uitloggen,
 * wachtwoord-reset en Google OAuth. De echte beveiliging zit server-side in RLS-policies;
 * de auth-tokens hier geven alleen toegang tot wat die policies toestaan.
 */
class SupabaseAuthApi(
    private val baseUrl: String = SupabaseConfig.URL,
    private val anonKey: String = SupabaseConfig.ANON_KEY,
) {
    private val client = HttpClient {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Serializable
    data class AuthSession(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        val user: AuthUser,
    )

    @Serializable
    data class AuthUser(
        val id: String,
        val email: String? = null,
        @SerialName("app_metadata") val appMetadata: AppMetadata? = null,
    )

    @Serializable
    data class AppMetadata(
        /** "email" voor wachtwoord-accounts, "google" na Google-OAuth. Bepaalt of de
         *  wachtwoord-wijzigen-optie in Instellingen zinvol is (zie ProfileScreen). */
        val provider: String? = null,
    )

    /** Eén identiteit (inlogmethode) die aan de gebruiker gekoppeld is — een gebruiker kan er
     *  meerdere hebben na het koppelen van bv. Google aan een bestaand e-mail-account. */
    @Serializable
    data class UserIdentity(
        val provider: String,
    )

    @Serializable
    data class UserWithIdentities(
        val id: String,
        val identities: List<UserIdentity> = emptyList(),
    )

    /** Inloggen met e-mail en wachtwoord (admin én gewone gebruikers). */
    suspend fun signInWithPassword(email: String, password: String): Result<AuthSession> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/token") {
            header("apikey", anonKey)
            parameter("grant_type", "password")
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email, "password" to password))
        }
        response.body<AuthSession>()
    }

    /** Uitkomst van registreren: meteen een sessie, of eerst het e-mailadres bevestigen. */
    sealed interface SignUpResult {
        data class SignedIn(val session: AuthSession) : SignUpResult
        /** E-mailbevestiging staat aan in Supabase: er is een mail met een link verstuurd en er
         * komt pas een sessie na bevestigen + inloggen. */
        data object ConfirmationRequired : SignUpResult
    }

    /**
     * Registreren met e-mail en wachtwoord. Met e-mailbevestiging aan (Supabase → Authentication →
     * Sign In / Providers → Confirm email) geeft Supabase géén sessie terug maar alleen een
     * gebruiker; voorheen liep het uitlezen daarvan stuk en zag de gebruiker "netwerkfout".
     */
    suspend fun signUp(email: String, password: String): Result<SignUpResult> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/signup") {
            header("apikey", anonKey)
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email, "password" to password))
        }
        val text = response.bodyAsText()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
        if (obj != null && obj["access_token"] != null) {
            SignUpResult.SignedIn(json.decodeFromString(AuthSession.serializer(), text))
        } else {
            SignUpResult.ConfirmationRequired
        }
    }

    /** Uitloggen: invalideert het access-token server-side. */
    suspend fun signOut(accessToken: String): Result<Unit> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/logout") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
        }
    }

    /** Stuurt een wachtwoord-reset-mail via Supabase — geen zelfgebouwde flow nodig. */
    suspend fun resetPassword(email: String): Result<Unit> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/recover") {
            header("apikey", anonKey)
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email))
        }
    }

    /** Ververst een sessie met de refresh-token, zodat de gebruiker niet elke app-start opnieuw
     *  hoeft in te loggen als het access-token (standaard 1 uur geldig) verlopen is. */
    suspend fun refresh(refreshToken: String): Result<AuthSession> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/token") {
            header("apikey", anonKey)
            parameter("grant_type", "refresh_token")
            contentType(ContentType.Application.Json)
            setBody(mapOf("refresh_token" to refreshToken))
        }
        response.body<AuthSession>()
    }

    /**
     * Vraagt via RPC of de ingelogde gebruiker een admin is. Roept de Postgres-functie
     * `public.am_i_admin()` aan — die lekt niets over andere admins, geeft alleen een boolean
     * terug. De `admins`-tabel zelf heeft bewust geen read-policy.
     */
    suspend fun amIAdmin(accessToken: String): Result<Boolean> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/rest/v1/rpc/am_i_admin") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        response.bodyAsText().trim().toBooleanStrictOrNull() ?: false
    }

    /**
     * Vraagt via RPC de rol van de ingelogde gebruiker op (`public.my_role()`, geeft alleen de
     * eigen rol terug als tekst, bv. "student"). Faalt als de rollen-migratie nog niet is
     * uitgevoerd — de aanroeper valt dan terug op [amIAdmin].
     */
    suspend fun myRole(accessToken: String): Result<String> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/rest/v1/rpc/my_role") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        response.bodyAsText().trim().trim('"')
    }

    /**
     * Haalt de ingelogde gebruiker op, inclusief alle gekoppelde identiteiten (e-mail, Google,
     * ...). Gebruikt om in Instellingen te tonen of Google al gekoppeld is (zie ProfileScreen).
     */
    suspend fun getUser(accessToken: String): Result<UserWithIdentities> = safeApiCall {
        val response: HttpResponse = client.get("$baseUrl/auth/v1/user") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
        }
        response.body<UserWithIdentities>()
    }

    /**
     * Geeft de PKCE-autorisatie-URL voor Google terug. De app opent deze URL in een echte browser
     * (Custom Tab op Android — zie [io.github.xavitje.signcoach.platform.BrowserLauncher]). Na
     * toestemming bij Google redirect Supabase naar [OAUTH_REDIRECT_URL] met een `?code=...`
     * query-parameter; die code wordt via [exchangeOAuthCode] samen met de bijbehorende
     * `codeVerifier` uitgewisseld voor een sessie. `codeChallenge` is de SHA-256(code_verifier),
     * base64url-gecodeerd zonder padding — zie [io.github.xavitje.signcoach.util.Pkce].
     */
    fun googleOAuthUrl(codeChallenge: String, redirectTo: String = OAUTH_REDIRECT_URL): String {
        return "$baseUrl/auth/v1/authorize" +
            "?provider=google" +
            "&redirect_to=$redirectTo" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=s256"
    }

    /**
     * Wisselt een OAuth authorization code uit voor een Supabase-sessie via PKCE.
     * Wordt aangeroepen nadat de browser de deep-link teruggeeft met `?code=...`. `codeVerifier`
     * moet exact de waarde zijn die gebruikt is om de `code_challenge` in [googleOAuthUrl] te
     * berekenen — zonder deze klopt de PKCE-uitwisseling niet en weigert Supabase de aanvraag.
     */
    suspend fun exchangeOAuthCode(code: String, codeVerifier: String): Result<AuthSession> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/token") {
            header("apikey", anonKey)
            parameter("grant_type", "pkce")
            contentType(ContentType.Application.Json)
            setBody(mapOf("auth_code" to code, "code_verifier" to codeVerifier))
        }
        response.body<AuthSession>()
    }

    /**
     * Wijzigt het wachtwoord van de ingelogde gebruiker (alleen zinvol voor e-mail-accounts,
     * zie [AppMetadata.provider]). Supabase vereist geen huidig wachtwoord bij deze aanroep —
     * het geldige access-token is het bewijs dat de gebruiker al is ingelogd.
     */
    suspend fun updatePassword(accessToken: String, newPassword: String): Result<Unit> = safeApiCall {
        val response: HttpResponse = client.put("$baseUrl/auth/v1/user") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(mapOf("password" to newPassword))
        }
    }

    /**
     * Verwijdert het eigen account definitief via de `delete_own_account()` RPC
     * (SECURITY DEFINER — verwijdert de rij in `auth.users`, wat via ON DELETE CASCADE ook
     * `profiles`, `user_progress` en `user_badges` opruimt). De database weigert dit voor het
     * admin-account.
     */
    suspend fun deleteAccount(accessToken: String): Result<Unit> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/rest/v1/rpc/delete_own_account") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
    }

    companion object {
        /**
         * Custom-scheme deep-link waarop de app de OAuth-redirect terugvangt. Moet exact
         * overeenkomen met het intent-filter in AndroidManifest.xml en met de "Redirect URLs" die
         * in het Supabase-dashboard (Authentication > URL Configuration) zijn toegestaan.
         */
        const val OAUTH_REDIRECT_URL = "signcoach://auth-callback"
    }
}
