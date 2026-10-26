package com.phuuun.tsundoku

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal val FieldShape = RoundedCornerShape(14.dp)

/** Text field colours with every border and container off: [fieldFrame] or a caller draws them. */
@Composable
internal fun bareFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
    errorBorderColor = Color.Transparent, disabledBorderColor = Color.Transparent,
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
    cursorColor = Bold.Ink,
)

/** Surface fill with a 2dp outline that fades Quiet line to Ink when [focused], and a 3dp hard shadow that fades in. */
@Composable
internal fun Modifier.fieldFrame(focused: Boolean): Modifier {
    val line by animateColorAsState(if (focused) Bold.Ink else Bold.QuietLine, Motion.settle())
    val shadow by animateFloatAsState(if (focused) 1f else 0f, Motion.settle())
    return hardShadow(FieldShape, 3.dp, pressed = false, color = Bold.Ink.copy(alpha = shadow.coerceIn(0f, 1f)))
        .background(Bold.Surface, FieldShape)
        .border(2.dp, line, FieldShape)
}

/** Two options as chips with an Ink fill under the chosen one; [position] runs from 0 (the first) to 1 (the second). */
@Composable
internal fun PillSwitch(options: List<String>, position: Float, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val gap = 8.dp
    BoxWithConstraints(modifier.height(40.dp)) {
        val chip = (maxWidth - gap) / 2
        Box(
            Modifier.width(chip).fillMaxHeight()
                .graphicsLayer { translationX = (chip + gap).toPx() * position }
                .background(Bold.Ink, CircleShape)
        )
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            options.forEachIndexed { i, name ->
                val selected = 1 - abs(position - i).coerceIn(0f, 1f)
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Box(
                    Modifier.weight(1f).fillMaxHeight().pressScale(pressed).clip(CircleShape).border(2.dp, Bold.Ink, CircleShape)
                        .clickable(source, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = lerp(Bold.Muted, Bold.Paper, selected),
                    )
                }
            }
        }
    }
}

// Drawn inside the sheet: Material's handle slot wraps it in a focusable box that turns grey once a text field goes away.
@Composable
internal fun DragHandle() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.padding(top = 12.dp, bottom = 20.dp).size(width = 36.dp, height = 4.dp)
                .background(Bold.Ink, CircleShape)
        )
    }
}

/**
 * A bottom sheet: Surface, 28dp top corners, an Ink drag handle, then [content]. Content gets a `close` that slides the
 * sheet down and then calls [onDismiss]; touches are swallowed while it goes, so a second tap can't repeat an action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val close = {
        if (!closing) {
            closing = true
            scope.launch { state.hide(); onDismiss() }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Bold.Surface,
        contentColor = Bold.Ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Box {
            Column {
                DragHandle()
                content(close)
            }
            if (closing) Box(Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            })
        }
    }
}

/** The primary button: Tomato with a Paper outline and a hard shadow it sinks into; [big] is the 56dp "Add book" size. */
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    big: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val active = enabled && !loading
    val fill by animateColorAsState(if (active) Bold.Tomato else Bold.Tomato.copy(alpha = 0.3f), Motion.settle())
    val shadow by animateFloatAsState(if (active) 1f else 0f, Motion.settle())
    NoRipple { Button(
        onClick = onClick,
        enabled = active,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = fill, contentColor = Bold.DarkInk,
            disabledContainerColor = fill, disabledContentColor = Bold.DarkInk,
        ),
        border = BorderStroke(2.dp, Bold.Paper),
        contentPadding = PaddingValues(horizontal = 32.dp),
        interactionSource = source,
        modifier = modifier.hardShadow(CircleShape, 4.dp, pressed, Bold.Ink.copy(alpha = shadow.coerceIn(0f, 1f))).height(if (big) 56.dp else 52.dp),
    ) {
        AnimatedContent(loading, contentAlignment = Alignment.Center) { busy ->
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Bold.DarkInk, strokeWidth = 2.dp)
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (big) Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                if (big) Text(text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                else Text(text, fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
            }
        }
    } }
}

/** The secondary button: transparent with a 2dp Ink outline. */
@Composable
internal fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val line by animateColorAsState(if (enabled) Bold.Ink else Bold.Ink.copy(alpha = 0.3f), Motion.settle())
    NoRipple { OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(2.dp, line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Bold.Ink),
        interactionSource = source,
        modifier = modifier.height(52.dp).pressScale(pressed),
    ) {
        Text(text, fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
    } }
}

/** A text field in the Bold frame. */
@Composable
internal fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = Bold.Placeholder) },
        singleLine = true,
        shape = FieldShape,
        colors = bareFieldColors(),
        interactionSource = source,
        modifier = modifier.fillMaxWidth().fieldFrame(focused),
    )
}

