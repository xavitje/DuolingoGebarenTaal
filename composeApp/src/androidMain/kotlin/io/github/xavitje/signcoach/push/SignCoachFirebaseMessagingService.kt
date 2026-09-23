package io.github.xavitje.signcoach.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.xavitje.signcoach.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ontvangt FCM-events. Wordt pas daadwerkelijk actief zodra Firebase geconfigureerd is
 * (google-services.json aanwezig, zie composeApp/build.gradle.kts) — de manifest-registratie zelf
 * is altijd onschadelijk om te hebben.
 */
class SignCoachFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registrar = PushNotificationRegistrar()

    companion object {
        private const val CHANNEL_ID = "signcoach_reminders"
    }

    /** FCM kan het token op elk moment vernieuwen (nieuwe install, cleared data, token-rotatie) —
     * dan moet Supabase dat zo snel mogelijk weer weten. */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch { registrar.registerToken(token) }
    }

    /** Toont een lokale notificatie voor binnenkomende pushberichten (bv. de dagelijkse
     * streak-herinnering, zie de geplande Edge Function). Werkt zowel voor "notification"- als
     * "data only"-berichten. */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"]
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String?) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Oefenherinneringen",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                manager.createNotificationChannel(channel)
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
