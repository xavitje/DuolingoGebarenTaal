package io.github.xavitje.signcoach

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.xavitje.signcoach.data.local.OAuthCallbackBus
import io.github.xavitje.signcoach.crash.CrashReporter
import io.github.xavitje.signcoach.data.repository.TemplateCache
import io.github.xavitje.signcoach.db.DatabaseDriverFactory
import io.github.xavitje.signcoach.platform.AndroidCameraPlatform
import io.github.xavitje.signcoach.platform.BrowserLauncher
import io.github.xavitje.signcoach.platform.NetworkMonitor
import io.github.xavitje.signcoach.push.PushNotificationRegistrar
import io.github.xavitje.signcoach.sync.AppSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pushRegistrar = PushNotificationRegistrar()

    // Moet als veld geregistreerd worden (vóór STARTED) — registreren binnen onCreate() ná
    // andere logica kan te laat zijn. Of Firebase daadwerkelijk geconfigureerd is maakt hier
    // niets uit: de toestemming zelf is een los Android-concept, PushNotificationRegistrar
    // handelt zelf fail-safe af of er ook echt een FCM-token beschikbaar is.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Wat de uitkomst ook is: probeer te registreren (geen token zonder toestemming, maar
        // dat geeft PushNotificationRegistrar zelf fail-safe aan).
        activityScope.launch { pushRegistrar.registerIfPossible() }
    }

    /**
     * App-taal (standaard Nederlands, zie SessionStore.getLanguage) toepassen vóórdat er iets
     * getekend wordt. Op Android 13+ regelt LocaleManager dit (changeAppLanguage); op Android 8-12
     * zou de app anders de taal van het toestel volgen — en schooltablets draaien vaak op die
     * oudere versies.
     */
    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.attachBaseContext(newBase)
            return
        }
        val tag = runCatching { io.github.xavitje.signcoach.data.local.SessionStore.getLanguage() }.getOrDefault("nl")
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidCameraPlatform.initialize(this)
        TemplateCache.initialize(this)
        BrowserLauncher.initialize(this)
        // Zero Trust / offline-first: lokale database + netwerkmonitor moeten klaarstaan vóórdat
        // enige ViewModel XP/streak probeert te lezen of op te slaan.
        DatabaseDriverFactory.initialize(this)
        // Crashrapportage moet zo vroeg mogelijk actief zijn, meteen na de lokale database
        // (de wachtrij van crashrapporten leeft daarin), zodat een crash tijdens app-start
        // ook wordt opgevangen.
        CrashReporter.initialize(this)
        CrashReporter.install()
        NetworkMonitor.initialize(this)
        AppSync.manager.start()
        requestNotificationPermissionAndRegister()
        handleIntent(intent)
        setContent { App() }
    }

    /** Vraagt op Android 13+ toestemming voor notificaties; op oudere versies is die er al
     * impliciet, dus dan meteen proberen te registreren. */
    private fun requestNotificationPermissionAndRegister() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                activityScope.launch { pushRegistrar.registerIfPossible() }
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            activityScope.launch { pushRegistrar.registerIfPossible() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Vangt de Google-OAuth deep-link (`signcoach://auth-callback?code=...`) op. De Activity
     * draait in launchMode "singleTask" (zie AndroidManifest.xml) zodat deze via onNewIntent
     * binnenkomt op de bestaande instantie, in plaats van een nieuwe Activity (en dus een verse
     * Compose-boom + verloren AuthViewModel-state) te starten.
     */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "signcoach" && data.host == "auth-callback") {
            val code = data.getQueryParameter("code")
            if (code != null) {
                OAuthCallbackBus.emit(code)
            }
        }
    }
}
