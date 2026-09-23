package io.github.xavitje.signcoach.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platformafhankelijke video-player voor de "bekijk eerst"-referentievideo per gebaar
 * (zie [io.github.xavitje.signcoach.ui.main.GestureDemoScreen]). Simpel gehouden — alleen wat
 * hiervoor nodig is: afspelen, evt. loopen, en autoplay — geen scrubber/volumeknoppen, dit
 * is een korte referentieclip, geen mediaplayer.
 */
@Composable
expect fun PlatformVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    // Zero Trust: neem nooit aan dat een videobron laadt of afspeelt (kapotte URL, geen
    // netwerk, onbekend codec, ...) — de aanroeper krijgt dit netjes te horen in plaats van
    // voor onbepaalde tijd naar een zwart vlak te staren.
    onError: () -> Unit = {},
)
