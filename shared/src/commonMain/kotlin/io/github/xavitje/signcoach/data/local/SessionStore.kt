package io.github.xavitje.signcoach.data.local

/**
 * Platformonafhankelijke interface voor lokale sessie-opslag (access + refresh token + user ID).
 * Implementatie via `multiplatform-settings` (expect/actual per platform).
 * Dit is de enige lokale opslag voor auth-state — bewuste keuze voor eenvoud.
 */
expect object SessionStore {
    /** Slaat een volledige sessie op. Overschrijft eventuele vorige waarden. */
    fun save(accessToken: String, refreshToken: String, userId: String, email: String?)

    /** Laadt de opgeslagen sessie, of null als er geen sessie is. */
    fun load(): StoredSession?

    /** Wist de opgeslagen sessie (bij uitloggen of verwijdering account). */
    fun clear()

    fun getLanguage(): String
    fun setLanguage(language: String)

    /** Actief gekozen gebarentaal (bv. "ngt", "asl"), default "ngt". Los van [getLanguage]/
     * [setLanguage] hierboven, wat de UI-taal (nl/en) is, niet de gebarentaal die geleerd wordt. */
    fun getSignLanguage(): String
    fun setSignLanguage(signLanguage: String)

    /** Of de gebruiker de eerste-installatie-taalkeuze al heeft doorlopen. Onafhankelijk van
     * inlogstatus, zodat de keuze ook vóór/zonder account werkt (zie ChooseSignLanguageScreen). */
    fun isOnboardingComplete(): Boolean
    fun setOnboardingComplete(complete: Boolean)
}

data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?,
)
