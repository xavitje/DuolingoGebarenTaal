package io.github.xavitje.signcoach.log

import android.util.Log

actual object AppLogger {
    actual var enabled: Boolean = true

    actual fun d(tag: String, message: String) {
        if (enabled) Log.d(tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (enabled) Log.e(tag, message, throwable)
    }
}
