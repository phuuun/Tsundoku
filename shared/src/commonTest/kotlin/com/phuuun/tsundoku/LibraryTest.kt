package com.phuuun.tsundoku

import kotlinx.datetime.LocalDate
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LibraryTest {
    private fun tempDir() =
        Path(SystemTemporaryDirectory, "tsundoku-${Random.nextLong()}").also { SystemFileSystem.createDirectories(it) }.toString()

    @Test
    fun photoAndReadsSurviveARestartAndRemoveCleansUp() {
        val dir = tempDir()
        Library(dir).add(Book("1", "Mine", "Me", finished = true, reads = listOf(Read(LocalDate(2026, 9, 30), 4, "Good."))), photo = byteArrayOf(1, 2, 3))

        val reloaded = Library(dir)
        val book = reloaded.books.single()
        assertEquals(listOf(Read(LocalDate(2026, 9, 30), 4, "Good.")), book.reads)
        val cover = Path(dir, assertNotNull(book.coverFile))
        assertTrue(SystemFileSystem.exists(cover))

        reloaded.remove(book)
        assertFalse(SystemFileSystem.exists(cover))
        assertEquals(emptyList(), Library(dir).books.toList())
    }

    @Test
    fun rereadKeepsHistoryAndReadsStayInDateOrder() {
        val library = Library(tempDir())
        val first = Read(LocalDate(2025, 3, 1), 5, "Awesome")
        library.add(Book("rr", "Red Rising", "Pierce Brown", finished = true, reads = listOf(first)))

        library.reread(library.books.single())
        assertTrue(library.books.single().rereading)

        val second = Read(LocalDate(2026, 9, 30), 3, "Worse than I remembered")
        library.finish(library.books.single(), second)
        assertEquals(listOf(first, second), library.books.single().reads)

        // Moving the first read's date past the second one reorders them.
        library.editRead(library.books.single(), 0, first.copy(finishedOn = LocalDate(2026, 10, 1)))
        assertEquals(listOf(second, first.copy(finishedOn = LocalDate(2026, 10, 1))), library.books.single().reads)

        library.deleteRead(library.books.single(), 0)
        library.deleteRead(library.books.single(), 0)
        assertFalse(library.books.single().finished) // no reads left: back to To read
    }

    @Test
    fun migratesVersionOneFiles() {
        // The shape v1.0 wrote: flat finishedAt (epoch millis), rating and review.
        val v1 = """[
            {"isbn":"1","title":"Laut Bercerita","author":"Leila S. Chudori"},
            {"isbn":"2","title":"Red Rising","author":"Pierce Brown","finished":true,"finishedAt":1790759575099,"rating":5},
            {"isbn":"3","title":"Moved back","author":"","rating":2}
        ]"""
        val books = decodeBooks(v1)
        assertEquals(emptyList(), books[0].reads)
        val redRising = books[1]
        assertTrue(redRising.finished)
        assertEquals(5, redRising.reads.single().rating)
        assertEquals(2026, redRising.reads.single().finishedOn.year)
        assertEquals(emptyList(), books[2].reads)
    }

    @Test
    fun backupRestoresEverythingOnAnotherPhone() {
        val old = Library(tempDir())
        old.add(Book("1", "Photo one", "", finished = true, reads = listOf(Read(LocalDate(2025, 1, 2), 5, "Yes"))), photo = byteArrayOf(9, 8, 7))
        old.add(Book("2", "Plain", "", coverUrl = "https://covers.openlibrary.org/b/id/1-L.jpg"))
        val file = old.exportBackup()

        val fresh = Library(tempDir())
        fresh.add(Book("2", "Stale copy", ""))
        fresh.import(readBackup(file), replace = setOf("2"))

        assertEquals(old.books.toList(), fresh.books.toList()) // same order, and "2" replaced by the backup's version
        val photo = Path(fresh.dir, assertNotNull(fresh.books.first { it.isbn == "1" }.coverFile))
        assertEquals(listOf<Byte>(9, 8, 7), SystemFileSystem.source(photo).buffered().use { it.readByteArray() }.toList())
    }

    @Test
    fun backupCannotWriteOutsideCovers() {
        val dir = tempDir()
        val evil = """{"books":[{"isbn":"1","title":"x","author":"","coverFile":"../books.json"}],"photos":{"../books.json":"AAAA"}}"""
        val library = Library(dir)
        library.import(readBackup(evil), replace = emptySet())
        assertEquals(null, library.books.single().coverFile)
        assertEquals("x", Library(dir).books.single().title) // books.json wasn't overwritten by the "photo"
    }

    @Test
    fun importKeepsMineUnlessChosen() {
        val library = Library(tempDir())
        library.add(Book("a", "Mine A", ""))
        library.add(Book("b", "Mine B", ""))
        val backup = Backup(listOf(Book("a", "Theirs A", ""), Book("b", "Theirs B", ""), Book("c", "New C", "")))

        library.import(backup, replace = setOf("b"))

        assertEquals(setOf("Mine A", "Theirs B", "New C"), library.books.map { it.title }.toSet())
    }

    @Test
    fun editingSwapsThePhotoAndAddingAReadKeepsTheShelf() {
        val library = Library(tempDir())
        library.add(Book("1", "Titel", "Autor"), photo = byteArrayOf(1))
        val oldPhoto = Path(library.dir, assertNotNull(library.books.single().coverFile))

        library.editDetails(library.books.single(), "Title", "Author", photo = byteArrayOf(2), removePhoto = false)
        val book = library.books.single()
        assertEquals("Title" to "Author", book.title to book.author)
        assertFalse(SystemFileSystem.exists(oldPhoto)) // the replaced photo doesn't linger
        assertTrue(SystemFileSystem.exists(Path(library.dir, assertNotNull(book.coverFile))))

        library.addRead(book, Read(LocalDate(2019, 5, 1), 4))
        assertTrue(library.books.single().rereading) // a past read on a To read book: it's a reread now

        library.editDetails(library.books.single(), "Title", "Author", photo = null, removePhoto = true)
        assertEquals(null, library.books.single().coverFile)
    }

    @Test
    fun favoritesFirstThenByAuthorThenTitle() {
        val books = listOf(
            Book("1", "Morning Star", "Pierce Brown"),
            Book("2", "No author", ""),
            Book("3", "Laut Bercerita", "Leila S. Chudori"),
            Book("4", "Golden Son", "Pierce Brown"),
            Book("5", "Laskar Pelangi", "andrea Hirata"),
            Book("6", "The Silent Patient", "Alex Michaelides", favorite = true),
            Book("7", "Red Rising", "Pierce Brown", favorite = true),
        )
        assertEquals(
            listOf("The Silent Patient", "Red Rising", "Laskar Pelangi", "Laut Bercerita", "Golden Son", "Morning Star", "No author"),
            books.sortedWith(ShelfOrder).map { it.title },
        )
    }

    @Test
    fun searchMatchesEveryWordInTitleOrAuthor() {
        val book = Book("1", "Red Rising", "Pierce Brown")
        assertTrue(book.matches("red"))
        assertTrue(book.matches("  BROWN rising "))
        assertTrue(book.matches(""))
        assertFalse(book.matches("red sun"))
    }
}
