package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * API voor het wegschrijven van voortgang nadat een oefening (gebaar) succesvol is afgerond,
 * en voor het opvragen van de vandaag-verdiende XP voor de voortgangsring.
 */
class ProgressApi(
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
    private data class RecordPracticeRequest(
        @SerialName("p_gesture_id") val gestureId: String,
        @SerialName("p_score") val score: Float,
    )

    /**
     * Slaat een voltooide oefening op in Supabase via de RPC-functie `record_practice`.
     * De backend berekent de streak, freezes en XP op basis van deze poging (15 XP voor
     * de eerste keer per dag, anders 2 XP). Streak-freezes (1 gratis per kalendermaand) worden
     * hierbij automatisch ingezet als er precies 1 dag is gemist.
     */
    suspend fun recordPractice(
        accessToken: String,
        gestureId: String,
        score: Float,
    ): Result<Unit> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/rest/v1/rpc/record_practice") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(RecordPracticeRequest(gestureId, score))
        }
    }

    @Serializable
    private data class CompletedRow(@SerialName("gesture_id") val gestureId: String)

    /**
     * Haalt op welke gebaren deze gebruiker in [signLanguage] al eens gehaald heeft, uit
     * `user_progress` (RLS: alleen eigen rijen). Een rij ontstaat pas via `record_practice`, dus
     * na een geslaagde oefening. Best-effort: bij een fout houdt de app de lokale waarheid aan.
     */
    suspend fun fetchCompletedGestureIds(
        accessToken: String,
        signLanguage: String,
    ): Result<List<String>> = safeApiCall {
        val response: HttpResponse = client.get("$baseUrl/rest/v1/user_progress") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            parameter("select", "gesture_id")
            parameter("sign_language", "eq.$signLanguage")
        }
        response.body<List<CompletedRow>>().map { it.gestureId }
    }

    /**
     * Haalt de vandaag verdiende XP op via de RPC-functie `get_today_xp` (datumvergelijking
     * gebeurt server-side op basis van `current_date`, zodat client- en servertijdzone niet uit
     * elkaar kunnen lopen). Gebruikt voor de dagdoel-ring op het Voortgang-scherm.
     */
    suspend fun getTodayXp(accessToken: String): Result<Int> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/rest/v1/rpc/get_today_xp") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        response.body<Int>()
    }
}
