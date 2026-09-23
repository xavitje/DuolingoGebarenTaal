package io.github.xavitje.signcoach.platform

/**
 * Opent een URL in de systeembrowser (Custom Tabs op Android). Gebruikt voor de Google-OAuth-flow:
 * de autorisatie-URL moet in een echte browser geopend worden (niet een embedded WebView), zodat
 * bestaande Google-sessies/cookies meegenomen worden en de gebruiker het adres kan verifiëren.
 */
expect object BrowserLauncher {
    fun open(url: String)
}
