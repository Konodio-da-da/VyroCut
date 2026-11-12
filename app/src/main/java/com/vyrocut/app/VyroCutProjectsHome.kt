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
import androidx.compose.material.icons.filled.PlayArrow
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

private val HomeBackground = Color(0xFF131313)
private val HomePanel = Color(0xFF1C1B1B)
private val HomePanelHigh = Color(0xFF2A2A2A)
private val HomeAccent = Color(0xFF8AFFD8)
private val HomeMuted = Color(0xFFB9CAC2)

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
            .sortedBy { it.second.lowercase() }
            .let { if (ascending) it else it.reversed() }
    }

    Column(
        Modifier.fillMaxSize().background(HomeBackground).padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(HomePanelHigh, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Movie, contentDescription = "VyroCut", tint = HomeAccent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text("VyroCut", color = Color(0xFFE5E2E1), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Surface(color = HomePanelHigh, shape = RoundedCornerShape(50)) {
                Text("OFFLINE", modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = HomeAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onImportVideo, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Import video", tint = HomeMuted)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search projects", color = HomeMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = HomeMuted) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = HomeMuted)
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = HomePanelHigh,
                unfocusedContainerColor = HomePanelHigh,
                focusedBorderColor = HomeAccent.copy(alpha = 0.65f),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = HomeAccent
            )
        )

        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF263B35), HomePanel, Color(0xFF202322))), RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = HomeAccent.copy(alpha = 0.13f), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = HomeAccent, modifier = Modifier.padding(8.dp).size(21.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text("YOUR CREATIVE SPACE", color = HomeAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            }
            Text("Make your next video.", color = Color(0xFFE5E2E1), fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Edit clips, shape your story, and export — all on your device.", color = HomeMuted, fontSize = 12.sp, lineHeight = 17.sp)
            Button(
                onClick = onNewProject,
                modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 2.dp),
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HomeAccent, contentColor = Color(0xFF00382A))
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New project", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            OutlinedButton(
                onClick = onImportVideo,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE5E2E1))
            ) {
                Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import video", fontSize = 13.sp)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", color = Color(0xFFE5E2E1), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Surface(color = HomePanelHigh, shape = RoundedCornerShape(20.dp)) {
                Text("${projects.size}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = HomeMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { ascending = !ascending }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                Icon(Icons.Default.Sort, contentDescription = "Sort projects", tint = HomeMuted, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(3.dp))
                Text(if (ascending) "A–Z" else "Z–A", color = HomeMuted, fontSize = 11.sp)
            }
        }

        if (visibleProjects.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = HomeMuted.copy(alpha = 0.65f), modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(10.dp))
                Text(if (projects.isEmpty()) "No projects yet" else "No matching projects", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(if (projects.isEmpty()) "Create a project or import a video to get started." else "Try another project name.", color = HomeMuted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(visibleProjects, key = { it.first }) { project ->
                    Row(
                        Modifier.fillMaxWidth().background(HomePanel, RoundedCornerShape(11.dp))
                            .clickable { onOpenProject(project.first) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(width = 92.dp, height = 64.dp)
                                .background(Brush.linearGradient(listOf(Color(0xFF34443E), Color(0xFF252A28))), RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = HomeAccent.copy(alpha = 0.9f), modifier = Modifier.size(27.dp))
                            if (project.first == activeProjectId) {
                                Surface(Modifier.align(Alignment.BottomStart).padding(4.dp), color = HomeAccent, shape = RoundedCornerShape(3.dp)) {
                                    Text("OPEN", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = Color(0xFF00382A), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(project.second, color = Color(0xFFE5E2E1), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (project.first == activeProjectId) "Last opened • On this device" else "Saved on this device", color = HomeMuted, fontSize = 10.sp)
                        }
                        Box {
                            IconButton(onClick = { menuProject = project.first }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.MoreHoriz, contentDescription = "Project actions", tint = HomeMuted)
                            }
                            DropdownMenu(expanded = menuProject == project.first, onDismissRequest = { menuProject = null }) {
                                DropdownMenuItem(
                                    text = { Text("Open project") },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                                    onClick = { menuProject = null; onOpenProject(project.first) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menuProject = null; renameValue = project.second; renameTarget = project }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuProject = null; deleteTarget = project }
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(HomeAccent, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text("Private by default • Projects stay on this device", color = HomeMuted, fontSize = 9.sp)
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename project") },
            text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true, label = { Text("Project name") }) },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameValue.trim()
                    if (newName.isNotEmpty()) onRenameProject(target.first, newName)
                    renameTarget = null
                }, enabled = renameValue.trim().isNotEmpty()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete project?") },
            text = { Text("“${target.second}” and its saved edit history will be removed from this device. Your original video files will not be deleted.") },
            confirmButton = {
                TextButton(onClick = { onDeleteProject(target.first); deleteTarget = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
