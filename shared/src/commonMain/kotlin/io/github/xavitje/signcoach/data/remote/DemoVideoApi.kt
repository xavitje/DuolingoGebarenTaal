package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Demovideo's door vakmensen (migratie 20261026230000): opnemen → privé-bucket 'demo-review' +
 * rij in `demo_videos` (pending) → een ANDERE vakman keurt goed of af via de edge function
 * `review-demo-video`. Pas na goedkeuring zien leerlingen de video.
 */
class DemoVideoApi(
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
    }

    @Serializable
    data class DemoVideo(
        val id: String,
        @SerialName("gesture_id") val gestureId: String,
        @SerialName("sign_language") val signLanguage: String = "ngt",
        @SerialName("storage_path") val storagePath: String,
        @SerialName("recorded_by") val recordedBy: String? = null,
        val status: String = "pending",
        @SerialName("review_note") val reviewNote: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
    )

    @Serializable private data class NewDemo(
        @SerialName("gesture_id") val gestureId: String,
        @SerialName("sign_language") val signLanguage: String,
        @SerialName("storage_path") val storagePath: String,
        @SerialName("recorded_by") val recordedBy: String,
    )
    @Serializable private data class SignRequest(val expiresIn: Int)
    @Serializable private data class SignResponse(val signedURL: String)
    @Serializable private data class ReviewRequest(val id: String, val approve: Boolean, val note: String? = null)

    /** Uploadt de video en zet hem in de wachtrij. */
    suspend fun submit(accessToken: String, userId: String, gestureId: String, signLanguage: String, path: String, bytes: ByteArray): Result<Unit> = safeApiCall {
        client.post("$baseUrl/storage/v1/object/demo-review/$path") {
            auth(accessToken)
            contentType(ContentType.parse("video/mp4"))
            setBody(bytes)
            timeout { requestTimeoutMillis = 120_000 }
        }
        client.post("$baseUrl/rest/v1/demo_videos") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(NewDemo(gestureId, signLanguage, path, userId))
        }
        Unit
    }

    suspend fun pending(accessToken: String, signLanguage: String): Result<List<DemoVideo>> = safeApiCall {
        client.get("$baseUrl/rest/v1/demo_videos") {
            auth(accessToken)
            parameter("select", "id,gesture_id,sign_language,storage_path,recorded_by,status,review_note,created_at")
            parameter("status", "eq.pending")
            parameter("sign_language", "eq.$signLanguage")
            parameter("order", "created_at.asc")
        }.body<List<DemoVideo>>()
    }

    /** Tijdelijke link (1 uur) om een video uit de privé-bucket te bekijken. */
    suspend fun signedUrl(accessToken: String, path: String): Result<String> = safeApiCall {
        val response = client.post("$baseUrl/storage/v1/object/sign/demo-review/$path") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(SignRequest(3600))
        }.body<SignResponse>()
        "$baseUrl/storage/v1" + response.signedURL
    }

    suspend fun review(accessToken: String, id: String, approve: Boolean, note: String?): Result<Unit> = safeApiCall {
        client.post("$baseUrl/functions/v1/review-demo-video") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(ReviewRequest(id, approve, note?.takeIf { it.isNotBlank() }))
            timeout { requestTimeoutMillis = 120_000 }
        }
        Unit
    }
}
