package com.example.signcoach.platform

import com.example.signcoach.data.model.Handedness
import com.example.signcoach.data.model.TrackerStatus

/**
 * Kleine fabrieksfuncties zodat de Swift-kant (iosApp/) Kotlin-enums nooit met de exacte,
 * gegenereerde Swift-naam van een enum-constante hoeft aan te roepen (die naamgeving kan per
 * Kotlin/Native-versie verschillen en is dus foutgevoelig om blind te schrijven).
 *
 * Kotlin top-level functies in dit bestand zijn vanuit Swift bereikbaar via de gegenereerde
 * facade-klasse `IosBridgeHelpersKt`, bv.:
 *   IosBridgeHelpersKt.handedness(fromLabel: "Left")
 *   IosBridgeHelpersKt.trackerStatusRunning()
 */
fun handedness(fromLabel: String?): Handedness = when (fromLabel?.lowercase()) {
    "left" -> Handedness.LEFT
    "right" -> Handedness.RIGHT
    else -> Handedness.UNKNOWN
}

fun trackerStatusStopped(): TrackerStatus = TrackerStatus.STOPPED
fun trackerStatusStarting(): TrackerStatus = TrackerStatus.STARTING
fun trackerStatusRunning(): TrackerStatus = TrackerStatus.RUNNING
fun trackerStatusNoPermission(): TrackerStatus = TrackerStatus.NO_PERMISSION
fun trackerStatusUnsupported(): TrackerStatus = TrackerStatus.UNSUPPORTED
fun trackerStatusError(): TrackerStatus = TrackerStatus.ERROR
