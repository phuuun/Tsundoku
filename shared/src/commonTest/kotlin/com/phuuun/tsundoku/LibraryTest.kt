package com.phuuun.tsundoku

import kotlinx.datetime.LocalDate
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
}
