package io.github.xavitje.signcoach.platform

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

actual fun changeAppLanguage(language: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = AndroidCameraPlatform.applicationContext ?: return
        val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
        localeManager?.applicationLocales = LocaleList.forLanguageTags(language)
    } else {
        // Android 8-12 heeft geen LocaleManager: MainActivity.attachBaseContext past de taal uit
        // SessionStore toe. Alleen opnieuw opbouwen als de taal echt verandert (anders een lus,
        // want App() roept dit bij elke start aan).
        val activity = AndroidCameraPlatform.currentActivity ?: return
        val current = activity.resources.configuration.locales[0].language
        if (current != java.util.Locale.forLanguageTag(language).language) activity.recreate()
    }
}
