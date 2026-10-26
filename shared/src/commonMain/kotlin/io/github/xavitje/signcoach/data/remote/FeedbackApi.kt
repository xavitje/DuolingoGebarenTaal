package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Feedbackformulier voor docenten (pilot) en het overzicht daarvan voor de admin. */
class FeedbackApi(
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

    /** Antwoorden van één docent. `usage`: "niet" | "soms" | "wekelijks" | "vaker". */
    @Serializable
    data class Answers(
        @SerialName("p_usage") val usage: String,
        @SerialName("p_pupils_enjoy") val pupilsEnjoy: Int,
        @SerialName("p_recognition") val recognition: Int,
        @SerialName("p_ease_of_use") val easeOfUse: Int,
        @SerialName("p_recommend") val recommend: Int,
        @SerialName("p_improve") val improve: String,
        @SerialName("p_best") val best: String,
        @SerialName("p_may_quote") val mayQuote: Boolean,
    )

    @Serializable
    data class Entry(
        @SerialName("created_at") val createdAt: String,
        @SerialName("teacher_email") val teacherEmail: String? = null,
        val usage: String? = null,
        @SerialName("pupils_enjoy") val pupilsEnjoy: Int? = null,
        val recognition: Int? = null,
        @SerialName("ease_of_use") val easeOfUse: Int? = null,
        val recommend: Int? = null,
        val improve: String? = null,
        val best: String? = null,
        @SerialName("may_quote") val mayQuote: Boolean = false,
    )

    @Serializable private data class SchoolArg(@SerialName("p_school_id") val schoolId: String)

    suspend fun isDue(accessToken: String): Result<Boolean> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/feedback_due") {
            auth(accessToken)
            setBody("{}")
        }.bodyAsText().trim() == "true"
    }

    suspend fun submit(accessToken: String, answers: Answers): Result<Unit> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/submit_teacher_feedback") {
            auth(accessToken)
            setBody(answers)
        }
        Unit
    }

    suspend fun forSchool(accessToken: String, schoolId: String): Result<List<Entry>> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/admin_list_feedback") {
            auth(accessToken)
            setBody(SchoolArg(schoolId))
        }.body<List<Entry>>()
    }
}
