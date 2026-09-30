package com.phuuun.tsundoku

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Same monochrome palette as Before: pure black at night, ivory paper by day. The covers are the only colour.
private val Dark = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color(0xFF0D0D0E), onSurface = Color.White,
    surfaceVariant = Color(0xFF161618), onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF26262A), outlineVariant = Color(0xFF3F3F46),
    error = Color(0xFFFF6B6B),
    // Sheets and menus read these; unset they fall back to Material's lavender tint.
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0D0D0E),
    surfaceContainer = Color(0xFF161618), surfaceContainerHigh = Color(0xFF1C1C1F),
    surfaceContainerHighest = Color(0xFF26262A),
)

private val Ink = Color(0xFF2A2924)
private val Light = lightColorScheme(
    primary = Ink, onPrimary = Color.White,
    background = Color(0xFFFFFDF4), onBackground = Ink,
    surface = Color(0xFFFFFEF9), onSurface = Ink,
    surfaceVariant = Color(0xFFF3F0E3), onSurfaceVariant = Color(0xFF6B685C),
    outline = Color(0xFFDDD9C8), outlineVariant = Color(0xFFBDB8A6),
    error = Color(0xFFC0392B),
    surfaceContainerLowest = Color(0xFFFFFEF9), surfaceContainerLow = Color(0xFFFFFDF4),
    surfaceContainer = Color(0xFFFAF8EE), surfaceContainerHigh = Color(0xFFF6F3E8),
    surfaceContainerHighest = Color(0xFFF3F0E3),
)

private val Type = Typography(
    // Big tight titles, like an iOS large title.
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    // Book title and author under a cover.
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    // Small caps-style eyebrows and pills: TSUNDOKU, TO READ, ADD A BOOK.
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.5.sp),
)

@Composable
fun TsundokuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, content = content)
}
