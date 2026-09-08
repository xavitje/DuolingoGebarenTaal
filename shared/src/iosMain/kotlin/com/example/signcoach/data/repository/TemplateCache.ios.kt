package com.example.signcoach.data.repository

import com.example.signcoach.data.model.SignTemplate

/**
 * TODO(iOS): in-memory stopgap, niet persistent tussen app-starts. De iOS-camera/tracking-pijplijn
 * is nog UNSUPPORTED (zie IosCameraPlatform), dus dit pad wordt momenteel niet echt gebruikt.
 * Zodra de iOS-build actief ontwikkeld wordt: vervangen door een bestand in NSFileManager's
 * Documents-directory, net als de Android-variant (TemplateCache.android.kt).
 */
actual object TemplateCache {
    private var cached: List<SignTemplate>? = null

    actual fun load(): List<SignTemplate>? = cached

    actual fun save(templates: List<SignTemplate>) {
        cached = templates
    }
}
