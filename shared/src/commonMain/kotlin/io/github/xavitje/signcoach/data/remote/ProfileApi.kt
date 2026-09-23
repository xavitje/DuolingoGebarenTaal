package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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

/**
 * API voor het aanmaken en ophalen van gebruikersprofielen in de `profiles`-tabel.
 * RLS zorgt ervoor dat een gebruiker alleen zijn eigen profiel kan lezen/schrijven.
 */
class ProfileApi(
    private val baseUrl: String = SupabaseConfig.URL,
    private val anonKey: String = SupabaseConfig.ANON_KEY,
) {
    private val client = HttpClient {
        expectSuccess = true
        // Zero Trust: neem nooit aan dat de server (op tijd) antwoordt — zonder expliciete
        // timeout kan een opgehangen verbinding de UI voor onbepaalde tijd laten hangen.
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
    data class UserProfile(
        val id: String,
        @SerialName("display_name") val displayName: String?,
        @SerialName("avatar_emoji") val avatarEmoji: String = "🧑",
        @SerialName("current_streak") val currentStreak: Int = 0,
        @SerialName("longest_streak") val longestStreak: Int = 0,
        @SerialName("total_xp") val totalXp: Int = 0,
        @SerialName("daily_goal_xp") val dailyGoalXp: Int = 35,
        @SerialName("streak_freezes_available") val streakFreezesAvailable: Int = 0,
        // Voor offline-first streak-berekening (zie OfflineProgressRepository): de app moet
        // na een sync weten "wanneer heb je voor het laatst geoefend" om diezelfde regels
        // lokaal te kunnen toepassen als de record_practice()-functie in Supabase.
        @SerialName("last_practice_date") val lastPracticeDate: String? = null,
        @SerialName("streak_freezes_reset_at") val streakFreezesResetAt: String? = null,
        /** Actief gekozen gebarentaal (bv. "ngt", "asl") — zie SignLanguage. */
        @SerialName("sign_language") val signLanguage: String = "ngt",
    )

    /**
     * Maakt een profielrij aan voor een nieuw geregistreerde gebruiker.
     * Wordt aangeroepen direct na succesvolle registratie (e-mail of Google).
     * [signLanguage] is de lokaal gekozen gebarentaal (zie onboarding), zodat het profiel meteen
     * met de juiste taal ontstaat i.p.v. later een aparte PATCH nodig te hebben.
     */
    suspend fun createProfile(
        accessToken: String,
        userId: String,
        displayName: String?,
        signLanguage: String = "ngt",
    ): Result<Unit> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/rest/v1/profiles") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(
                buildMap<String, String?> {
                    put("id", userId)
                    if (displayName != null) put("display_name", displayName)
                    put("sign_language", signLanguage)
                }
            )
        }
    }

    /** Werkt de actieve gebarentaal bij (instellingen -> taal wisselen). */
    suspend fun updateSignLanguage(accessToken: String, userId: String, signLanguage: String): Result<Unit> = safeApiCall {
        val response: HttpResponse = client.patch("$baseUrl/rest/v1/profiles") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            url { parameters.append("id", "eq.$userId") }
            setBody(mapOf("sign_language" to signLanguage))
        }
    }

    /** Haalt het profiel van de ingelogde gebruiker op. */
    suspend fun getProfile(accessToken: String): Result<UserProfile> = safeApiCall {
        val response: HttpResponse = client.get("$baseUrl/rest/v1/profiles") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            url { parameters.append("select", "*") }
        }
        val profiles = response.body<List<UserProfile>>()
        profiles.firstOrNull() ?: throw io.github.xavitje.signcoach.domain.AppError.NotFound
    }

    /** Werkt alleen de avatar-emoji bij (Fase D: avatar-picker in het profielscherm). */
    suspend fun updateAvatarEmoji(accessToken: String, userId: String, emoji: String): Result<Unit> = safeApiCall {
        val response: HttpResponse = client.patch("$baseUrl/rest/v1/profiles") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            url { parameters.append("id", "eq.$userId") }
            setBody(mapOf("avatar_emoji" to emoji))
        }
    }
}
