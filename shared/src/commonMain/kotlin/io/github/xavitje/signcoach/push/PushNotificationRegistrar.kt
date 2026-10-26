package io.github.xavitje.signcoach.push

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.remote.PushTokenApi

/**
 * Verbindt [FcmTokenProvider] (het devicetoken) met [PushTokenApi] (opslaan in Supabase). Wordt
 * aangeroepen bij app-start (als er al een sessie is) en na een geslaagde login, en telkens als
 * FCM een vernieuwd token doorgeeft (zie SignCoachFirebaseMessagingService.onNewToken). Volledig
 * fail-safe: geen sessie, geen token of een netwerkfout resulteert gewoon in "niets gedaan",
 * nooit een crash.
 */
class PushNotificationRegistrar(
    private val pushTokenApi: PushTokenApi = PushTokenApi(),
) {
    suspend fun registerIfPossible() {
        val session = SessionStore.load() ?: return
        if (isSchoolStudent(session.email)) return
        val token = FcmTokenProvider.getToken() ?: return
        runCatching {
            pushTokenApi.registerToken(
                accessToken = session.accessToken,
                userId = session.userId,
                token = token,
                platform = "android",
            )
        }
    }

    /** Voor SignCoachFirebaseMessagingService.onNewToken, waar het token al bekend is en niet
     * opnieuw bij FCM opgevraagd hoeft te worden. */
    suspend fun registerToken(token: String) {
        val session = SessionStore.load() ?: return
        if (isSchoolStudent(session.email)) return
        runCatching {
            pushTokenApi.registerToken(
                accessToken = session.accessToken,
                userId = session.userId,
                token = token,
                platform = "android",
            )
        }
    }

    /** Schoolleerlingen krijgen geen pushberichten: de school bepaalt wanneer er geoefend wordt,
     * en zo slaan we voor kinderen geen apparaat-token op (dataminimalisatie, fase 4). */
    private fun isSchoolStudent(email: String?): Boolean =
        io.github.xavitje.signcoach.data.remote.SchoolApi.isSchoolStudentEmail(email)
}
