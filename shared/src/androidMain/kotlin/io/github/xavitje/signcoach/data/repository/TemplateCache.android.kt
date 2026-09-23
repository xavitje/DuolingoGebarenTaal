package io.github.xavitje.signcoach.data.repository

import android.content.Context
import android.util.Log
import io.github.xavitje.signcoach.data.model.SignLanguage
import io.github.xavitje.signcoach.data.model.SignTemplate
import java.io.File
import java.lang.ref.WeakReference
import kotlinx.serialization.json.Json

actual object TemplateCache {
    private const val TAG = "SignCoach"
    private var appContext: WeakReference<Context>? = null
    private val json = Json { ignoreUnknownKeys = true }

    /** Moet vóór gebruik één keer aangeroepen worden, bv. in MainActivity.onCreate zoals
     * AndroidCameraPlatform.initialize(). */
    fun initialize(context: Context) {
        appContext = WeakReference(context.applicationContext)
    }

    private fun cacheFile(language: SignLanguage): File? =
        appContext?.get()?.let { File(it.filesDir, "synced_templates_${language.code}.json") }

    actual fun load(language: SignLanguage): List<SignTemplate>? {
        val file = cacheFile(language) ?: return null
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<List<SignTemplate>>(file.readText()) }
            .onFailure { Log.w(TAG, "TemplateCache.load failed", it) }
            .getOrNull()
    }

    actual fun save(language: SignLanguage, templates: List<SignTemplate>) {
        val file = cacheFile(language) ?: return
        runCatching { file.writeText(json.encodeToString(templates)) }
            .onFailure { Log.w(TAG, "TemplateCache.save failed", it) }
    }
}
