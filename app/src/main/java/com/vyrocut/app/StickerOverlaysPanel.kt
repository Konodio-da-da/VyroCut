package com.vyrocut.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun StickerOverlaysPanel(
    selectedClip: EditClip?,
    selectedStickerPosition: ClipStickerPosition,
    selectedStickerLayerIndex: Int,
    onPositionSelected: (ClipStickerPosition) -> Unit,
    onAddSticker: (String, String?) -> Unit,
    onRemoveSticker: (Int) -> Unit,
    onScaleChange: (Int, Float) -> Unit,
    onRotationChange: (Int, Float) -> Unit,
    onTimingChange: (Int, Long?, Long?) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(
                VyroCutPalette.Surface,
                RoundedCornerShape(VyroCutShapes.CompactPanelCornerDp.dp),
            )
            .padding(
                horizontal = VyroCutStickerPanelMetrics.HorizontalPaddingDp.dp,
                vertical = VyroCutStickerPanelMetrics.VerticalPaddingDp.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(VyroCutSpacing.SmallDp.dp),
    ) {
        Text(
            "STICKER OVERLAYS",
            color = VyroCutPalette.TextPrimary,
            fontSize = VyroCutTypography.LabelSp.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        if (selectedClip == null) {
            Text(
                "Select a video clip before adding a sticker.",
                color = VyroCutPalette.TextMuted,
                fontSize = VyroCutTypography.CaptionSp.sp,
            )
        } else {
            Text(
                "Choose a position, then tap a built-in vector sticker or emoji to add it to this clip.",
                color = VyroCutPalette.TextMuted,
                fontSize = VyroCutTypography.CaptionSp.sp,
            )
            StickerPositionPicker(
                selectedPosition = selectedStickerPosition,
                onPositionSelected = onPositionSelected,
            )
            StickerAssetPicker(
                enabled = selectedClip.allStickerOverlays.size < MAX_CLIP_STICKER_OVERLAYS,
                onAddSticker = onAddSticker,
            )
            StickerQuickEmojiPicker(
                enabled = selectedClip.allStickerOverlays.size < MAX_CLIP_STICKER_OVERLAYS,
                onAddSticker = { symbol -> onAddSticker(symbol, null) },
            )
            StickerLayersEditor(
                clip = selectedClip,
                selectedLayerIndex = selectedStickerLayerIndex,
                onRemoveSticker = onRemoveSticker,
                onScaleChange = onScaleChange,
                onTimingChange = onTimingChange,
            )
            Text(
                "Clip-local IN/OUT timing • Survives split, trim, save and MP4 export",
                color = VyroCutPalette.TextMuted,
                fontSize = VyroCutTypography.ToolLabelSp.sp,
            )
        }
    }
}

@Composable
private fun StickerPositionPicker(
    selectedPosition: ClipStickerPosition,
    onPositionSelected: (ClipStickerPosition) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(VyroCutStickerPanelMetrics.ChipGapDp.dp),
    ) {
        ClipStickerPosition.values().forEach { position ->
            FilterChip(
                selected = selectedPosition == position,
                onClick = { onPositionSelected(position) },
                label = {
                    Text(
                        position.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                        fontSize = VyroCutTypography.ToolLabelSp.sp,
                    )
                },
            )
        }
    }
}

