package com.phuuun.tsundoku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

/**
 * The book-shaped frame, as fractions of the 3:4 viewfinder: 80% wide, 90% tall, centred, so it's 2:3 like a cover.
 * The viewfinder has the photo's own 3:4 shape (the preview shows the whole shot, never zoomed in), so cropping the
 * photo to these same fractions keeps exactly what was inside the frame.
 */
private val CoverFrame = Rect(left = 0.1f, top = 0.05f, right = 0.9f, bottom = 0.95f)

/** Crops a JPEG to [frame] (fractions of its width and height) and re-encodes it. */
expect fun cropJpeg(bytes: ByteArray, frame: Rect, quality: Int): ByteArray

/**
 * A tappable cover: asks "Take a photo" (the framed camera) or "Choose from gallery", and hands back the cover as a
 * JPEG of at most 800 × 1200. A gallery image isn't cropped; the cover view crops it to shape.
 */
@Composable
fun CoverPicker(modifier: Modifier, onCover: (ByteArray) -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var shooting by remember { mutableStateOf(false) }
    if (shooting) CoverCamera { cover ->
        shooting = false
        cover?.let(onCover)
    }
    val gallery = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) scope.launch {
            runCatching { FileKit.compressImage(file.readBytes(), ImageFormat.JPEG, quality = 80, maxWidth = 800, maxHeight = 1200) }
                .onSuccess(onCover) // an image it can't decode just leaves the cover as it was
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

/** Full-screen camera with the cover frame; hands back the cropped JPEG, or nothing if you cancel. */
@Composable
fun CoverCamera(onDone: (ByteArray?) -> Unit) {
    Dialog(onDismissRequest = { onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "FIT THE COVER IN THE FRAME",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp),
            )
            val permission = rememberCameraPermissionState()
            LaunchedEffect(Unit) {
                if (permission.status == CameraPermissionStatus.Denied) permission.requestCameraPermission()
            }
            if (permission.status == CameraPermissionStatus.Granted) Viewfinder(onDone)
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
private fun Viewfinder(onDone: (ByteArray?) -> Unit) {
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
        FrameOverlay()
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
                        (controller.takePictureToFile() as? ImageCaptureResult.SuccessWithFile)?.let { cropToFrame(it.filePath) }
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
private fun FrameOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val frame = Rect(
            size.width * CoverFrame.left, size.height * CoverFrame.top,
            size.width * CoverFrame.right, size.height * CoverFrame.bottom,
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

private suspend fun cropToFrame(path: String): ByteArray {
    val file = FilePath(path)
    val original = SystemFileSystem.source(file).buffered().use { it.readByteArray() }
    SystemFileSystem.delete(file, mustExist = false)
    // Resize first: it also turns the photo upright (Android's EXIF rotation, iOS's orientation flag), so the frame fractions line up.
    val upright = FileKit.compressImage(original, ImageFormat.JPEG, quality = 95, maxWidth = 900, maxHeight = 1200)
    return cropJpeg(upright, CoverFrame, quality = 80)
}
