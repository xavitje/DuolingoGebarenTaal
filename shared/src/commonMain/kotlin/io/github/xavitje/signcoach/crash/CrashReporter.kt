package io.github.xavitje.signcoach.crash

/**
 * Vangt onverwachte (uncaught) crashes op en rapporteert ze lokaal-eerst, zodat ze ook zichtbaar
 * worden als het toestel offline was op het moment van de crash (zie [io.github.xavitje.signcoach.data.repository.CrashReportRepository]).
 * Op Android is dit een echte `Thread.setDefaultUncaughtExceptionHandler`. Op iOS is dit voorlopig
 * een no-op stub (net als [io.github.xavitje.signcoach.platform.NetworkMonitor] en
 * [io.github.xavitje.signcoach.platform.VideoPlayerPlatform] daar nog stubs zijn) — een volwaardige iOS-
 * crashhandler (NSSetUncaughtExceptionHandler + signal handlers) is een aparte vervolgstap.
 */
expect object CrashReporter {
    /** Eén keer aanroepen bij app-start, ná [io.github.xavitje.signcoach.db.DatabaseDriverFactory.initialize]
     * zodat de lokale wachtrij al klaarstaat. */
    fun install()
}
