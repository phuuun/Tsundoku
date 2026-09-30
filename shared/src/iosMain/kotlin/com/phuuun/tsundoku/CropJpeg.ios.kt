package com.phuuun.tsundoku

import androidx.compose.ui.geometry.Rect
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

// The input comes out of FileKit.compressImage, which redraws it upright, so the CGImage's pixels are already the right way up.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun cropJpeg(bytes: ByteArray, frame: Rect, quality: Int): ByteArray {
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    val image = requireNotNull(UIImage(data = data).CGImage) { "Not an image" }
    val width = CGImageGetWidth(image).toDouble()
    val height = CGImageGetHeight(image).toDouble()
    val cropped = CGImageCreateWithImageInRect(
        image,
        CGRectMake(frame.left * width, frame.top * height, frame.width * width, frame.height * height),
    )
    val jpeg = requireNotNull(UIImageJPEGRepresentation(UIImage.imageWithCGImage(cropped), quality / 100.0)) { "Couldn't encode JPEG" }
    return ByteArray(jpeg.length.toInt()).also { out ->
        if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
    }
}