/** Same round outline as the ⋯ button in the header. */
@Composable
internal fun RoundButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier.size(40.dp).pressScale(pressed).clip(CircleShape).border(2.dp, Bold.Ink, CircleShape)
            .clickable(source, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A small pill in [color] with a Dark ink label: TO READ, FINISHED, REREADING. */
@Composable
internal fun StatusSticker(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Bold.DarkInk,
        modifier = Modifier.background(color, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Ink fill with Paper text when [chosen]; otherwise a Quiet line outline. */
@Composable
internal fun ChoicePill(text: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill by animateColorAsState(if (chosen) Bold.Ink else Color.Transparent, Motion.settle())
    val line by animateColorAsState(if (chosen) Bold.Ink else Bold.QuietLine, Motion.settle())
    val label by animateColorAsState(if (chosen) Bold.Paper else Bold.Ink, Motion.settle())
    Box(
        modifier.pressScale(pressed).clip(CircleShape).background(fill).border(2.dp, line, CircleShape)
            .selectable(chosen, source, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = label) }
}

/** Five stars; read-only when [onRate] is null. Stars a tap lights pop in turn, 40ms apart. */
@Composable
internal fun Stars(rating: Int?, size: TextUnit, onRate: ((Int?) -> Unit)? = null) {
    val haptics = LocalHapticFeedback.current
    val lit = rating ?: 0
    val before = remember { intArrayOf(lit) }
    SideEffect { before[0] = lit } // runs after composition, so below it still holds the previous rating
    Row(horizontalArrangement = Arrangement.spacedBy(size.value.dp / 6)) {
        (1..5).forEach { star ->
            val on = star <= lit
            val pop = remember { Animatable(if (on) 1f else 0.86f) }
            val first = remember { booleanArrayOf(true) }
            val turn = star - before[0] - 1
            LaunchedEffect(on) {
                if (first[0]) { first[0] = false; return@LaunchedEffect }
                if (on) {
                    val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                    delay((turn.coerceAtLeast(0) * 40L * scale).toLong())
                    pop.snapTo(0.6f)
                    pop.animateTo(1f, Motion.pop())
                } else pop.animateTo(0.86f, Motion.settle())
            }
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            StarShape(
                filled = on,
                color = if (on) Bold.Yellow else Bold.QuietLine,
                outline = if (on) Bold.DarkInk else null,
                modifier = Modifier.size(size.value.dp)
                    .pressScale(pressed)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .then(
                        if (onRate == null) Modifier
                        else Modifier.semantics { contentDescription = if (star == 1) "1 star" else "$star stars"; selected = on }
                            .clickable(source, indication = null, role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onRate(if (rating == star) null else star)
                        }
                    ),
            )
        }
    }
}

/** A five-point star, outlined or filled; [outline] adds a Dark ink edge to a filled one (the icon pack's outline star isn't in the core set). */
@Composable
internal fun StarShape(filled: Boolean, color: Color, modifier: Modifier, outline: Color? = null) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val star = Path().apply {
            val outer = (size.minDimension - stroke) / 2 // leave room for the outline
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) outer else outer * 0.45f
                val angle = -PI / 2 + i * PI / 5
                val x = center.x + radius * cos(angle).toFloat()
                val y = center.y + radius * sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        if (filled) drawPath(star, color)
        else drawPath(star, color, style = Stroke(stroke, join = StrokeJoin.Round))
        if (outline != null) drawPath(star, outline, style = Stroke(stroke, join = StrokeJoin.Round))
    }
}

/** Material buttons draw a ripple from this; our own controls give press feedback with [pressScale] or a sinking shadow instead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoRipple(content: @Composable () -> Unit) = CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)

/** The search field's clear button: a round press-scaling target with no ripple. */
@Composable
private fun ClearButton(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier.size(40.dp).pressScale(pressed).clip(CircleShape).clickable(source, indication = null, role = Role.Button, onClickLabel = "Clear search", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = Bold.Ink) }
}

/** Filters as you type, no enter needed; the keyboard's search key just puts the keyboard away. */
@Composable
internal fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Search title or author", color = Bold.Placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Bold.Ink, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (query.isEmpty()) null else {
            { ClearButton { onQuery(""); focusManager.clearFocus() } }
        },
        singleLine = true,
        shape = FieldShape,
        colors = bareFieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier.fillMaxWidth()
            .hardShadow(FieldShape, 3.dp, pressed = false)
            .background(Bold.Surface, FieldShape)
            .border(2.dp, Bold.Ink, FieldShape),
    )
}
