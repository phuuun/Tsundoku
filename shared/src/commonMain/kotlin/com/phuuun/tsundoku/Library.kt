package com.phuuun.tsundoku

import androidx.compose.runtime.mutableStateListOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** One time through the book. A plain date, so no timezone can ever move it a day. */
@Serializable
data class Read(
    val finishedOn: LocalDate,
    /** 1 to 5 stars, or null when you didn't rate it. */
    val rating: Int? = null,
    val review: String? = null,
)

@Serializable
data class Book(
    val isbn: String,
    val title: String,
    val author: String,
    val coverUrl: String? = null,
    /** A photo you took of the cover, relative to the library folder (iOS moves that folder on every update). */
    val coverFile: String? = null,
    /** On the Finished shelf. False with [reads] means you're rereading it. */
    val finished: Boolean = false,
    /** Every finished read, oldest first. */
    val reads: List<Read> = emptyList(),
) {
    val rereading get() = !finished && reads.isNotEmpty()
    val lastRead get() = reads.lastOrNull()
}

/** Every book, newest first, saved to `books.json` in [dir]. ISBN is the key. */
// ponytail: rewrites the whole file on the main thread on every change; fine for thousands of books, move to SQLite if it ever stutters
class Library(val dir: String) {
    private val file = Path(dir, "books.json")
    val books = mutableStateListOf<Book>()

    init {
        if (SystemFileSystem.exists(file)) {
            books.addAll(decodeBooks(SystemFileSystem.source(file).buffered().use { it.readString() }))
        }
    }

    fun has(isbn: String) = books.any { it.isbn == isbn }

    /** [photo] is a JPEG of the cover; it's written to `covers/` and wins over [Book.coverUrl]. */
    fun add(book: Book, photo: ByteArray? = null) {
        books.add(0, book.copy(coverFile = photo?.let { writePhoto(book.isbn, it) } ?: book.coverFile))
        save()
    }

    /** New title and author; [photo] replaces the cover photo, [removePhoto] drops it for the Open Library cover. */
    fun editDetails(book: Book, title: String, author: String, photo: ByteArray?, removePhoto: Boolean) {
        val coverFile = when {
            photo != null -> writePhoto(book.isbn, photo)
            removePhoto -> null
            else -> book.coverFile
        }
        if (coverFile != book.coverFile) book.coverFile?.let { SystemFileSystem.delete(Path(dir, it), mustExist = false) }
        put(book.copy(title = title, author = author, coverFile = coverFile), toTop = false)
    }

    // Timestamped so a new photo of the same book never shows the old one from Coil's cache.
    private fun writePhoto(isbn: String, jpeg: ByteArray): String {
        val name = "covers/$isbn-${nowMillis()}.jpg"
        SystemFileSystem.createDirectories(Path(dir, "covers"))
        SystemFileSystem.sink(Path(dir, name)).buffered().use { it.write(jpeg) }
        return name
    }

    fun remove(book: Book) {
        books.removeAll { it.isbn == book.isbn }
        book.coverFile?.let { SystemFileSystem.delete(Path(dir, it), mustExist = false) }
        save()
    }

    fun finish(book: Book, read: Read) = put(book.copy(finished = true, reads = book.reads + read))

    /** Back to To read, keeping every past read. */
    fun reread(book: Book) = put(book.copy(finished = false))

    /** A past read, e.g. from years ago. It doesn't move the book: one on To read becomes a reread. */
    fun addRead(book: Book, read: Read) = put(book.copy(reads = book.reads + read), toTop = false)

    /** [index] is the read's position in [Book.reads]. */
    fun editRead(book: Book, index: Int, read: Read) =
        put(book.copy(reads = book.reads.toMutableList().also { it[index] = read }), toTop = false)

    /** Deleting the only read of a finished book sends it back to To read, unread. */
    fun deleteRead(book: Book, index: Int) {
        val reads = book.reads.filterIndexed { i, _ -> i != index }
        put(book.copy(reads = reads, finished = book.finished && reads.isNotEmpty()), toTop = reads.isEmpty())
    }

