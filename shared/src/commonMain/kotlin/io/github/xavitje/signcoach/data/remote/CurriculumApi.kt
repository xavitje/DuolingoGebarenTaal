package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Haalt de curriculum-structuur op uit de Supabase `gestures`-tabel.
 * Deze tabel is de bron van waarheid voor *welke* gebaren er zijn en in welke volgorde
 * ze in het leerpad staan — onafhankelijk van de herkenningstemplates (SignTemplate).
 *
 * Gebruikt de anon-key (geen authenticatie nodig — curriculumdata is publiek leesbaar).
 * Valt terug op de offline-definitie in [FallbackCurriculum] als het netwerk niet beschikbaar is.
 */
class CurriculumApi(
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
    data class GestureDto(
        val id: String,
        val name: String,
        val category: String = "letter",
        @SerialName("order_index") val orderIndex: Int = 0,
        @SerialName("unit_id") val unitId: String = "alphabet",
        @SerialName("demo_video_url") val demoVideoUrl: String? = null,
    )

    /**
     * Haalt alle gebaren op voor de gegeven gebarentaal, gesorteerd op unit + volgorde.
     * Geeft een lege lijst terug bij netwerk-fout — de aanroeper valt dan terug op de lokale set.
     */
    suspend fun fetchGestures(signLanguage: String): Result<List<GestureDto>> = safeApiCall {
        val response: HttpResponse = client.get("$baseUrl/rest/v1/gestures") {
            header("apikey", anonKey)
            parameter("select", "id,name,category,order_index,unit_id,demo_video_url")
            parameter("sign_language", "eq.$signLanguage")
            parameter("order", "unit_id.asc,order_index.asc")
        }
        response.body<List<GestureDto>>()
    }
}
