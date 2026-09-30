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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import kotlin.math.abs

@Composable
fun App(dataDir: String) {
    val library = remember { Library(dataDir) }
    TsundokuTheme { Shelves(library) }
}

private val ShelfNames = listOf("To read", "Finished")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shelves(library: Library) {
    val colors = MaterialTheme.colorScheme
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Book?>(null) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Header(pager) { scope.launch { pager.animateScrollToPage(it) } }
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                val shelf = library.books.filter { it.finished == (page == 1) }
                if (shelf.isEmpty()) EmptyShelf(page)
                else LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shelf, key = { it.isbn }) { book ->
                        ShelfBook(book, Modifier.animateItem()) { open = book }
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
        ModalBottomSheet(onDismissRequest = { adding = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surfaceContainerLow, dragHandle = { DragHandle() }) {
            AddBook(library, onAdded = {
                adding = false
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { pager.animateScrollToPage(0) }
            })
        }
    }

    open?.let { book ->
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surfaceContainerLow, dragHandle = { DragHandle() }) {
            BookDetails(
                book,
                onToggleFinished = {
                    library.setFinished(book, !book.finished)
                    open = null
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch { pager.animateScrollToPage(if (book.finished) 0 else 1) }
                },
                onRemove = {
                    library.remove(book)
                    open = null
                },
            )
        }
    }
}

/** TSUNDOKU eyebrow over the pill switch, both centred. */
@Composable
private fun Header(pager: PagerState, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TSUNDOKU", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

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
        Cover(book, Modifier.fillMaxWidth())
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
private fun Cover(book: Book, modifier: Modifier, corner: Int = 4) {
    val colors = MaterialTheme.colorScheme
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
            model = book.coverUrl,
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// Material's handle is focusable and shows a grey box once the text field goes away.
@Composable
private fun DragHandle() {
    Box(
        Modifier.padding(top = 12.dp, bottom = 20.dp).size(width = 36.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
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

private enum class Step { Search, Found, Manual }

@Composable
private fun AddBook(library: Library, onAdded: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(Step.Search) }
    var book by remember { mutableStateOf(Book("", "", "")) }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun search() {
        val isbn = cleanIsbn(input)
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
                step = if (found != null) Step.Found else Step.Manual
            } catch (e: Exception) {
                error = "Couldn't reach Open Library. Check your connection."
            }
            loading = false
        }
    }

    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 28.dp).navigationBarsPadding().imePadding()) {
        Text("ADD A BOOK", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))

        AnimatedContent(step, transitionSpec = { fadeIn() togetherWith fadeOut() }) { current ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (current) {
                    Step.Search -> {
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
                            keyboardActions = KeyboardActions(onSearch = { search() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                        PillButton("Look it up", ::search, Modifier.fillMaxWidth(), enabled = input.isNotBlank(), loading = loading)
                    }

                    Step.Found -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cover(book, Modifier.width(84.dp).shadow(12.dp, RoundedCornerShape(4.dp)))
                            Column {
                                Text(book.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        PillButton("Add to shelf", { library.add(book); onAdded() }, Modifier.fillMaxWidth())
                        TextButton({ step = Step.Manual }, Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Not this one? Type it in", color = colors.onSurfaceVariant)
                        }
                    }

                    Step.Manual -> {
                        Text("Type it in", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Open Library doesn't know this one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Field(book.title, { book = book.copy(title = it) }, "Title")
                        Field(book.author, { book = book.copy(author = it) }, "Author")
                        PillButton(
                            "Add to shelf",
                            { library.add(book.copy(title = book.title.trim(), author = book.author.trim())); onAdded() },
                            Modifier.fillMaxWidth(),
                            enabled = book.title.isNotBlank(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookDetails(book: Book, onToggleFinished: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var confirmRemove by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(book, Modifier.width(128.dp).shadow(24.dp, RoundedCornerShape(6.dp)), corner = 6)
        Spacer(Modifier.height(24.dp))
        Text(
            if (book.finished) "FINISHED" else "TO READ",
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
        Spacer(Modifier.height(28.dp))
        PillButton(if (book.finished) "Move back to To read" else "Finished it", onToggleFinished, Modifier.fillMaxWidth())
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
