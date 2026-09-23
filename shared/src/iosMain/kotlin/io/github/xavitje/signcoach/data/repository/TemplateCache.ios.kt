package io.github.xavitje.signcoach.data.repository

import io.github.xavitje.signcoach.data.model.SignLanguage
import io.github.xavitje.signcoach.data.model.SignTemplate

/**
 * TODO(iOS): in-memory stopgap, niet persistent tussen app-starts. De iOS-camera/tracking-pijplijn
 * is nog UNSUPPORTED (zie IosCameraPlatform), dus dit pad wordt momenteel niet echt gebruikt.
 * Zodra de iOS-build actief ontwikkeld wordt: vervangen door een bestand in NSFileManager's
 * Documents-directory, net als de Android-variant (TemplateCache.android.kt).
 */
actual object TemplateCache {
    private val cached = mutableMapOf<SignLanguage, List<SignTemplate>>()

    actual fun load(language: SignLanguage): List<SignTemplate>? = cached[language]

    actual fun save(language: SignLanguage, templates: List<SignTemplate>) {
        cached[language] = templates
    }
}
