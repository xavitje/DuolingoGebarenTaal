package io.github.xavitje.signcoach.data.remote

import io.github.xavitje.signcoach.data.model.LandmarkPoint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
 * REST-client voor het admin-goedkeurscherm. Elke aanroep stuurt het access-token van de
 * ingelogde admin mee als Bearer-token (naast de vaste anon-key, die Supabase altijd als
 * "apikey"-header verwacht) — de RLS-policies uit migratie "admin_review_access" laten alleen
 * de ene geregistreerde admin daadwerkelijk iets terugzien of wijzigen. Een gewone gebruiker die
 * deze endpoints met de kale anon-key zou aanroepen krijgt gewoon niets terug.
 */
class AdminApi(
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
    data class AdminSampleDto(
        val id: String,
        @SerialName("gesture_id") val gestureId: String,
        val landmarks: List<LandmarkPoint>,
        val handedness: String = "UNKNOWN",
        @SerialName("video_path") val videoPath: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
    )

    @Serializable
    private data class StorageObjectDto(val name: String)

    @Serializable
    private data class StorageListRequest(val prefix: String, val limit: Int = 100)

    @Serializable
    private data class UpdateStatusBody(
        val status: String,
        @SerialName("reviewer_note") val reviewerNote: String? = null,
        @SerialName("reviewed_by") val reviewedBy: String,
    )

    /** Alle nog te beoordelen bijdragen, oudste eerst. */
    suspend fun fetchPendingSamples(accessToken: String): Result<List<AdminSampleDto>> = safeApiCall {
        val response: HttpResponse = client.get("$baseUrl/rest/v1/gesture_samples") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            parameter("select", "id,gesture_id,landmarks,handedness,video_path,created_at")
            parameter("status", "eq.pending")
            parameter("order", "created_at.asc")
        }
        response.body<List<AdminSampleDto>>()
    }

    /** Volledige paden (t.o.v. de bucket-root) van alle review-beelden onder [videoPath]. */
    suspend fun listReviewImages(accessToken: String, videoPath: String): Result<List<String>> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/storage/v1/object/list/gesture-videos") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(StorageListRequest(prefix = videoPath))
        }
        response.body<List<StorageObjectDto>>()
            .map { "$videoPath/${it.name}" }
            .sorted()
    }

    suspend fun fetchImageBytes(accessToken: String, path: String): Result<ByteArray> = safeApiCall {
        val response: HttpResponse = client.get("$baseUrl/storage/v1/object/gesture-videos/$path") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
        }
        response.body<ByteArray>()
    }

    /** Keurt een bijdrage goed of af. [reviewedBy] is het e-mailadres van de ingelogde admin —
     * puur informatief (audit-spoor), de daadwerkelijke autorisatie zit in de RLS-policy. */
    suspend fun updateSampleStatus(
        accessToken: String,
        id: String,
        status: String,
        reviewerNote: String?,
        reviewedBy: String,
    ): Result<Unit> = safeApiCall {
        val response: HttpResponse = client.patch("$baseUrl/rest/v1/gesture_samples") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            parameter("id", "eq.$id")
            contentType(ContentType.Application.Json)
            setBody(UpdateStatusBody(status = status, reviewerNote = reviewerNote, reviewedBy = reviewedBy))
        }
    }
}
