package com.phuuun.tsundoku

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage

/** How tall a [BookSize.Tall] book stands, in dp. */
const val ShelfHeight = 170f

/** Space between two books on a shelf, in dp. */
private const val Gap = 2f

/** Face out if you said so, else about one book in ten. Picked by ISBN, so it's the same book every time. */
fun Book.faceOut() = showCover ?: (isbn.hashCode().mod(10) == 0)

/** Its size plus up to 5% off. Picked by author, so their books (a series, usually) stand level next to each other. */
fun Book.spineHeight() = ShelfHeight * (size ?: BookSize.Regular).height * (0.95f + author.lowercase().hashCode().mod(50) / 1000f)

/** How thick it is, in dp: from the spine photo, else the page count, else picked by ISBN. */
fun Book.spineWidth(): Float = spineAspect?.let { (spineHeight() * it).coerceIn(10f, spineHeight()) }
    ?: pages?.let { (it / 16f + 8f).coerceIn(16f, 64f) }
    ?: (16f + isbn.hashCode().mod(17))

/** How much shelf it takes up, in dp. */
fun Book.shelfWidth() = if (faceOut()) spineHeight() * 2 / 3 else spineWidth()

/** Fills each shelf left to right until the next book won't fit, then starts a new shelf below. */
fun <T> packShelves(books: List<T>, width: (T) -> Float, max: Float, gap: Float): List<List<T>> {
    val shelves = mutableListOf<MutableList<T>>()
    var used = 0f
    for (book in books) {
        val w = width(book)
        if (shelves.isEmpty() || used + gap + w > max) {
            shelves.add(mutableListOf(book))
            used = w
        } else {
            shelves.last().add(book)
            used += gap + w
        }
    }
    return shelves
}

/** The books in [books]' order, standing on as many shelves as they need. */
@Composable
fun Bookcase(books: List<Book>, newIsbn: String?, onOpen: (Book) -> Unit) {
    // Books composed on the first pass rise in; ones that scroll or filter in later don't.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val dropped = remember(newIsbn) { mutableSetOf<String>() } // ponytail: marked while composing, fine for one drop
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val shelves = packShelves(books, Book::shelfWidth, maxWidth.value - 40, Gap)
        val firstOnShelf = shelves.runningFold(0) { n, shelf -> n + shelf.size }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(shelves, key = { _, shelf -> shelf.first().isbn }) { row, shelf ->
                Column(Modifier.animateItem()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap.dp), verticalAlignment = Alignment.Bottom) {
                        shelf.forEachIndexed { i, book ->
                            val entry = when {
                                book.isbn == newIsbn && dropped.add(book.isbn) -> Entry.Drop
                                !entered -> Entry.Rise
                                else -> Entry.None
                            }
                            key(book.isbn) { ShelfBook(book, entry, firstOnShelf[row] + i) { onOpen(book) } }
                        }
                    }
                    // The plank they stand on.
                    Box(Modifier.fillMaxWidth().height(8.dp).background(Bold.Ink, CircleShape))
                }
            }
        }
    }
}

/** How a book arrives: standing already, rising with the rest of its shelf, or dropping in because it was just added. */
private enum class Entry { None, Rise, Drop }

/** How far up you slide a held book to open it. */
private val SwipeToOpen = 64.dp

/**
 * Tap to open it. Hold to pull it off the shelf: it lifts out and turns to show its cover; slide it up to open it,
 * or let go to put it back.
 */
