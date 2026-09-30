package com.phuuun.tsundoku

import androidx.compose.runtime.mutableStateListOf
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class Book(
    val isbn: String,
    val title: String,
    val author: String,
    val coverUrl: String? = null,
    /** A photo you took of the cover, relative to the library folder (iOS moves that folder on every update). */
    val coverFile: String? = null,
    val finished: Boolean = false,
    val finishedAt: Long? = null,
    /** 1 to 5 stars, or null when you didn't rate it. */
    val rating: Int? = null,
    val review: String? = null,
)

/** Every book, newest first, saved to `books.json` in [dir]. ISBN is the key. */
// ponytail: rewrites the whole file on the main thread on every change; fine for thousands of books, move to SQLite if it ever stutters
class Library(val dir: String) {
    private val file = Path(dir, "books.json")
    val books = mutableStateListOf<Book>()

    init {
        if (SystemFileSystem.exists(file)) {
            books.addAll(Json.decodeFromString<List<Book>>(SystemFileSystem.source(file).buffered().use { it.readString() }))
        }
    }

    fun has(isbn: String) = books.any { it.isbn == isbn }

    /** [photo] is a JPEG of the cover; it's written to `covers/` and wins over [Book.coverUrl]. */
    fun add(book: Book, photo: ByteArray? = null) {
        val coverFile = photo?.let {
            // Timestamped so re-adding a book with a new photo never shows the old one from Coil's cache.
            val name = "covers/${book.isbn}-${now()}.jpg"
            SystemFileSystem.createDirectories(Path(dir, "covers"))
            SystemFileSystem.sink(Path(dir, name)).buffered().use { sink -> sink.write(it) }
            name
        }
        books.add(0, book.copy(coverFile = coverFile ?: book.coverFile, finishedAt = if (book.finished) now() else null))
        save()
    }

    fun remove(book: Book) {
        books.removeAll { it.isbn == book.isbn }
        book.coverFile?.let { SystemFileSystem.delete(Path(dir, it), mustExist = false) }
        save()
    }

    fun setFinished(book: Book, finished: Boolean, rating: Int? = book.rating, review: String? = book.review) {
        books.removeAll { it.isbn == book.isbn }
        books.add(0, book.copy(finished = finished, finishedAt = if (finished) now() else null, rating = rating, review = review))
        save()
    }

    // Write to a temp file and swap it in, so a crash mid-write can't wipe the library.
    private fun save() {
        val tmp = Path("$file.tmp")
        SystemFileSystem.sink(tmp).buffered().use { it.writeString(Json.encodeToString(books.toList())) }
        SystemFileSystem.atomicMove(tmp, file)
    }

    @OptIn(ExperimentalTime::class)
    private fun now() = Clock.System.now().toEpochMilliseconds()
}
