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
        ModalBottomSheet(onDismissRequest = { adding = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surfaceContainerLow, dragHandle = null) {
            DragHandle()
            AddBook(library, onAdded = { finished ->
                adding = false
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { pager.animateScrollToPage(if (finished) 1 else 0) }
            })
        }
    }

    open?.let { book ->
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surfaceContainerLow, dragHandle = null) {
            DragHandle()
            BookDetails(
                book,
                onFinish = { rating, review ->
                    library.setFinished(book, true, rating, review)
                    open = null
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch { pager.animateScrollToPage(1) }
                },
                onUnfinish = {
                    library.setFinished(book, false)
                    open = null
                    scope.launch { pager.animateScrollToPage(0) }
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

    var shooting by remember { mutableStateOf(false) }
    if (shooting) CoverCamera { cover ->
        shooting = false
        if (cover != null) photo = cover
    }

    fun add(finished: Boolean, rating: Int? = null, review: String? = null) {
        library.add(book.copy(title = book.title.trim(), author = book.author.trim(), finished = finished, rating = rating, review = review), photo)
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
                            "Open Library doesn't know this one. Tap the cover to take a photo of it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                Modifier.width(84.dp).clip(RoundedCornerShape(4.dp)).clickable { shooting = true },
                                contentAlignment = Alignment.Center,
                            ) {
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

                    Step.Rate -> RateBook(book, photo, "Add to Finished") { rating, review -> add(true, rating, review) }
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

/** Stars and a review, both optional: an empty review is saved as none, and tapping the lit star again clears the rating. */
@Composable
private fun RateBook(book: Book, photo: ByteArray?, button: String, onDone: (rating: Int?, review: String?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var rating by remember { mutableStateOf(book.rating) }
    var review by remember { mutableStateOf(book.review.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(book, Modifier.width(44.dp), photo = photo)
            Column {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        Text("How was it?", style = MaterialTheme.typography.headlineSmall)
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
        PillButton(button, { onDone(rating, review.trim().ifEmpty { null }) }, Modifier.fillMaxWidth())
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

@Composable
private fun BookDetails(
    book: Book,
    onFinish: (rating: Int?, review: String?) -> Unit,
    onUnfinish: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var finishing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    if (finishing) {
        Box(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(start = 24.dp, end = 24.dp, bottom = 28.dp)) {
            RateBook(book, null, "Add to Finished", onFinish)
        }
        return
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
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
        if (book.finished && book.rating != null) {
            Spacer(Modifier.height(14.dp))
            Stars(book.rating, size = 20.sp)
        }
        if (book.finished && book.review != null) Text(
            book.review,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        Spacer(Modifier.height(28.dp))
        if (book.finished) OutlinePill("Move back to To read", onUnfinish, Modifier.fillMaxWidth())
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
