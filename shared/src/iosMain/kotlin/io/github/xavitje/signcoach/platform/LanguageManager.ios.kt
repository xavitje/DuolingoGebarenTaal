package io.github.xavitje.signcoach.platform

import platform.Foundation.NSUserDefaults

actual fun changeAppLanguage(language: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(language), forKey = "AppleLanguages")
    NSUserDefaults.standardUserDefaults.synchronize()
}
