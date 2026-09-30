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
    val finished: Boolean = false,
    val finishedAt: Long? = null,
)

/** Every book, newest first, saved to `books.json` in [dir]. ISBN is the key. */
// ponytail: rewrites the whole file on the main thread on every change; fine for thousands of books, move to SQLite if it ever stutters
class Library(dir: String) {
    private val file = Path(dir, "books.json")
    val books = mutableStateListOf<Book>()

    init {
        if (SystemFileSystem.exists(file)) {
            books.addAll(Json.decodeFromString<List<Book>>(SystemFileSystem.source(file).buffered().use { it.readString() }))
        }
    }

    fun has(isbn: String) = books.any { it.isbn == isbn }

    fun add(book: Book) {
        books.add(0, book)
        save()
    }

    fun remove(book: Book) {
        books.removeAll { it.isbn == book.isbn }
        save()
    }

    @OptIn(ExperimentalTime::class)
    fun setFinished(book: Book, finished: Boolean) {
        books.removeAll { it.isbn == book.isbn }
        books.add(0, book.copy(finished = finished, finishedAt = if (finished) Clock.System.now().toEpochMilliseconds() else null))
        save()
    }

    // Write to a temp file and swap it in, so a crash mid-write can't wipe the library.
    private fun save() {
        val tmp = Path("$file.tmp")
        SystemFileSystem.sink(tmp).buffered().use { it.writeString(Json.encodeToString(books.toList())) }
        SystemFileSystem.atomicMove(tmp, file)
    }
}
