package com.vyrocut.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaCodec
import android.media.MediaFormat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.nio.ByteOrder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val StudioBackground = Color(0xFF111313)
private val StudioPanel = Color(0xFF1C2221)
private val StudioAccent = Color(0xFF0FE8B8)
private val StudioText = Color(0xFFE5EDEA)
private val StudioMuted = Color(0xFF9BAAA5)

private data class ProjectInfo(val id: String, val name: String)

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

@OptIn(UnstableApi::class)
@Composable
private fun VyroCutApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("vyrocut_project", 0) }
    val projects = remember { mutableStateListOf<ProjectInfo>().apply { addAll(loadProjectIndex(preferences)) } }
    var activeProjectId by remember {
        mutableStateOf(preferences.getString("active_project_id", projects.first().id) ?: projects.first().id)
    }
    var clips by remember { mutableStateOf(loadProject(preferences, activeProjectId)) }
    var showProjects by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var projectToRename by remember { mutableStateOf<ProjectInfo?>(null) }
    var renameProjectName by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<ProjectInfo?>(null) }
    val undoStack = remember(activeProjectId) { mutableStateListOf<List<EditClip>>().apply { addAll(loadProjectHistory(preferences, activeProjectId, "undo")) } }
    val redoStack = remember(activeProjectId) { mutableStateListOf<List<EditClip>>().apply { addAll(loadProjectHistory(preferences, activeProjectId, "redo")) } }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var exportedUri by remember { mutableStateOf<Uri?>(null) }
    var activeTransformer by remember { mutableStateOf<Transformer?>(null) }
    val selectedClip = clips.getOrNull(selectedIndex)
    var timelinePositionMs by remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }
    val clipDurationsMs = remember(clips) { clips.map { it.durationMs } }
    val timelineDurationMs = clipDurationsMs.sum().coerceAtLeast(1L)

    val player = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = false } }
    DisposableEffect(player) { onDispose { player.release() } }
    // Load the ordered edit decision list as a real ExoPlayer playlist. Each source
    // segment is clipped non-destructively; playback naturally advances between clips.
    LaunchedEffect(clips) {
        if (clips.isEmpty()) {
            player.clearMediaItems()
            isPlaying = false
        } else {
            val items = clips.map { clip ->
                MediaItem.Builder()
                    .setMediaId(clip.id)
                    .setUri(Uri.parse(clip.uri))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.sourceStartMs)
                            .setEndPositionMs(clip.sourceEndMs)
                            .build()
                    )
                    .build()
            }
            val startIndex = selectedIndex.coerceIn(0, items.lastIndex)
            player.setMediaItems(items, startIndex, 0L)
            player.prepare()
            player.playWhenReady = false
            isPlaying = false
        }
    }
    LaunchedEffect(player, clips, isPlaying) {
        while (true) {
            if (!isScrubbing && clips.isNotEmpty() && player.currentMediaItemIndex in clips.indices) {
                val currentIndex = player.currentMediaItemIndex
                timelinePositionMs = calculateTimelinePositionMs(
                    clipDurationsMs,
                    currentIndex,
                    player.currentPosition,
                ).coerceIn(0L, timelineDurationMs).toFloat()
            }
            delay(200)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val activeIndex = player.currentMediaItemIndex
                if (activeIndex >= 0) selectedIndex = activeIndex
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun saveProject(items: List<EditClip>) {
        preferences.edit()
            .putString("project_$activeProjectId", encodeProject(items))
            .putString("active_project_id", activeProjectId)
            .apply()
    }
    fun switchProject(project: ProjectInfo) {
        if (project.id == activeProjectId) return
        saveProject(clips)
        activeProjectId = project.id
        preferences.edit().putString("active_project_id", project.id).apply()
        clips = loadProject(preferences, project.id)
        selectedIndex = 0
        // History state is keyed by activeProjectId and reloads from that
        // project's persisted snapshots after this state change recomposes.
        timelinePositionMs = 0f
        exportMessage = "Opened ${project.name}"
    }
    fun createProject() {
        val name = newProjectName.trim().ifBlank { "Untitled project" }
        val project = ProjectInfo(java.util.UUID.randomUUID().toString(), name)
        projects.add(project)
        saveProjectIndex(preferences, projects)
        preferences.edit().putString("project_${project.id}", encodeProject(emptyList())).apply()
        newProjectName = ""
        showProjects = false
        switchProject(project)
    }
    fun renameProject(project: ProjectInfo) {
        val name = renameProjectName.trim()
        if (name.isBlank()) return
        val index = projects.indexOfFirst { it.id == project.id }
        if (index < 0) return
        projects[index] = project.copy(name = name)
        saveProjectIndex(preferences, projects)
        projectToRename = null
        exportMessage = "Renamed project to $name"
    }
    fun deleteProject(project: ProjectInfo) {
        if (projects.size <= 1) return
        if (project.id == activeProjectId) {
            val replacement = projects.firstOrNull { it.id != project.id } ?: return
            switchProject(replacement)
        }
        projects.removeAll { it.id == project.id }
        preferences.edit()
            .remove("project_${project.id}")
            .remove("project_${project.id}_undo")
            .remove("project_${project.id}_redo")
            .apply()
        saveProjectIndex(preferences, projects)
        projectToDelete = null
        exportMessage = "Deleted ${project.name}"
    }
    fun changed(items: List<EditClip>) {
        if (items == clips) return
        undoStack.add(clips.toList())
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
        clips = items.toList()
        saveProjectHistory(preferences, activeProjectId, undoStack, redoStack)
        selectedIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        saveProject(items)
    }
    fun undoEdit() {
        if (undoStack.isEmpty()) return
        redoStack.add(clips.toList())
        val previous = undoStack.removeAt(undoStack.lastIndex)
        clips = previous
        selectedIndex = selectedIndex.coerceIn(0, (previous.size - 1).coerceAtLeast(0))
        saveProject(previous)
        saveProjectHistory(preferences, activeProjectId, undoStack, redoStack)
        exportMessage = "Undo completed"
    }
    fun redoEdit() {
        if (redoStack.isEmpty()) return
        undoStack.add(clips.toList())
        val next = redoStack.removeAt(redoStack.lastIndex)
        clips = next
        selectedIndex = selectedIndex.coerceIn(0, (next.size - 1).coerceAtLeast(0))
        saveProject(next)
        saveProjectHistory(preferences, activeProjectId, undoStack, redoStack)
        exportMessage = "Redo completed"
    }

    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val projectName = projects.firstOrNull { it.id == activeProjectId }?.name ?: "VyroCut project"
            val backup = encodeProjectBackup(projectName, clips)
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup) }
                    ?: error("Could not open the backup destination")
            }
            exportMessage = if (result.isSuccess) "Project backup saved" else "Backup failed: ${result.exceptionOrNull()?.message}"
        }
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { backupUri ->
            val result = runCatching {
                context.contentResolver.openInputStream(backupUri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read the selected backup")
            }.mapCatching(::decodeProjectBackup)
            result.onSuccess { backup ->
                val imported = ProjectInfo(java.util.UUID.randomUUID().toString(), backup.name)
                projects.add(imported)
                saveProjectIndex(preferences, projects)
                preferences.edit().putString("project_${imported.id}", encodeProject(backup.clips)).apply()
                switchProject(imported)
                showProjects = false
                exportMessage = "Restored ${imported.name} (${backup.second.size} clips)"
            }.onFailure { error ->
                exportMessage = "Could not restore backup: ${error.message}"
            }
        }
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
                    TextButton(onClick = { showProjects = true }) {
                        Text(projects.firstOrNull { it.id == activeProjectId }?.name ?: "Project", color = StudioAccent, maxLines = 1)
                    }
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = timelinePositionMs.coerceIn(0f, timelineDurationMs.toFloat()),
                        onValueChange = {
                            isScrubbing = true
                            timelinePositionMs = it
                        },
                        onValueChangeFinished = {
                            resolveTimelineSeekTarget(clipDurationsMs, timelinePositionMs.toLong())?.let { target ->
                                selectedIndex = target.clipIndex
                                player.seekTo(target.clipIndex, target.positionMs)
                            }
                            isScrubbing = false
                        },
                        enabled = clips.isNotEmpty(),
                        colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(timelinePositionMs.toLong()), color = StudioMuted, fontSize = 10.sp)
                        Text(formatTime(timelineDurationMs), color = StudioMuted, fontSize = 10.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TIMELINE", color = StudioMuted, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { undoEdit() }, enabled = undoStack.isNotEmpty(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Undo", fontSize = 11.sp)
                    }
                    TextButton(onClick = { redoEdit() }, enabled = redoStack.isNotEmpty(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Redo", fontSize = 11.sp)
                    }
                    Text("${clips.size} clips", color = StudioMuted, fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    clips.forEachIndexed { index, clip ->
                        Column(Modifier.width(116.dp).background(if (index == selectedIndex) Color(0xFF16483D) else StudioPanel, RoundedCornerShape(10.dp))
                            .clickable {
                                selectedIndex = index
                                if (index < player.mediaItemCount) player.seekTo(index, 0L)
                            }.padding(10.dp)) {
                            Text("CLIP ${index + 1}", color = if (index == selectedIndex) StudioAccent else StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            ClipThumbnail(clip = clip, modifier = Modifier.fillMaxWidth().height(62.dp))
                            Spacer(Modifier.height(4.dp))
                            AudioWaveform(clip = clip, modifier = Modifier.fillMaxWidth().height(24.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (clips.isNotEmpty() && !isExporting) {
                                val outputRoot = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                                    ?: File(context.filesDir, "Movies")
                                val outputDirectory = File(outputRoot, "VyroCut").apply { mkdirs() }
                                val outputFile = File(outputDirectory, "VyroCut_${System.currentTimeMillis()}.mp4")
                                val editedItems = clips.map { clip ->
                                    val source = MediaItem.Builder()
                                        .setUri(Uri.parse(clip.uri))
                                        .setClippingConfiguration(
                                            MediaItem.ClippingConfiguration.Builder()
                                                .setStartPositionMs(clip.sourceStartMs)
                                                .setEndPositionMs(clip.sourceEndMs)
                                                .build()
                                        ).build()
                                    EditedMediaItem.Builder(source).build()
                                }
                                // Media3 1.5.1 exposes the list-based sequence builder.
                                @Suppress("DEPRECATION")
                                val sequence = EditedMediaItemSequence.Builder(editedItems).build()
                                val composition = Composition.Builder(sequence).build()
                                val transformer = Transformer.Builder(context)
                                    .addListener(object : Transformer.Listener {
                                        override fun onCompleted(composition: Composition, result: ExportResult) {
                                            isExporting = false
                                            activeTransformer = null
                                            var pendingDestination: Uri? = null
                                            runCatching {
                                                val values = ContentValues().apply {
                                                    put(MediaStore.Video.Media.DISPLAY_NAME, outputFile.name)
                                                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VyroCut")
                                                    put(MediaStore.Video.Media.IS_PENDING, 1)
                                                }
                                                pendingDestination = context.contentResolver.insert(
                                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                                                ) ?: error("Could not create a Movies library entry")
                                                context.contentResolver.openOutputStream(pendingDestination!!)?.use { output ->
                                                    FileInputStream(outputFile).use { input -> input.copyTo(output) }
                                                } ?: error("Could not write the exported video")
                                                val publishedRows = context.contentResolver.update(
                                                    pendingDestination!!,
                                                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                                                    null,
                                                    null
                                                )
                                                check(publishedRows > 0) { "Could not finish publishing the video" }
                                                exportedUri = pendingDestination
                                                exportMessage = "Export complete • Saved to Movies/VyroCut"
                                                outputFile.delete()
                                            }.onFailure { error ->
                                                pendingDestination?.let { uri ->
                                                    runCatching { context.contentResolver.delete(uri, null, null) }
                                                }
                                                exportMessage = "Export finished, but saving to Movies failed: ${error.message}"
                                            }
                                        }
                                        override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                                            isExporting = false
                                            activeTransformer = null
                                            exportMessage = "Export failed: ${exception.message ?: "Unsupported media"}"
                                            outputFile.delete()
                                        }
                                    }).build()
                                activeTransformer = transformer
                                exportedUri = null
                                exportMessage = "Preparing MP4 export…"
                                isExporting = true
                                player.pause()
                                runCatching { transformer.start(composition, outputFile.absolutePath) }
                                    .onFailure { error ->
                                        isExporting = false
                                        activeTransformer = null
                                        outputFile.delete()
                                        exportMessage = "Could not start export: ${error.message}"
                                    }
                            }
                        },
                        enabled = clips.isNotEmpty() && !isExporting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF00382A))
                    ) {
                        Text(if (isExporting) "Exporting…" else "Export MP4", fontWeight = FontWeight.Bold)
                    }
                    if (isExporting) {
                        OutlinedButton(onClick = {
                            activeTransformer?.cancel()
                            isExporting = false
                            activeTransformer = null
                            exportMessage = "Export cancelled"
                        }) {
                            Text("Cancel")
                        }
                    }
                    exportedUri?.let { uri ->
                        OutlinedButton(onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Share exported video"))
                        }) { Text("Share") }
                    }
                }
                exportMessage?.let { Text(it, color = StudioAccent, fontSize = 11.sp) }
                Text("Offline editing • Project autosaves on this device", color = StudioMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                if (showProjects) {
                    AlertDialog(
                        onDismissRequest = { showProjects = false },
                        title = { Text("Your projects") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                projects.forEach { project ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = { switchProject(project); showProjects = false },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                if (project.id == activeProjectId) "✓  ${project.name}" else project.name,
                                                modifier = Modifier.fillMaxWidth(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        TextButton(onClick = {
                                            projectToRename = project
                                            renameProjectName = project.name
                                        }) { Text("Rename", fontSize = 11.sp) }
                                        TextButton(
                                            onClick = { projectToDelete = project },
                                            enabled = projects.size > 1
                                        ) { Text("Delete", color = if (projects.size > 1) Color(0xFFFF8A80) else StudioMuted, fontSize = 11.sp) }
                                    }
                                }
                                HorizontalDivider()
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val name = projects.firstOrNull { it.id == activeProjectId }?.name ?: "VyroCut"
                                            backupExporter.launch("${name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.vyrocut.json")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Backup") }
                                    OutlinedButton(
                                        onClick = { backupImporter.launch(arrayOf("application/json", "text/json", "*/*")) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Restore") }
                                }
                                Text(
                                    "Backups save your edit list and media references. Keep the original video files available; the videos themselves are not copied into the backup.",
                                    color = StudioMuted,
                                    fontSize = 10.sp
                                )
                                OutlinedTextField(
                                    value = newProjectName,
                                    onValueChange = { newProjectName = it },
                                    label = { Text("New project name") },
                                    singleLine = true
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { createProject() }) { Text("Create project") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showProjects = false }) { Text("Close") }
                        }
                    )
                }
                projectToRename?.let { project ->
                    AlertDialog(
                        onDismissRequest = { projectToRename = null },
                        title = { Text("Rename project") },
                        text = {
                            OutlinedTextField(
                                value = renameProjectName,
                                onValueChange = { renameProjectName = it },
                                label = { Text("Project name") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { renameProject(project) },
                                enabled = renameProjectName.isNotBlank()
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { projectToRename = null }) { Text("Cancel") }
                        }
                    )
                }
                projectToDelete?.let { project ->
                    AlertDialog(
                        onDismissRequest = { projectToDelete = null },
                        title = { Text("Delete project?") },
                        text = { Text("Delete \"${project.name}\" and its saved edit list? This cannot be undone.") },
                        confirmButton = {
                            TextButton(onClick = { deleteProject(project) }) {
                                Text("Delete", color = Color(0xFFFF8A80))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { projectToDelete = null }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}

 
@Composable
private fun ClipThumbnail(clip: EditClip, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, clip.uri, clip.sourceStartMs) {
        value = withContext(Dispatchers.IO) {
            // Cache frames on disk so scrolling away, reopening the project, or
            // recomposing the timeline does not repeatedly decode the same video.
            val cacheDirectory = File(context.cacheDir, "vyrocut-thumbnails").apply { mkdirs() }
            val cacheKey = MessageDigest.getInstance("SHA-256")
                .digest("${clip.uri}:${clip.sourceStartMs}".toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
            val cacheFile = File(cacheDirectory, "$cacheKey.jpg")
            val cachedBitmap = runCatching {
                if (cacheFile.isFile) BitmapFactory.decodeFile(cacheFile.absolutePath) else null
            }.getOrNull()
            if (cachedBitmap != null) {
                cachedBitmap
            } else {
                val retriever = MediaMetadataRetriever()
                val frame = try {
                    retriever.setDataSource(context, Uri.parse(clip.uri))
                    retriever.getFrameAtTime(
                        (clip.sourceStartMs * 1000L).coerceAtLeast(0L),
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                } catch (_: Exception) {
                    null
                } finally {
                    runCatching { retriever.release() }
                }
                if (frame != null) {
                    runCatching {
                        cacheFile.outputStream().use { output ->
                            frame.compress(Bitmap.CompressFormat.JPEG, 82, output)
                        }
                    }
                }
                frame
            }
        }
    }
    Box(
        modifier = modifier.background(Color(0xFF293330), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = "Video clip thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Default.Movie, contentDescription = null, tint = StudioMuted)
        }
    }
}


@Composable
private fun AudioWaveform(clip: EditClip, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bars by produceState<List<Float>>(initialValue = emptyList(), clip.uri, clip.sourceStartMs, clip.sourceEndMs) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeAudioWaveform(context, clip, 48) }.getOrDefault(emptyList())
        }
    }
    Canvas(modifier = modifier) {
        if (bars.isEmpty()) {
            drawLine(
                color = StudioMuted.copy(alpha = 0.45f),
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx()
            )
        } else {
            val gap = 1.5.dp.toPx()
            val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1f)
            bars.forEachIndexed { index, amplitude ->
                val barHeight = (amplitude.coerceIn(0.06f, 1f) * size.height).coerceAtLeast(2.dp.toPx())
                val x = index * (barWidth + gap)
                drawRoundRect(
                    color = StudioAccent.copy(alpha = 0.45f + amplitude.coerceIn(0f, 1f) * 0.55f),
                    topLeft = androidx.compose.ui.geometry.Offset(x, (size.height - barHeight) / 2f),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                )
            }
        }
    }
}

private fun decodeAudioWaveform(
    context: android.content.Context,
    clip: EditClip,
    bucketCount: Int
): List<Float> {
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    val peaks = FloatArray(bucketCount)
    try {
        extractor.setDataSource(context, Uri.parse(clip.uri), null)
        var selectedFormat: MediaFormat? = null
        var audioTrack = -1
        for (index in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(index)
            val candidateMime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
            if (candidateMime.startsWith("audio/")) {
                selectedFormat = candidate
                audioTrack = index
                break
            }
        }
        val format = selectedFormat ?: return emptyList()
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
        val startUs = clip.sourceStartMs * 1000L
        val endUs = clip.sourceEndMs * 1000L
        val durationUs = (endUs - startUs).coerceAtLeast(1L)
        extractor.selectTrack(audioTrack)
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        var pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var safetyCounter = 0
        while (!outputDone && safetyCounter++ < 20000) {
            if (!inputDone) {
                val inputIndex = decoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    val sampleTime = extractor.sampleTime
                    if (inputBuffer == null || sampleTime < 0L || sampleTime >= endUs) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, maxOf(sampleTime, endUs), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, endUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, sampleTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }
            }
            val outputIndex = decoder.dequeueOutputBuffer(info, 10000)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val decoded = decoder.outputFormat
                    sampleRate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
                    channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = decoded.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                }
                outputIndex >= 0 -> {
                    val output = decoder.getOutputBuffer(outputIndex)
                    if (output != null && info.size > 0) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        val pcm = output.slice().order(ByteOrder.LITTLE_ENDIAN)
                        val bytesPerSample = when (pcmEncoding) {
                            android.media.AudioFormat.ENCODING_PCM_8BIT -> 1
                            android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                            android.media.AudioFormat.ENCODING_PCM_FLOAT,
                            android.media.AudioFormat.ENCODING_PCM_32BIT -> 4
                            else -> 2
                        }
                        val frameBytes = bytesPerSample * channels
                        val frameCount = if (frameBytes > 0) pcm.remaining() / frameBytes else 0
                        for (frame in 0 until frameCount) {
                            val timeUs = info.presentationTimeUs + frame * 1_000_000L / sampleRate
                            var peak = 0f
                            // Consume preroll too, keeping PCM channel reads aligned.
                            repeat(channels) {
                                val sample = when (pcmEncoding) {
                                    android.media.AudioFormat.ENCODING_PCM_8BIT -> ((pcm.get().toInt() and 0xff) - 128) / 128f
                                    android.media.AudioFormat.ENCODING_PCM_FLOAT -> pcm.float.coerceIn(-1f, 1f)
                                    android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                                        val b0 = pcm.get().toInt() and 0xff
                                        val b1 = pcm.get().toInt() and 0xff
                                        val b2 = pcm.get().toInt()
                                        (((b2 shl 16) or (b1 shl 8) or b0).toFloat() / 8_388_608f).coerceIn(-1f, 1f)
                                    }
                                    android.media.AudioFormat.ENCODING_PCM_32BIT ->
                                        (pcm.int.toDouble() / Int.MAX_VALUE.toDouble()).toFloat().coerceIn(-1f, 1f)
                                    else -> pcm.short.toFloat() / Short.MAX_VALUE.toFloat()
                                }
                                peak = maxOf(peak, kotlin.math.abs(sample))
                            }
                            if (timeUs >= startUs && timeUs < endUs) {
                                val bucket = (((timeUs - startUs).toDouble() / durationUs) * bucketCount)
                                    .toInt().coerceIn(0, bucketCount - 1)
                                peaks[bucket] = maxOf(peaks[bucket], peak)
                            }
                        }
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(outputIndex, false)
                    if (eos) outputDone = true
                }
            }
        }
        return peaks.map { it.coerceIn(0f, 1f) }
    } catch (_: Exception) {
        return emptyList()
    } finally {
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        runCatching { extractor.release() }
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

private data class ProjectBackup(val name: String, val clips: List<EditClip>)

private fun encodeProjectBackup(name: String, clips: List<EditClip>): String =
    JSONObject().apply {
        put("format", "vyrocut-project")
        put("version", 1)
        put("projectName", name)
        put("createdAt", System.currentTimeMillis())
        put("clips", JSONArray(encodeProject(clips)))
    }.toString(2)

private fun decodeProjectBackup(raw: String): ProjectBackup {
    val root = JSONObject(raw)
    require(root.getString("format") == "vyrocut-project") { "This is not a VyroCut project backup" }
    require(root.getInt("version") == 1) { "Unsupported backup version" }
    val name = root.optString("projectName", "Restored project").ifBlank { "Restored project" }
    val clipsJson = root.getJSONArray("clips")
    val clips = decodeProject(clipsJson.toString())
    require(clips.size == clipsJson.length()) { "The backup contains invalid clip entries" }
    return ProjectBackup(name, clips)
}

private fun saveProjectHistory(
    preferences: android.content.SharedPreferences,
    projectId: String,
    undo: List<List<EditClip>>,
    redo: List<List<EditClip>>
) {
    fun encodeHistory(history: List<List<EditClip>>): String {
        val array = JSONArray()
        history.takeLast(50).forEach { snapshot -> array.put(encodeProject(snapshot)) }
        return array.toString()
    }
    preferences.edit()
        .putString("project_${projectId}_undo", encodeHistory(undo))
        .putString("project_${projectId}_redo", encodeHistory(redo))
        .apply()
}

private fun loadProjectHistory(
    preferences: android.content.SharedPreferences,
    projectId: String,
    kind: String
): List<List<EditClip>> = runCatching {
    val raw = preferences.getString("project_${projectId}_${kind}", null) ?: return emptyList()
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        decodeProject(array.getString(index))
    }.takeLast(50)
}.getOrDefault(emptyList())