    // Reads stay in date order, so editing a date can't leave the "latest" one somewhere in the middle.
    private fun put(book: Book, toTop: Boolean = true) {
        val updated = book.copy(reads = book.reads.sortedBy { it.finishedOn })
        val at = books.indexOfFirst { it.isbn == book.isbn }
        if (toTop || at < 0) {
            books.removeAll { it.isbn == book.isbn }
            books.add(0, updated)
        } else {
            books[at] = updated
        }
        save()
    }

    /** The whole library as one JSON file, cover photos included. */
    fun exportBackup(): String = Json.encodeToString(
        Backup(
            books = books.toList(),
            photos = books.mapNotNull { book ->
                book.coverFile?.let { name ->
                    Path(dir, name).takeIf { SystemFileSystem.exists(it) }
                        ?.let { name to Base64.encode(SystemFileSystem.source(it).buffered().use { src -> src.readByteArray() }) }
                }
            }.toMap(),
        )
    )

    /**
     * Puts the backup's new books on top. A book already here (same ISBN) is swapped for the backup's version only if
     * its ISBN is in [replace]; otherwise yours stays as it is.
     */
    fun import(backup: Backup, replace: Set<String>) {
        val incoming = backup.books.distinctBy { it.isbn }.filter { !has(it.isbn) || it.isbn in replace }.map { book ->
            val name = book.coverFile
            val photo = name?.let { backup.photos[it] }
            if (name != null && photo != null && SafeCoverName.matches(name)) {
                SystemFileSystem.createDirectories(Path(dir, "covers"))
                SystemFileSystem.sink(Path(dir, name)).buffered().use { it.write(Base64.decode(photo)) }
                book
            } else {
                book.copy(coverFile = null) // falls back to the Open Library cover
            }
        }
        val replaced = books.filter { old -> incoming.any { it.isbn == old.isbn } }
        replaced.mapNotNull { it.coverFile }.filter { file -> incoming.none { it.coverFile == file } }
            .forEach { SystemFileSystem.delete(Path(dir, it), mustExist = false) }
        books.removeAll(replaced)
        books.addAll(0, incoming)
        save()
    }

    // Write to a temp file and swap it in, so a crash mid-write can't wipe the library.
    private fun save() {
        val tmp = Path("$file.tmp")
        SystemFileSystem.sink(tmp).buffered().use { it.writeString(Json.encodeToString(books.toList())) }
        SystemFileSystem.atomicMove(tmp, file)
    }
}

/** What Export writes. [photos] maps each [Book.coverFile] to its JPEG in Base64, so one file is the whole library. */
@Serializable
class Backup(val books: List<Book>, val photos: Map<String, String> = emptyMap())

/** Throws if [text] isn't a backup. Unknown keys are fine: a backup from a newer version still imports. */
fun readBackup(text: String): Backup = Json { ignoreUnknownKeys = true }.decodeFromString(text)

// A backup is a file from outside the app: only let it write where Library.add would have.
private val SafeCoverName = Regex("covers/[0-9A-Za-z-]+\\.jpg")

@OptIn(ExperimentalTime::class)
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

@OptIn(ExperimentalTime::class)
private fun nowMillis() = Clock.System.now().toEpochMilliseconds()

/**
 * Reads books.json, including v1.0 files where a finished book had one flat `finishedAt` (epoch millis),
 * `rating` and `review` instead of a list of reads.
 */
@OptIn(ExperimentalTime::class)
internal fun decodeBooks(text: String): List<Book> {
    val migrated = buildJsonArray {
        for (element in Json.parseToJsonElement(text).jsonArray) {
            val book = element.jsonObject
            val finishedAt = book["finishedAt"]?.jsonPrimitive?.longOrNull
            if ("reads" in book || finishedAt == null) {
                add(JsonObject(book - "finishedAt" - "rating" - "review"))
                continue
            }
            add(buildJsonObject {
                (book - "finishedAt" - "rating" - "review").forEach { (key, value) -> put(key, value) }
                put("reads", JsonArray(listOf(buildJsonObject {
                    val day = Instant.fromEpochMilliseconds(finishedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
                    put("finishedOn", JsonPrimitive(day.toString()))
                    book["rating"]?.let { put("rating", it) }
                    book["review"]?.takeIf { it.jsonPrimitive.contentOrNull != null }?.let { put("review", it) }
                })))
            })
        }
    }
    return Json.decodeFromJsonElement<List<Book>>(migrated)
}
