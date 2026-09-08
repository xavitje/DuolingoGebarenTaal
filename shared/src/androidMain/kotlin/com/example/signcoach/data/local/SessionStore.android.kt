package com.example.signcoach.data.local

import com.russhwolf.settings.Settings

/**
 * Android-implementatie van [SessionStore] via `multiplatform-settings`.
 * Op Android gebruikt dit SharedPreferences intern.
 */
actual object SessionStore {
    private val settings: Settings = Settings()

    private const val KEY_ACCESS_TOKEN  = "session_access_token"
    private const val KEY_REFRESH_TOKEN = "session_refresh_token"
    private const val KEY_USER_ID       = "session_user_id"
    private const val KEY_EMAIL         = "session_email"

    actual fun save(accessToken: String, refreshToken: String, userId: String, email: String?) {
        settings.putString(KEY_ACCESS_TOKEN, accessToken)
        settings.putString(KEY_REFRESH_TOKEN, refreshToken)
        settings.putString(KEY_USER_ID, userId)
        if (email != null) settings.putString(KEY_EMAIL, email)
        else settings.remove(KEY_EMAIL)
    }

    actual fun load(): StoredSession? {
        val access  = settings.getStringOrNull(KEY_ACCESS_TOKEN)  ?: return null
        val refresh = settings.getStringOrNull(KEY_REFRESH_TOKEN) ?: return null
        val userId  = settings.getStringOrNull(KEY_USER_ID)       ?: return null
        val email   = settings.getStringOrNull(KEY_EMAIL)
        return StoredSession(access, refresh, userId, email)
    }

    actual fun clear() {
        settings.remove(KEY_ACCESS_TOKEN)
        settings.remove(KEY_REFRESH_TOKEN)
        settings.remove(KEY_USER_ID)
        settings.remove(KEY_EMAIL)
    }
}
