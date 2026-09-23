package io.github.xavitje.signcoach.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Android-implementatie via Media3 ExoPlayer. Autoplay + loop, geen playback-controls (dit is
 * een korte instructie-clip die gewoon meteen moet spelen, geen scrubber nodig) — een tik erop
 * pauzeert/hervat wel (standaard ExoPlayer/PlayerView-gedrag), zodat je 'm even stil kan zetten.
 */
@Composable
actual fun PlatformVideoPlayer(url: String, modifier: Modifier, loop: Boolean, onError: () -> Unit) {
    val context = LocalContext.current
    val currentOnError by rememberUpdatedState(onError)
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        // Zero Trust: een kapotte/onbereikbare video-URL of een netwerkstoring halverwege het
        // buffer en mag nooit stilzwijgend een zwart vlak achterlaten — meld het door naar de
        // aanroeper zodat die de "video niet beschikbaar"-fallback kan tonen.
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                currentOnError()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
            }
        },
    )
}
