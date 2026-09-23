package io.github.xavitje.signcoach.crash

/**
 * Nog niet geïmplementeerd op iOS (zie de uitleg in de expect-declaratie). Een volwaardige
 * iOS-crashhandler heeft NSSetUncaughtExceptionHandler + signal handlers nodig, wat een aparte
 * vervolgstap is — dit is bewust een no-op zodat de multiplatform build niet breekt.
 */
actual object CrashReporter {
    actual fun install() {
        // TODO: iOS crash capture (NSSetUncaughtExceptionHandler + signal handlers).
    }
}
