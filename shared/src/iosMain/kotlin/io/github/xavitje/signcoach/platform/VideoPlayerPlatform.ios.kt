package io.github.xavitje.signcoach.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * iOS heeft nog geen echte video-player-koppeling (AVPlayer/AVKit) — net als de MediaPipe-
 * cinterop in [IosHandTrackerBridge] is dit te foutgevoelig om blind te schrijven zonder Xcode
 * om het te bouwen/testen (zie PLAN.md, Fase 1). Toont daarom bewust een duidelijke placeholder
 * in plaats van een ongeteste AVPlayer-implementatie die op de eerste build al kan crashen.
 * Vervolgstap zodra er een Mac beschikbaar is: `UIKitView` rond een `AVPlayerViewController`,
 * zelfde patroon als de rest van de iOS-laag hier.
 */
@Composable
actual fun PlatformVideoPlayer(url: String, modifier: Modifier, loop: Boolean, onError: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Video-afspelen op iOS volgt nog",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
