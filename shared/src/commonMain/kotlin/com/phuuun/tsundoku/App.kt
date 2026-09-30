package com.phuuun.tsundoku

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
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
private val LocalDataDir = staticCompositionLocalOf { "" }

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
    var importing by remember { mutableStateOf<Incoming?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

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

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                pager,
                onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                onExport = { saver.launch(suggestedName = "tsundoku-${today()}", defaultExtension = "json") },
                onImport = { picker.launch() },
            )
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                val shelf =
                    if (page == 0) library.books.filter { !it.finished }
                    else library.books.filter { it.finished }.sortedByDescending { it.lastRead?.finishedOn }
                if (shelf.isEmpty()) EmptyShelf(page)
                else LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shelf, key = { it.isbn }) { book ->
                        ShelfBook(book, Modifier.animateItem()) { openIsbn = book.isbn }
                    }
                }
            }
        }

        // The grid fades out under the add button instead of hitting a hard edge.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background)))
        )
        PillButton(
            "Add book",
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        )
    }

    if (adding) {
        ModalBottomSheet(onDismissRequest = { adding = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surfaceContainerLow, dragHandle = null) {
            DragHandle()
            AddBook(library, onAdded = { finished ->
                adding = false
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { pager.animateScrollToPage(if (finished) 1 else 0) }
            })
        }
    }

    importing?.let { incoming ->
        ModalBottomSheet(onDismissRequest = { importing = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surfaceContainerLow, dragHandle = null) {
            DragHandle()
            ImportReview(incoming, library) { replace ->
                library.import(incoming.backup, replace)
                importing = null
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }
    problem?.let {
        AlertDialog(
            onDismissRequest = { problem = null },
            containerColor = colors.surfaceContainerHigh,
            text = { Text(it) },
            confirmButton = { TextButton({ problem = null }) { Text("OK", color = colors.onSurface) } },
        )
    }

    // Looked up live, so editing a read inside the sheet shows straight away.
    library.books.find { it.isbn == openIsbn }?.let { book ->
        ModalBottomSheet(onDismissRequest = { openIsbn = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surfaceContainerLow, dragHandle = null) {
            DragHandle()
            BookDetails(
                book,
                onFinish = { read ->
                    library.finish(book, read)
                    openIsbn = null
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch { pager.animateScrollToPage(1) }
                },
                onReread = {
                    library.reread(book)
                    openIsbn = null
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch { pager.animateScrollToPage(0) }
                },
                onEditRead = { index, read -> library.editRead(book, index, read) },
                onAddRead = { read -> library.addRead(book, read) },
                onEditDetails = { title, author, photo, removePhoto -> library.editDetails(book, title, author, photo, removePhoto) },
                onDeleteRead = { index ->
                    library.deleteRead(book, index)
                    // Deleting the only read sends the book back to To read; follow it there.
                    if (book.finished && book.reads.size == 1) {
                        openIsbn = null
                        scope.launch { pager.animateScrollToPage(0) }
                    }
                },
                onRemove = {
                    library.remove(book)
                    openIsbn = null
                },
            )
        }
    }
}

/** TSUNDOKU eyebrow over the pill switch, both centred, with the library menu on the right. */
@Composable
private fun Header(pager: PagerState, onSelect: (Int) -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text("TSUNDOKU", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Box(Modifier.align(Alignment.CenterEnd)) { LibraryMenu(onExport, onImport) }
        }
        Spacer(Modifier.height(10.dp))

        // Pill switch: the white pill slides with your finger, not after the swipe ends.
        Box(
            Modifier.width(240.dp).height(40.dp)
                .border(1.dp, colors.outline, CircleShape)
                .padding(4.dp)
        ) {
            Box(
                Modifier.fillMaxWidth(0.5f).fillMaxHeight()
                    .graphicsLayer { translationX = size.width * position }
                    .background(colors.primary, CircleShape)
            )
            Row(Modifier.matchParentSize()) {
                ShelfNames.forEachIndexed { i, name ->
                    val selected = 1 - abs(position - i).coerceIn(0f, 1f)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            name.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = lerp(colors.onSurfaceVariant, colors.onPrimary, selected),
                        )
                    }
                }
            }
        }
    }
}

/** The round ⋯ button top right: export and import the library. The Instagram card can go here later. */
@Composable
private fun LibraryMenu(onExport: () -> Unit, onImport: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(36.dp).clip(CircleShape).border(1.dp, colors.outline, CircleShape).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(width = 16.dp, height = 4.dp)) {
                val r = size.height / 2
                listOf(r, size.width / 2, size.width - r).forEach { x -> drawCircle(colors.onSurface, r, Offset(x, r)) }
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = colors.surfaceContainerHigh,
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
                    HorizontalDivider(color = colors.outline)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { replace = if (swap) replace - mine.isbn else replace + mine.isbn }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Cover(mine, Modifier.width(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mine.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Yours: ${mine.status()}", style = MaterialTheme.typography.bodyMedium, color = if (swap) colors.onSurfaceVariant else colors.onSurface)
                            Text("File: ${theirs.status()}", style = MaterialTheme.typography.bodyMedium, color = if (swap) colors.onSurface else colors.onSurfaceVariant)
                        }
                        Text(
                            if (swap) "REPLACE" else "KEEP MINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (swap) colors.onPrimary else colors.onSurface,
                            modifier = Modifier
                                .background(if (swap) colors.primary else Color.Transparent, CircleShape)
                                .border(1.dp, if (swap) colors.primary else colors.outlineVariant, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
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
private fun EmptyShelf(page: Int) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (page == 0) "NOTHING TO READ" else "NOTHING FINISHED YET",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (page == 0) "Add the book on your nightstand.\nThe ISBN is on the back cover."
            else "Tap a book when you're done with it\nand it lands here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShelfBook(book: Book, modifier: Modifier, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f))

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = press, indication = null, onClick = onClick)
    ) {
        Box {
            Cover(book, Modifier.fillMaxWidth())
            if (book.rereading) Text(
                "REREAD",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = Color.White,
                modifier = Modifier.padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(
            book.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (book.author.isNotEmpty()) Text(
            book.author,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The title sits underneath the image, so a missing or broken cover still reads as the book. */
@Composable
private fun Cover(book: Book, modifier: Modifier, corner: Int = 4, photo: ByteArray? = null) {
    val colors = MaterialTheme.colorScheme
    val dir = LocalDataDir.current
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(corner.dp))
            .background(colors.surfaceVariant)
            .border(0.5.dp, colors.outline, RoundedCornerShape(corner.dp)),
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
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// Drawn inside the sheet: Material's handle slot wraps it in a focusable box that turns grey once a text field goes away.
@Composable
private fun DragHandle() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.padding(top = 12.dp, bottom = 20.dp).size(width = 36.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 32.dp),
        modifier = modifier.height(52.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        else Text(text, fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.height(52.dp),
    ) {
        Text(text, fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = colors.onSurfaceVariant) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = colors.outline,
            focusedBorderColor = colors.onSurfaceVariant,
            cursorColor = colors.onSurface,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

private enum class Step { Scan, Type, Found, Manual, Rate }

@Composable
private fun AddBook(library: Library, onAdded: (finished: Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(Step.Scan) }
    var book by remember { mutableStateOf(Book("", "", "")) }
    var lastScan by remember { mutableStateOf<String?>(null) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    val focusManager = LocalFocusManager.current

    fun add(finished: Boolean, read: Read? = null) {
        library.add(book.copy(title = book.title.trim(), author = book.author.trim(), finished = finished, reads = listOfNotNull(read)), photo)
        onAdded(finished)
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

        AnimatedContent(step, transitionSpec = { fadeIn() togetherWith fadeOut() }) { current ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
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
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it; error = null },
                            placeholder = { Text("978…", color = colors.outlineVariant) },
                            textStyle = TextStyle(fontSize = 22.sp, letterSpacing = 2.sp, color = colors.onSurface),
                            singleLine = true,
                            isError = error != null,
                            supportingText = error?.let { { Text(it) } },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = colors.outline,
                                focusedBorderColor = colors.onSurfaceVariant,
                                cursorColor = colors.onSurface,
                            ),
                            // ponytail: number pad can't type the X some old ISBN-10s end in; their barcodes carry an ISBN-13 anyway
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search(input) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
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
                        TextButton({ step = Step.Manual }, Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Not this one? Type it in", color = colors.onSurfaceVariant)
                        }
                    }

                    Step.Manual -> {
                        Text("Type it in", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Open Library doesn't know this one. Tap the cover to add a picture of it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CoverPicker(Modifier.width(84.dp), onCover = { photo = it }) {
                                val shot = photo
                                if (shot != null) Cover(book, Modifier.fillMaxWidth(), photo = shot)
                                else Box(
                                    Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(4.dp)),
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
                        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
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
    var confirmDelete by remember { mutableStateOf(false) }

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
        OutlinedTextField(
            value = review,
            onValueChange = { review = it },
            placeholder = { Text("Write a review, or don't", color = colors.onSurfaceVariant) },
            minLines = 3,
            maxLines = 8,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = colors.outline,
                focusedBorderColor = colors.onSurfaceVariant,
                cursorColor = colors.onSurface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FINISHED ON", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { picking = true },
                shape = CircleShape,
                border = BorderStroke(1.dp, colors.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
            ) { Text(finishedOn.pretty()) }
        }
        PillButton(button, { onDone(Read(finishedOn, rating, review.trim().ifEmpty { null })) }, Modifier.fillMaxWidth())
        if (onDelete != null) TextButton(
            onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (confirmDelete) "Tap again to delete this read" else "Delete this read",
                color = if (confirmDelete) colors.error else colors.onSurfaceVariant,
            )
        }
    }
}

private val Months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** 30 Sep 2026 */
private fun LocalDate.pretty() = "$day ${Months[month.ordinal]} $year"

/** Material's calendar, up to today. It speaks UTC-midnight millis, so dates go in and out through UTC. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun PickDate(date: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val todayMillis = today().atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    val pickerColors = DatePickerDefaults.colors(containerColor = colors.surfaceContainerHigh)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                state.selectedDateMillis?.let { onPick(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
                onDismiss()
            }) { Text("Done", color = colors.onSurface) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) } },
        colors = pickerColors,
    ) {
        DatePicker(state, colors = pickerColors, showModeToggle = false)
    }
}

/** Five stars; read-only when [onRate] is null. */
@Composable
private fun Stars(rating: Int?, size: TextUnit, onRate: ((Int?) -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(size.value.dp / 6)) {
        (1..5).forEach { star ->
            val lit = rating != null && star <= rating
            val scale by animateFloatAsState(if (lit) 1f else 0.86f, spring(dampingRatio = 0.4f, stiffness = 500f))
            Text(
                "★",
                fontSize = size,
                color = if (lit) colors.onSurface else colors.outlineVariant,
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .then(
                        if (onRate == null) Modifier
                        else Modifier.clickable(interactionSource = null, indication = null) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onRate(if (rating == star) null else star)
                        }
                    ),
            )
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

/** Title, author and cover as they are while you edit; saved only when you tap Save. */
private data class Draft(val title: String, val author: String, val photo: ByteArray? = null, val removePhoto: Boolean = false)

@Composable
private fun BookDetails(
    book: Book,
    onFinish: (Read) -> Unit,
    onReread: () -> Unit,
    onEditRead: (index: Int, Read) -> Unit,
    onDeleteRead: (index: Int) -> Unit,
    onAddRead: (Read) -> Unit,
    onEditDetails: (title: String, author: String, photo: ByteArray?, removePhoto: Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var finishing by remember { mutableStateOf(false) }
    var editingRead by remember { mutableStateOf<Int?>(null) }
    var addingRead by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Draft?>(null) } // non-null while the pen's edit mode is on
    var confirmRemove by remember { mutableStateOf(false) }

    val form = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()
        .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
    if (finishing) {
        Box(form) {
            RateBook(book, null, Read(today()), if (book.rereading) "How was it this time?" else "How was it?", "Add to Finished", onDone = onFinish)
        }
        return
    }
    editingRead?.let { index ->
        Box(form) {
            RateBook(
                book, null, book.reads[index], "Edit this read", "Save",
                onDelete = { editingRead = null; onDeleteRead(index) },
            ) { onEditRead(index, it); editingRead = null }
        }
        return
    }
    if (addingRead) {
        Box(form) {
            RateBook(book, null, Read(today()), "Add a read", "Add read") { onAddRead(it); addingRead = false }
        }
        return
    }
    draft?.let {
        Box(form) {
            EditBook(
                book, it,
                onChange = { draft = it },
                onEditRead = { index -> editingRead = index },
                onAddRead = { addingRead = true },
                onCancel = { draft = null },
                onSave = {
                    onEditDetails(it.title.trim().ifEmpty { book.title }, it.author.trim(), it.photo, it.removePhoto)
                    draft = null
                },
            )
        }
        return
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Cover(book, Modifier.align(Alignment.TopCenter).width(128.dp).shadow(24.dp, RoundedCornerShape(6.dp)), corner = 6)
            RoundIconButton(Icons.Outlined.Edit, "Edit book", Modifier.align(Alignment.TopEnd)) {
                draft = Draft(book.title, book.author)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            when {
                book.finished && book.reads.size > 1 -> "FINISHED · READ ${book.reads.size}×"
                book.finished -> "FINISHED"
                book.rereading -> "REREADING"
                else -> "TO READ"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
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
        else PillButton("Finished it", { finishing = true }, Modifier.fillMaxWidth())
        TextButton(
            onClick = { if (confirmRemove) onRemove() else confirmRemove = true },
            border = if (confirmRemove) BorderStroke(1.dp, colors.error) else null,
            shape = CircleShape,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                if (confirmRemove) "Tap again to remove" else "Remove from shelf",
                color = if (confirmRemove) colors.error else colors.onSurfaceVariant,
            )
        }
    }
}

/** The pen's edit mode: cover, title and author (saved with Save), and every read (each saved on its own). */
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
    // What the cover will be after Save.
    val preview = if (draft.removePhoto) book.copy(coverFile = null) else book
    val hasPhoto = draft.photo != null || (book.coverFile != null && !draft.removePhoto)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EDIT BOOK", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onCancel) { Text("Cancel", color = colors.onSurfaceVariant) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CoverPicker(Modifier.width(84.dp), onCover = { onChange(draft.copy(photo = it, removePhoto = false)) }) {
                    Cover(preview, Modifier.fillMaxWidth(), photo = draft.photo)
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
                if (hasPhoto) TextButton({ onChange(draft.copy(photo = null, removePhoto = true)) }) {
                    Text(
                        if (book.coverUrl != null) "Use the Open Library cover" else "Remove photo",
                        color = colors.onSurfaceVariant,
                    )
                }
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

/** Same round outline as the ⋯ button in the header. */
@Composable
private fun RoundIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.size(36.dp).clip(CircleShape).border(1.dp, colors.outline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = colors.onSurface, modifier = Modifier.size(18.dp))
    }
}

/** One read; tappable with an "Edit" on the right only in edit mode. */
@Composable
private fun ReadRow(read: Read, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(top = 12.dp),
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
