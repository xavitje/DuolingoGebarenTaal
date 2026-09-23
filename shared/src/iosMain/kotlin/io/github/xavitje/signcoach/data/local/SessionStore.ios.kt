package io.github.xavitje.signcoach.data.local

import com.russhwolf.settings.Settings

/**
 * iOS-implementatie van [SessionStore] via `multiplatform-settings`.
 * Op iOS gebruikt dit NSUserDefaults intern.
 */
actual object SessionStore {
    private val settings: Settings = Settings()

    private const val KEY_ACCESS_TOKEN  = "session_access_token"
    private const val KEY_REFRESH_TOKEN = "session_refresh_token"
    private const val KEY_USER_ID       = "session_user_id"
    private const val KEY_EMAIL         = "session_email"
    private const val KEY_LANGUAGE      = "app_language"
    private const val KEY_SIGN_LANGUAGE = "sign_language"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

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

    actual fun getLanguage(): String {
        return settings.getString(KEY_LANGUAGE, "nl")
    }

    actual fun setLanguage(language: String) {
        settings.putString(KEY_LANGUAGE, language)
    }

    actual fun getSignLanguage(): String {
        return settings.getString(KEY_SIGN_LANGUAGE, "ngt")
    }

    actual fun setSignLanguage(signLanguage: String) {
        settings.putString(KEY_SIGN_LANGUAGE, signLanguage)
    }

    actual fun isOnboardingComplete(): Boolean {
        return settings.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    actual fun setOnboardingComplete(complete: Boolean) {
        settings.putBoolean(KEY_ONBOARDING_COMPLETE, complete)
    }
}
