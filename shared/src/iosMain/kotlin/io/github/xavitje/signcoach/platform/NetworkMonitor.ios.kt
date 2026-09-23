package io.github.xavitje.signcoach.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * iOS: nog geen NWPathMonitor-integratie (vereist een Mac/Xcode om te bouwen en testen — zelfde
 * precedent als PlatformVideoPlayer.ios.kt in dit project). Rapporteert daarom optimistisch
 * altijd "online"; dit is veilig, want een mislukte netwerkcall wordt door safeApiCall() nog
 * steeds netjes opgevangen (AppError.NetworkUnavailable) en de wachtrij blijft dan gewoon intact
 * voor een volgende poging. TODO: vervang door een echte NWPathMonitor-implementatie.
 */
actual class NetworkMonitor actual constructor() {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline
    actual fun start() {}
    actual fun stop() {}
}
