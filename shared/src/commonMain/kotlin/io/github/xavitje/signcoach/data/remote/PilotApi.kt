package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pilotmetingen en content-overzicht (fase 5, migratie 20261026210000_fase5_pilot.sql).
 * Metingen zijn anoniem per gebaar per school per dag; per leerling wordt alleen bijgehouden op
 * welke dagen er geoefend is.
 */
class PilotApi(
    private val baseUrl: String = SupabaseConfig.URL,
    private val anonKey: String = SupabaseConfig.ANON_KEY,
) {
    private val client = HttpClient {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }
    }

    private fun HttpRequestBuilder.auth(accessToken: String) {
        header("apikey", anonKey)
        header("Authorization", "Bearer $accessToken")
        contentType(ContentType.Application.Json)
    }

    @Serializable private data class AttemptArgs(
        @SerialName("p_gesture_id") val gestureId: String,
        @SerialName("p_success") val success: Boolean,
    )
    @Serializable private data class SchoolStatsArgs(@SerialName("p_school_id") val schoolId: String)
    @Serializable private data class GestureStatsArgs(@SerialName("p_school_id") val schoolId: String? = null)
    @Serializable private data class LanguageArgs(@SerialName("p_sign_language") val signLanguage: String)

    @Serializable
    data class WeekStats(
        @SerialName("week_start") val weekStart: String,
        @SerialName("active_students") val activeStudents: Int = 0,
        @SerialName("total_students") val totalStudents: Int = 0,
        val attempts: Int = 0,
        val successes: Int = 0,
    )

    @Serializable
    data class GestureStats(
        @SerialName("gesture_id") val gestureId: String,
        val attempts: Int = 0,
        val successes: Int = 0,
    ) {
        val successRate: Float get() = if (attempts == 0) 0f else successes.toFloat() / attempts
    }

    @Serializable
    data class ContentStatus(
        @SerialName("gesture_id") val gestureId: String,
        val name: String = "",
        @SerialName("unit_id") val unitId: String? = null,
        @SerialName("has_demo_video") val hasDemoVideo: Boolean = false,
        val pending: Int = 0,
        val approved: Int = 0,
    )

    /** Na een camera-oefening: gelukt (3 herhalingen) of opgegeven. Best-effort, fouten worden genegeerd. */
    suspend fun logAttempt(accessToken: String, gestureId: String, success: Boolean): Result<Unit> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/log_practice_attempt") {
            auth(accessToken)
            setBody(AttemptArgs(gestureId, success))
        }
        Unit
    }

    /** Zelfcheck afgerond: telt als oefendag voor "actieve leerlingen", niet als herkenningspoging. */
    suspend fun markPracticeDay(accessToken: String): Result<Unit> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/mark_practice_day") {
            auth(accessToken)
            setBody("{}")
        }
        Unit
    }

    suspend fun schoolStats(accessToken: String, schoolId: String): Result<List<WeekStats>> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/school_pilot_stats") {
            auth(accessToken)
            setBody(SchoolStatsArgs(schoolId))
        }.body<List<WeekStats>>()
    }

    suspend fun gestureStats(accessToken: String, schoolId: String?): Result<List<GestureStats>> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/admin_gesture_stats") {
            auth(accessToken)
            setBody(GestureStatsArgs(schoolId))
        }.body<List<GestureStats>>()
    }

    suspend fun contentStatus(accessToken: String, signLanguage: String): Result<List<ContentStatus>> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/content_status") {
            auth(accessToken)
            setBody(LanguageArgs(signLanguage))
        }.body<List<ContentStatus>>()
    }
}
