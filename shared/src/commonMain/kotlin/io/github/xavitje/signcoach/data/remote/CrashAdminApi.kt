package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * REST-client voor het crashoverzicht in het adminpaneel. Zelfde patroon als [AdminApi]: de
 * RLS-policy "admins can view crash reports" (migratie `crash_reporting_and_push_tokens`) zorgt
 * dat alleen de geregistreerde admin hier daadwerkelijk iets terugkrijgt.
 */
class CrashAdminApi(
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
    data class CrashReportDto(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        val platform: String,
        @SerialName("app_version") val appVersion: String? = null,
        @SerialName("os_version") val osVersion: String? = null,
        @SerialName("device_model") val deviceModel: String? = null,
        @SerialName("error_type") val errorType: String? = null,
        @SerialName("error_message") val errorMessage: String? = null,
        @SerialName("stack_trace") val stackTrace: String? = null,
        @SerialName("is_fatal") val isFatal: Boolean = true,
        @SerialName("created_at") val createdAt: String? = null,
    )

    /** Meest recente crashes eerst, gelimiteerd zodat het scherm niet duizenden rijen laadt. */
    suspend fun fetchCrashReports(accessToken: String, limit: Int = 200): Result<List<CrashReportDto>> = safeApiCall {
        val response: HttpResponse = client.get("$baseUrl/rest/v1/crash_reports") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            parameter(
                "select",
                "id,user_id,platform,app_version,os_version,device_model,error_type,error_message,stack_trace,is_fatal,created_at",
            )
            parameter("order", "created_at.desc")
            parameter("limit", limit)
        }
        response.body<List<CrashReportDto>>()
    }
}
