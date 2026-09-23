package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stuurt lokaal opgeslagen crashrapporten (zie [io.github.xavitje.signcoach.data.repository.CrashReportRepository])
 * naar de `crash_reports`-tabel in Supabase, zodat admins ze kunnen bekijken in het adminpaneel.
 * Werkt ook zonder ingelogde gebruiker (crash vóór inloggen): dan wordt de anon-key zelf als
 * bearer-token meegestuurd, wat overeenkomt met een niet-geauthenticeerd REST-verzoek. De RLS-
 * policy "anyone can insert own crash report" staat dit expliciet toe zolang user_id leeg is of
 * overeenkomt met de ingelogde gebruiker.
 */
class CrashReportApi(
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
    data class CrashReportBody(
        @SerialName("user_id") val userId: String? = null,
        val platform: String,
        @SerialName("app_version") val appVersion: String? = null,
        @SerialName("os_version") val osVersion: String? = null,
        @SerialName("device_model") val deviceModel: String? = null,
        @SerialName("error_type") val errorType: String? = null,
        @SerialName("error_message") val errorMessage: String? = null,
        @SerialName("stack_trace") val stackTrace: String,
        @SerialName("is_fatal") val isFatal: Boolean,
    )

    suspend fun reportCrash(accessToken: String?, body: CrashReportBody): Result<Unit> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/rest/v1/crash_reports") {
            header("apikey", anonKey)
            header("Authorization", "Bearer ${accessToken ?: anonKey}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }
}
