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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
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

private val HomeBackground = Color(0xFF111111)
private val HomePanel = Color(0xFF1C1C1C)
private val HomePanelHigh = Color(0xFF292929)
private val HomeAccent = Color(0xFF12E6B8)
private val HomeMuted = Color(0xFFB8C4C0)

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
    var selectedTab by remember { mutableStateOf("Edit") }
    val visibleProjects = remember(projects, query, ascending) {
        projects.filter { it.second.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.second.lowercase() }.let { if (ascending) it else it.reversed() }
    }

    Column(Modifier.fillMaxSize().background(HomeBackground)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { }, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Default.Settings, "Settings", tint = HomeMuted, modifier = Modifier.size(23.dp))
                    }
                    Surface(color = HomePanelHigh, shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudQueue, null, tint = HomeAccent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PRO", color = HomeAccent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.weight(1f).height(48.dp),
                        singleLine = true,
                        placeholder = { Text("Projects", fontSize = 13.sp, color = HomeMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = HomeMuted, modifier = Modifier.size(19.dp)) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear", tint = HomeMuted) } },
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = HomePanelHigh, unfocusedContainerColor = HomePanelHigh,
                            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = HomeAccent
                        )
                    )
                    IconButton(onClick = onImportVideo) { Icon(Icons.Default.CameraAlt, "Import media", tint = HomeMuted) }
                    Surface(color = HomeAccent, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(42.dp).clickable { selectedTab = "Me" }) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, "Profile", tint = Color(0xFF00382A)) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = HomePanel, shape = CircleShape) {
                        Icon(Icons.Default.HelpOutline, "Help", tint = HomeMuted, modifier = Modifier.padding(12.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(color = HomePanelHigh, shape = RoundedCornerShape(30.dp)) {
                        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(HomeAccent, CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text("PRO PASS", color = HomeAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { ascending = !ascending }) { Icon(Icons.Default.FilterList, "Filter projects", tint = HomeMuted) }
                    IconButton(onClick = { ascending = !ascending }) { Icon(Icons.Default.Tune, "Sort projects", tint = HomeMuted) }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().height(220.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF202725), Color(0xFF171918))), RoundedCornerShape(14.dp))
                        .clickable { onNewProject() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(color = HomeAccent, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Add, "New project", tint = Color(0xFF00382A), modifier = Modifier.padding(17.dp).size(42.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("New project", color = Color(0xFFE8E8E8), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("Tap to import clips, audios, or canvas presets", color = HomeMuted, fontSize = 12.sp)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf("Camera" to Icons.Default.CameraAlt, "Retouch" to Icons.Default.AutoAwesome, "AutoCut" to Icons.Default.Movie, "Prompter" to Icons.Default.Person).forEach { (label, icon) ->
                        Column(
                            Modifier.weight(1f).height(86.dp).background(HomePanel, RoundedCornerShape(11.dp))
                                .clickable { if (label == "Camera") onImportVideo() },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, null, tint = HomeAccent, modifier = Modifier.size(25.dp))
                            Spacer(Modifier.height(7.dp))
                            Text(label, color = Color(0xFFE3E3E3), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().background(HomePanel, RoundedCornerShape(12.dp)).padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudQueue, null, tint = HomeAccent, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Cloud space", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("Local storage", color = HomeMuted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFF414141), CircleShape)) {
                        Box(Modifier.fillMaxWidth(0.24f).fillMaxHeight().background(HomeAccent, CircleShape))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Projects saved on this device", color = HomeMuted, fontSize = 10.sp)
                        Spacer(Modifier.weight(1f))
                        Text("Offline mode", color = HomeAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Projects", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Surface(color = HomePanelHigh, shape = CircleShape) {
                        Text("${projects.size}", Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = HomeMuted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { ascending = !ascending }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                        Icon(Icons.Default.Sort, null, tint = HomeMuted, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (ascending) "A–Z" else "Z–A", color = HomeMuted, fontSize = 11.sp)
                    }
                }
            }
            if (visibleProjects.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Movie, null, tint = HomeMuted, modifier = Modifier.size(38.dp))
                        Text(if (projects.isEmpty()) "No projects yet" else "No matching projects", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Create a project or import a video to get started.", color = HomeMuted, fontSize = 11.sp)
                    }
                }
            } else {
                items(visibleProjects, key = { it.first }) { project ->
                    Row(
                        Modifier.fillMaxWidth().background(HomePanel, RoundedCornerShape(11.dp))
                            .clickable { onOpenProject(project.first) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(104.dp, 72.dp).background(Brush.linearGradient(listOf(Color(0xFF34443E), Color(0xFF252A28))), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Movie, null, tint = HomeAccent, modifier = Modifier.size(28.dp))
                            if (project.first == activeProjectId) Surface(Modifier.align(Alignment.BottomStart).padding(4.dp), color = HomeAccent, shape = RoundedCornerShape(3.dp)) {
                                Text("OPEN", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = Color(0xFF00382A), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(project.second, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (project.first == activeProjectId) "Today • On this device" else "Saved on this device", color = HomeMuted, fontSize = 10.sp)
                        }
                        Box {
                            IconButton(onClick = { menuProject = project.first }) { Icon(Icons.Default.MoreHoriz, "Project actions", tint = HomeMuted) }
                            DropdownMenu(expanded = menuProject == project.first, onDismissRequest = { menuProject = null }) {
                                DropdownMenuItem(text = { Text("Open") }, onClick = { menuProject = null; onOpenProject(project.first) })
                                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuProject = null; renameValue = project.second; renameTarget = project })
                                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuProject = null; deleteTarget = project })
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(62.dp).background(Color(0xFF151515)),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Edit", "Templates", "Inbox", "Me").forEach { tab ->
                Column(Modifier.clickable { selectedTab = tab }.padding(horizontal = 12.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (tab == "Edit") Icons.Default.Movie else if (tab == "Templates") Icons.Default.Checklist else if (tab == "Inbox") Icons.Default.CloudQueue else Icons.Default.Person, null, tint = if (selectedTab == tab) HomeAccent else HomeMuted, modifier = Modifier.size(21.dp))
                    Text(tab, color = if (selectedTab == tab) HomeAccent else HomeMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null }, title = { Text("Rename project") },
            text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true, label = { Text("Project name") }) },
            confirmButton = { TextButton(onClick = { val name = renameValue.trim(); if (name.isNotEmpty()) onRenameProject(target.first, name); renameTarget = null }, enabled = renameValue.trim().isNotEmpty()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null }, title = { Text("Delete project?") },
            text = { Text("“${target.second}” and its saved edit history will be removed from this device. Original video files will not be deleted.") },
            confirmButton = { TextButton(onClick = { onDeleteProject(target.first); deleteTarget = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
