package io.github.xavitje.signcoach.log

import platform.Foundation.NSLog

actual object AppLogger {
    actual var enabled: Boolean = true

    actual fun d(tag: String, message: String) {
        if (enabled) NSLog("[$tag] $message")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (enabled) NSLog("[$tag] ERROR: $message ${throwable?.message.orEmpty()}")
    }
}
