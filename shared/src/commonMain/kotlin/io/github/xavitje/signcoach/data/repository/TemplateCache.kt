package io.github.xavitje.signcoach.data.repository

import io.github.xavitje.signcoach.data.model.SignLanguage
import io.github.xavitje.signcoach.data.model.SignTemplate

/**
 * Platformopslag voor de laatst gesynchroniseerde, samengevoegde templateset (bundled
 * basisset + goedgekeurde cloud-referentievarianten). Hiermee kan de app zonder internet
 * verder werken op de laatst bekende (mogelijk iets oudere) versie in plaats van alleen op
 * de kale basisset terug te vallen. Zie [SyncingTemplateRepository].
 *
 * Eén cache per [SignLanguage] — bij wisselen van gebarentaal in instellingen mag de oude
 * taal zijn offline cache niet verliezen, en de nieuwe taal mag niet per ongeluk de vorige
 * taal se templates tonen voordat er weer verbinding is.
 */
expect object TemplateCache {
    fun load(language: SignLanguage): List<SignTemplate>?
    fun save(language: SignLanguage, templates: List<SignTemplate>)
}