@Composable
private fun ShelfBook(book: Book, entryNow: Entry, position: Int, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var lift by remember { mutableStateOf(0f) } // px, how far it's been slid up (negative)
    // Only the entry it first composed with counts: later recompositions must not change it mid-animation.
    val entry = remember { entryNow }
    val arrival = remember { Animatable(if (entry == Entry.None) 1f else 0f) }
    LaunchedEffect(Unit) {
        when (entry) {
            Entry.None -> {}
            Entry.Rise -> {
                delay((riseDelayMillis(position) * (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f)).toLong())
                arrival.animateTo(1f, Motion.settle())
            }
            Entry.Drop -> arrival.animateTo(1f, Motion.pop())
        }
    }
    val scale by animateFloatAsState(if (pressed && !held) 0.95f else 1f, Motion.press())
    val out by animateFloatAsState(if (held) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 400f))
    // Follows the finger exactly, then springs back when let go.
    val slid by animateFloatAsState(lift, if (held) snap() else spring(dampingRatio = 0.7f, stiffness = 400f))
    val height = book.spineHeight()

    Box(
        Modifier.size(book.shelfWidth().dp, height.dp)
            .graphicsLayer {
                val rise = if (entry == Entry.Drop) -40.dp else 24.dp
                translationY = rise.toPx() * (1f - arrival.value)
                alpha = arrival.value.coerceIn(0f, 1f)
            }
            .zIndex(if (out > 0f) 1f else 0f) // over its neighbours while it's out
            .semantics(mergeDescendants = true) { role = Role.Button; onClick { onClick(); true } }
            .pointerInput(book.isbn) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    val hold = awaitLongPressOrCancellation(down.id)
                    if (hold == null) {
                        pressed = false
                        // Let go before it counted as a hold: a tap. Not when the list took the touch for a scroll.
                        if (currentEvent.changes.any { it.id == down.id && it.changedToUp() && !it.isConsumed }) onClick()
                        return@awaitEachGesture
                    }
                    held = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == hold.id } ?: break
                        if (change.changedToUp()) break
                        // Read the move before consuming it: a consumed change reports no movement.
                        lift = (lift + change.positionChange().y).coerceAtMost(0f)
                        change.consume() // so the list doesn't scroll under it
                        if (lift < -SwipeToOpen.toPx()) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onClick()
                            break
                        }
                    }
                    pressed = false
                    held = false
                    lift = 0f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Its place on the shelf empties as it comes out.
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; alpha = 1f - out }) {
            if (book.faceOut()) Cover(book, Modifier.fillMaxSize())
            else Spine(book, Modifier.fillMaxSize())
            if (book.favorite) StarShape(true, Bold.Yellow, Modifier.align(Alignment.TopCenter).padding(top = 5.dp).size(14.dp), outline = Bold.DarkInk)
        }
        if (out > 0f) Cover(
            book,
            Modifier.requiredSize((height * 2 / 3).dp, height.dp)
                .graphicsLayer {
                    translationY = -28.dp.toPx() * out + slid
                    scaleX = 0.8f + 0.35f * out
                    scaleY = 0.8f + 0.35f * out
                    rotationY = if (book.faceOut()) 0f else 90f * (1 - out) // turns from its spine to its cover; a face-out book already shows it
                    cameraDistance = 12 * density
                }
                .shadow(32.dp * out, RoundedCornerShape(6.dp)),
        )
    }
}

/** Bold cloth, with ink that reads on it. The drawn spines are covers too, so they get colour. */
private val SpineColors = listOf(
    Color(0xFF2D4BFF) to Color.White, // blue
    Color(0xFFFF5A36) to Bold.DarkInk, // tomato
    Color(0xFF3DDC97) to Bold.DarkInk, // mint
    Color(0xFFFFE8C2) to Bold.DarkInk, // cream
    Color(0xFFF4F4F5) to Bold.DarkInk, // off-white (pure white would vanish on Paper)
    Color(0xFFFFC93C) to Bold.DarkInk, // yellow
    Color(0xFF7B5CFF) to Color.White, // violet
    Color(0xFF8FD5FF) to Bold.DarkInk, // sky
    Color(0xFFFF4F9A) to Bold.DarkInk, // pink
    Color(0xFFC6F432) to Bold.DarkInk, // lime
)

private val SpineShape = RoundedCornerShape(4.dp)

/** The spine photo, or a drawn spine: title top to bottom, the author's surname at the foot. */
@Composable
fun Spine(book: Book, modifier: Modifier, photo: ByteArray? = null) {
    val dir = LocalDataDir.current
    val (cloth, ink) = SpineColors[book.isbn.hashCode().mod(SpineColors.size)]
    Box(modifier.clip(SpineShape).background(cloth).border(2.dp, Bold.Paper, SpineShape), contentAlignment = Alignment.Center) {
        val image = photo ?: book.spineFile?.let { "file://$dir/$it" }
        if (image != null) AsyncImage(image, book.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        // Starts below the favorite star; a fat spine has room for the title on two lines.
        else Row(
            Modifier.sideways().fillMaxWidth().padding(start = if (book.favorite) 24.dp else 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                book.title, color = ink, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp, maxLines = if (book.spineWidth() >= 30) 2 else 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (book.author.isNotBlank()) Text(
                book.surname.uppercase(),
                color = ink.copy(alpha = 0.7f),
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Lays the content out along the height instead of the width and turns it to read top to bottom, like a spine. */
private fun Modifier.sideways() = layout { measurable, constraints ->
    val p = measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(p.height, p.width) { p.place((p.height - p.width) / 2, (p.width - p.height) / 2) }
}.rotate(90f)
