package io.github.xavitje.signcoach.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.xavitje.signcoach.data.model.HandPose
import io.github.xavitje.signcoach.data.model.TrackerStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface HandTrackerBridge {
    val status: StateFlow<TrackerStatus>
    fun start(): Flow<HandPose>
    suspend fun stop()
}

interface CameraController {
    suspend fun requestPermission(): Boolean
    fun createHandTracker(): HandTrackerBridge
}

expect class CameraPreview

@Composable
expect fun CameraPreview(
    modifier: Modifier = Modifier,
    isFrontFacing: Boolean = true,
)

expect class PlatformCameraController() : CameraController {
    override suspend fun requestPermission(): Boolean
    override fun createHandTracker(): HandTrackerBridge
}

interface CameraPreviewHost {
    @Composable
    fun Content(modifier: Modifier = Modifier)
}
