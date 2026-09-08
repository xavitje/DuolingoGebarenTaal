package com.example.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
    )

    /** Inloggen met e-mail en wachtwoord (admin én gewone gebruikers). */
    suspend fun signInWithPassword(email: String, password: String): Result<AuthSession> = runCatching {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/token") {
            header("apikey", anonKey)
            parameter("grant_type", "password")
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email, "password" to password))
        }
        if (!response.status.isSuccess()) error("Inloggen mislukt: ${response.status}")
        response.body<AuthSession>()
    }

    /** Registreren met e-mail en wachtwoord. */
    suspend fun signUp(email: String, password: String): Result<AuthSession> = runCatching {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/signup") {
            header("apikey", anonKey)
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email, "password" to password))
        }
        if (!response.status.isSuccess()) error("Registreren mislukt: ${response.status}")
        response.body<AuthSession>()
    }

    /** Uitloggen: invalideert het access-token server-side. */
    suspend fun signOut(accessToken: String): Result<Unit> = runCatching {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/logout") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) error("Uitloggen mislukt: ${response.status}")
    }

    /** Stuurt een wachtwoord-reset-mail via Supabase — geen zelfgebouwde flow nodig. */
    suspend fun resetPassword(email: String): Result<Unit> = runCatching {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/recover") {
            header("apikey", anonKey)
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email))
        }
        if (!response.status.isSuccess()) error("Wachtwoord-reset mislukt: ${response.status}")
    }

    /** Ververst een sessie met de refresh-token, zodat de gebruiker niet elke app-start opnieuw
     *  hoeft in te loggen als het access-token (standaard 1 uur geldig) verlopen is. */
    suspend fun refresh(refreshToken: String): Result<AuthSession> = runCatching {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/token") {
            header("apikey", anonKey)
            parameter("grant_type", "refresh_token")
            contentType(ContentType.Application.Json)
            setBody(mapOf("refresh_token" to refreshToken))
        }
        if (!response.status.isSuccess()) error("Sessie verversen mislukt: ${response.status}")
        response.body<AuthSession>()
    }

    /**
     * Vraagt via RPC of de ingelogde gebruiker een admin is. Roept de Postgres-functie
     * `public.am_i_admin()` aan — die lekt niets over andere admins, geeft alleen een boolean
     * terug. De `admins`-tabel zelf heeft bewust geen read-policy.
     */
    suspend fun amIAdmin(accessToken: String): Result<Boolean> = runCatching {
        val response: HttpResponse = client.post("$baseUrl/rest/v1/rpc/am_i_admin") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        if (!response.status.isSuccess()) error("Admin-check mislukt: ${response.status}")
        val json = response.body<JsonObject>()
        // De RPC geeft een boolean terug als top-level JSON-waarde
        response.body<kotlinx.serialization.json.JsonElement>().jsonPrimitive.boolean
    }

    /**
     * Geeft de Supabase OAuth-URL voor Google terug. De app opent deze URL in een browser
     * (Custom Tab op Android, ASWebAuthenticationSession op iOS). Na toestemming redirectt
     * Supabase naar de redirect_uri met een code; die code wordt dan uitgewisseld voor een sessie.
     * Voor web-only flow: redirect_uri is de Supabase callback zelf, de app vangt de diep-link op.
     */
    fun googleOAuthUrl(redirectTo: String = "${baseUrl}/auth/v1/callback"): String {
        return "$baseUrl/auth/v1/authorize?provider=google&redirect_to=${redirectTo}"
    }

    /**
     * Wisselt een OAuth authorization code uit voor een Supabase-sessie.
     * Wordt aangeroepen nadat de browser de deep-link teruggeeft met `?code=...`.
     */
    suspend fun exchangeOAuthCode(code: String): Result<AuthSession> = runCatching {
        val response: HttpResponse = client.post("$baseUrl/auth/v1/token") {
            header("apikey", anonKey)
            parameter("grant_type", "pkce")
            contentType(ContentType.Application.Json)
            setBody(mapOf("auth_code" to code))
        }
        if (!response.status.isSuccess()) error("OAuth code uitwisselen mislukt: ${response.status}")
        response.body<AuthSession>()
    }
}
