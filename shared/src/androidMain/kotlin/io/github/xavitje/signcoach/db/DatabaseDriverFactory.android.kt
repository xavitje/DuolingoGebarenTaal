package io.github.xavitje.signcoach.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.lang.ref.WeakReference

actual object DatabaseDriverFactory {
    private var appContext: WeakReference<Context>? = null

    /** Moet vóór gebruik één keer aangeroepen worden, bv. in MainActivity.onCreate zoals
     * TemplateCache.initialize() / AndroidCameraPlatform.initialize(). */
    fun initialize(context: Context) {
        appContext = WeakReference(context.applicationContext)
    }

    actual fun createDriver(): SqlDriver {
        val context = appContext?.get()
            ?: error("DatabaseDriverFactory.initialize(context) is niet aangeroepen vóór gebruik")
        return AndroidSqliteDriver(SignCoachDatabase.Schema, context, "signcoach.db")
    }
}
