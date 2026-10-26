package com.phuuun.tsundoku

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.foundation.Canvas
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.geometry.Offset
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.write
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import org.publicvalue.multiplatform.qrcode.CameraPermissionState
import org.publicvalue.multiplatform.qrcode.CameraPosition
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions
import kotlin.math.abs

@Composable
fun App(dataDir: String) {
    val library = remember { Library(dataDir) }
    CompositionLocalProvider(LocalDataDir provides dataDir) {
        TsundokuTheme { Shelves(library) }
    }
}

/** Where photo covers live; [Book.coverFile] is relative to it. */
val LocalDataDir = staticCompositionLocalOf { "" }

private val ShelfNames = listOf("To read", "Finished")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shelves(library: Library) {
    val colors = MaterialTheme.colorScheme
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var adding by remember { mutableStateOf(false) }
    var openIsbn by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf<Incoming?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var newIsbn by remember { mutableStateOf<String?>(null) } // the book just added, so its shelf drops it in

    // It only drops in once: clear it after the pager has had time to show it.
    LaunchedEffect(newIsbn) {
        if (newIsbn == null) return@LaunchedEffect
        delay((1500 * (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f)).toLong())
        newIsbn = null
    }

    // Export: the system's "save as" screen (Downloads, Drive, ...), then the whole library goes into that file.
    val saver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault(), onError = { problem = "Couldn't save the backup." }) { file ->
        if (file != null) scope.launch {
            try {
                file.write(library.exportBackup().encodeToByteArray())
            } catch (e: Exception) {
                problem = "Couldn't save the backup."
            }
        }
    }
    // Import: any file type, since some file managers don't tag .json; the contents decide.
    val picker = rememberFilePickerLauncher(onError = { problem = "Couldn't open that file." }) { file ->
        if (file != null) scope.launch {
            try {
                importing = readImport(file.readString()).takeIf { it.backup.books.isNotEmpty() }
                if (importing == null) problem = "There are no books in that file."
            } catch (e: Exception) {
                problem = "That file isn't a Tsundoku backup or a Goodreads or StoryGraph export."
            }
        }
    }

    // Page count and paperback-or-hardcover from Open Library, once per book: each new one, and every book from before v1.5.
    LaunchedEffect(library) {
        snapshotFlow { library.books.filter { it.size == null }.map { it.isbn } }.collect { isbns ->
            for (isbn in isbns) {
                val (pages, size) = try {
                    lookupSize(isbn) ?: (null to BookSize.Regular) // Open Library doesn't know it: a regular book
                } catch (e: Exception) {
                    continue // offline: it's asked again next launch
                }
                library.setSize(isbn, pages, size)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Bold.Paper)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                pager,
                counts = listOf(false, true).map { done -> library.books.count { it.finished == done } },
                onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                onExport = { saver.launch(suggestedName = "tsundoku-${today()}", defaultExtension = "json") },
                onImport = { picker.launch() },
            )
            SearchField(query, { query = it }, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                // ponytail: filters and sorts the whole shelf on every keystroke; instant for thousands of books, no spinner needed
                val shelf = library.books.filter { it.finished == (page == 1) && it.matches(query) }.sortedWith(ShelfOrder)
                if (shelf.isEmpty()) EmptyShelf(page, query)
                else Bookcase(shelf, newIsbn) { openIsbn = it.isbn }
            }
        }

        // The shelves fade out under the add button instead of hitting a hard edge.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Bold.Paper)))
        )
        PillButton(
            "Add book",
            onClick = { adding = true },
            big = true,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        )
    }

    if (adding) {
        Sheet(onDismiss = { adding = false }) { close ->
            AddBook(library, onAdded = { added ->
                close()
                newIsbn = added.isbn
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { pager.animateScrollToPage(if (added.finished) 1 else 0) }
            })
        }
    }

    importing?.let { incoming ->
        Sheet(onDismiss = { importing = null }) { close ->
            ImportReview(incoming, library) { replace ->
                library.import(incoming.backup, replace)
                close()
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }
    problem?.let {
        AlertDialog(
            onDismissRequest = { problem = null },
            modifier = Modifier.enterFrom().border(2.dp, Bold.Ink, RoundedCornerShape(20.dp)),
            containerColor = Bold.SurfaceHigh,
            shape = RoundedCornerShape(20.dp),
            text = { Text(it) },
            confirmButton = { TextButton({ problem = null }) { Text("OK", color = colors.onSurface) } },
        )
    }

    // Looked up live, so editing a read inside the sheet shows straight away.
    // Kept past a remove, so the sheet still has its book to show while it slides down.
    val last = remember { arrayOfNulls<Book>(1) }
    val shown = library.books.find { it.isbn == openIsbn }?.also { last[0] = it } ?: last[0]?.takeIf { it.isbn == openIsbn }
    shown?.let { book ->
        Sheet(onDismiss = { openIsbn = null }) { close ->
            BookDetails(
                book,
                onFinish = { read ->
                    library.finish(book, read)
                    close()
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch { pager.animateScrollToPage(1) }
                },
                onReread = {
                    library.reread(book)
                    close()
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch { pager.animateScrollToPage(0) }
                },
                onEditRead = { index, read -> library.editRead(book, index, read) },
                onAddRead = { read -> library.addRead(book, read) },
                onEditDetails = { draft ->
                    library.editDetails(
                        book, draft.title.trim().ifEmpty { book.title }, draft.author.trim(), draft.photo, draft.removePhoto,
                        draft.spine, draft.spineAspect, draft.removeSpine, draft.size,
                    )
                },
                onFavorite = {
                    library.setFavorite(book, !book.favorite)
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                },
                onShowCover = {
                    library.setShowCover(book, it)
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
                onDeleteRead = { index ->
                    library.deleteRead(book, index)
                    // Deleting the only read sends the book back to To read; follow it there.
                    if (book.finished && book.reads.size == 1) {
                        close()
                        scope.launch { pager.animateScrollToPage(0) }
                    }
                },
                onRemove = {
                    library.remove(book)
                    close()
                },
            )
        }
    }
}

/** The wordmark and library menu, then the big tab title with its count sticker, the tab chips, all following the pager. */
@Composable
private fun Header(pager: PagerState, counts: List<Int>, onSelect: (Int) -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("tsundoku", color = Bold.Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            LibraryMenu(onExport, onImport)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTitle(position)
            CountSticker(counts[pager.settledPage])
        }
        Spacer(Modifier.height(14.dp))
        PillSwitch(ShelfNames, position, onSelect, Modifier.fillMaxWidth())
    }
}

/** Both titles, each sliding and fading by how far the pager is from its page; the row is as wide as the one showing. */
@Composable
private fun BigTitle(position: Float) {
    Layout(content = {
        ShelfNames.forEachIndexed { i, name ->
            Text(
                name, style = MaterialTheme.typography.headlineLarge, color = Bold.Ink, maxLines = 1, softWrap = false,
                modifier = Modifier.graphicsLayer {
                    translationX = (i - position) * 60.dp.toPx()
                    alpha = (1f - abs(i - position)).coerceIn(0f, 1f)
                },
            )
        }
    }) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = (placeables[0].width + (placeables[1].width - placeables[0].width) * position).roundToInt()
        layout(width, placeables.maxOf { it.height }) { placeables.forEach { it.placeRelative(0, 0) } }
    }
}

