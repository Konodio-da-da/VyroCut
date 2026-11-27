package com.vyrocut.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HomeBackground = VyroCutPalette.Background
private val HomePanel = VyroCutPalette.Surface
private val HomePanelHigh = VyroCutPalette.SurfaceElevated
private val HomeAccent = VyroCutPalette.Accent
private val HomeMuted = VyroCutPalette.TextMuted

@Composable
internal fun VyroCutProjectsHome(
    projects: List<Pair<String, String>>,
    activeProjectId: String,
    onNewProject: () -> Unit,
    onImportVideo: () -> Unit,
    onOpenProject: (String) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var ascending by remember { mutableStateOf(true) }
    var menuProject by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val visibleProjects = remember(projects, query, ascending) {
        projects.filter { it.second.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.second.lowercase() }.let { if (ascending) it else it.reversed() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(HomeBackground),
        contentPadding = PaddingValues(start = VyroCutSpacing.LargeDp.dp, end = VyroCutSpacing.LargeDp.dp, top = VyroCutSpacing.MediumDp.dp, bottom = VyroCutSpacing.XLargeDp.dp),
        verticalArrangement = Arrangement.spacedBy(VyroCutHomeMetrics.ProjectListItemGapDp.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "VyroCut",
                    color = HomeAccent,
                    fontSize = VyroCutTypography.BrandSp.sp,
                    fontWeight = VyroCutTypography.StrongWeight,
                )
                Spacer(Modifier.width(VyroCutSpacing.MediumDp.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).height(VyroCutHomeMetrics.SearchFieldHeightDp.dp),
                    singleLine = true,
                    placeholder = { Text("Search projects", fontSize = VyroCutHomeMetrics.SearchPlaceholderSp.sp, color = HomeMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = HomeMuted, modifier = Modifier.size(VyroCutHomeMetrics.SearchIconSizeDp.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, "Clear search", tint = HomeMuted)
                            }
                        }
                    },
                    shape = RoundedCornerShape(VyroCutShapes.PillCornerDp.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = HomePanelHigh,
                        unfocusedContainerColor = HomePanelHigh,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = VyroCutPalette.TextPrimary,
                        unfocusedTextColor = VyroCutPalette.TextPrimary,
                        cursorColor = HomeAccent,
                    ),
                )
                Spacer(Modifier.width(VyroCutSpacing.XSmallDp.dp))
                IconButton(onClick = onImportVideo, modifier = Modifier.size(VyroCutHomeMetrics.ImportButtonSizeDp.dp)) {
                    Icon(Icons.Default.Add, "Import video", tint = HomeAccent, modifier = Modifier.size(VyroCutHomeMetrics.ImportIconSizeDp.dp))
                }
            }
        }
        item {
            Surface(
                color = HomePanel,
                shape = RoundedCornerShape(VyroCutShapes.CardCornerDp.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = VyroCutHomeMetrics.OfflineBannerHorizontalPaddingDp.dp, vertical = VyroCutHomeMetrics.OfflineBannerVerticalPaddingDp.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(VyroCutHomeMetrics.OfflineIndicatorSizeDp.dp).background(HomeAccent, CircleShape))
                    Spacer(Modifier.width(VyroCutSpacing.SmallDp.dp))
                    Text("Offline-first", color = HomeAccent, fontSize = VyroCutTypography.LabelSp.sp, fontWeight = VyroCutTypography.StrongWeight)
                    Spacer(Modifier.width(VyroCutSpacing.SmallDp.dp))
                    Text("Projects stay on this device", color = HomeMuted, fontSize = VyroCutTypography.LabelSp.sp)
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .height(VyroCutHomeMetrics.HeroCardHeightDp.dp)
                    .background(
                        Brush.linearGradient(listOf(VyroCutPalette.HeroGradientStart, VyroCutPalette.HeroGradientEnd)),
                        RoundedCornerShape(VyroCutShapes.LargeCornerDp.dp),
                    )
                    .clickable { onNewProject() }
                    .padding(horizontal = VyroCutHomeMetrics.HeroCardHorizontalPaddingDp.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(color = HomeAccent, shape = RoundedCornerShape(VyroCutHomeMetrics.HeroIconCornerDp.dp)) {
                    Icon(
                        Icons.Default.Add,
                        "New project",
                        tint = VyroCutPalette.AccentInk,
                        modifier = Modifier.padding(VyroCutHomeMetrics.HeroIconPaddingDp.dp).size(VyroCutHomeMetrics.HeroIconSizeDp.dp),
                    )
                }
                Spacer(Modifier.height(VyroCutHomeMetrics.HeroTitleGapDp.dp))
                Text("New project", color = VyroCutPalette.TextPrimary, fontSize = VyroCutTypography.HeroTitleSp.sp, fontWeight = VyroCutTypography.StrongWeight)
                Spacer(Modifier.height(VyroCutSpacing.XSmallDp.dp))
                Text(
                    "Import videos from your device and start editing",
                    color = HomeMuted,
                    fontSize = VyroCutTypography.LabelSp.sp,
                    maxLines = 2,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Your projects", color = VyroCutPalette.TextPrimary, fontSize = VyroCutTypography.SectionTitleSp.sp, fontWeight = VyroCutTypography.StrongWeight)
                    Text(
                        "${projects.size} saved on this device",
                        color = HomeMuted,
                        fontSize = VyroCutTypography.LabelSp.sp,
                    )
                }
                TextButton(
                    onClick = { ascending = !ascending },
                    contentPadding = PaddingValues(horizontal = VyroCutSpacing.SmallDp.dp, vertical = VyroCutSpacing.XSmallDp.dp),
                ) {
                    Icon(Icons.Default.Sort, null, tint = HomeMuted, modifier = Modifier.size(VyroCutHomeMetrics.SortIconSizeDp.dp))
                    Spacer(Modifier.width(VyroCutHomeMetrics.SortLabelGapDp.dp))
                    Text(if (ascending) "Name A–Z" else "Name Z–A", color = HomeMuted, fontSize = VyroCutTypography.LabelSp.sp)
                }
            }
        }
        if (visibleProjects.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = VyroCutHomeMetrics.EmptyStateVerticalPaddingDp.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Movie, null, tint = HomeMuted, modifier = Modifier.size(VyroCutHomeMetrics.EmptyStateIconSizeDp.dp))
                    Spacer(Modifier.height(VyroCutSpacing.SmallDp.dp))
                    Text(
                        if (projects.isEmpty()) "No projects yet" else "No matching projects",
                        color = VyroCutPalette.TextPrimary,
                        fontWeight = VyroCutTypography.EmphasisWeight,
                    )
                    Text(
                        if (projects.isEmpty()) "Create a project to start your first edit."
                        else "Try a different project name.",
                        color = HomeMuted,
                        fontSize = VyroCutTypography.LabelSp.sp,
                    )
                }
            }
        } else {
            items(visibleProjects, key = { it.first }) { project ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(HomePanel, RoundedCornerShape(VyroCutHomeMetrics.ProjectCardCornerDp.dp))
                        .clickable { onOpenProject(project.first) }
                        .padding(VyroCutHomeMetrics.ProjectCardPaddingDp.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(width = VyroCutHomeMetrics.ProjectThumbnailWidthDp.dp, height = VyroCutHomeMetrics.ProjectThumbnailHeightDp.dp)
                            .background(
                                Brush.linearGradient(listOf(VyroCutPalette.ThumbnailGradientStart, VyroCutPalette.SurfaceClip)),
                                RoundedCornerShape(VyroCutShapes.SmallCornerDp.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Movie, null, tint = HomeAccent, modifier = Modifier.size(VyroCutHomeMetrics.ProjectThumbnailIconSizeDp.dp))
                        if (project.first == activeProjectId) {
                            Surface(
                                Modifier.align(Alignment.BottomStart).padding(VyroCutSpacing.XSmallDp.dp),
                                color = HomeAccent,
                                shape = RoundedCornerShape(VyroCutHomeMetrics.ProjectBadgeCornerDp.dp),
                            ) {
                                Text(
                                    "OPEN",
                                    Modifier.padding(horizontal = VyroCutHomeMetrics.ProjectBadgeHorizontalPaddingDp.dp, vertical = VyroCutHomeMetrics.ProjectBadgeVerticalPaddingDp.dp),
                                    color = VyroCutPalette.AccentInk,
                                    fontSize = VyroCutTypography.BadgeSp.sp,
                                    fontWeight = VyroCutTypography.StrongWeight,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(VyroCutSpacing.MediumDp.dp))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(VyroCutSpacing.XSmallDp.dp),
                    ) {
                        Text(
                            project.second,
                            color = VyroCutPalette.TextPrimary,
                            fontWeight = VyroCutTypography.EmphasisWeight,
                            fontSize = VyroCutTypography.BodySp.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (project.first == activeProjectId) "Current project • Saved locally" else "Saved on this device",
                            color = HomeMuted,
                            fontSize = VyroCutTypography.CaptionSp.sp,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuProject = project.first }) {
                            Icon(Icons.Default.MoreHoriz, "Project actions", tint = HomeMuted)
                        }
                        DropdownMenu(
                            expanded = menuProject == project.first,
                            onDismissRequest = { menuProject = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open") },
                                onClick = { menuProject = null; onOpenProject(project.first) },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    menuProject = null
                                    renameValue = project.second
                                    renameTarget = project
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menuProject = null; deleteTarget = project },
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("Project name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = renameValue.trim()
                        if (name.isNotEmpty()) onRenameProject(target.first, name)
                        renameTarget = null
                    },
                    enabled = renameValue.trim().isNotEmpty(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete project?") },
            text = {
                Text(
                    "“${target.second}” and its saved edit history will be removed from this device. " +
                        "Original video files will not be deleted.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteProject(target.first); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}
