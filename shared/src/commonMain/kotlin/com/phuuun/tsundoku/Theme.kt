package com.phuuun.tsundoku

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.phuuun.tsundoku.res.Res
import com.phuuun.tsundoku.res.bricolage_grotesque_bold
import com.phuuun.tsundoku.res.bricolage_grotesque_extrabold
import com.phuuun.tsundoku.res.bricolage_grotesque_regular
import com.phuuun.tsundoku.res.bricolage_grotesque_semibold
import org.jetbrains.compose.resources.Font

/** The Bold palette: dark paper, ink, and a few loud colours. UI code takes every colour from here. */
object Bold {
    val Paper = Color(0xFF0B0B0C)
    val Surface = Color(0xFF161618)
    val SurfaceHigh = Color(0xFF1F1F22)
    val Ink = Color(0xFFF4F4F5)
    val Muted = Color(0xFFA1A1AA)
    val Placeholder = Color(0xFF8B8B93)
    val QuietLine = Color(0xFF3F3F46)
    val DarkInk = Color(0xFF151515)
    val Tomato = Color(0xFFFF5A36)
    val Pink = Color(0xFFFF4F9A)
    val Yellow = Color(0xFFFFC93C)
    val Mint = Color(0xFF3DDC97)
    val Error = Color(0xFFFF6B6B)
}

private val Dark = darkColorScheme(
    primary = Bold.Tomato, onPrimary = Bold.DarkInk,
    background = Bold.Paper, onBackground = Bold.Ink,
    surface = Bold.Paper, onSurface = Bold.Ink,
    surfaceVariant = Bold.Surface, onSurfaceVariant = Bold.Muted,
    outline = Bold.QuietLine, outlineVariant = Bold.QuietLine,
    error = Bold.Error, onError = Bold.DarkInk,
    // Sheets and menus read these; unset they fall back to Material's lavender tint.
    surfaceContainerLowest = Bold.Paper, surfaceContainerLow = Bold.Surface,
    surfaceContainer = Bold.Surface, surfaceContainerHigh = Bold.SurfaceHigh,
    surfaceContainerHighest = Bold.SurfaceHigh,
)

@Composable
private fun boldTypography(): Typography {
    val bricolage = FontFamily(
        Font(Res.font.bricolage_grotesque_regular, FontWeight.Normal),
        Font(Res.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
        Font(Res.font.bricolage_grotesque_bold, FontWeight.Bold),
        Font(Res.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
    )
    return Typography(
        // The big tab title.
        headlineLarge = TextStyle(fontFamily = bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 50.sp, lineHeight = 50.sp, letterSpacing = (-2.5).sp),
        headlineSmall = TextStyle(fontFamily = bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp),
        titleMedium = TextStyle(fontFamily = bricolage, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp),
        bodyLarge = TextStyle(fontFamily = bricolage, fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = bricolage, fontSize = 14.sp, lineHeight = 20.sp),
        // Book title and author under a cover.
        labelLarge = TextStyle(fontFamily = bricolage, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp),
        labelMedium = TextStyle(fontFamily = bricolage, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        // Small caps-style eyebrows and stickers: TO READ, FINISHED.
        labelSmall = TextStyle(fontFamily = bricolage, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.sp),
    )
}

@Composable
fun TsundokuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, typography = boldTypography(), content = content)
}
