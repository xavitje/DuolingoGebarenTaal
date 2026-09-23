package io.github.xavitje.signcoach.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Android-implementatie: opent de URL in een Chrome Custom Tab. Vereist een eenmalige
 * [initialize] met de application-context, net als [io.github.xavitje.signcoach.platform.AndroidCameraPlatform].
 *
 * FLAG_ACTIVITY_NEW_TASK is verplicht hier: [appContext] is de application-context (geen
 * Activity), en Android staat startActivity() vanuit een niet-Activity-context alleen toe met
 * deze flag (anders AndroidRuntimeException: "Calling startActivity() from outside of an
 * Activity context requires the FLAG_ACTIVITY_NEW_TASK flag").
 */
actual object BrowserLauncher {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun open(url: String) {
        val ctx = appContext ?: return
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(ctx, Uri.parse(url))
    }
}
