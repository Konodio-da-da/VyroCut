package com.vyrocut.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

private val StudioBackground = Color(0xFF131313)
private val StudioPanel = Color(0xFF1C1B1B)
private val StudioAccent = Color(0xFF0FE8B8)
private val StudioText = Color(0xFFE5E2E1)
private val StudioMuted = Color(0xFFB9CAC2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VyroCutApp() }
    }
}

@Composable
private fun VyroCutApp() {
    val context = LocalContext.current
    var selectedMedia by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedMedia = it
            selectedName = it.lastPathSegment?.substringAfterLast('/') ?: "Selected media"
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = StudioBackground) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = StudioAccent, modifier = Modifier.size(30.dp))
                    Text("VyroCut", color = StudioText, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                    Spacer(Modifier.weight(1f))
                    Text("OFFLINE STUDIO", color = StudioMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(18.dp)).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF00382A), modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Create your first project", color = StudioText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Import video, audio, or images stored on this device.", color = StudioMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(22.dp))
                        Button(
                            onClick = { mediaPicker.launch(arrayOf("video/*", "audio/*", "image/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF00382A)),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("  Import media", fontWeight = FontWeight.Bold)
                        }
                        selectedName?.let {
                            Spacer(Modifier.height(14.dp))
                            Text("Selected: $it", color = StudioAccent, fontSize = 12.sp)
                        }
                    }
                }
                Text("Your media stays on your device.", color = StudioMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
