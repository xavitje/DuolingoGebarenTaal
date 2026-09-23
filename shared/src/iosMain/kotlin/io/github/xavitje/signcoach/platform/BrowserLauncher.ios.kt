package io.github.xavitje.signcoach.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS-implementatie: opent de URL via UIApplication (Safari). Dit is een minimale, ongeteste
 * fallback (in deze sessie is geen Mac/iOS-build beschikbaar om te verifiëren) — voor een
 * naadloze in-app-ervaring is ASWebAuthenticationSession de betere vervolgstap, met dezelfde
 * `signcoach://auth-callback` als callback-URL.
 */
actual object BrowserLauncher {
    actual fun open(url: String) {
        val nsUrl = NSURL(string = url)
        UIApplication.sharedApplication.openURL(nsUrl)
    }
}
