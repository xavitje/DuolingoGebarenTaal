package io.github.xavitje.signcoach.push

/** Nog niet geïmplementeerd op iOS (zie taakomschrijving: push-notificaties zijn voorlopig
 * Android-first). Bewust een no-op stub, net als [io.github.xavitje.signcoach.crash.CrashReporter] op
 * iOS. */
actual object FcmTokenProvider {
    actual suspend fun getToken(): String? = null
}
