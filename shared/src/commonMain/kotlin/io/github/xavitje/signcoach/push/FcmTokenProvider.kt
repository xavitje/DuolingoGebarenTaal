package io.github.xavitje.signcoach.push

/**
 * Haalt het huidige FCM-devicetoken op, of `null` als dat (nog) niet kan — bv. omdat Firebase nog
 * niet geconfigureerd is (geen `google-services.json`), er geen netwerk is, of de gebruiker geen
 * notificatietoestemming heeft gegeven. Dit MOET altijd fail-safe zijn: nooit een exception laten
 * ontsnappen, gewoon `null` teruggeven zodat [PushNotificationRegistrar] stilletjes niets doet.
 */
expect object FcmTokenProvider {
    suspend fun getToken(): String?
}
