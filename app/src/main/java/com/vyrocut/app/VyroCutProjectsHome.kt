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
import androidx.compose.ui.text.font.FontWeight
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "VyroCut",
                    color = HomeAccent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).height(50.dp),
                    singleLine = true,
                    placeholder = { Text("Search projects", fontSize = 12.sp, color = HomeMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = HomeMuted, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, "Clear search", tint = HomeMuted)
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = HomePanelHigh,
                        unfocusedContainerColor = HomePanelHigh,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = HomeAccent,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onImportVideo, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Add, "Import video", tint = HomeAccent, modifier = Modifier.size(27.dp))
                }
            }
        }
        item {
            Surface(
                color = HomePanel,
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(HomeAccent, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("Offline-first", color = HomeAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("Projects stay on this device", color = HomeMuted, fontSize = 11.sp)
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .height(176.dp)
                    .background(
                        Brush.linearGradient(listOf(VyroCutPalette.HeroGradientStart, VyroCutPalette.HeroGradientEnd)),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable { onNewProject() }
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(color = HomeAccent, shape = RoundedCornerShape(14.dp)) {
                    Icon(
                        Icons.Default.Add,
                        "New project",
                        tint = VyroCutPalette.AccentInk,
                        modifier = Modifier.padding(13.dp).size(34.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("New project", color = VyroCutPalette.TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Import videos from your device and start editing",
                    color = HomeMuted,
                    fontSize = 11.sp,
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
                    Text("Your projects", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${projects.size} saved on this device",
                        color = HomeMuted,
                        fontSize = 11.sp,
                    )
                }
                TextButton(
                    onClick = { ascending = !ascending },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Sort, null, tint = HomeMuted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (ascending) "Name A–Z" else "Name Z–A", color = HomeMuted, fontSize = 11.sp)
                }
            }
        }
        if (visibleProjects.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Movie, null, tint = HomeMuted, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (projects.isEmpty()) "No projects yet" else "No matching projects",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (projects.isEmpty()) "Create a project to start your first edit."
                        else "Try a different project name.",
                        color = HomeMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        } else {
            items(visibleProjects, key = { it.first }) { project ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(HomePanel, RoundedCornerShape(13.dp))
                        .clickable { onOpenProject(project.first) }
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(76.dp, 68.dp)
                            .background(
                                Brush.linearGradient(listOf(VyroCutPalette.ThumbnailGradientStart, VyroCutPalette.SurfaceClip)),
                                RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Movie, null, tint = HomeAccent, modifier = Modifier.size(27.dp))
                        if (project.first == activeProjectId) {
                            Surface(
                                Modifier.align(Alignment.BottomStart).padding(4.dp),
                                color = HomeAccent,
                                shape = RoundedCornerShape(3.dp),
                            ) {
                                Text(
                                    "OPEN",
                                    Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    color = VyroCutPalette.AccentInk,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            project.second,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (project.first == activeProjectId) "Current project • Saved locally" else "Saved on this device",
                            color = HomeMuted,
                            fontSize = 10.sp,
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
