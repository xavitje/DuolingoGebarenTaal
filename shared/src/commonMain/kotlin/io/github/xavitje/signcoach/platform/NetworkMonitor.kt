package io.github.xavitje.signcoach.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Zero Trust: neem nooit aan dat er verbinding is. Dit meldt de werkelijke connectiviteitsstatus
 * van het besturingssysteem, zodat SyncManager alleen probeert te synchroniseren wanneer er
 * echt een netwerkpad is. Dit garandeert NIET dat Supabase zelf bereikbaar is — dat blijkt pas
 * uit een mislukte call, die door safeApiCall() alsnog netjes wordt opgevangen.
 */
expect class NetworkMonitor() {
    val isOnline: StateFlow<Boolean>
    fun start()
    fun stop()
}
