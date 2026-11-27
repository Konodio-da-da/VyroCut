package com.vyrocut.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Preview transport actions kept separate from the main editor composition.
 *
 * Playback, frame stepping, seeking, and speed persistence remain owned by
 * the editor; this component only presents the existing controls and forwards
 * user actions through explicit callbacks.
 */
@Composable
internal fun VyroCutEditorTransportControls(
    hasSelectedClip: Boolean,
    hasClips: Boolean,
    isResolvingFrameStep: Boolean,
    isPlaying: Boolean,
    playbackSpeedLabel: String,
    onPreviousFrame: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onNextFrame: () -> Unit,
    onPlaybackSpeed: () -> Unit,
    onPlayPause: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onPreviousFrame,
            enabled = hasSelectedClip && !isResolvingFrameStep,
            modifier = Modifier.size(VyroCutEditorChromeMetrics.CompactIconButtonSizeDp.dp),
        ) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "Previous frame",
                tint = VyroCutPalette.Accent,
                modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportIconSizeDp.dp),
            )
        }
        IconButton(
            onClick = onSeekBackward,
            enabled = hasClips,
            modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportSeekButtonSizeDp.dp),
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back 5 seconds",
                tint = VyroCutPalette.Accent,
                modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportSeekIconSizeDp.dp),
            )
        }
        IconButton(
            onClick = onSeekForward,
            enabled = hasClips,
            modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportSeekButtonSizeDp.dp),
        ) {
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "Forward 5 seconds",
                tint = VyroCutPalette.Accent,
                modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportSeekIconSizeDp.dp),
            )
        }
        IconButton(
            onClick = onNextFrame,
            enabled = hasSelectedClip && !isResolvingFrameStep,
            modifier = Modifier.size(VyroCutEditorChromeMetrics.CompactIconButtonSizeDp.dp),
        ) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Next frame",
                tint = VyroCutPalette.Accent,
                modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportIconSizeDp.dp),
            )
        }
        TextButton(
            onClick = onPlaybackSpeed,
            enabled = hasSelectedClip,
            contentPadding = PaddingValues(
                horizontal = VyroCutEditorChromeMetrics.PlaybackSpeedButtonHorizontalPaddingDp.dp,
                vertical = 0.dp,
            ),
        ) {
            Text(
                playbackSpeedLabel,
                color = VyroCutPalette.Accent,
                fontSize = VyroCutTypography.LabelSp.sp,
            )
        }
        FilledTonalButton(
            onClick = onPlayPause,
            enabled = hasSelectedClip,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = VyroCutPalette.Accent,
                contentColor = VyroCutPalette.AccentInk,
            ),
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(VyroCutEditorChromeMetrics.PlaybackButtonIconSizeDp.dp),
            )
        }
    }
}
