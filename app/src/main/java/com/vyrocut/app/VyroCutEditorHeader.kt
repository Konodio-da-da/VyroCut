package com.vyrocut.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Project navigation and save actions for the editor workspace.
 *
 * Kept separate from the main editor composition so compact-header behaviour
 * can evolve without increasing the already large editor screen composable.
 */
@Composable
internal fun VyroCutEditorHeader(
    projectName: String,
    windowWidthDp: Int,
    fontScale: Float,
    onNavigateToProjects: () -> Unit,
    onOpenProjects: () -> Unit,
    onSave: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = onNavigateToProjects,
            contentPadding = PaddingValues(
                horizontal = VyroCutEditorChromeMetrics.ProjectNavigationButtonHorizontalPaddingDp.dp,
                vertical = 0.dp,
            ),
        ) {
            Text(
                "‹ Projects",
                color = VyroCutPalette.Accent,
                fontSize = VyroCutTypography.SmallBodySp.sp,
            )
        }
        Icon(
            Icons.Default.Movie,
            contentDescription = null,
            tint = VyroCutPalette.Accent,
            modifier = Modifier.size(VyroCutEditorChromeMetrics.BrandIconSizeDp.dp),
        )
        if (editorHeaderShowsBrandTitle(windowWidthDp, fontScale)) {
            Text(
                "VyroCut",
                color = VyroCutPalette.TextPrimary,
                fontSize = VyroCutTypography.BrandSp.sp,
                fontWeight = VyroCutTypography.StrongWeight,
                modifier = Modifier.padding(
                    start = VyroCutEditorChromeMetrics.BrandTitleStartPaddingDp.dp,
                ),
            )
        }
        TextButton(
            modifier = Modifier.weight(1f),
            onClick = onOpenProjects,
            contentPadding = PaddingValues(
                horizontal = VyroCutEditorChromeMetrics.ProjectNavigationButtonHorizontalPaddingDp.dp,
                vertical = 0.dp,
            ),
        ) {
            Text(
                projectName,
                color = VyroCutPalette.Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onSave) {
            Icon(
                Icons.Default.Save,
                contentDescription = "Save project",
                tint = VyroCutPalette.Accent,
            )
        }
    }
}
