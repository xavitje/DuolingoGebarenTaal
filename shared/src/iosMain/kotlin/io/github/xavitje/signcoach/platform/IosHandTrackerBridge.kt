package io.github.xavitje.signcoach.platform

import io.github.xavitje.signcoach.data.model.HandPose
import io.github.xavitje.signcoach.data.model.TrackerStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wikkelt een [IosNativeHandTracker] (geïmplementeerd in Swift, zie IosPlatformHooks.kt) in de
 * gedeelde [HandTrackerBridge]-interface. Is er geen native tracker geregistreerd - bv. omdat de
 * MediaPipe-koppeling in iosApp/ nog niet is gebouwd - dan valt dit terug op UNSUPPORTED, precies
 * het gedrag dat de oorspronkelijke MVP-stub ook had, zodat de UI correct blijft reageren.
 */
class IosHandTrackerBridge(
    private val native: IosNativeHandTracker?,
) : HandTrackerBridge {
    private val mutableStatus = MutableStateFlow(
        if (native != null) TrackerStatus.STOPPED else TrackerStatus.UNSUPPORTED,
    )
    override val status: StateFlow<TrackerStatus> = mutableStatus

    override fun start(): Flow<HandPose> = callbackFlow {
        val tracker = native
        if (tracker == null) {
            mutableStatus.value = TrackerStatus.UNSUPPORTED
            awaitClose { }
            return@callbackFlow
        }
        tracker.start(
            onPose = { pose -> trySend(pose) },
            onStatus = { status -> mutableStatus.value = status },
        )
        awaitClose { tracker.stop() }
    }

    override suspend fun stop() {
        native?.stop()
        mutableStatus.value = if (native != null) TrackerStatus.STOPPED else TrackerStatus.UNSUPPORTED
    }
}
