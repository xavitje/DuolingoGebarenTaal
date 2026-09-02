package com.example.signcoach.platform
import com.example.signcoach.data.model.*
import kotlinx.coroutines.flow.*
class IosHandTrackerBridge : HandTrackerBridge {
 override val status = MutableStateFlow(TrackerStatus.UNSUPPORTED)
 override fun start(): Flow<HandPose> = emptyFlow()
 override suspend fun stop() { status.value = TrackerStatus.STOPPED }
}
