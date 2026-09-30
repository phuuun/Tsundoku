package com.phuuun.tsundoku

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

actual fun cropJpeg(bytes: ByteArray, frame: Rect, quality: Int): ByteArray {
    val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val x = (frame.left * image.width).roundToInt()
    val y = (frame.top * image.height).roundToInt()
    val cropped = Bitmap.createBitmap(
        image, x, y,
        ((frame.right * image.width).roundToInt() - x).coerceAtMost(image.width - x),
        ((frame.bottom * image.height).roundToInt() - y).coerceAtMost(image.height - y),
    )
    return ByteArrayOutputStream().also { cropped.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
