package com.phuuun.tsundoku

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShelfTest {
    @Test
    fun aFullShelfStartsANewOneBelow() {
        // 100 wide: 40 + 2 + 40 fits, the next 40 doesn't; a book wider than the shelf gets one to itself.
        assertEquals(
            listOf(listOf(40f, 40f), listOf(40f, 10f), listOf(150f), listOf(5f)),
            packShelves(listOf(40f, 40f, 40f, 10f, 150f, 5f), { it }, max = 100f, gap = 2f),
        )
        assertEquals(emptyList(), packShelves(emptyList<Float>(), { it }, 100f, 2f))
    }

    @Test
    fun anAuthorsBooksStandLevelAndThickBooksAreThick() {
        val one = Book("1", "Golden Son", "Pierce Brown", pages = 440)
        val two = Book("2", "Red Rising", "pierce brown", pages = 380)
        assertEquals(one.spineHeight(), two.spineHeight())
        assertTrue(one.copy(size = BookSize.Tall).spineHeight() > one.copy(size = BookSize.Pocket).spineHeight())
        assertTrue(one.copy(pages = 1000).spineWidth() > one.copy(pages = 150).spineWidth())
        assertEquals(one.spineHeight() * 0.2f, one.copy(spineAspect = 0.2f).spineWidth(), 0.01f) // the photo wins
    }

    @Test
    fun dragsTheCrop() {
        val crop = Rect(0.2f, 0.2f, 0.6f, 0.6f)
        assertNear(Rect(0.3f, 0.2f, 0.6f, 0.7f), dragCrop(crop, Grip.TopLeft, 0.1f, 0f).let { dragCrop(it, Grip.BottomLeft, 0f, 0.1f) })
        assertNear(Rect(0.6f, 0.2f, 1f, 0.6f), dragCrop(crop, Grip.Move, 0.9f, 0f)) // stops at the edge
        assertEquals(0.05f, dragCrop(crop, Grip.BottomRight, -1f, -1f).width, 0.0001f) // never smaller than 5%
        assertNear(Rect(0.2f, 0.2f, 0.6f, 0.7f), dragCrop(crop, Grip.Bottom, 0.3f, 0.1f)) // an edge ignores sideways drift
        assertNear(Rect(0f, 0.2f, 0.6f, 0.6f), dragCrop(crop, Grip.Left, -0.5f, 0.3f)) // and stops at the photo's edge
    }

    private fun assertNear(expected: Rect, actual: Rect) =
        assertTrue(listOf(expected.left - actual.left, expected.top - actual.top, expected.right - actual.right, expected.bottom - actual.bottom)
            .all { kotlin.math.abs(it) < 0.0001f }, "$expected != $actual")
}
