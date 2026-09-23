package io.github.xavitje.signcoach.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

// LET OP (kon niet gecompileerd worden zonder iOS-toolchain, zelfde voorbehoud als elders in
// dit bestand-cluster): Compose Multiplatform op iOS tekent via Skia/Skiko, dus dit decodeert
// via dezelfde route als de desktop-target. Mocht de exacte importpaden afwijken van de
// gebruikte Compose Multiplatform-versie, vervang dan door de equivalente Skiko-aanroep.
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap =
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
