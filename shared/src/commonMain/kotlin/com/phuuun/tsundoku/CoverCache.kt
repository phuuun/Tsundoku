package com.phuuun.tsundoku

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.compressImage
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

private const val FOLDER = "ol-covers"

/** File names in [FOLDER] that are saved so far; [Cover] reads it, so it switches to the file as soon as a download lands. */
private var saved by mutableStateOf(emptySet<String>())

/** Where the saved copy of an Open Library cover lives, relative to the data folder. Named after the URL, so a changed URL never shows a stale cover. */
private fun savedName(url: String) = "$FOLDER/${url.hashCode().toUInt().toString(16)}.jpg"

/** The saved copy of [url], or null if it isn't saved (yet). */
fun savedCover(url: String?): String? = url?.let(::savedName)?.takeIf { it in saved }

/** Reads the folder once, so a launch knows what is already saved. */
fun loadSavedCovers(dir: String) {
    val folder = Path(dir, FOLDER)
    if (SystemFileSystem.exists(folder)) saved = SystemFileSystem.list(folder).map { "$FOLDER/${it.name}" }.toSet()
}

/** Downloads [url], shrinks it and saves it; false when this cover couldn't be fetched. Throws [RateLimited] or when offline. */
suspend fun saveCover(dir: String, url: String): Boolean {
    val bytes = downloadCover(url) ?: return false
    val jpeg = FileKit.compressImage(bytes, ImageFormat.JPEG, quality = 70, maxWidth = 600, maxHeight = 900)
    val name = savedName(url)
    SystemFileSystem.createDirectories(Path(dir, FOLDER))
    SystemFileSystem.sink(Path(dir, name)).buffered().use { it.write(jpeg) }
    saved = saved + name
    return true
}
