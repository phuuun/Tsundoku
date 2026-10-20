package com.phuuun.tsundoku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.kashif.cameraK.compose.CameraPreviewView
import com.kashif.cameraK.compose.rememberCameraKState
import com.kashif.cameraK.enums.AspectRatio
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.enums.Directory
import com.kashif.cameraK.result.ImageCaptureResult
import com.kashif.cameraK.state.CameraConfiguration
import com.kashif.cameraK.state.CameraKState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.compressImage
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path as FilePath
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.publicvalue.multiplatform.qrcode.CameraPermissionStatus
import org.publicvalue.multiplatform.qrcode.rememberCameraPermissionState
import kotlin.math.abs

/**
 * The book-shaped frame, as fractions of the 3:4 viewfinder: 80% wide, 90% tall, centred, so it's 2:3 like a cover.
 * The viewfinder has the photo's own 3:4 shape (the preview shows the whole shot, never zoomed in), so cropping the
 * photo to these same fractions keeps exactly what was inside the frame.
 */
private val CoverFrame = Rect(left = 0.1f, top = 0.05f, right = 0.9f, bottom = 0.95f)

/** A spine is too thin to frame ahead of time: keep the whole shot and crop it after. */
private val WholeShot = Rect(0f, 0f, 1f, 1f)

/** Crops a JPEG to [frame] (fractions of its width and height) and re-encodes it. */
expect fun cropJpeg(bytes: ByteArray, frame: Rect, quality: Int): ByteArray

/**
 * A tappable cover or spine: asks "Take a photo" (the camera) or "Choose from gallery", then you crop it. [aspect]
 * (width ÷ height) locks the crop's shape, null leaves it free and frames a spine instead of a cover. [onPhoto] gets
 * the cropped JPEG and its width ÷ height.
 */
@Composable
fun CoverPicker(modifier: Modifier, aspect: Float?, onPhoto: (ByteArray, Float) -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var shooting by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<ByteArray?>(null) }
    if (shooting) {
        val spine = aspect == null
        CoverCamera(if (spine) WholeShot else CoverFrame, if (spine) "PUT THE SPINE IN THE MIDDLE" else "FIT THE COVER IN THE FRAME") { shot ->
            shooting = false
            cropping = shot
        }
    }
    cropping?.let { photo ->
        CropPhoto(photo, aspect) { cropped ->
            cropping = null
            cropped?.let { (jpeg, shape) -> onPhoto(jpeg, shape) }
        }
    }
    val gallery = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) scope.launch {
            runCatching { FileKit.compressImage(file.readBytes(), ImageFormat.JPEG, quality = 80, maxWidth = 800, maxHeight = 1200) }
                .onSuccess { cropping = it } // an image it can't decode just leaves the cover as it was
        }
    }
    Box(modifier.clip(RoundedCornerShape(4.dp)).clickable { menu = true }) {
        content()
        DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(14.dp), containerColor = colors.surfaceContainerHigh) {
            DropdownMenuItem(text = { Text("Take a photo") }, onClick = { menu = false; shooting = true })
            DropdownMenuItem(text = { Text("Choose from gallery") }, onClick = { menu = false; gallery.launch() })
        }
    }
}

