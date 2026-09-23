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
    }
}
