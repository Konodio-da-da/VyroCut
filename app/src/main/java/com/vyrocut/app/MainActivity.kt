package com.vyrocut.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.media.MediaMetadataRetriever
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject

private val StudioBackground = Color(0xFF111313)
private val StudioPanel = Color(0xFF1C2221)
private val StudioAccent = Color(0xFF0FE8B8)
private val StudioText = Color(0xFFE5EDEA)
private val StudioMuted = Color(0xFF9BAAA5)

private data class EditClip(
    val id: String,
    val uri: String,
    val name: String,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
) {
    val durationMs: Long get() = (sourceEndMs - sourceStartMs).coerceAtLeast(0L)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VyroCutApp() }
    }
}

@Composable
private fun VyroCutApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("vyrocut_project", 0) }
    var clips by remember { mutableStateOf(loadProject(preferences)) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    val selectedClip = clips.getOrNull(selectedIndex)

    val player = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = false } }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(selectedClip?.id, selectedClip?.sourceStartMs, selectedClip?.sourceEndMs) {
        val clip = selectedClip
        if (clip != null) {
            val item = MediaItem.Builder().setUri(Uri.parse(clip.uri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.sourceStartMs)
                        .setEndPositionMs(clip.sourceEndMs).build()
                ).build()
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = false
            isPlaying = false
        } else {
            player.clearMediaItems()
            isPlaying = false
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun saveProject(items: List<EditClip>) {
        preferences.edit().putString("clips", encodeProject(items)).apply()
    }
    fun changed(items: List<EditClip>) {
        clips = items
        selectedIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        saveProject(items)
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val retriever = MediaMetadataRetriever()
            val duration = runCatching {
                retriever.setDataSource(context, it)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
            }.getOrNull()?.coerceAtLeast(1L) ?: 1L
            runCatching { retriever.release() }
            val name = MediaMetadataReader.displayName(context.contentResolver, it)
            changed(clips + EditClip(java.util.UUID.randomUUID().toString(), it.toString(), name, 0L, duration))
            selectedIndex = clips.lastIndex
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = StudioBackground) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Movie, null, tint = StudioAccent, modifier = Modifier.size(28.dp))
                    Text("VyroCut", color = StudioText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp))
                    Spacer(Modifier.weight(1f))
                    Text("AUTOSAVED", color = StudioAccent, fontSize = 10.sp, letterSpacing = 1.sp)
                    IconButton(onClick = { saveProject(clips) }) { Icon(Icons.Default.Save, "Save project", tint = StudioAccent) }
                }
                Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    if (selectedClip == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Movie, null, tint = StudioMuted, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Import a video to start editing", color = StudioMuted)
                        }
                    } else {
                        AndroidView(
                            factory = { viewContext -> PlayerView(viewContext).apply {
                                useController = true
                            } },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(selectedClip?.name ?: "No media selected", color = StudioText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (selectedClip != null) "Segment ${selectedIndex + 1} of ${clips.size} • ${formatTime(selectedClip.durationMs)}" else "Your project is stored locally", color = StudioMuted, fontSize = 11.sp)
                    }
                    FilledTonalButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, enabled = selectedClip != null,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = StudioAccent, contentColor = Color(0xFF00382A))) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TIMELINE", color = StudioMuted, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${clips.size} clips", color = StudioMuted, fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    clips.forEachIndexed { index, clip ->
                        Column(Modifier.width(116.dp).background(if (index == selectedIndex) Color(0xFF16483D) else StudioPanel, RoundedCornerShape(10.dp))
                            .clickable { selectedIndex = index }.padding(10.dp)) {
                            Text("CLIP ${index + 1}", color = if (index == selectedIndex) StudioAccent else StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text(clip.name, color = StudioText, fontSize = 11.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Text(formatTime(clip.durationMs), color = StudioAccent, fontSize = 10.sp)
                        }
                    }
                    if (clips.isEmpty()) Text("Your clips will appear here", color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { mediaPicker.launch(arrayOf("video/*")) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Add, null); Text("Import", maxLines = 1)
                    }
                    OutlinedButton(onClick = {
                        val clip = selectedClip ?: return@OutlinedButton
                        val midpoint = clip.sourceStartMs + clip.durationMs / 2
                        if (midpoint > clip.sourceStartMs && midpoint < clip.sourceEndMs) {
                            val left = clip.copy(id = java.util.UUID.randomUUID().toString(), sourceEndMs = midpoint)
                            val right = clip.copy(id = java.util.UUID.randomUUID().toString(), sourceStartMs = midpoint)
                            changed(clips.toMutableList().apply { removeAt(selectedIndex); add(selectedIndex, right); add(selectedIndex, left) })
                        }
                    }, enabled = selectedClip != null && selectedClip.durationMs >= 2L, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.ContentCut, null); Text("Split", maxLines = 1)
                    }
                    OutlinedButton(onClick = {
                        val clip = selectedClip ?: return@OutlinedButton
                        if (clip.durationMs > 1000L) changed(clips.toMutableList().apply { this[selectedIndex] = clip.copy(sourceStartMs = clip.sourceStartMs + 1000L) })
                    }, enabled = selectedClip != null && selectedClip.durationMs > 1000L, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Trim 1s", maxLines = 1) }
                    IconButton(onClick = { if (selectedClip != null) changed(clips.toMutableList().apply { removeAt(selectedIndex) }) }, enabled = selectedClip != null) {
                        Icon(Icons.Default.Delete, "Delete selected clip", tint = Color(0xFFFF8A80))
                    }
                }
                Text("Offline editing • Project autosaves on this device", color = StudioMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

private fun encodeProject(clips: List<EditClip>): String {
    val array = JSONArray()
    clips.forEach { clip -> array.put(JSONObject().apply {
        put("id", clip.id); put("uri", clip.uri); put("name", clip.name)
        put("start", clip.sourceStartMs); put("end", clip.sourceEndMs)
    }) }
    return array.toString()
}

private fun loadProject(preferences: android.content.SharedPreferences): List<EditClip> {
    val raw = preferences.getString("clips", null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val start = item.getLong("start"); val end = item.getLong("end")
            if (end <= start) null else EditClip(item.getString("id"), item.getString("uri"), item.optString("name", "Imported video"), start, end)
        }
    }.getOrDefault(emptyList())
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}
