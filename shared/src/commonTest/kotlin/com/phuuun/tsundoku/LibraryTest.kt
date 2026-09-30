package com.phuuun.tsundoku

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
    @Test
    fun photoRatingAndReviewSurviveARestartAndRemoveCleansUp() {
        val dir = Path(SystemTemporaryDirectory, "tsundoku-${Random.nextLong()}").also { SystemFileSystem.createDirectories(it) }.toString()

        Library(dir).add(Book("1", "Mine", "Me", finished = true, rating = 4, review = "Good."), photo = byteArrayOf(1, 2, 3))

        val reloaded = Library(dir)
        val book = reloaded.books.single()
        assertEquals(4, book.rating)
        assertEquals("Good.", book.review)
        assertNotNull(book.finishedAt)
        val cover = Path(dir, assertNotNull(book.coverFile))
        assertTrue(SystemFileSystem.exists(cover))

        reloaded.remove(book)
        assertFalse(SystemFileSystem.exists(cover))
        assertEquals(emptyList(), Library(dir).books.toList())
    }
}
