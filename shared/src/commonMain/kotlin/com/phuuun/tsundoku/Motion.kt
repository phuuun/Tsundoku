package com.phuuun.tsundoku

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The three springs everything animates with. */
object Motion {
    /** Quick with a little bounce: presses. */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 800f)

    /** Positions, sizes, layout. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 400f)

    /** Stickers, stars, favourite. */
    fun <T> pop(): SpringSpec<T> = spring(dampingRatio = 0.4f, stiffness = 500f)
}

/** Scales to 0.96 while pressed and springs back. */
@Composable
fun Modifier.pressScale(pressed: Boolean): Modifier {
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.press())
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Draws [shape] in [color] offset by [depth] behind the element; pressed, the element sinks into it. */
@Composable
fun Modifier.hardShadow(shape: Shape, depth: Dp, pressed: Boolean, color: Color = Bold.Ink): Modifier {
    val sink by animateDpAsState(if (pressed) depth - 1.dp else 0.dp, Motion.press())
    return drawBehind {
        translate(depth.toPx(), depth.toPx()) {
            drawOutline(shape.createOutline(size, layoutDirection, this@drawBehind), color)
        }
    }.graphicsLayer {
        translationX = sink.toPx()
        translationY = sink.toPx()
    }
}

/** Wiggles sideways three times whenever [trigger] changes (not on first composition). */
@Composable
fun Modifier.shake(trigger: Int): Modifier {
    val offset = remember { Animatable(0f) }
    val last = remember { intArrayOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger == last[0]) return@LaunchedEffect
        last[0] = trigger
        offset.animateTo(0f, keyframes {
            durationMillis = 300
            6f at 50; -6f at 100; 6f at 150; -6f at 200; 3f at 250; 0f at 300
        })
    }
    return graphicsLayer { translationX = offset.value.dp.toPx() }
}

/** On first composition, settles in from [scale], [offsetY] and transparent. */
@Composable
fun Modifier.enterFrom(scale: Float = 0.92f, offsetY: Dp = 0.dp): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, Motion.settle()) }
    return graphicsLayer {
        val p = progress.value
        val s = scale + (1f - scale) * p
        scaleX = s; scaleY = s
        translationY = offsetY.toPx() * (1f - p)
        alpha = p.coerceIn(0f, 1f)
    }
}

/** Delay before the book at [position] rises: 25ms apiece, capped so a big library doesn't wait. */
fun riseDelayMillis(position: Int): Long = minOf(position * 25L, 600L)

/** Swallows every touch while this content slides out, so a second tap can't reach a screen that has already done its job. */
fun Modifier.blockWhileLeaving(scope: AnimatedVisibilityScope): Modifier = pointerInput(scope) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (scope.transition.targetState != EnterExitState.Visible) event.changes.forEach { it.consume() }
        }
    }
}
