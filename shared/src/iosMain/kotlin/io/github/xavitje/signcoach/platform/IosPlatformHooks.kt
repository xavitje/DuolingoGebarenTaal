package io.github.xavitje.signcoach.platform

import platform.UIKit.UIViewController

/**
 * Registratiepunt voor de native iOS-implementatie (Swift, in het Xcode-project onder iosApp/).
 *
 * Kotlin/Native kan geen Swift-code in de app-target aanroepen (de afhankelijkheid loopt de
 * andere kant op: iosApp linkt tegen het door dit project gegenereerde framework, niet andersom).
 * In plaats van rechtstreeks cinterop tegen MediaPipeTasksVision te schrijven - zeer foutgevoelig
 * om blind te doen, zonder Xcode om het te compileren - registreert de Swift-kant hier bij het
 * opstarten van de app een fabrieksfunctie. Zie iosApp/README_BRIDGE.md voor de exacte call en
 * IOS_SETUP.md (repo-root) voor het volledige stappenplan.
 *
 * Is er niets geregistreerd (de MediaPipe-koppeling in iosApp/ is nog niet gebouwd), dan valt de
 * app terug op TrackerStatus.UNSUPPORTED - hetzelfde gedrag als de oorspronkelijke MVP-stub.
 */
object IosPlatformHooks {
    var nativeTrackerFactory: (() -> IosNativeHandTracker)? = null
    var permissionRequester: ((onResult: (Boolean) -> Unit) -> Unit)? = null
    var previewControllerFactory: (() -> UIViewController)? = null
}

/**
 * Callback-gebaseerde interface (bewust geen Kotlin Flow/coroutines - die zijn zonder extra
 * tooling zoals SKIE/KMP-NativeCoroutines niet praktisch vanuit Swift te implementeren) die de
 * Swift-kant implementeert bovenop MediaPipeTasksVision. [start] kan na [stop] opnieuw worden
 * aangeroepen; implementaties moeten daarmee om kunnen gaan (nieuwe sessie starten).
 */
interface IosNativeHandTracker {
    fun start(
        onPose: (io.github.xavitje.signcoach.data.model.HandPose) -> Unit,
        onStatus: (io.github.xavitje.signcoach.data.model.TrackerStatus) -> Unit,
    )
    fun stop()
}
