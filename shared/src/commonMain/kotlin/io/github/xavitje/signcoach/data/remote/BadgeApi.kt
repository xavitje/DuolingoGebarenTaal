package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * API voor badges (Fase D, zie PRODUCT_PLAN.md §4/§6a). `badges` is de statische, publiek
 * leesbare catalogus; `user_badges` zijn de per-gebruiker behaalde badges (alleen de eigen
 * rijen zichtbaar, zie RLS-policy). Toekenning gebeurt volledig server-side via de
 * SECURITY DEFINER-functie `check_and_award_badges()` (aangeroepen vanuit `record_practice`
 * na elke oefening); [checkAndAwardBadges] hieronder is er om diezelfde functie ook expliciet
 * aan te roepen wanneer een badge pas later "waar" kan worden — bijvoorbeeld het moment
 * waarop een reviewer een bijdrage goedkeurt gebeurt in een ándere sessie dan die van de
 * bijdrager, dus die badge wordt pas zichtbaar zodra de bijdrager zelf weer de app opent.
 * Idempotent (ON CONFLICT DO NOTHING in de database), dus veilig om vaker te roepen dan strikt
 * nodig.
 */
class BadgeApi(
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
    data class Badge(
        val id: String,
        val name: String,
        val description: String,
        @SerialName("icon_key") val iconKey: String,
        @SerialName("sort_order") val sortOrder: Int = 0,
    )

    @Serializable
    data class UserBadge(
        @SerialName("badge_id") val badgeId: String,
        @SerialName("earned_at") val earnedAt: String,
    )

    /** Alle mogelijke badges, gesorteerd op weergavevolgorde. Publiek leesbaar. */
    suspend fun fetchAllBadges(): Result<List<Badge>> = safeApiCall {
        val response: HttpResponse = client.get("$baseUrl/rest/v1/badges") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $anonKey")
            url {
                parameters.append("select", "*")
                parameters.append("order", "sort_order.asc")
            }
        }
        response.body<List<Badge>>()
    }

    /** De badges die de ingelogde gebruiker al behaald heeft. */
    suspend fun fetchEarnedBadges(accessToken: String): Result<List<UserBadge>> = safeApiCall {
        val response: HttpResponse = client.get("$baseUrl/rest/v1/user_badges") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            url { parameters.append("select", "badge_id,earned_at") }
        }
        response.body<List<UserBadge>>()
    }

    /** Herbereken en ken eventuele nieuwe badges toe voor de ingelogde gebruiker. */
    suspend fun checkAndAwardBadges(accessToken: String): Result<Unit> = safeApiCall {
        val response: HttpResponse = client.post("$baseUrl/rest/v1/rpc/check_and_award_badges") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
    }
}
