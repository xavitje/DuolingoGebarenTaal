package io.github.xavitje.signcoach.log

/**
 * Vervangt losse `println()`-debugregels door een echte, platform-logger (Logcat op Android,
 * NSLog op iOS) met tag + niveau, in plaats van ongestructureerde output naar stdout die je in
 * productie niet kunt filteren of uitschakelen. [enabled] staat standaard aan; zet 'm uit vanaf
 * app-start in een release-build (bv. `AppLogger.enabled = !BuildConfig.DEBUG` in MainActivity)
 * om debug-logging volledig te onderdrukken.
 */
expect object AppLogger {
    var enabled: Boolean

    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
