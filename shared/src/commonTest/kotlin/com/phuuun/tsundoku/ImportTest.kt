package com.phuuun.tsundoku

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImportTest {
    @Test
    fun csvQuoting() {
        assertEquals(
            listOf(listOf("a", "b, c", "say \"hi\"", "two\nlines"), listOf("", "x")),
            parseCsv("a,\"b, c\",\"say \"\"hi\"\"\",\"two\nlines\"\r\n,x\r\n"),
        )
    }

    @Test
    fun goodreads() {
        // Real rows from a Goodreads export (plus a series title and an HTML review), BOM and all.
        val csv = "﻿" + """Book Id,Title,Author,Author l-f,Additional Authors,ISBN,ISBN13,My Rating,Average Rating,Publisher,Binding,Number of Pages,Year Published,Original Publication Year,Date Read,Date Added,Bookshelves,Bookshelves with positions,Exclusive Shelf,My Review,Spoiler,Private Notes,Read Count,Recommended For,Recommended By,Owned Copies,Original Purchase Date,Original Purchase Location,Condition,Condition Description,BCID
13278990,The Housing Monster,prole.info,"prole.info, prole.info",,="160486530X",="9781604865301",0,3.77,PM Press,Paperback,160,2012,2011,,2017/12/07,currently-reading,currently-reading (#3),currently-reading,,,,1,,,0,,,,,
15839976,Red Rising (Red Rising Saga #1),Pierce Brown,"Brown, Pierce",,="0345539788",="9780345539786",5,4.27,Del Rey,Hardcover,382,2014,2014,2025/03/14,2024/12/01,,,read,"Awesome.<br/>Loved <i>Darrow</i> &amp; Sevro.",,,2,,,0,,,,,
7805,Pale Fire,Vladimir Nabokov,"Nabokov, Vladimir",,="",="",0,4.19,Penguin,Paperback,246,2000,1962,,2013/10/09,to-read,to-read (#26),to-read,,,,0,,,0,,,,,
"""
        val incoming = readImport(csv)
        assertEquals("Goodreads", incoming.source)
        val (reading, redRising, paleFire) = incoming.backup.books

        assertEquals("9781604865301", reading.isbn)
        assertTrue(!reading.finished && reading.reads.isEmpty())

        assertEquals("Red Rising", redRising.title)
        assertTrue(redRising.finished)
        assertEquals(listOf(Read(LocalDate(2025, 3, 14), 5, "Awesome.\nLoved Darrow & Sevro.")), redRising.reads)
        assertEquals("https://covers.openlibrary.org/b/isbn/9780345539786-L.jpg?default=false", redRising.coverUrl)

        assertEquals("gr-7805", paleFire.isbn) // no ISBN: keyed on the Goodreads id, no cover
        assertEquals(null, paleFire.coverUrl)
    }

    @Test
    fun storyGraph() {
        val csv = """Title,Authors,Contributors,ISBN/UID,Format,Read Status,Date Added,Last Date Read,Dates Read,Read Count,Moods,Pace,Character- or Plot-Driven?,Strong Character Development?,Loveable Characters?,Diverse Characters?,Flawed Characters?,Star Rating,Review,Content Warnings,Content Warning Description,Tags,Owned?
Red Rising,"Pierce Brown, Someone Else",,9780345539786,paperback,read,2024/12/01,2026/09/12,"2025/03/01-2025/03/14, 2026/09/01-2026/09/12",2,,,,,,,,3.75,Worse than I remembered,,,,No
Laskar Pelangi,Andrea Hirata,,9789793062792,paperback,to-read,2025/01/01,,,0,,,,,,,,,,,,,No
Some Web Novel,Anon,,a1b2-c3d4,digital,did-not-finish,2025/01/01,,,0,,,,,,,,,,,,,No
"""
        val incoming = readImport(csv)
        assertEquals("StoryGraph", incoming.source)
        val (redRising, laskar, webNovel) = incoming.backup.books

        assertEquals("Pierce Brown", redRising.author)
        assertEquals(
            listOf(Read(LocalDate(2025, 3, 14)), Read(LocalDate(2026, 9, 12), 4, "Worse than I remembered")),
            redRising.reads,
        )
        assertTrue(!laskar.finished && laskar.reads.isEmpty())
        assertEquals("sga1b2c3d4", webNovel.isbn.replace("-", "")) // UID that isn't an ISBN
        assertTrue(!webNovel.finished)
    }

    @Test
    fun rejectsOtherFiles() {
        assertFailsWith<IllegalArgumentException> { readImport("Name,Email\nA,b@c.d\n") }
    }
}
