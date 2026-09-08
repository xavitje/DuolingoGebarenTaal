package com.example.signcoach.data.local

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
}

data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?,
)
