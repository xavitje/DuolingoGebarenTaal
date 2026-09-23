package io.github.xavitje.signcoach.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Echte FCM-implementatie. Zolang er geen `google-services.json` is toegevoegd (zie
 * composeApp/build.gradle.kts), is het google-services-plugin niet actief en gooit
 * `FirebaseMessaging.getInstance()` een `IllegalStateException` ("Default FirebaseApp is not
 * initialized") — die vangen we hier af en geven gewoon `null` terug, zodat de rest van de app
 * (en de registratie-flow) hier niets van merkt totdat Firebase daadwerkelijk is opgezet.
 */
actual object FcmTokenProvider {
    actual suspend fun getToken(): String? = runCatching {
        suspendCancellableCoroutine<String?> { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> if (cont.isActive) cont.resume(token) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }.getOrNull()
}
