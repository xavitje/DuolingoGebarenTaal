package com.example.signcoach.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Decodeert JPEG-bytes (zoals een review-frame uit de 'gesture-videos' bucket) naar een
 * weergeefbare [ImageBitmap], voor de galerij in het admin-goedkeurscherm. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap
