package io.github.xavitje.signcoach.platform

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: error("Kon review-afbeelding niet decoderen (ongeldige JPEG-data)")
    return bitmap.asImageBitmap()
}