private fun decodeProject(raw: String): List<EditClip> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        val item = array.getJSONObject(index)
        val start = item.getLong("start")
        val end = item.getLong("end")
        if (end <= start) null else EditClip(
            item.getString("id"),
            item.getString("uri"),
            item.optString("name", "Imported video"),
            start,
            end
        )
    }
}.getOrDefault(emptyList())

private fun loadProjectIndex(preferences: android.content.SharedPreferences): List<ProjectInfo> {
    val raw = preferences.getString("project_index", null)
    val loaded = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ProjectInfo(item.getString("id"), item.getString("name"))
        }
    }.getOrDefault(emptyList())
    if (loaded.isNotEmpty()) return loaded
    val initial = listOf(ProjectInfo("default", "My Project"))
    saveProjectIndex(preferences, initial)
    return initial
}

private fun saveProjectIndex(
    preferences: android.content.SharedPreferences,
    projects: List<ProjectInfo>
) {
    val array = JSONArray()
    projects.forEach { project ->
        array.put(JSONObject().apply {
            put("id", project.id)
            put("name", project.name)
        })
    }
    preferences.edit().putString("project_index", array.toString()).apply()
}

private fun loadProject(
    preferences: android.content.SharedPreferences,
    projectId: String
): List<EditClip> {
    val raw = preferences.getString("project_$projectId", null)
        ?: if (projectId == "default") preferences.getString("clips", null) else null
        ?: return emptyList()
    return decodeProject(raw)
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}
