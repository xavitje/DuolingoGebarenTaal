package io.github.xavitje.signcoach.crash

import android.content.Context
import android.os.Build
import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.repository.CrashReportRepository
import java.lang.ref.WeakReference

/**
 * Registreert een globale `Thread.setDefaultUncaughtExceptionHandler` die elke onafgevangen
 * crash EERST synchroon lokaal wegschrijft (werkt altijd, ook zonder netwerk of vlak vóór het
 * proces wordt beëindigd) en daarna de vorige handler aanroept, zodat het normale Android-
 * crashgedrag (logcat, crashdialoog, proces afsluiten) ongewijzigd blijft. De handler zelf mag
 * nooit zelf een exception gooien — vandaar de `runCatching` om de hele body.
 */
actual object CrashReporter {
    private var appContext: WeakReference<Context>? = null

    /** Moet vóór [install] aangeroepen worden, bv. in MainActivity.onCreate. */
    fun initialize(context: Context) {
        appContext = WeakReference(context.applicationContext)
    }

    actual fun install() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val ctx = appContext?.get()
                val appVersion = ctx?.let { c ->
                    runCatching {
                        @Suppress("DEPRECATION")
                        c.packageManager.getPackageInfo(c.packageName, 0).versionName
                    }.getOrNull()
                }
                val userId = runCatching { SessionStore.load()?.userId }.getOrNull()
                CrashReportRepository().recordCrashLocally(
                    userId = userId,
                    platform = "android",
                    appVersion = appVersion,
                    osVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    errorType = throwable::class.qualifiedName ?: throwable::class.simpleName,
                    errorMessage = throwable.message,
                    stackTrace = throwable.stackTraceToString(),
                    isFatal = true,
                )
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
