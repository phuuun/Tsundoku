package com.phuuun.tsundoku

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenLibraryTest {
    @Test
    fun cleansIsbns() {
        assertEquals("9780143127550", cleanIsbn(" 978-0-14-312755-0 "))
        assertEquals("602036124X", cleanIsbn("602036124x"))
        assertNull(cleanIsbn("12345"))
        assertNull(cleanIsbn("97801431275X0"))
    }

    @Test
    fun parsesSearch() {
        val body = """{"numFound":1,"docs":[{"author_name":["Andrea Hirata","Some Translator"],"cover_i":7079796,"title":"Laskar Pelangi"}]}"""
        assertEquals(
            Book("9789793062792", "Laskar Pelangi", "Andrea Hirata", "https://covers.openlibrary.org/b/id/7079796-L.jpg"),
            parseSearch("9789793062792", body),
        )
        assertEquals(Book("1", "No Cover", ""), parseSearch("1", """{"docs":[{"title":"No Cover"}]}"""))
        assertNull(parseSearch("1", """{"numFound":0,"docs":[]}"""))
    }
}