/** The pink tilted number: pops and wobbles whenever [count] changes. */
@Composable
private fun CountSticker(count: Int) {
    val scale = remember { Animatable(1f) }
    val tilt = remember { Animatable(-10f) }
    var shown by remember { mutableStateOf(count) }
    LaunchedEffect(count) {
        if (count == shown) return@LaunchedEffect
        shown = count
        scale.snapTo(1.25f)
        tilt.snapTo(-18f)
        launch { scale.animateTo(1f, Motion.pop()) }
        tilt.animateTo(-10f, Motion.pop())
    }
    Box(
        Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = tilt.value }
            .defaultMinSize(minWidth = 38.dp, minHeight = 38.dp)
            .background(Bold.Pink, CircleShape)
            .border(2.dp, Bold.Paper, CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", color = Bold.DarkInk, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** The round ⋯ button top right: export and import the library. The Instagram card can go here later. */
@Composable
private fun LibraryMenu(onExport: () -> Unit, onImport: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box {
        Box(
            Modifier.size(40.dp).pressScale(pressed).clip(CircleShape).background(Bold.Ink)
                .clickable(source, indication = null, role = Role.Button, onClickLabel = "Library menu") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(width = 16.dp, height = 4.dp)) {
                val r = size.height / 2
                listOf(r, size.width / 2, size.width - r).forEach { x -> drawCircle(Bold.Paper, r, Offset(x, r)) }
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Bold.SurfaceHigh,
            border = BorderStroke(2.dp, Bold.Ink),
        ) {
            DropdownMenuItem(text = { Text("Export library") }, onClick = { open = false; onExport() })
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Import library")
                        Text("Backup, Goodreads or StoryGraph", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                },
                onClick = { open = false; onImport() },
            )
        }
    }
}

/**
 * What's in the file, and for every book you already have: keep yours or take the file's. Tap a row to flip it; the two
 * buttons flip them all.
 */
@Composable
private fun ImportReview(incoming: Incoming, library: Library, onImport: (replace: Set<String>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val books = remember(incoming) { incoming.backup.books.distinctBy { it.isbn } }
    val clashes = remember(incoming) { books.mapNotNull { theirs -> library.books.find { it.isbn == theirs.isbn }?.let { it to theirs } } }
    var replace by remember(incoming) { mutableStateOf(emptySet<String>()) }
    val added = books.size - clashes.size

    Column(Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        Text("IMPORT FROM ${incoming.source.uppercase()}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Text(if (books.size == 1) "1 book" else "${books.size} books", style = MaterialTheme.typography.headlineSmall)
        Text(
            when {
                clashes.isEmpty() -> "They'll all be added to your shelf."
                clashes.size == 1 -> "1 is already on your shelf. Keep yours, or replace it with this one?"
                else -> "${clashes.size} are already on your shelf. Keep yours, or replace them with these?"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (clashes.isNotEmpty()) {
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinePill("Keep all mine", { replace = emptySet() }, Modifier.weight(1f))
                OutlinePill("Replace all", { replace = clashes.map { it.first.isbn }.toSet() }, Modifier.weight(1f))
            }
            LazyColumn(Modifier.weight(1f, fill = false).padding(top = 8.dp)) {
                items(clashes, key = { it.first.isbn }) { (mine, theirs) ->
                    val swap = mine.isbn in replace
                    val flip = { replace = if (swap) replace - mine.isbn else replace + mine.isbn }
                    val source = remember { MutableInteractionSource() }
                    val pressed by source.collectIsPressedAsState()
                    val mineColor by animateColorAsState(if (swap) colors.onSurfaceVariant else colors.onSurface, Motion.settle())
                    val theirColor by animateColorAsState(if (swap) colors.onSurface else colors.onSurfaceVariant, Motion.settle())
                    HorizontalDivider(color = Bold.QuietLine)
                    Row(
                        Modifier.fillMaxWidth().pressScale(pressed)
                            .clickable(source, indication = null, onClick = flip)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Cover(mine, Modifier.width(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mine.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Yours: ${mine.status()}", style = MaterialTheme.typography.bodyMedium, color = mineColor)
                            Text("File: ${theirs.status()}", style = MaterialTheme.typography.bodyMedium, color = theirColor)
                        }
                        ChoicePill(if (swap) "REPLACE" else "KEEP MINE", chosen = swap, onClick = flip)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        val swapped = replace.size
        PillButton(
            when {
                added > 0 && swapped > 0 -> "Add $added, replace $swapped"
                added > 0 -> if (added == 1) "Add 1 book" else "Add $added books"
                swapped > 0 -> "Replace $swapped"
                else -> "Nothing to import"
            },
            { onImport(replace) },
            Modifier.fillMaxWidth(),
            enabled = added + swapped > 0,
        )
    }
}

/** "Finished · 5★ · 2 reads", "Rereading", "To read" */
private fun Book.status() = when {
    finished -> buildString {
        append("Finished")
        lastRead?.rating?.let { append(" · $it★") }
        if (reads.size > 1) append(" · ${reads.size} reads")
    }
    rereading -> "Rereading"
    else -> "To read"
}

@Composable
private fun EmptyShelf(page: Int, query: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                query.isNotBlank() -> "No matches"
                page == 0 -> "Nothing to read"
                else -> "Nothing finished yet"
            },
            color = Bold.Ink,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                query.isNotBlank() -> "Nothing on this shelf matches “${query.trim()}”."
                page == 0 -> "Add the book on your nightstand.\nThe ISBN is on the back cover."
                else -> "Tap a book when you're done with it\nand it lands here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The title sits underneath the image, so a missing or broken cover still reads as the book. */
@Composable
fun Cover(book: Book, modifier: Modifier, photo: ByteArray? = null) {
    val colors = MaterialTheme.colorScheme
    val dir = LocalDataDir.current
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceVariant)
            .border(2.dp, Bold.Paper, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            book.title,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(10.dp),
        )
        AsyncImage(
            model = photo ?: book.coverFile?.let { "file://$dir/$it" } ?: book.coverUrl,
            contentDescription = book.title,
            // Your own photo shows exactly as you cropped it (squeezed a little if it isn't 2:3); Open Library's is trimmed to fit.
            contentScale = if (photo != null || book.coverFile != null) ContentScale.FillBounds else ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** An error line that expands in and fades; it keeps its last message while collapsing. */
@Composable
private fun ErrorText(message: String?) {
    val last = remember { arrayOf(message) }
    if (message != null) last[0] = message
    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically(Motion.settle()) + fadeIn(Motion.settle()),
        exit = shrinkVertically(Motion.settle()) + fadeOut(Motion.settle()),
    ) { Text(last[0].orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Bold.Error) }
}

private enum class Step { Scan, Type, Found, Manual, Rate }

@Composable
private fun AddBook(library: Library, onAdded: (Book) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(Step.Scan) }
    var book by remember { mutableStateOf(Book("", "", "")) }
    var lastScan by remember { mutableStateOf<String?>(null) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var wrongMatch by remember { mutableStateOf(false) } // Open Library found a book, but you said it's not this one
    val focusManager = LocalFocusManager.current

    fun add(finished: Boolean, read: Read? = null) {
        val added = book.copy(title = book.title.trim(), author = book.author.trim(), finished = finished, reads = listOfNotNull(read))
        library.add(added, photo)
        onAdded(added)
    }

    fun search(raw: String) {
        val isbn = cleanIsbn(raw)
        error = when {
            isbn == null -> "An ISBN is 10 or 13 digits."
            library.has(isbn) -> "That one's already on your shelf."
            else -> null
        }
        if (isbn == null || error != null) return
        loading = true
        scope.launch {
            try {
                val found = lookup(isbn)
                book = found ?: Book(isbn, "", "")
                focusManager.clearFocus() // otherwise focus hops from the vanishing ISBN field to the next thing and lights it up
                step = if (found != null) Step.Found else Step.Manual
            } catch (e: Exception) {
                error = "Couldn't reach Open Library. Check your connection."
                scope.launch { delay(3_000); lastScan = null } // then the barcode still in front of the camera tries again
            }
            loading = false
        }
    }

    // The camera reports the same barcode every frame; only act when it changes.
    fun scanned(code: String): Boolean {
        if (code == lastScan || loading) return false
        lastScan = code
        if (code.startsWith("978") || code.startsWith("979")) search(code)
        else error = "That's not the ISBN barcode. Books sometimes have a second one for the price."
        return false // keep the camera running; the sheet moves on once the lookup lands
    }

    Column(
        Modifier.verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp).navigationBarsPadding().imePadding()
    ) {
        Text("ADD A BOOK", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))

        AnimatedContent(
            step,
            transitionSpec = {
                // Forward comes in from the right, back from the left.
                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (slideInHorizontally(Motion.settle()) { dir * it / 3 } + fadeIn(Motion.settle()))
                    .togetherWith(slideOutHorizontally(Motion.settle()) { -dir * it / 3 } + fadeOut(Motion.settle()))
                    .using(SizeTransform(clip = false) { _, _ -> Motion.settle() })
            },
        ) { current ->
            Column(Modifier.blockWhileLeaving(this), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (current) {
                    Step.Scan -> {
                        Text("Scan the barcode", style = MaterialTheme.typography.headlineSmall)
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            ScannerWithPermissions(
                                modifier = Modifier.fillMaxSize(),
                                onScanned = ::scanned,
                                types = listOf(CodeType.EAN13),
                                cameraPosition = CameraPosition.BACK,
                                enableTorch = false,
                                permissionDeniedContent = { CameraOff(it) },
                            )
                            if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                        }
                        ErrorText(error)
                        TextButton({ step = Step.Type; error = null }, Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Type the ISBN instead", color = colors.onSurfaceVariant)
                        }
                    }

                    Step.Type -> {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        Text("What's the ISBN?", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "It's on the back cover, right above the barcode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        val source = remember { MutableInteractionSource() }
                        val focused by source.collectIsFocusedAsState()
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it; error = null },
                            placeholder = { Text("978…", color = Bold.Placeholder) },
                            textStyle = TextStyle(fontSize = 22.sp, letterSpacing = 2.sp, color = Bold.Ink),
                            singleLine = true,
                            shape = FieldShape,
                            colors = bareFieldColors(),
                            interactionSource = source,
                            // ponytail: number pad can't type the X some old ISBN-10s end in; their barcodes carry an ISBN-13 anyway
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search(input) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus).fieldFrame(focused),
                        )
                        ErrorText(error)
                        PillButton("Look it up", { search(input) }, Modifier.fillMaxWidth(), enabled = input.isNotBlank(), loading = loading)
                    }

                    Step.Found -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cover(book, Modifier.width(84.dp).shadow(12.dp, RoundedCornerShape(4.dp)))
                            Column {
                                Text(book.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                        ReadYet(onNotYet = { add(finished = false) }, onYes = { step = Step.Rate })
                        TextButton(
                            // Its cover is the wrong book's too.
                            { book = book.copy(coverUrl = null); wrongMatch = true; step = Step.Manual },
                            Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("Not this one? Type it in", color = colors.onSurfaceVariant)
                        }
                    }

                    Step.Manual -> {
                        Text("Type it in", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (wrongMatch) "Fix the title and author. Tap the cover to add a picture of it."
                            else "Open Library doesn't know this one. Tap the cover to add a picture of it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CoverPicker(Modifier.width(84.dp), spine = false, onPhoto = { jpeg, _ -> photo = jpeg }) {
                                val shot = photo
                                if (shot != null) Cover(book, Modifier.fillMaxWidth(), photo = shot)
                                else Box(
                                    Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                                        .border(2.dp, Bold.QuietLine, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "ADD\nCOVER",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Field(book.title, { book = book.copy(title = it) }, "Title")
                                Field(book.author, { book = book.copy(author = it) }, "Author")
                            }
                        }
                        ErrorText(error)
                        ReadYet(
                            onNotYet = { add(finished = false) },
                            onYes = { step = Step.Rate },
                            enabled = book.title.isNotBlank(),
                        )
                    }

                    Step.Rate -> RateBook(book, photo, Read(today()), "How was it?", "Add to Finished") { add(true, it) }
                }
            }
        }
    }
}

@Composable
private fun ReadYet(onNotYet: () -> Unit, onYes: () -> Unit, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "HAVE YOU READ IT?",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinePill("Not yet", onNotYet, Modifier.weight(1f), enabled)
            PillButton("Yes", onYes, Modifier.weight(1f), enabled)
        }
    }
}

/**
 * One read: when you finished it, stars and a review. Stars and review are optional: an empty review is saved as none,
 * and tapping the lit star again clears the rating. [onDelete] adds a "Delete this read" button.
 */
@Composable
private fun RateBook(
    book: Book,
    photo: ByteArray?,
    initial: Read,
    heading: String,
    button: String,
    onDelete: (() -> Unit)? = null,
    onDone: (Read) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var finishedOn by remember { mutableStateOf(initial.finishedOn) }
    var rating by remember { mutableStateOf(initial.rating) }
    var review by remember { mutableStateOf(initial.review.orEmpty()) }
    var picking by remember { mutableStateOf(false) }

    if (picking) PickDate(finishedOn, onPick = { finishedOn = it }, onDismiss = { picking = false })

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(book, Modifier.width(44.dp), photo = photo)
            Column {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        Text(heading, style = MaterialTheme.typography.headlineSmall)
        Stars(rating, size = 40.sp) { rating = it }
        val reviewSource = remember { MutableInteractionSource() }
        val reviewFocused by reviewSource.collectIsFocusedAsState()
        OutlinedTextField(
            value = review,
            onValueChange = { review = it },
            placeholder = { Text("Write a review, or don't", color = Bold.Placeholder) },
            minLines = 3,
            maxLines = 8,
            shape = FieldShape,
            colors = bareFieldColors(),
            interactionSource = reviewSource,
            modifier = Modifier.fillMaxWidth().fieldFrame(reviewFocused),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FINISHED ON", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            OutlinePill(finishedOn.pretty(), { picking = true })
        }
        PillButton(button, { onDone(Read(finishedOn, rating, review.trim().ifEmpty { null })) }, Modifier.fillMaxWidth())
        if (onDelete != null) TapTwice("Delete this read", "Tap again to delete this read", onDelete, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** A quiet text button that wants a second tap: the first fades it to Error and shakes it. */
@Composable
private fun TapTwice(label: String, armedLabel: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    var armed by remember { mutableStateOf(false) }
    var shakes by remember { mutableIntStateOf(0) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val color by animateColorAsState(if (armed) Bold.Error else Bold.Muted, Motion.settle())
    Text(
        if (armed) armedLabel else label,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier.shake(shakes).pressScale(pressed).clip(CircleShape)
            .clickable(source, indication = null, role = Role.Button) { if (armed) onConfirm() else { armed = true; shakes++ } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

private val Months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** 30 Sep 2026 */
private fun LocalDate.pretty() = "$day ${Months[month.ordinal]} $year"

/** Material's calendar, up to today. It speaks UTC-midnight millis, so dates go in and out through UTC. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun PickDate(date: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayMillis = today().atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    val pickerColors = DatePickerDefaults.colors(
        containerColor = Bold.SurfaceHigh,
        titleContentColor = Bold.Muted,
        headlineContentColor = Bold.Ink,
        weekdayContentColor = Bold.Muted,
        subheadContentColor = Bold.Muted,
        navigationContentColor = Bold.Ink,
        yearContentColor = Bold.Ink,
        currentYearContentColor = Bold.Ink,
        selectedYearContainerColor = Bold.Tomato,
        selectedYearContentColor = Bold.DarkInk,
        dayContentColor = Bold.Ink,
        disabledDayContentColor = Bold.QuietLine,
        selectedDayContainerColor = Bold.Tomato,
        selectedDayContentColor = Bold.DarkInk,
        todayContentColor = Bold.Ink,
        todayDateBorderColor = Bold.Ink,
    )
    val shape = RoundedCornerShape(20.dp)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                state.selectedDateMillis?.let { onPick(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
                onDismiss()
            }) { Text("Done", color = Bold.Ink) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Bold.Muted) } },
        shape = shape,
        colors = pickerColors,
        modifier = Modifier.enterFrom().border(2.dp, Bold.Ink, shape),
    ) {
        val type = MaterialTheme.typography
        MaterialTheme(typography = type.copy(headlineLarge = type.headlineSmall)) { // the picker's headline reads headlineLarge, the 50sp tab title
            DatePicker(state, colors = pickerColors, showModeToggle = false)
        }
    }
}

/** Shown in the viewfinder until the camera is allowed (also behind the system's permission prompt). */
@Composable
private fun CameraOff(permission: CameraPermissionState) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CAMERA IS OFF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Tsundoku needs it to read the barcode.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        PillButton("Allow camera", permission::requestCameraPermission)
        // Once someone taps "Don't allow" twice, Android stops asking; only settings can turn it back on.
        TextButton(permission::goToSettings) { Text("Open settings", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Title, author, size, cover and spine as they are while you edit; saved only when you tap Save. */
private data class Draft(
    val title: String,
    val author: String,
    val size: BookSize,
    val photo: ByteArray? = null,
    val removePhoto: Boolean = false,
    val spine: ByteArray? = null,
    val spineAspect: Float? = null,
    val removeSpine: Boolean = false,
)

/** Which screen the book sheet shows; [depth] sets the slide direction. */
private sealed interface BookScreen {
    val depth: Int

    data object Main : BookScreen { override val depth = 0 }
    /** [draft] is only where editing began; the live one is BookDetails' `draft`. */
    data class Edit(val draft: Draft) : BookScreen { override val depth = 1 }
    data object Finish : BookScreen { override val depth = 2 }
    data class EditRead(val index: Int, val read: Read) : BookScreen { override val depth = 2 }
    data object AddRead : BookScreen { override val depth = 2 }
}

/** The book sheet: the book itself, and the screens that slide in from it (edit, finish, edit a read, add a read). */
@Composable
private fun BookDetails(
    book: Book,
    onFinish: (Read) -> Unit,
    onReread: () -> Unit,
    onEditRead: (index: Int, Read) -> Unit,
    onDeleteRead: (index: Int) -> Unit,
    onAddRead: (Read) -> Unit,
    onFavorite: () -> Unit,
    onShowCover: (Boolean) -> Unit,
    onEditDetails: (Draft) -> Unit,
    onRemove: () -> Unit,
) {
    var screen by remember { mutableStateOf<BookScreen>(BookScreen.Main) }
    var draft by remember { mutableStateOf<Draft?>(null) } // non-null while the pen's edit mode is on; outlives a trip to a read
    fun back() { screen = draft?.let { BookScreen.Edit(it) } ?: BookScreen.Main }

    val form = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()
        .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
    AnimatedContent(
        screen,
        transitionSpec = {
            // Deeper comes in from the right, shallower from the left.
            val dir = if (targetState.depth >= initialState.depth) 1 else -1
            (slideInHorizontally(Motion.settle()) { dir * it / 3 } + fadeIn(Motion.settle()))
                .togetherWith(slideOutHorizontally(Motion.settle()) { -dir * it / 3 } + fadeOut(Motion.settle()))
                .using(SizeTransform(clip = false) { _, _ -> Motion.settle() })
        },
        contentKey = { it::class }, // typing in the edit screen changes the draft, not the screen
    ) { current ->
        Box(Modifier.blockWhileLeaving(this)) { when (current) {
            BookScreen.Main -> BookMain(
                book, onFinish = { screen = BookScreen.Finish }, onReread, onFavorite, onShowCover,
                onEdit = { val d = Draft(book.title, book.author, book.size ?: BookSize.Regular); draft = d; screen = BookScreen.Edit(d) },
                onRemove,
            )
            is BookScreen.Edit -> Box(form) {
                val live = draft ?: current.draft // null once saved or cancelled, while this screen slides out
                EditBook(
                    book, live,
                    onChange = { draft = it; screen = BookScreen.Edit(it) }, // keeps the leaving screen on the last typed draft
                    onEditRead = { index -> screen = BookScreen.EditRead(index, book.reads[index]) },
                    onAddRead = { screen = BookScreen.AddRead },
                    onCancel = { draft = null; screen = BookScreen.Main },
                    onSave = { onEditDetails(live); draft = null; screen = BookScreen.Main },
                )
            }
            BookScreen.Finish -> Box(form) {
                RateBook(book, null, Read(today()), if (book.rereading) "How was it this time?" else "How was it?", "Add to Finished", onDone = onFinish)
            }
            is BookScreen.EditRead -> Box(form) {
                RateBook(
                    book, null, current.read, "Edit this read", "Save",
                    onDelete = { onDeleteRead(current.index); back() },
                ) { onEditRead(current.index, it); back() }
            }
            BookScreen.AddRead -> Box(form) {
                RateBook(book, null, Read(today()), "Add a read", "Add read") { onAddRead(it); back() }
            }
        } }
    }
}

/** The favourite star: pops and turns a quarter when switched on. */
@Composable
private fun FavoriteStar(favorite: Boolean) {
    val scale = remember { Animatable(1f) }
    val turn = remember { Animatable(0f) }
    var was by remember { mutableStateOf(favorite) }
    LaunchedEffect(favorite) {
        if (favorite && !was) {
            scale.snapTo(1.4f); turn.snapTo(-90f)
            launch { turn.animateTo(0f, Motion.pop()) }
            scale.animateTo(1f, Motion.pop())
        }
        was = favorite
    }
    StarShape(
        filled = favorite,
        color = if (favorite) Bold.Yellow else Bold.Ink,
        outline = if (favorite) Bold.DarkInk else null,
        modifier = Modifier.size(17.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = turn.value },
    )
}

/** The sheet's first screen: cover, how it stands, status, reads, and the actions. */
@Composable
private fun BookMain(
    book: Book,
    onFinish: () -> Unit,
    onReread: () -> Unit,
    onFavorite: () -> Unit,
    onShowCover: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Cover(book, Modifier.align(Alignment.TopCenter).width(128.dp).enterFrom(scale = 0.9f, offsetY = 8.dp).shadow(24.dp, RoundedCornerShape(6.dp)))
            RoundButton(if (book.favorite) "Unfavorite" else "Favorite", Modifier.align(Alignment.TopStart), onClick = onFavorite) {
                FavoriteStar(book.favorite)
            }
            RoundButton("Edit book", Modifier.align(Alignment.TopEnd), onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
            }
        }
        // How it stands on the shelf, changed straight away.
        Spacer(Modifier.height(20.dp))
        val face by animateFloatAsState(if (book.faceOut()) 1f else 0f, Motion.settle())
        PillSwitch(listOf("Spine", "Cover"), face, { onShowCover(it == 1) }, Modifier.width(200.dp))
        Spacer(Modifier.height(24.dp))
        StatusSticker(
            when {
                book.finished && book.reads.size > 1 -> "FINISHED · READ ${book.reads.size}×"
                book.finished -> "FINISHED"
                book.rereading -> "REREADING"
                else -> "TO READ"
            },
            if (book.finished) Bold.Pink else Bold.Mint,
        )
        Spacer(Modifier.height(8.dp))
        Text(book.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (book.author.isNotEmpty()) Text(
            book.author,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (book.reads.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text(
                if (book.reads.size == 1) "YOUR READ" else "YOUR READS",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            book.reads.reversed().forEach { read ->
                HorizontalDivider(Modifier.padding(top = 12.dp), color = colors.outline)
                ReadRow(read)
            }
        }

        Spacer(Modifier.height(28.dp))
        if (book.finished) PillButton("Read it again", onReread, Modifier.fillMaxWidth())
        else PillButton("Finished it", onFinish, Modifier.fillMaxWidth())
        TapTwice("Remove from shelf", "Tap again to remove", onRemove, Modifier.padding(top = 8.dp))
    }
}

/** The pen's edit mode: spine, cover, title, author and size (saved with Save), and every read (each saved on its own). */
@Composable
private fun EditBook(
    book: Book,
    draft: Draft,
    onChange: (Draft) -> Unit,
    onEditRead: (index: Int) -> Unit,
    onAddRead: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var cover by remember { mutableStateOf(false) } // the switch: spine first, like on the shelf
    val position by animateFloatAsState(if (cover) 1f else 0f, Motion.settle())
    // What the cover and spine will be after Save.
    val preview = book.copy(
        coverFile = book.coverFile.takeUnless { draft.removePhoto },
        spineFile = book.spineFile.takeUnless { draft.removeSpine },
        spineAspect = draft.spineAspect ?: book.spineAspect.takeUnless { draft.removeSpine },
        size = draft.size,
    )
    val hasPhoto = draft.photo != null || preview.coverFile != null
    val hasSpine = draft.spine != null || preview.spineFile != null

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EDIT BOOK", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onCancel) { Text("Cancel", color = colors.onSurfaceVariant) }
        }
        PillSwitch(listOf("Spine", "Cover"), position, { cover = it == 1 }, Modifier.width(200.dp).align(Alignment.CenterHorizontally))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // At its real proportions, standing in a box as tall as a Tall book, so the sizes visibly differ.
                val height = 126 * draft.size.height
                Box(Modifier.height(126.dp), contentAlignment = Alignment.BottomCenter) {
                    if (cover) CoverPicker(Modifier.width((height * 2 / 3).dp), spine = false, onPhoto = { jpeg, _ -> onChange(draft.copy(photo = jpeg, removePhoto = false)) }) {
                        Cover(preview, Modifier.fillMaxWidth(), photo = draft.photo)
                    } else CoverPicker(Modifier, spine = true, onPhoto = { jpeg, shape -> onChange(draft.copy(spine = jpeg, spineAspect = shape, removeSpine = false)) }) {
                        Spine(preview, Modifier.height(height.dp).width((height * preview.spineWidth() / preview.spineHeight()).dp), photo = draft.spine)
                    }
                }
                Text(
                    "TAP TO CHANGE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(draft.title, { onChange(draft.copy(title = it)) }, "Title")
                Field(draft.author, { onChange(draft.copy(author = it)) }, "Author")
                if (cover && hasPhoto) TextButton({ onChange(draft.copy(photo = null, removePhoto = true)) }) {
                    Text(
                        if (book.coverUrl != null) "Use the Open Library cover" else "Remove photo",
                        color = colors.onSurfaceVariant,
                    )
                }
                if (!cover && hasSpine) TextButton({ onChange(draft.copy(spine = null, spineAspect = null, removeSpine = true)) }) {
                    Text("Remove spine photo", color = colors.onSurfaceVariant)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookSize.entries.forEach { size ->
                ChoicePill(size.name, size == draft.size, { onChange(draft.copy(size = size)) }, Modifier.weight(1f))
            }
        }
        Text("YOUR READS", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        // Newest first; tap one to change its date, stars or review, or to delete it.
        book.reads.withIndex().reversed().forEach { (index, read) ->
            HorizontalDivider(color = colors.outline)
            ReadRow(read) { onEditRead(index) }
        }
        OutlinePill("Add a read", onAddRead, Modifier.fillMaxWidth())
        PillButton("Save", onSave, Modifier.fillMaxWidth(), enabled = draft.title.isNotBlank())
    }
}

/** One read; tappable with an "Edit" on the right only in edit mode. */
@Composable
private fun ReadRow(read: Read, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(pressed).clickable(source, indication = null, onClick = onClick) else Modifier)
            .padding(top = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(read.finishedOn.pretty(), style = MaterialTheme.typography.labelLarge)
                if (read.rating != null) Stars(read.rating, size = 13.sp)
            }
            if (read.review != null) Text(read.review, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (onClick != null) Text("Edit", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
    }
}
