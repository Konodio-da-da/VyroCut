package com.vyrocut.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shared visual tokens for the home screen, editor, and Material components.
 *
 * Keep UI colors here so the app can evolve without each screen drifting into
 * a separate palette. Track and canvas colors remain semantic and distinct.
 */
internal object VyroCutPalette {
    val Background = Color(0xFF131313)
    val Surface = Color(0xFF1C1B1B)
    val SurfaceElevated = Color(0xFF292929)
    val SurfaceToolbar = Color(0xFF1B201F)
    val SurfaceSelected = Color(0xFF16483D)
    val SurfaceSelectedTimeline = Color(0xFF1B4239)
    val SurfaceTimeline = Color(0xFF172C28)
    val SurfaceClip = Color(0xFF252A28)
    val SurfaceBadge = Color(0xFF29312E)
    val SurfaceTrack = Color(0xFF293330)
    val TimelinePlayhead = Accent
    val TimelinePlayheadOutline = Background
    val TimelineSelectionOutline = AccentSoft
    val HeroGradientStart = Color(0xFF202725)
    val HeroGradientEnd = Color(0xFF171918)
    val ThumbnailGradientStart = Color(0xFF34443E)
    val CanvasDark = Color(0xFF202322)

    val Accent = Color(0xFF0FE8B8)
    val AccentSecondary = Color(0xFF00E5FF)
    val AccentSoft = Color(0xFF8AFFD8)
    val AccentInk = Color(0xFF00382A)

    val TextPrimary = Color(0xFFE5EDEA)
    val TextMuted = Color(0xFF9BAAA5)
    val TextSubtle = Color(0xFF71827B)
    val Danger = Color(0xFFFF8A80)
    val OnDanger = Color.Black
    val Outline = Color(0xFF39423F)

    // Semantic media/canvas colors: keep true black/white distinct from UI text.
    val CanvasBlack = Color.Black
    val CanvasWhite = Color.White
    val MediaOverlayText = Color.White
    val MediaOverlayScrim = Color.Black.copy(alpha = 0.68f)
    val WaveformForeground = Color.White.copy(alpha = 0.9f)

    val MusicTrack = Color(0xFF185C48)
    val SfxTrack = Color(0xFF57452A)
    val VoiceoverTrack = Color(0xFF344A67)
}

internal fun vyroCutColorScheme(): ColorScheme = darkColorScheme(
    primary = VyroCutPalette.Accent,
    onPrimary = VyroCutPalette.AccentInk,
    primaryContainer = VyroCutPalette.SurfaceSelected,
    onPrimaryContainer = VyroCutPalette.AccentSoft,
    secondary = VyroCutPalette.AccentSecondary,
    onSecondary = VyroCutPalette.AccentInk,
    background = VyroCutPalette.Background,
    onBackground = VyroCutPalette.TextPrimary,
    surface = VyroCutPalette.Surface,
    onSurface = VyroCutPalette.TextPrimary,
    surfaceVariant = VyroCutPalette.SurfaceElevated,
    onSurfaceVariant = VyroCutPalette.TextMuted,
    error = VyroCutPalette.Danger,
    onError = VyroCutPalette.OnDanger,
    outline = VyroCutPalette.Outline,
)

@Composable
internal fun VyroCutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = vyroCutColorScheme(),
        content = content,
    )
}