@Composable
private fun StickerAssetPicker(
    enabled: Boolean,
    onAddSticker: (String, String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VyroCutSpacing.XSmallDp.dp)) {
        Text(
            "BUILT-IN VECTOR PACK",
            color = VyroCutPalette.TextMuted,
            fontSize = VyroCutTypography.ToolLabelSp.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(VyroCutStickerPanelMetrics.ChipGapDp.dp),
        ) {
            ClipStickerAsset.values().forEach { asset ->
                FilterChip(
                    selected = false,
                    onClick = { onAddSticker(asset.fallbackSymbol, asset.id) },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(VyroCutSpacing.XSmallDp.dp),
                        ) {
                            Image(
                                painter = painterResource(id = asset.drawableResId),
                                contentDescription = asset.label,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(asset.label, fontSize = VyroCutTypography.ToolLabelSp.sp)
                        }
                    },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun StickerQuickEmojiPicker(
    enabled: Boolean,
    onAddSticker: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VyroCutSpacing.XSmallDp.dp)) {
        Text(
            "QUICK EMOJI",
            color = VyroCutPalette.TextMuted,
            fontSize = VyroCutTypography.ToolLabelSp.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(VyroCutStickerPanelMetrics.ChipGapDp.dp),
        ) {
            listOf("⭐", "❤️", "🔥", "😂", "😎", "✨", "🎉", "🚀", "💯", "👏").forEach { symbol ->
                FilterChip(
                    selected = false,
                    onClick = { onAddSticker(symbol) },
                    label = {
                        Text(
                            symbol,
                            fontSize = VyroCutStickerPanelMetrics.SymbolChipFontSizeSp.sp,
                        )
                    },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun StickerLayersEditor(
    clip: EditClip,
    selectedLayerIndex: Int,
    onRemoveSticker: (Int) -> Unit,
    onScaleChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onTimingChange: (Long?, Long?) -> Unit,
) {
    Text(
        "ADDED • ${clip.allStickerOverlays.size}/${MAX_CLIP_STICKER_OVERLAYS}",
        color = VyroCutPalette.TextMuted,
        fontSize = VyroCutTypography.ToolLabelSp.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
    )
    if (clip.allStickerOverlays.isEmpty()) {
        Text(
            "No stickers on this clip yet.",
            color = VyroCutPalette.TextMuted,
            fontSize = VyroCutTypography.CaptionSp.sp,
        )
    } else {
        clip.allStickerOverlays.forEachIndexed { index, sticker ->
            StickerLayerEditor(
                clipId = clip.id,
                clipDurationMs = clip.durationMs,
                sticker = sticker,
                selected = selectedLayerIndex == index,
                onRemove = { onRemoveSticker(index) },
                onScaleChange = { scale -> onScaleChange(index, scale) },
                onRotationChange = { degrees -> onRotationChange(index, degrees) },
                onTimingChange = { startMs, endMs ->
                    onTimingChange(index, startMs, endMs)
                },
            )
        }
    }
}

@Composable
private fun StickerLayerEditor(
    clipId: String,
    clipDurationMs: Long,
    sticker: ClipStickerOverlay,
    selected: Boolean,
    onRemove: () -> Unit,
    onScaleChange: (Float) -> Unit,
    onTimingChange: (Long?, Long?) -> Unit,
) {
    val safeDurationMs = clipDurationMs.coerceAtLeast(1L)
    val safeStartMs = sticker.startMs.coerceIn(0L, safeDurationMs - 1L)
    val safeEndMs = (sticker.endMs ?: safeDurationMs)
        .coerceIn(safeStartMs + 1L, safeDurationMs)
    val durationSeconds = safeDurationMs / 1_000f
    val stickerAsset = ClipStickerAsset.fromId(sticker.assetId)
    var startSeconds by remember(clipId, sticker) {
        mutableFloatStateOf(safeStartMs / 1_000f)
    }
    var endSeconds by remember(clipId, sticker) {
        mutableFloatStateOf(safeEndMs / 1_000f)
    }
    var scaleDraft by remember(clipId, sticker) {
        mutableFloatStateOf(sticker.scale)
    }
    var rotationDraft by remember(clipId, sticker) {
        mutableFloatStateOf(sticker.rotationDegrees.toFloat())
    }

    Column(
        Modifier.fillMaxWidth()
            .background(
                if (selected) VyroCutPalette.SurfaceSelected else VyroCutPalette.Background,
                RoundedCornerShape(VyroCutShapes.SmallCornerDp.dp),
            )
            .padding(
                horizontal = VyroCutSpacing.SmallDp.dp,
                vertical = VyroCutStickerPanelMetrics.LayerVerticalPaddingDp.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(VyroCutStickerPanelMetrics.LayerItemGapDp.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (stickerAsset != null) {
                Image(
                    painter = painterResource(id = stickerAsset.drawableResId),
                    contentDescription = stickerAsset.label,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    stickerAsset.label,
                    color = VyroCutPalette.TextPrimary,
                    fontSize = VyroCutTypography.CaptionSp.sp,
                )
            } else {
                Text(
                    sticker.symbol,
                    fontSize = VyroCutStickerPanelMetrics.LayerSymbolFontSizeSp.sp,
                )
            }
            Spacer(Modifier.width(VyroCutSpacing.SmallDp.dp))
            Text(
                sticker.position.name.lowercase().replace('_', ' '),
                color = VyroCutPalette.TextMuted,
                fontSize = VyroCutTypography.CaptionSp.sp,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onRemove,
                contentPadding = PaddingValues(
                    horizontal = VyroCutStickerPanelMetrics.RemoveButtonHorizontalPaddingDp.dp,
                    vertical = 0.dp,
                ),
            ) {
                Text(
                    "Remove",
                    color = VyroCutPalette.Danger,
                    fontSize = VyroCutTypography.CaptionSp.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SIZE ${String.format(Locale.US, "%.1f", scaleDraft)}×",
                color = VyroCutPalette.TextMuted,
                fontSize = VyroCutTypography.ToolLabelSp.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${MIN_CLIP_STICKER_SCALE}× – ${MAX_CLIP_STICKER_SCALE}×",
                color = VyroCutPalette.TextMuted,
                fontSize = VyroCutTypography.ToolLabelSp.sp,
            )
        }
        Slider(
            value = scaleDraft.coerceIn(MIN_CLIP_STICKER_SCALE, MAX_CLIP_STICKER_SCALE),
            onValueChange = { scaleDraft = it },
            valueRange = MIN_CLIP_STICKER_SCALE..MAX_CLIP_STICKER_SCALE,
            steps = 14,
            onValueChangeFinished = { onScaleChange(scaleDraft) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ROTATION ${rotationDraft.toInt()}°",
                color = VyroCutPalette.TextMuted,
                fontSize = VyroCutTypography.ToolLabelSp.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${MIN_CLIP_STICKER_ROTATION_DEGREES}° – ${MAX_CLIP_STICKER_ROTATION_DEGREES}°",
                color = VyroCutPalette.TextMuted,
                fontSize = VyroCutTypography.ToolLabelSp.sp,
            )
        }
        Slider(
            value = rotationDraft.coerceIn(
                MIN_CLIP_STICKER_ROTATION_DEGREES.toFloat(),
                MAX_CLIP_STICKER_ROTATION_DEGREES.toFloat(),
            ),
            onValueChange = { rotationDraft = it },
            valueRange = MIN_CLIP_STICKER_ROTATION_DEGREES.toFloat()..MAX_CLIP_STICKER_ROTATION_DEGREES.toFloat(),
            steps = 71,
            onValueChangeFinished = { onRotationChange(rotationDraft) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "IN ${String.format(Locale.US, "%.1f", startSeconds)}s",
                color = VyroCutPalette.TextMuted,
                fontSize = VyroCutTypography.ToolLabelSp.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "OUT ${String.format(Locale.US, "%.1f", endSeconds)}s",
                color = VyroCutPalette.TextMuted,
                fontSize = VyroCutTypography.ToolLabelSp.sp,
            )
        }
        Slider(
            value = startSeconds,
            onValueChange = { startSeconds = it },
            valueRange = 0f..durationSeconds,
            onValueChangeFinished = {
                onTimingChange((startSeconds * 1_000f).toLong(), null)
            },
        )
        Slider(
            value = endSeconds,
            onValueChange = { endSeconds = it },
            valueRange = 0f..durationSeconds,
            onValueChangeFinished = {
                onTimingChange(null, (endSeconds * 1_000f).toLong())
            },
        )
    }
}
