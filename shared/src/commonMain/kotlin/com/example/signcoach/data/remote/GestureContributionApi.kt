package com.example.signcoach.data.remote

import com.example.signcoach.data.model.LandmarkPoint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
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

/**
 * Dunne REST-client naar de Supabase-referentiedatabase (PostgREST + Storage). Bewust geen
 * volledige Supabase-SDK: alleen de drie operaties die deze app nodig heeft, met expliciete
 * RLS-afhankelijke garanties (zie SupabaseConfig en de `gesture_reference_dataset`-migratie):
 * - [fetchApprovedSamples]: alleen door een reviewer goedgekeurde referentievarianten.
 * - [submitSample] / [uploadReviewClip]: een nieuwe bijdrage komt altijd als 'pending' binnen
 *   en wordt pas na menselijke beoordeling van de video onderdeel van wat andere gebruikers
 *   synchroniseren.
 */
class GestureContributionApi(
    private val baseUrl: String = SupabaseConfig.URL,
    private val anonKey: String = SupabaseConfig.ANON_KEY,
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) { level = LogLevel.INFO }
    }

    @Serializable
    data class ApprovedSampleDto(
        @SerialName("gesture_id") val gestureId: String,
        val landmarks: List<LandmarkPoint>,
        val handedness: String = "UNKNOWN",
    )

    @Serializable
    data class NewSampleDto(
        @SerialName("gesture_id") val gestureId: String,
        val landmarks: List<LandmarkPoint>,
        val handedness: String,
        @SerialName("video_path") val videoPath: String? = null,
        @SerialName("contributor_id") val contributorId: String? = null,
    )

    /** Sync-bij-opstarten: alle referentievarianten die een reviewer heeft goedgekeurd. */
    suspend fun fetchApprovedSamples(): Result<List<ApprovedSampleDto>> = runCatching {
        val response: HttpResponse = client.get("$baseUrl/rest/v1/gesture_samples") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $anonKey")
            parameter("select", "gesture_id,landmarks,handedness")
            parameter("status", "eq.approved")
        }
        if (!response.status.isSuccess()) error("fetchApprovedSamples failed: ${response.status}")
        response.body<List<ApprovedSampleDto>>()
    }

    /**
     * Dient een nieuwe bijdrage in. De database-trigger `force_pending_on_insert` zet de
     * status server-side altijd op 'pending', ongeacht wat hier wordt meegestuurd — een
     * bijdrage wordt dus nooit direct live voor andere gebruikers.
     */
    suspend fun submitSample(sample: NewSampleDto): Result<Unit> = runCatching {
        val response: HttpResponse = client.post("$baseUrl/rest/v1/gesture_samples") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $anonKey")
            contentType(ContentType.Application.Json)
            setBody(sample)
        }
        if (!response.status.isSuccess()) error("submitSample failed: ${response.status}")
    }

    /**
     * Uploadt de review-clip (korte beeldreeks van de opname) naar de privé 'gesture-videos'
     * bucket, zodat een menselijke reviewer het gebaar visueel kan controleren — alleen
     * zichtbaar via Supabase Studio (reviewer-login), niet via de anon-key die de app gebruikt.
     * [path] moet uniek zijn, bv. "<gestureId>/<uuid>.gif".
     *
     * BELANGRIJK: geen "x-upsert"-header. De Storage-RLS-policy op deze bucket staat bewust
     * alleen INSERT toe (nooit overschrijven/lezen via de anon-key) — een upsert-upload vertaalt
     * zich naar een INSERT ... ON CONFLICT DO UPDATE, wat óók een UPDATE-policy vereist en anders
     * hard faalt met "new row violates row-level security policy", zelfs als er niets bestaat om
     * te overschrijven. [randomId] in ContributionViewModel garandeert al een uniek pad per
     * opname, dus upsert is hier nooit nodig.
     */
    suspend fun uploadReviewClip(path: String, bytes: ByteArray, contentType: String): Result<Unit> = runCatching {
        val response: HttpResponse = client.post("$baseUrl/storage/v1/object/gesture-videos/$path") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $anonKey")
            contentType(ContentType.parse(contentType))
            setBody(bytes)
        }
        if (!response.status.isSuccess()) error("uploadReviewClip failed: ${response.status}")
    }
}
