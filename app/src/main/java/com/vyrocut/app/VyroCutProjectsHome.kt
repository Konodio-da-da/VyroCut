package com.vyrocut.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun VyroCutProjectsHome(
    projects: List<Pair<String, String>>,
    activeProjectId: String,
    onNewProject: () -> Unit,
    onImportVideo: () -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val bg = Color(0xFF131313)
    val panel = Color(0xFF202322)
    val accent = Color(0xFF0FE8B8)
    val muted = Color(0xFF929B98)
    Column(
        Modifier.fillMaxSize().background(bg).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, null, tint = muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("VyroCut", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("OFFLINE STUDIO", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Column(
            Modifier.fillMaxWidth().background(panel, RoundedCornerShape(12.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Create something", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Start with your footage and build your next video.", color = muted, fontSize = 13.sp)
            Button(
                onClick = onNewProject,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New project", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onImportVideo,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.Movie, null)
                Spacer(Modifier.width(8.dp))
                Text("Import video")
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${projects.size} saved", color = muted, fontSize = 11.sp)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items(projects, key = { it.first }) { project ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(panel, RoundedCornerShape(10.dp))
                        .clickable { onOpenProject(project.first) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(width = 88.dp, height = 62.dp)
                            .background(Color(0xFF303735), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Movie, null, tint = accent, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(project.second, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (project.first == activeProjectId) "Last opened • Active project" else "On this device", color = muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = accent)
                }
            }
            if (projects.isEmpty()) {
                item { Text("Your projects will appear here.", color = muted, fontSize = 13.sp) }
            }
        }
        Text("Offline editing • Projects autosave on this device", color = muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
