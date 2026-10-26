package io.github.xavitje.signcoach.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Alles rond scholen (fase 3): leerling-login, klassen en leerlingen voor docenten, en het
 * beheer van scholen/licenties voor admins. De rechten worden in Supabase afgedwongen (RLS en
 * security-definer-functies, zie migratie 20261025210000_fase3_scholen.sql); deze klasse doet
 * alleen de aanroepen.
 */
class SchoolApi(
    private val baseUrl: String = SupabaseConfig.URL,
    private val anonKey: String = SupabaseConfig.ANON_KEY,
) {
    private val client = HttpClient {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }
    }

    private fun HttpRequestBuilder.auth(accessToken: String?) {
        header("apikey", anonKey)
        header("Authorization", "Bearer ${accessToken ?: anonKey}")
    }

    // ─── Modellen ────────────────────────────────────────────────────────────

    @Serializable
    data class RosterEntry(
        @SerialName("user_id") val userId: String,
        @SerialName("first_name") val firstName: String,
    )

    @Serializable
    data class MySchool(
        @SerialName("school_id") val schoolId: String,
        @SerialName("school_name") val schoolName: String,
        @SerialName("class_name") val className: String? = null,
        @SerialName("staff_role") val staffRole: String? = null,
        @SerialName("license_end") val licenseEnd: String,
        @SerialName("license_active") val licenseActive: Boolean,
        @SerialName("is_student") val isStudent: Boolean,
        /** Mag deze leerling met de camera oefenen? De docent kan dit per leerling uitzetten. */
        @SerialName("camera_allowed") val cameraAllowed: Boolean = true,
    )

    @Serializable
    data class SchoolClass(
        val id: String,
        @SerialName("school_id") val schoolId: String,
        val name: String,
        val code: String,
    )

    @Serializable
    data class StudentProgress(
        @SerialName("user_id") val userId: String,
        @SerialName("first_name") val firstName: String,
        @SerialName("completed_lessons") val completedLessons: Int = 0,
        @SerialName("total_xp") val totalXp: Int = 0,
        @SerialName("current_streak") val currentStreak: Int = 0,
        @SerialName("last_practice_date") val lastPracticeDate: String? = null,
        @SerialName("camera_allowed") val cameraAllowed: Boolean = true,
    )

    @Serializable
    data class CreatedStudent(
        @SerialName("user_id") val userId: String,
        @SerialName("first_name") val firstName: String,
        val pin: String,
    )

    @Serializable
    data class AdminSchool(
        val id: String,
        val name: String,
        val brin: String? = null,
        @SerialName("contact_name") val contactName: String? = null,
        @SerialName("contact_email") val contactEmail: String? = null,
        @SerialName("max_students") val maxStudents: Int,
        @SerialName("license_start") val licenseStart: String,
        @SerialName("license_end") val licenseEnd: String,
        @SerialName("seats_used") val seatsUsed: Int = 0,
        @SerialName("staff_count") val staffCount: Int = 0,
        @SerialName("license_active") val licenseActive: Boolean = false,
    )

    @Serializable
    data class StaffMember(
        @SerialName("user_id") val userId: String,
        val email: String,
        @SerialName("staff_role") val staffRole: String,
    )

    @Serializable private data class CodeArg(@SerialName("p_code") val code: String)
    @Serializable private data class ClassArg(@SerialName("p_class_id") val classId: String)
    @Serializable private data class SchoolArg(@SerialName("p_school_id") val schoolId: String)
    @Serializable private data class StudentLoginBody(
        @SerialName("class_code") val classCode: String,
        @SerialName("user_id") val userId: String,
        val pin: String,
    )
    @Serializable private data class NewClass(@SerialName("school_id") val schoolId: String, val name: String)
    @Serializable private data class StudentsAction(
        val action: String,
        @SerialName("class_id") val classId: String,
        val names: List<String>? = null,
        @SerialName("user_id") val userId: String? = null,
    )
    @Serializable private data class CreatedStudents(val students: List<CreatedStudent> = emptyList())
    @Serializable private data class PinResult(val pin: String)
    @Serializable private data class NewSchool(
        val name: String,
        val brin: String? = null,
        @SerialName("contact_name") val contactName: String? = null,
        @SerialName("contact_email") val contactEmail: String? = null,
        @SerialName("max_students") val maxStudents: Int,
        @SerialName("license_end") val licenseEnd: String,
    )
    @Serializable private data class SchoolUpdate(
        @SerialName("max_students") val maxStudents: Int,
        @SerialName("license_end") val licenseEnd: String,
    )
    @Serializable private data class AddStaffArgs(
        @SerialName("p_school_id") val schoolId: String,
        @SerialName("p_email") val email: String,
        @SerialName("p_staff_role") val staffRole: String,
    )
    @Serializable private data class CameraArgs(
        @SerialName("p_class_id") val classId: String,
        @SerialName("p_user_id") val userId: String,
        @SerialName("p_allowed") val allowed: Boolean,
    )

    @Serializable private data class SchoolIdArgs(@SerialName("p_school_id") val schoolId: String)

    @Serializable private data class RemoveStaffArgs(
        @SerialName("p_school_id") val schoolId: String,
        @SerialName("p_user_id") val userId: String,
    )

    // ─── Leerling-login (zonder sessie) ────────────────────────────────────────

    /** Voornamen in de klas met deze klascode. Leeg = onbekende code of verlopen licentie. */
    suspend fun classRoster(classCode: String): Result<List<RosterEntry>> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/class_roster") {
            auth(null)
            contentType(ContentType.Application.Json)
            setBody(CodeArg(classCode.trim().uppercase()))
        }.body<List<RosterEntry>>()
    }

    /** Pin controleren en een sessie ophalen. Foute pin = [io.github.xavitje.signcoach.domain.AppError.Unauthorized],
     * geblokkeerd = ServerError(423). */
    suspend fun studentLogin(classCode: String, userId: String, pin: String): Result<SupabaseAuthApi.AuthSession> = safeApiCall {
        client.post("$baseUrl/functions/v1/student-login") {
            auth(null)
            contentType(ContentType.Application.Json)
            setBody(StudentLoginBody(classCode.trim().uppercase(), userId, pin))
        }.body<SupabaseAuthApi.AuthSession>()
    }

    // ─── Ingelogd ──────────────────────────────────────────────────────────────

    suspend fun mySchool(accessToken: String): Result<List<MySchool>> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/my_school") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }.body<List<MySchool>>()
    }

    suspend fun myClasses(accessToken: String): Result<List<SchoolClass>> = safeApiCall {
        client.get("$baseUrl/rest/v1/classes") {
            auth(accessToken)
            parameter("select", "id,school_id,name,code")
            parameter("order", "name.asc")
        }.body<List<SchoolClass>>()
    }

    suspend fun createClass(accessToken: String, schoolId: String, name: String): Result<SchoolClass> = safeApiCall {
        client.post("$baseUrl/rest/v1/classes") {
            auth(accessToken)
            header("Prefer", "return=representation")
            parameter("select", "id,school_id,name,code")
            contentType(ContentType.Application.Json)
            setBody(NewClass(schoolId, name.trim()))
        }.body<List<SchoolClass>>().first()
    }

    suspend fun classOverview(accessToken: String, classId: String): Result<List<StudentProgress>> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/class_overview") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(ClassArg(classId))
        }.body<List<StudentProgress>>()
    }

    /** Leerlingen aanmaken. Fouten: ServerError(409) met "no_seats" of "license_expired" in details. */
    suspend fun createStudents(accessToken: String, classId: String, names: List<String>): Result<List<CreatedStudent>> = safeApiCall {
        client.post("$baseUrl/functions/v1/school-students") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(StudentsAction(action = "create", classId = classId, names = names))
        }.body<CreatedStudents>().students
    }

    suspend fun resetPin(accessToken: String, classId: String, userId: String): Result<String> = safeApiCall {
        client.post("$baseUrl/functions/v1/school-students") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(StudentsAction(action = "reset_pin", classId = classId, userId = userId))
        }.body<PinResult>().pin
    }

    suspend fun removeStudent(accessToken: String, classId: String, userId: String): Result<Unit> = safeApiCall {
        client.post("$baseUrl/functions/v1/school-students") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(StudentsAction(action = "remove", classId = classId, userId = userId))
        }
        Unit
    }

    suspend fun setStudentCamera(accessToken: String, classId: String, userId: String, allowed: Boolean): Result<Unit> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/set_student_camera") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(CameraArgs(classId, userId, allowed))
        }
        Unit
    }

    // ─── Beheer (admin) ────────────────────────────────────────────────────────

    suspend fun adminListSchools(accessToken: String): Result<List<AdminSchool>> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/admin_list_schools") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }.body<List<AdminSchool>>()
    }

    suspend fun adminCreateSchool(
        accessToken: String,
        name: String,
        brin: String?,
        contactName: String?,
        contactEmail: String?,
        maxStudents: Int,
        licenseEnd: String,
    ): Result<Unit> = safeApiCall {
        client.post("$baseUrl/rest/v1/schools") {
            auth(accessToken)
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(NewSchool(name.trim(), brin?.trim()?.ifBlank { null }, contactName?.trim()?.ifBlank { null },
                contactEmail?.trim()?.ifBlank { null }, maxStudents, licenseEnd))
        }
        Unit
    }

    suspend fun adminUpdateSchool(accessToken: String, schoolId: String, maxStudents: Int, licenseEnd: String): Result<Unit> = safeApiCall {
        client.patch("$baseUrl/rest/v1/schools") {
            auth(accessToken)
            header("Prefer", "return=minimal")
            parameter("id", "eq.$schoolId")
            contentType(ContentType.Application.Json)
            setBody(SchoolUpdate(maxStudents, licenseEnd))
        }
        Unit
    }

    suspend fun adminListStaff(accessToken: String, schoolId: String): Result<List<StaffMember>> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/admin_list_staff") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(SchoolArg(schoolId))
        }.body<List<StaffMember>>()
    }

    /** Geeft "ok" of "not_found" (geen account met dat e-mailadres). */
    suspend fun adminAddStaff(accessToken: String, schoolId: String, email: String, staffRole: String): Result<String> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/admin_add_staff") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(AddStaffArgs(schoolId, email.trim(), staffRole))
        }.bodyAsText().trim().trim('"')
    }

    /** Wist alle leerlingaccounts, voortgang en klassen van een school (RPC admin_purge_school).
     * Geeft het aantal verwijderde leerlingaccounts terug. */
    suspend fun adminPurgeSchool(accessToken: String, schoolId: String): Result<Int> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/admin_purge_school") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(SchoolIdArgs(schoolId))
        }.bodyAsText().trim().toIntOrNull() ?: 0
    }

    suspend fun adminRemoveStaff(accessToken: String, schoolId: String, userId: String): Result<Unit> = safeApiCall {
        client.post("$baseUrl/rest/v1/rpc/admin_remove_staff") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(RemoveStaffArgs(schoolId, userId))
        }
        Unit
    }

    companion object {
        /** Leerlingaccounts hebben een e-mailadres op dit domein (geen echt adres). */
        const val STUDENT_EMAIL_DOMAIN = "students.signcoach.invalid"

        fun isSchoolStudentEmail(email: String?): Boolean =
            email?.endsWith("@$STUDENT_EMAIL_DOMAIN", ignoreCase = true) == true
    }
}
