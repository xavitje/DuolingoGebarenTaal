package com.example.signcoach.data.repository

import com.example.signcoach.data.model.SignTemplate

/**
 * Platformopslag voor de laatst gesynchroniseerde, samengevoegde templateset (bundled
 * basisset + goedgekeurde cloud-referentievarianten). Hiermee kan de app zonder internet
 * verder werken op de laatst bekende (mogelijk iets oudere) versie in plaats van alleen op
 * de kale basisset terug te vallen. Zie [SyncingTemplateRepository].
 */
expect object TemplateCache {
    fun load(): List<SignTemplate>?
    fun save(templates: List<SignTemplate>)
}
