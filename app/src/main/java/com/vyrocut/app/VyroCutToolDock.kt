package com.vyrocut.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class EditorTool(val title: String, val icon: ImageVector)

@Composable
internal fun VyroCutToolDock(
    activeTool: String,
    onToolSelected: (String) -> Unit,
) {
    val tools = listOf(
        EditorTool("Edit", Icons.Default.ContentCut),
        EditorTool("Audio", Icons.Default.AudioFile),
        EditorTool("Text", Icons.Default.TextFields),
        EditorTool("Stickers", Icons.Default.EmojiEmotions),
        EditorTool("Effects", Icons.Default.AutoAwesome),
        EditorTool("Filters", Icons.Default.FilterVintage),
        EditorTool("Ratio", Icons.Default.AspectRatio),
        EditorTool("Canvas", Icons.Default.Palette),
        EditorTool("Adjust", Icons.Default.Tune),
    )
    Row(
        Modifier.background(VyroCutPalette.SurfaceToolbar, RoundedCornerShape(VyroCutShapes.MediumCornerDp.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 7.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(VyroCutSpacing.XSmallDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEach { tool ->
            val selected = activeTool == tool.title
            Column(
                modifier = Modifier
                    .background(
                        if (selected) VyroCutPalette.SurfaceSelected else Color.Transparent,
                        RoundedCornerShape(VyroCutShapes.ToolItemCornerDp.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = VyroCutSpacing.XSmallDp.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(
                    onClick = { onToolSelected(tool.title) },
                    modifier = Modifier.size(width = VyroCutControlMetrics.ToolButtonWidthDp.dp, height = VyroCutControlMetrics.ToolButtonHeightDp.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(
                            tool.icon,
                            contentDescription = tool.title,
                            tint = if (selected) VyroCutPalette.AccentSoft else VyroCutPalette.TextMuted,
                            modifier = Modifier.size(VyroCutControlMetrics.ToolIconSizeDp.dp)
                        )
                        Spacer(Modifier.height(VyroCutSpacing.XSmallDp.dp))
                        Text(
                            tool.title,
                            color = if (selected) VyroCutPalette.AccentSoft else VyroCutPalette.TextMuted,
                            fontSize = VyroCutTypography.ToolLabelSp.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(VyroCutSpacing.XXSmallDp.dp))
                Spacer(
                    Modifier.size(width = VyroCutControlMetrics.ToolIndicatorWidthDp.dp, height = VyroCutControlMetrics.ToolIndicatorHeightDp.dp)
                        .background(if (selected) VyroCutPalette.AccentSoft else Color.Transparent, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
