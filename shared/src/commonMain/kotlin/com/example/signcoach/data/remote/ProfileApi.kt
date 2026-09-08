package com.example.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
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
        @SerialName("daily_goal_xp") val dailyGoalXp: Int = 20,
    )

    /**
     * Maakt een profielrij aan voor een nieuw geregistreerde gebruiker.
     * Wordt aangeroepen direct na succesvolle registratie (e-mail of Google).
     */
    suspend fun createProfile(
        accessToken: String,
        userId: String,
        displayName: String?,
    ): Result<Unit> = runCatching {
        val response: HttpResponse = client.post("$baseUrl/rest/v1/profiles") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(
                buildMap<String, String?> {
                    put("id", userId)
                    if (displayName != null) put("display_name", displayName)
                }
            )
        }
        if (!response.status.isSuccess()) error("Profiel aanmaken mislukt: ${response.status}")
    }

    /** Haalt het profiel van de ingelogde gebruiker op. */
    suspend fun getProfile(accessToken: String): Result<UserProfile> = runCatching {
        val response: HttpResponse = client.get("$baseUrl/rest/v1/profiles") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            header("Accept", "application/vnd.pgrst.object+json")
            url { parameters.append("select", "*") }
        }
        if (!response.status.isSuccess()) error("Profiel ophalen mislukt: ${response.status}")
        response.body<UserProfile>()
    }
}
