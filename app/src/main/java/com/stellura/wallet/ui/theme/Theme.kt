package com.stellura.wallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.stellura.wallet.data.ThemeMode

/** "Deep Orbit" palette: near-black navy, crimson accent, cyan for gains. */
@Immutable
data class StelluraColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val up: Color,
    val down: Color,
    val isDark: Boolean,
)

val DarkPalette = StelluraColors(
    bg = Color(0xFF070A14), surface = Color(0xFF0F1424), surface2 = Color(0xFF141A2E),
    line = Color(0xFF1E2640), text = Color(0xFFE6EAF5), muted = Color(0xFF8A93B0),
    accent = Color(0xFFE0284A), onAccent = Color.White,
    up = Color(0xFF2DD4E8), down = Color(0xFFFF5470), isDark = true,
)

val LightPalette = StelluraColors(
    bg = Color(0xFFF4F6FB), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF4F6FB),
    line = Color(0xFFDDE2EE), text = Color(0xFF0B1020), muted = Color(0xFF5A6380),
    accent = Color(0xFFC41E3A), onAccent = Color.White,
    up = Color(0xFF0B6E7C), down = Color(0xFFB3203D), isDark = false,
)

val LocalStellura = staticCompositionLocalOf { DarkPalette }

/** Monospace for numbers, tickers and addresses. Swap for JetBrains Mono via res/font later. */
val Mono = FontFamily.Monospace

@Composable
fun StelluraTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text, surfaceVariant = p.surface2, onSurfaceVariant = p.muted,
        outline = p.line, secondary = p.up, error = p.down,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text, surfaceVariant = p.surface2, onSurfaceVariant = p.muted,
        outline = p.line, secondary = p.up, error = p.down,
    )
    val base = Typography()
    val type = base.copy(
        headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    )
    CompositionLocalProvider(LocalStellura provides p) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}

object S { val c: StelluraColors @Composable get() = LocalStellura.current }
