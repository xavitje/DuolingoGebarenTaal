package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
 * Registreert een FCM-devicetoken bij Supabase (tabel `device_push_tokens`), zodat de
 * streak-herinnering-Edge Function (zie migratie `crash_reporting_and_push_tokens`) weet waar hij
 * naartoe moet pushen. Een "upsert op token" (via `Prefer: resolution=merge-duplicates` +
 * `on_conflict=token`) voorkomt dubbele rijen als hetzelfde toestel zich opnieuw registreert.
 */
class PushTokenApi(
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
    private data class RegisterTokenBody(
        @SerialName("user_id") val userId: String,
        val token: String,
        val platform: String,
    )

    suspend fun registerToken(accessToken: String, userId: String, token: String, platform: String): Result<Unit> =
        safeApiCall {
            val response: HttpResponse = client.post("$baseUrl/rest/v1/device_push_tokens") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                parameter("on_conflict", "token")
                contentType(ContentType.Application.Json)
                setBody(RegisterTokenBody(userId = userId, token = token, platform = platform))
            }
        }
}