/** Full-screen camera with [frame] drawn on it; hands back the photo cropped to it, or nothing if you cancel. */
@Composable
fun CoverCamera(frame: Rect, hint: String, onDone: (ByteArray?) -> Unit) {
    Dialog(onDismissRequest = { onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp),
            )
            val permission = rememberCameraPermissionState()
            LaunchedEffect(Unit) {
                if (permission.status == CameraPermissionStatus.Denied) permission.requestCameraPermission()
            }
            if (permission.status == CameraPermissionStatus.Granted) Viewfinder(frame, onDone)
            else Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f), contentAlignment = Alignment.Center) {
                Text(
                    "Tsundoku needs the camera to photograph the cover.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
            TextButton({ onDone(null) }, Modifier.padding(top = 8.dp)) { Text("Cancel", color = Color.White.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
private fun Viewfinder(frame: Rect, onDone: (ByteArray?) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val camera by rememberCameraKState(
        CameraConfiguration(
            cameraLens = CameraLens.BACK,
            aspectRatio = AspectRatio.RATIO_4_3,
            directory = Directory.DOCUMENTS, // the app's own folder: no storage permission, never in the gallery
        )
    )

    Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f), contentAlignment = Alignment.Center) {
        (camera as? CameraKState.Ready)?.let { CameraPreviewView(it.controller, Modifier.fillMaxSize()) }
        FrameOverlay(frame)
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
    }

    Spacer(Modifier.height(28.dp))
    // Shutter: a white ring around a white dot, like every phone camera.
    Box(
        Modifier.size(76.dp).border(3.dp, Color.White, CircleShape).padding(7.dp)
            .background(if (busy) Color.White.copy(alpha = 0.4f) else Color.White, CircleShape)
            .clickable(enabled = !busy && camera is CameraKState.Ready) {
                val controller = (camera as CameraKState.Ready).controller
                busy = true
                scope.launch {
                    val cover = try {
                        (controller.takePictureToFile() as? ImageCaptureResult.SuccessWithFile)?.let { cropToFrame(it.filePath, frame) }
                    } catch (e: Exception) {
                        null // closes the camera; the cover slot stays empty and you can try again
                    }
                    onDone(cover)
                }
            }
    )
}

/** Everything outside the frame is dimmed, so it's obvious what ends up on the cover. */
@Composable
private fun FrameOverlay(fractions: Rect) {
    Canvas(Modifier.fillMaxSize()) {
        val frame = Rect(
            size.width * fractions.left, size.height * fractions.top,
            size.width * fractions.right, size.height * fractions.bottom,
        )
        val corner = CornerRadius(8.dp.toPx())
        drawPath(
            Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(RoundRect(frame, corner))
            },
            Color.Black.copy(alpha = 0.6f),
        )
        drawRoundRect(Color.White, frame.topLeft, frame.size, corner, style = Stroke(2.dp.toPx()))
    }
}

private suspend fun cropToFrame(path: String, frame: Rect): ByteArray {
    val file = FilePath(path)
    val original = SystemFileSystem.source(file).buffered().use { it.readByteArray() }
    SystemFileSystem.delete(file, mustExist = false)
    // Resize first: it also turns the photo upright (Android's EXIF rotation, iOS's orientation flag), so the frame fractions line up.
    val upright = FileKit.compressImage(original, ImageFormat.JPEG, quality = 95, maxWidth = 900, maxHeight = 1200)
    return cropJpeg(upright, frame, quality = 80)
}

enum class Grip { TopLeft, TopRight, BottomLeft, BottomRight, Move }

/** The smallest a crop gets, as a fraction of the photo. */
private const val MinCrop = 0.05f

/**
 * [crop] after dragging [grip] by ([dx], [dy]); all in fractions of the photo, and the crop never leaves it.
 * [lock] keeps the crop's width ÷ height (in those fractions) as it is.
 */
fun dragCrop(crop: Rect, grip: Grip, dx: Float, dy: Float, lock: Float?): Rect {
    if (grip == Grip.Move) {
        val x = (crop.left + dx).coerceIn(0f, 1f - crop.width)
        val y = (crop.top + dy).coerceIn(0f, 1f - crop.height)
        return Rect(x, y, x + crop.width, y + crop.height)
    }
    val left = grip == Grip.TopLeft || grip == Grip.BottomLeft
    val top = grip == Grip.TopLeft || grip == Grip.TopRight
    // The opposite corner stays put.
    val anchorX = if (left) crop.right else crop.left
    val anchorY = if (top) crop.bottom else crop.top
    val maxW = if (left) anchorX else 1f - anchorX
    val maxH = if (top) anchorY else 1f - anchorY
    var w = (crop.width + if (left) -dx else dx).coerceIn(MinCrop, maxW)
    var h = (crop.height + if (top) -dy else dy).coerceIn(MinCrop, maxH)
    if (lock != null) {
        // Follow whichever way the finger moved more, then shrink to fit rather than run off the photo.
        if (abs(dx) >= abs(dy * lock)) h = w / lock else w = h * lock
        if (w > maxW) { w = maxW; h = w / lock }
        if (h > maxH) { h = maxH; w = h * lock }
    }
    val x = if (left) anchorX - w else anchorX
    val y = if (top) anchorY - h else anchorY
    return Rect(x, y, x + w, y + h)
}

/** The biggest [lock]-shaped crop that fits, or a tall strip down the middle when the shape is free (a spine). */
private fun startCrop(lock: Float?): Rect {
    if (lock == null) return Rect(0.3f, 0.05f, 0.7f, 0.95f)
    val w = minOf(1f, lock)
    val h = w / lock
    return Rect((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2)
}

/**
 * Full-screen crop: drag a corner to resize, or inside to move. [aspect] (width ÷ height) locks the shape.
 * Hands back the cropped JPEG and its width ÷ height, or nothing if you cancel.
 */
@Composable
private fun CropPhoto(jpeg: ByteArray, aspect: Float?, onDone: (Pair<ByteArray, Float>?) -> Unit) {
    var photo by remember { mutableStateOf<Size?>(null) } // its size, once Coil has decoded it
    var crop by remember { mutableStateOf(Rect.Zero) }
    // The aspect in fractions of this photo, which is rarely square.
    val lock = photo?.let { p -> aspect?.let { it * p.height / p.width } }

    Dialog(onDismissRequest = { onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (aspect == null) "DRAG THE CORNERS TO THE EDGES OF THE SPINE" else "DRAG THE CORNERS TO THE EDGES OF THE COVER",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(photo?.let { it.width / it.height } ?: (3f / 4f))) {
                    AsyncImage(
                        jpeg, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize(),
                        onSuccess = {
                            val size = Size(it.result.image.width.toFloat(), it.result.image.height.toFloat())
                            if (photo == null) crop = startCrop(aspect?.let { a -> a * size.height / size.width })
                            photo = size
                        },
                    )
                    if (photo != null) Canvas(
                        Modifier.fillMaxSize().pointerInput(lock) {
                            var grip: Grip? = null
                            detectDragGestures(
                                onDragStart = { at ->
                                    val box = Rect(crop.left * size.width, crop.top * size.height, crop.right * size.width, crop.bottom * size.height)
                                    val corners = mapOf(
                                        Grip.TopLeft to box.topLeft, Grip.TopRight to box.topRight,
                                        Grip.BottomLeft to box.bottomLeft, Grip.BottomRight to box.bottomRight,
                                    )
                                    // A corner within reach of the finger, else the middle to move it, else nothing.
                                    grip = corners.entries.minBy { (it.value - at).getDistance() }
                                        .takeIf { (it.value - at).getDistance() < 40.dp.toPx() }?.key
                                        ?: Grip.Move.takeIf { box.contains(at) }
                                },
                            ) { change, drag ->
                                change.consume()
                                grip?.let { crop = dragCrop(crop, it, drag.x / size.width, drag.y / size.height, lock) }
                            }
                        }
                    ) {
                        val box = Rect(crop.left * size.width, crop.top * size.height, crop.right * size.width, crop.bottom * size.height)
                        drawPath(
                            Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(Offset.Zero, size))
                                addRect(box)
                            },
                            Color.Black.copy(alpha = 0.6f),
                        )
                        drawRect(Color.White, box.topLeft, box.size, style = Stroke(2.dp.toPx()))
                        listOf(box.topLeft, box.topRight, box.bottomLeft, box.bottomRight).forEach { drawCircle(Color.White, 7.dp.toPx(), it) }
                    }
                }
            }
            Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TextButton({ onDone(null) }) { Text("Cancel", color = Color.White.copy(alpha = 0.7f)) }
                TextButton(
                    onClick = { photo?.let { p -> onDone(cropJpeg(jpeg, crop, quality = 85) to crop.width * p.width / (crop.height * p.height)) } },
                    enabled = photo != null,
                ) { Text("Use photo", color = Color.White) }
            }
        }
    }
}
