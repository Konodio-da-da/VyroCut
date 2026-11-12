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
import android.os.Environment
import java.io.File
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
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
import kotlinx.coroutines.launch

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
    val exportScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember { context.getSharedPreferences("vyrocut_project", 0) }
    val projects = remember { mutableStateListOf<ProjectInfo>().apply { addAll(loadProjectIndex(preferences)) } }
    var activeProjectId by remember {
        mutableStateOf(preferences.getString("active_project_id", projects.first().id) ?: projects.first().id)
    }
    var clips by remember { mutableStateOf(loadProject(preferences, activeProjectId)) }
    var showHome by remember { mutableStateOf(true) }
    var activeTool by remember { mutableStateOf("Edit") }
    var selectedEffect by remember { mutableStateOf("Edge Glow") }
    var effectIntensity by remember { mutableFloatStateOf(0.65f) }
    var canvasAspectRatio by remember { mutableFloatStateOf(9f / 16f) }
    var canvasBackground by remember { mutableStateOf(Color.Black) }
    var showProjects by remember { mutableStateOf(false) }
    var showTrimMenu by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var projectToRename by remember { mutableStateOf<ProjectInfo?>(null) }
    var renameProjectName by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<ProjectInfo?>(null) }
    val undoStack = remember(activeProjectId) { mutableStateListOf<List<EditClip>>().apply { addAll(loadProjectHistory(preferences, activeProjectId, "undo")) } }
    val redoStack = remember(activeProjectId) { mutableStateListOf<List<EditClip>>().apply { addAll(loadProjectHistory(preferences, activeProjectId, "redo")) } }
    var recoveryCandidate by remember(activeProjectId) {
        mutableStateOf(findProjectRecoveryCandidate(preferences, activeProjectId))
    }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var exportedUri by remember { mutableStateOf<Uri?>(null) }
    var activeTransformer by remember { mutableStateOf<Transformer?>(null) }
    var activeExportFile by remember { mutableStateOf<File?>(null) }
    var exportSessionId by remember { mutableStateOf<String?>(null) }
    var relinkTargetClipId by remember { mutableStateOf<String?>(null) }
    var missingMediaUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isReadingMedia by remember { mutableStateOf(false) }
    val selectedClip = clips.getOrNull(selectedIndex)
    var timelinePositionMs by remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var previousClipEdits by remember { mutableStateOf(clips.toList()) }
    var previousProjectId by remember { mutableStateOf(activeProjectId) }
    var previousSelectedIndex by remember { mutableIntStateOf(selectedIndex) }
    val clipDurationsMs = remember(clips) { clips.map { it.durationMs } }
    val timelineDurationMs = calculateTimelineDurationMs(clipDurationsMs).coerceAtLeast(1L)
    val mediaSourceRequirements = remember(clips) {
        clips.groupBy { it.uri }.map { (uri, uriClips) ->
            uri to uriClips.map { ClipRange(it.sourceStartMs, it.sourceEndMs) }
                .sortedWith(compareBy<ClipRange> { it.startMs }.thenBy { it.endMs })
        }.sortedBy { it.first }
    }

    // Re-check when a project's URI/range requirements change. The stable, sorted
    // key avoids re-probing files for a pure clip reorder, but undo/relink/trim changes
    // are detected. Document-provider work stays off the Compose main thread.
    LaunchedEffect(activeProjectId, mediaSourceRequirements) {
        missingMediaUris = emptySet()
        val requirements = mediaSourceRequirements
        val unavailableUris = withContext(Dispatchers.IO) {
            requirements.mapNotNull { (uri, ranges) ->
                val durationMs = MediaMetadataReader.videoDurationMs(context, Uri.parse(uri))
                if (durationMs == null || ranges.any { range ->
                        !isClipSourceCompatible(range.startMs, range.endMs, durationMs)
                    }
                ) uri else null
            }.toSet()
        }
        missingMediaUris = unavailableUris
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    DisposableEffect(player, lifecycleOwner, clips, activeProjectId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                clips.getOrNull(player.currentMediaItemIndex)?.let { clip ->
                    savePreviewPosition(preferences, activeProjectId, clip, player.currentPosition)
                }
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Rebuild the ordered playlist after an edit while preserving the active
    // source-time playhead when the same clip still exists (including reorders/trims).
    // Initial load and project switches restore that project's saved preview location.
    LaunchedEffect(clips, activeProjectId) {
        val sameProject = previousProjectId == activeProjectId
        val currentClipId = if (sameProject) player.currentMediaItem?.mediaId else null
        val currentPositionMs = if (sameProject) player.currentPosition else 0L
        val wasPlaying = sameProject && player.isPlaying
        val previousRanges = if (sameProject) {
            previousClipEdits.map { ClipPlaybackRange(it.id, it.sourceStartMs, it.sourceEndMs) }
        } else {
            emptyList()
        }
        val nextRanges = clips.map { ClipPlaybackRange(it.id, it.sourceStartMs, it.sourceEndMs) }
        val preferredClipId = if (sameProject && previousSelectedIndex != selectedIndex) {
            clips.getOrNull(selectedIndex)?.id
        } else {
            null
        }
        val restoreTarget = if (sameProject && currentClipId != null) {
            resolvePlaybackRestoreTarget(
                previousClips = previousRanges,
                nextClips = nextRanges,
                currentClipId = currentClipId,
                currentClipPositionMs = currentPositionMs,
                fallbackClipIndex = selectedIndex,
                preferredClipId = preferredClipId,
            )
        } else {
            resolveSavedPlaybackTarget(
                nextClips = nextRanges,
                savedPosition = loadSavedPlaybackPosition(preferences, activeProjectId),
                fallbackClipIndex = if (sameProject) selectedIndex else 0,
            )
        }

        if (restoreTarget == null) {
            player.playWhenReady = false
            player.clearMediaItems()
            selectedIndex = 0
            previousSelectedIndex = 0
            timelinePositionMs = 0f
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
            player.playWhenReady = false
            player.setMediaItems(items, restoreTarget.clipIndex, restoreTarget.positionMs)
            player.prepare()
            player.playWhenReady = wasPlaying
            selectedIndex = restoreTarget.clipIndex
            previousSelectedIndex = restoreTarget.clipIndex
            timelinePositionMs = calculateTimelinePositionMs(
                clips.map { it.durationMs },
                restoreTarget.clipIndex,
                restoreTarget.positionMs,
            ).toFloat()
            clips.getOrNull(restoreTarget.clipIndex)?.let { clip ->
                savePreviewPosition(preferences, activeProjectId, clip, restoreTarget.positionMs)
            }
        }
        previousClipEdits = clips.toList()
        previousProjectId = activeProjectId
    }
    LaunchedEffect(player, clips, isPlaying, activeProjectId) {
        var lastPersistedAtMs = 0L
        while (true) {
            if (!isScrubbing && clips.isNotEmpty() && player.currentMediaItemIndex in clips.indices) {
                val currentIndex = player.currentMediaItemIndex
                val currentPositionMs = player.currentPosition
                timelinePositionMs = calculateTimelinePositionMs(
                    clipDurationsMs,
                    currentIndex,
                    currentPositionMs,
                ).coerceIn(0L, timelineDurationMs).toFloat()

                val nowMs = System.currentTimeMillis()
                if (player.isPlaying && nowMs - lastPersistedAtMs >= 2_000L) {
                    clips.getOrNull(currentIndex)?.let { clip ->
                        savePreviewPosition(preferences, activeProjectId, clip, currentPositionMs)
                    }
                    lastPersistedAtMs = nowMs
                }
            }
            delay(200)
        }
    }
    fun syncPlayerPosition(clipIndex: Int, clipPositionMs: Long) {
        val position = resolvePlayerTimelinePosition(clipDurationsMs, clipIndex, clipPositionMs) ?: return
        selectedIndex = position.clipIndex
        timelinePositionMs = position.timelinePositionMs.coerceIn(0L, timelineDurationMs).toFloat()
        clips.getOrNull(position.clipIndex)?.let { clip ->
            savePreviewPosition(preferences, activeProjectId, clip, position.clipPositionMs)
        }
    }

    DisposableEffect(player, clips, activeProjectId) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                // The built-in PlayerView controller can pause independently of
                // the editor's Play button, so checkpoint that position too.
                if (!playing) {
                    syncPlayerPosition(player.currentMediaItemIndex, player.currentPosition)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                isPlaying = false
                exportMessage = "Preview failed: ${error.errorCodeName}"
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                // Keep the editor scrubber, selected clip and saved project
                // preview in sync with seeks made inside Media3's own controls.
                syncPlayerPosition(newPosition.mediaItemIndex, newPosition.positionMs)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncPlayerPosition(player.currentMediaItemIndex, player.currentPosition)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun saveProject(items: List<EditClip>) {
        val projectName = projects.firstOrNull { it.id == activeProjectId }?.name ?: "Untitled project"
        persistProjectWithRecovery(preferences, activeProjectId, projectName, items)
        preferences.edit().putString("active_project_id", activeProjectId).apply()
    }
    fun switchProject(project: ProjectInfo) {
        if (project.id == activeProjectId) return
        clips.getOrNull(player.currentMediaItemIndex)?.let { clip ->
            savePreviewPosition(preferences, activeProjectId, clip, player.currentPosition)
        }
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
        persistProjectWithRecovery(preferences, project.id, project.name, emptyList())
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
            .remove("project_${project.id}_recovery")
            .remove("project_${project.id}_recovery_dismissed")
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
                persistProjectWithRecovery(preferences, imported.id, imported.name, backup.clips)
                switchProject(imported)
                showProjects = false
                exportMessage = "Restored ${imported.name} (${backup.clips.size} clips)"
            }.onFailure { error ->
                exportMessage = "Could not restore backup: ${error.message}"
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            relinkTargetClipId = null
        } else {
            val selectedUri = uri
            val targetClipId = relinkTargetClipId
            val projectIdAtPick = activeProjectId
            relinkTargetClipId = null
            val permission = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (permission.isFailure) {
                exportMessage = "Could not keep access to this video. Choose a file provider that supports persistent access."
            } else {
                isReadingMedia = true
                exportMessage = "Reading video metadata…"
                exportScope.launch {
                    val metadata = withContext(Dispatchers.IO) {
                        val durationMs = MediaMetadataReader.videoDurationMs(context, selectedUri)
                        durationMs?.let {
                            it to runCatching {
                                MediaMetadataReader.displayName(context.contentResolver, selectedUri)
                            }.getOrDefault("Imported video")
                        }
                    }
                    isReadingMedia = false
                    if (activeProjectId != projectIdAtPick) {
                        exportMessage = "The active project changed while the video was being read. Choose the source again."
                    } else if (metadata == null) {
                        exportMessage = "Could not read this video's duration. It was not added."
                    } else {
                        val (durationMs, name) = metadata
                        val relinkTarget = targetClipId?.let { id -> clips.firstOrNull { it.id == id } }
                        if (targetClipId != null && relinkTarget == null) {
                            exportMessage = "The clip selected for relinking is no longer in this project."
                        } else if (relinkTarget != null) {
                            if (!isClipSourceCompatible(
                                    relinkTarget.sourceStartMs,
                                    relinkTarget.sourceEndMs,
                                    durationMs,
                                )
                            ) {
                                exportMessage = "Replacement video is too short for this clip's existing trim. The edit was left unchanged."
                            } else {
                                val oldUriStillUsed = clips.any { clip ->
                                    clip.id != relinkTarget.id && clip.uri == relinkTarget.uri
                                }
                                val updatedClips = clips.map { clip ->
                                    if (clip.id == relinkTarget.id) {
                                        clip.copy(uri = selectedUri.toString(), name = name)
                                    } else {
                                        clip
                                    }
                                }
                                changed(updatedClips)
                                val newUriHasIncompatibleClip = updatedClips.any { clip ->
                                    clip.uri == selectedUri.toString() &&
                                        !isClipSourceCompatible(clip.sourceStartMs, clip.sourceEndMs, durationMs)
                                }
                                val retainedMissingUris = if (oldUriStillUsed) {
                                    missingMediaUris
                                } else {
                                    missingMediaUris - relinkTarget.uri
                                }
                                missingMediaUris = if (newUriHasIncompatibleClip) {
                                    retainedMissingUris + selectedUri.toString()
                                } else {
                                    retainedMissingUris - selectedUri.toString()
                                }
                                exportMessage = "Relinked ${relinkTarget.name} • Existing trim preserved"
                            }
                        } else {
                            val importedClip = EditClip(
                                java.util.UUID.randomUUID().toString(),
                                selectedUri.toString(),
                                name,
                                0L,
                                durationMs
                            )
                            val appendResult = appendTimelineItemAndSelect(clips, importedClip)
                            val importedUri = selectedUri.toString()
                            val uriHasIncompatibleClip = appendResult.items.any { clip ->
                                clip.uri == importedUri &&
                                    !isClipSourceCompatible(clip.sourceStartMs, clip.sourceEndMs, durationMs)
                            }
                            missingMediaUris = if (uriHasIncompatibleClip) {
                                missingMediaUris + importedUri
                            } else {
                                missingMediaUris - importedUri
                            }
                            changed(appendResult.items)
                            selectedIndex = appendResult.selectedIndex
                            exportMessage = "Imported $name"
                        }
                    }
                }
            }
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = StudioBackground) {
            if (showHome) {
                VyroCutProjectsHome(
                    projects = projects.map { it.id to it.name },
                    activeProjectId = activeProjectId,
                    onNewProject = {
                        newProjectName = "Untitled project"
                        createProject()
                        showHome = false
                        mediaPicker.launch(arrayOf("video/*"))
                    },
                    onImportVideo = {
                        showHome = false
                        mediaPicker.launch(arrayOf("video/*"))
                    },
                    onOpenProject = { id ->
                        projects.firstOrNull { it.id == id }?.let { switchProject(it) }
                        showHome = false
                    },
                    onRenameProject = { id, name ->
                        val index = projects.indexOfFirst { it.id == id }
                        if (index >= 0 && name.isNotBlank()) {
                            projects[index] = projects[index].copy(name = name.trim())
                            saveProjectIndex(preferences, projects)
                        }
                    },
                    onDeleteProject = { id ->
                        projects.firstOrNull { it.id == id }?.let { deleteProject(it) }
                    }
                )
            } else {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { saveProject(clips); player.pause(); showHome = true },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) { Text("‹ Projects", color = StudioAccent, fontSize = 12.sp) }
                    Icon(Icons.Default.Movie, null, tint = StudioAccent, modifier = Modifier.size(28.dp))
                    Text("VyroCut", color = StudioText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showProjects = true }) {
                        Text(projects.firstOrNull { it.id == activeProjectId }?.name ?: "Project", color = StudioAccent, maxLines = 1)
                    }
                    IconButton(onClick = { saveProject(clips) }) { Icon(Icons.Default.Save, "Save project", tint = StudioAccent) }
                }
                Box(Modifier.fillMaxWidth().height(280.dp).background(canvasBackground, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
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
                            modifier = Modifier.fillMaxHeight().aspectRatio(canvasAspectRatio)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(selectedClip?.name ?: "No media selected", color = StudioText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sourceMissing = selectedClip?.uri?.let { it in missingMediaUris } == true
                        Text(
                            when {
                                selectedClip == null -> "Your project is stored locally"
                                sourceMissing -> "Source unavailable • Relink to keep this edit"
                                else -> "Segment ${selectedIndex + 1} of ${clips.size} • ${formatTime(selectedClip.durationMs)}"
                            },
                            color = if (sourceMissing) Color(0xFFFF8A80) else StudioMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (selectedClip != null && selectedClip.uri in missingMediaUris) {
                        TextButton(
                            onClick = {
                                relinkTargetClipId = selectedClip.id
                                mediaPicker.launch(arrayOf("video/*"))
                            },
                            enabled = !isReadingMedia,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) { Text(if (isReadingMedia) "Reading…" else "Relink", fontSize = 11.sp) }
                    }
                    FilledTonalButton(onClick = {
                        if (player.isPlaying) {
                            clips.getOrNull(player.currentMediaItemIndex)?.let { clip ->
                                savePreviewPosition(preferences, activeProjectId, clip, player.currentPosition)
                            }
                            player.pause()
                        } else {
                            if (
                                player.playbackState == Player.STATE_ENDED ||
                                isTimelinePositionAtEnd(timelinePositionMs.toLong(), timelineDurationMs)
                            ) {
                                player.seekTo(0, 0L)
                                clips.firstOrNull()?.let { clip ->
                                    savePreviewPosition(preferences, activeProjectId, clip, 0L)
                                }
                            }
                            player.play()
                        }
                    }, enabled = selectedClip != null,
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
                                clips.getOrNull(target.clipIndex)?.let { clip ->
                                    savePreviewPosition(preferences, activeProjectId, clip, target.positionMs)
                                }
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
                TimelineEditorStrip(
                    clips = clips,
                    selectedIndex = selectedIndex,
                    playheadPositionMs = timelinePositionMs.toLong(),
                    onSelectClip = { index ->
                        selectedIndex = index
                        clips.getOrNull(index)?.let { clip ->
                            player.seekTo(index, 0L)
                            savePreviewPosition(preferences, activeProjectId, clip, 0L)
                        }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("CLIP ORDER", color = StudioMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            val destination = selectedIndex - 1
                            if (destination in clips.indices) {
                                changed(moveTimelineItem(clips, selectedIndex, destination))
                                selectedIndex = destination
                            }
                        },
                        enabled = selectedClip != null && selectedIndex > 0
                    ) {
                        Icon(Icons.Default.ArrowBack, "Move selected clip left", tint = StudioAccent)
                    }
                    OutlinedButton(
                        onClick = {
                            val clip = selectedClip ?: return@OutlinedButton
                            val destination = selectedIndex + 1
                            val duplicate = clip.copy(id = java.util.UUID.randomUUID().toString())
                            changed(clips.toMutableList().apply { add(destination, duplicate) })
                            selectedIndex = destination
                        },
                        enabled = selectedClip != null
                    ) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Duplicate")
                    }
                    IconButton(
                        onClick = {
                            val destination = selectedIndex + 1
                            if (destination in clips.indices) {
                                changed(moveTimelineItem(clips, selectedIndex, destination))
                                selectedIndex = destination
                            }
                        },
                        enabled = selectedClip != null && selectedIndex < clips.lastIndex
                    ) {
                        Icon(Icons.Default.ArrowForward, "Move selected clip right", tint = StudioAccent)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { mediaPicker.launch(arrayOf("video/*")) },
                        enabled = !isReadingMedia,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, null); Text("Import", maxLines = 1)
                    }
                    OutlinedButton(onClick = {
                        val clip = selectedClip ?: return@OutlinedButton
                        if (player.currentMediaItemIndex != selectedIndex) return@OutlinedButton
                        val halves = splitClipRangeAtPlaybackPosition(
                            clip.sourceStartMs,
                            clip.sourceEndMs,
                            player.currentPosition,
                        ) ?: return@OutlinedButton
                        val splitIndex = selectedIndex
                        // Keep the left segment's stable identity; the new right
                        // segment becomes selected at the exact split boundary.
                        val left = clip.copy(
                            sourceEndMs = halves.first.endMs,
                        )
                        val right = clip.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            sourceStartMs = halves.second.startMs,
                            sourceEndMs = halves.second.endMs,
                        )
                        val updated = clips.toMutableList().apply {
                            removeAt(splitIndex)
                            add(splitIndex, left)
                            add(splitIndex + 1, right)
                        }
                        selectedIndex = splitIndex + 1
                        changed(updated)
                    }, enabled = selectedClip?.let { clip ->
                        player.currentMediaItemIndex == selectedIndex &&
                            splitClipRangeAtPlaybackPosition(
                                clip.sourceStartMs,
                                clip.sourceEndMs,
                                player.currentPosition,
                            ) != null
                    } == true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.ContentCut, null); Text("Split at playhead", maxLines = 1)
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showTrimMenu = true },
                            enabled = selectedClip != null && selectedClip.durationMs > 1000L,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Trim", maxLines = 1) }
                        DropdownMenu(
                            expanded = showTrimMenu,
                            onDismissRequest = { showTrimMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Trim start by 1s") },
                                onClick = {
                                    showTrimMenu = false
                                    val clip = selectedClip ?: return@DropdownMenuItem
                                    val range = trimClipRange(clip.sourceStartMs, clip.sourceEndMs, 1000L, fromStart = true)
                                        ?: return@DropdownMenuItem
                                    changed(clips.toMutableList().apply {
                                        this[selectedIndex] = clip.copy(sourceStartMs = range.startMs, sourceEndMs = range.endMs)
                                    })
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Trim end by 1s") },
                                onClick = {
                                    showTrimMenu = false
                                    val clip = selectedClip ?: return@DropdownMenuItem
                                    val range = trimClipRange(clip.sourceStartMs, clip.sourceEndMs, 1000L, fromStart = false)
                                        ?: return@DropdownMenuItem
                                    changed(clips.toMutableList().apply {
                                        this[selectedIndex] = clip.copy(sourceStartMs = range.startMs, sourceEndMs = range.endMs)
                                    })
                                }
                            )
                        }
                    }
                    IconButton(onClick = { if (selectedClip != null) changed(clips.toMutableList().apply { removeAt(selectedIndex) }) }, enabled = selectedClip != null) {
                        Icon(Icons.Default.Delete, "Delete selected clip", tint = Color(0xFFFF8A80))
                    }
                }
                VyroCutToolDock(
                    activeTool = activeTool,
                    onToolSelected = { activeTool = it }
                )
                if (activeTool == "Ratio" || activeTool == "Canvas") {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (activeTool == "Ratio") "ASPECT RATIO" else "CANVAS BACKGROUND", color = StudioText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (activeTool == "Ratio") {
                                listOf("9:16" to (9f / 16f), "16:9" to (16f / 9f), "1:1" to 1f).forEach { (label, ratio) ->
                                    FilterChip(
                                        selected = canvasAspectRatio == ratio,
                                        onClick = { canvasAspectRatio = ratio },
                                        label = { Text(label) }
                                    )
                                }
                            } else {
                                listOf("Black" to Color.Black, "Dark" to Color(0xFF202322), "White" to Color.White).forEach { (label, color) ->
                                    FilterChip(
                                        selected = canvasBackground == color,
                                        onClick = { canvasBackground = color },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (activeTool == "Effects" || activeTool == "Filters" || activeTool == "Adjust") {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            when (activeTool) {
                                "Effects" -> "VIDEO EFFECTS"
                                "Filters" -> "FILTERS"
                                else -> "ADJUST"
                            },
                            color = StudioText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (if (activeTool == "Effects") listOf("Edge Glow", "Blur", "Glitch", "Vignette", "Shake") else if (activeTool == "Filters") listOf("Original", "Cinema", "Warm", "Cool", "Mono") else listOf("Brightness", "Contrast", "Saturation", "Sharpness")).forEach { item ->
                                FilterChip(
                                    selected = selectedEffect == item,
                                    onClick = { selectedEffect = item },
                                    label = { Text(item, fontSize = 11.sp) }
                                )
                            }
                        }
                        if (activeTool == "Effects" || activeTool == "Adjust") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (activeTool == "Effects") "Intensity" else selectedEffect, color = StudioMuted, fontSize = 11.sp)
                                Spacer(Modifier.weight(1f))
                                Text("${(effectIntensity * 100).toInt()}%", color = StudioAccent, fontSize = 11.sp)
                            }
                            Slider(
                                value = effectIntensity,
                                onValueChange = { effectIntensity = it },
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent)
                            )
                            Text("Control panel layout is in place; effect rendering is a separate implementation step.", color = StudioMuted, fontSize = 10.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (clips.isNotEmpty() && !isExporting && !isReadingMedia) {
                                val exportSession = java.util.UUID.randomUUID().toString()
                                val exportClips = clips.toList()
                                val exportRequirements = exportClips.map { clip ->
                                    ExportClipRequirement(
                                        id = clip.id,
                                        uri = clip.uri,
                                        name = clip.name,
                                        sourceStartMs = clip.sourceStartMs,
                                        sourceEndMs = clip.sourceEndMs,
                                    )
                                }
                                exportSessionId = exportSession
                                isExporting = true
                                exportedUri = null
                                player.pause()
                                exportMessage = "Checking source videos…"

                                exportScope.launch exportPreparation@ {
                                    val preflightError = withContext(Dispatchers.IO) {
                                        val sourceMetadata = inspectExportSources(context, exportRequirements)
                                        validateExportPreflight(exportRequirements, sourceMetadata)
                                    }
                                    if (exportSessionId != exportSession) return@exportPreparation
                                    if (preflightError != null) {
                                        isExporting = false
                                        exportSessionId = null
                                        exportMessage = "Export stopped: $preflightError"
                                        return@exportPreparation
                                    }

                                    val outputRoot = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                                        ?: File(context.filesDir, "Movies")
                                    val outputDirectory = File(outputRoot, "VyroCut")
                                    if (!outputDirectory.isDirectory && !outputDirectory.mkdirs()) {
                                        isExporting = false
                                        exportSessionId = null
                                        exportMessage = "Could not create the temporary export directory"
                                        return@exportPreparation
                                    }
                                    val outputFile = File(outputDirectory, "VyroCut_${System.currentTimeMillis()}.mp4")
                                    val sourceUris = exportClips.map { it.uri }
                                    val expectedDurationMs = calculateTimelineDurationMs(exportClips.map { it.durationMs })
                                    val composition = try {
                                        val editedItems = exportClips.map { clip ->
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
                                        Composition.Builder(sequence).build()
                                    } catch (error: Exception) {
                                        isExporting = false
                                        exportSessionId = null
                                        exportMessage = "Could not prepare export: ${error.message}"
                                        return@exportPreparation
                                    }
                                    val transformer = try {
                                        Transformer.Builder(context)
                                            .addListener(object : Transformer.Listener {
                                                override fun onCompleted(composition: Composition, result: ExportResult) {
                                                    if (exportSessionId != exportSession) {
                                                        outputFile.delete()
                                                        return
                                                    }
                                                    exportScope.launch exportPublishing@ {
                                                        val publishResult = runCatching {
                                                            withContext(Dispatchers.IO) {
                                                                verifyExportedMp4(
                                                                    context = context,
                                                                    file = outputFile,
                                                                    sourceUris = sourceUris,
                                                                    expectedDurationMs = expectedDurationMs,
                                                                )
                                                                publishExportToMovies(context, outputFile)
                                                            }
                                                        }
                                                        if (exportSessionId != exportSession) {
                                                            publishResult.getOrNull()?.let { uri ->
                                                                withContext(Dispatchers.IO) {
                                                                    runCatching { context.contentResolver.delete(uri, null, null) }
                                                                }
                                                            }
                                                            outputFile.delete()
                                                            return@exportPublishing
                                                        }
                                                        isExporting = false
                                                        activeTransformer = null
                                                        activeExportFile = null
                                                        exportSessionId = null
                                                        publishResult.onSuccess { uri ->
                                                            exportedUri = uri
                                                            exportMessage = "Export complete • Video/audio samples and duration verified • Saved to Movies/VyroCut"
                                                        }.onFailure { error ->
                                                            exportedUri = null
                                                            exportMessage = "Export verification or saving failed: ${error.message}"
                                                        }
                                                        outputFile.delete()
                                                    }
                                                }
                                                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                                                    outputFile.delete()
                                                    if (exportSessionId == exportSession) {
                                                        isExporting = false
                                                        activeTransformer = null
                                                        activeExportFile = null
                                                        exportSessionId = null
                                                        exportMessage = "Export failed: ${exception.message ?: "Unsupported media"}"
                                                    }
                                                }
                                            }).build()
                                    } catch (error: Exception) {
                                        outputFile.delete()
                                        isExporting = false
                                        exportSessionId = null
                                        exportMessage = "Could not initialize export: ${error.message}"
                                        return@exportPreparation
                                    }
                                    activeTransformer = transformer
                                    activeExportFile = outputFile
                                    exportMessage = "Rendering MP4…"
                                    runCatching { transformer.start(composition, outputFile.absolutePath) }
                                        .onFailure { error ->
                                            if (exportSessionId == exportSession) {
                                                isExporting = false
                                                activeTransformer = null
                                                activeExportFile = null
                                                exportSessionId = null
                                                exportMessage = "Could not start export: ${error.message}"
                                            }
                                            outputFile.delete()
                                        }
                                }
                            }
                        },
                        enabled = clips.isNotEmpty() && !isExporting && !isReadingMedia,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF00382A))
                    ) {
                        Text(if (isExporting) "Exporting…" else "Export MP4", fontWeight = FontWeight.Bold)
                    }
                    if (isExporting) {
                        OutlinedButton(onClick = {
                            exportSessionId = null
                            activeTransformer?.cancel()
                            activeTransformer = null
                            activeExportFile?.delete()
                            activeExportFile = null
                            isExporting = false
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
                recoveryCandidate?.let { snapshot ->
                    val recoverableClips = decodeProjectStrict(snapshot.projectJson)
                    AlertDialog(
                        onDismissRequest = {
                            preferences.edit()
                                .putString("project_${snapshot.projectId}_recovery_dismissed", snapshot.checksum)
                                .apply()
                            recoveryCandidate = null
                        },
                        title = { Text("Recover project checkpoint?") },
                        text = {
                            Text(
                                "VyroCut found a separate local checkpoint for \"${snapshot.projectName}\" " +
                                    "saved ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(snapshot.savedAtMs))}. " +
                                    "${recoverableClips?.size ?: 0} clips can be recovered. Your current project data differs from this checkpoint."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                enabled = recoverableClips != null,
                                onClick = {
                                    val restored = recoverableClips
                                    if (restored != null) {
                                        val name = projects.firstOrNull { it.id == snapshot.projectId }?.name
                                            ?: snapshot.projectName
                                        persistProjectWithRecovery(preferences, snapshot.projectId, name, restored)
                                        if (snapshot.projectId == activeProjectId) {
                                            clips = restored
                                            selectedIndex = 0
                                            timelinePositionMs = 0f
                                        }
                                        recoveryCandidate = null
                                        exportMessage = "Recovered ${restored.size} clips from the local checkpoint"
                                    }
                                }
                            ) { Text("Recover") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    preferences.edit()
                                        .putString("project_${snapshot.projectId}_recovery_dismissed", snapshot.checksum)
                                        .apply()
                                    recoveryCandidate = null
                                }
                            ) { Text("Keep current project") }
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
    val snapshots = (0 until array.length()).map { index -> array.getString(index) }
    decodeValidHistorySnapshots(snapshots, ::decodeProjectStrict)
}.getOrDefault(emptyList())

private fun decodeProject(raw: String): List<EditClip> =
    decodeProjectStrict(raw) ?: emptyList()

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

private fun decodeProjectStrict(raw: String): List<EditClip>? = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.getString("id").takeIf { it.isNotBlank() }
                ?: error("A clip is missing its ID")
            val uri = item.getString("uri").takeIf { it.isNotBlank() }
                ?: error("A clip is missing its media reference")
            val start = item.getLong("start")
            val end = item.getLong("end")
            require(start >= 0L && end > start) { "A clip has an invalid source range" }
            add(EditClip(id, uri, item.optString("name", "Imported video"), start, end))
        }
    }
}.getOrNull()

private fun persistProjectWithRecovery(
    preferences: android.content.SharedPreferences,
    projectId: String,
    projectName: String,
    clips: List<EditClip>,
) {
    val projectJson = encodeProject(clips)
    val recoveryJson = ProjectRecoveryJournal.encode(
        projectId = projectId,
        projectName = projectName,
        savedAtMs = System.currentTimeMillis().coerceAtLeast(1L),
        projectJson = projectJson,
    )
    // Both values are committed in one SharedPreferences edit. The independent
    // recovery key can still rescue the project if its primary value is damaged.
    preferences.edit()
        .putString("project_$projectId", projectJson)
        .putString("project_${projectId}_recovery", recoveryJson)
        .remove("project_${projectId}_recovery_dismissed")
        .apply()
}

private fun findProjectRecoveryCandidate(
    preferences: android.content.SharedPreferences,
    projectId: String,
): ProjectRecoverySnapshot? {
    val snapshot = ProjectRecoveryJournal.decode(
        preferences.getString("project_${projectId}_recovery", null),
        projectId,
    ) ?: return null
    if (preferences.getString("project_${projectId}_recovery_dismissed", null) == snapshot.checksum) {
        return null
    }
    val primaryJson = preferences.getString("project_$projectId", null)
        ?: (if (projectId == "default") preferences.getString("clips", null) else null)
    if (!ProjectRecoveryJournal.shouldOfferRecovery(primaryJson, snapshot)) return null
    if (decodeProjectStrict(snapshot.projectJson) == null) return null
    return snapshot
}

private fun loadProject(
    preferences: android.content.SharedPreferences,
    projectId: String
): List<EditClip> {
    val raw = preferences.getString("project_$projectId", null)
        ?: (if (projectId == "default") preferences.getString("clips", null) else null)
        ?: return emptyList()
    return decodeProject(raw)
}

private fun savePreviewPosition(
    preferences: android.content.SharedPreferences,
    projectId: String,
    clip: EditClip,
    clipPositionMs: Long,
) {
    val maximumPositionMs = (clip.durationMs - 1L).coerceAtLeast(0L)
    val relativePositionMs = clipPositionMs.coerceIn(0L, maximumPositionMs)
    val sourceTimeMs = (clip.sourceStartMs + relativePositionMs)
        .coerceAtMost((clip.sourceEndMs - 1L).coerceAtLeast(clip.sourceStartMs))
    preferences.edit()
        .putString("project_${projectId}_preview_clip_id", clip.id)
        .putLong("project_${projectId}_preview_source_time_ms", sourceTimeMs)
        .apply()
}

private fun loadSavedPlaybackPosition(
    preferences: android.content.SharedPreferences,
    projectId: String,
): SavedPlaybackPosition? {
    val clipId = preferences.getString("project_${projectId}_preview_clip_id", null)
        ?.takeIf { it.isNotBlank() } ?: return null
    val positionKey = "project_${projectId}_preview_source_time_ms"
    if (!preferences.contains(positionKey)) return null
    return SavedPlaybackPosition(
        clipId = clipId,
        sourceTimeMs = preferences.getLong(positionKey, 0L).coerceAtLeast(0L),
    )
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}


@Composable
private fun TimelineEditorStrip(
    clips: List<EditClip>,
    selectedIndex: Int,
    playheadPositionMs: Long,
    onSelectClip: (Int) -> Unit,
) {
    // One scroll state keeps the ruler, video thumbnails, playhead and audio
    // waveform locked together like a real multitrack timeline.
    val timelineScroll = rememberScrollState()
    val pixelsPerSecond = 24f
    val gap = 6f
    val clipWidths = clips.map { (it.durationMs / 1000f * pixelsPerSecond).coerceIn(120f, 320f) }
    val totalWidthPx = (clipWidths.sum() + (clips.size - 1).coerceAtLeast(0) * gap).coerceAtLeast(1f)
    val totalWidth = totalWidthPx.dp
    val totalDuration = clips.sumOf { it.durationMs }.coerceAtLeast(1L)

    var markerX = 0f
    var remainingMs = playheadPositionMs.coerceIn(0L, totalDuration)
    for (index in clips.indices) {
        val clip = clips[index]
        val width = clipWidths[index]
        if (remainingMs <= clip.durationMs || index == clips.lastIndex) {
            markerX += width * (remainingMs.toFloat() / clip.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            break
        }
        markerX += width + gap
        remainingMs -= clip.durationMs
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("VIDEO", color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
            Text("Main track", color = StudioMuted, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text("Swipe timeline", color = StudioMuted, fontSize = 9.sp)
        }

        if (clips.isEmpty()) {
            Text("Import footage to build your timeline", color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            // Time ruler shares the exact horizontal offset with both tracks.
            Row(Modifier.fillMaxWidth().horizontalScroll(timelineScroll)) {
                Box(Modifier.width(totalWidth).height(22.dp)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Start) {
                        var elapsed = 0L
                        clips.forEachIndexed { index, clip ->
                            val width = clipWidths[index]
                            Box(Modifier.width(width.dp).fillMaxHeight()) {
                                val seconds = (clip.durationMs / 1000L).toInt().coerceAtLeast(1)
                                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    for (tick in 0..seconds) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(Modifier.width(1.dp).height(if ((elapsed / 1000L + tick) % 5L == 0L) 9.dp else 5.dp).background(StudioMuted.copy(alpha = 0.65f)))
                                            if ((elapsed / 1000L + tick) % 5L == 0L) {
                                                Text(formatTime((elapsed / 1000L + tick) * 1000L), color = StudioMuted, fontSize = 7.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            if (index != clips.lastIndex) Spacer(Modifier.width(gap.dp))
                            elapsed += clip.durationMs
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().horizontalScroll(timelineScroll)) {
                Box(Modifier.width(totalWidth).height(76.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                        clips.forEachIndexed { index, clip ->
                            Column(
                                Modifier.width(clipWidths[index].dp)
                                    .background(if (index == selectedIndex) Color(0xFF16483D) else Color(0xFF252A28), RoundedCornerShape(5.dp))
                                    .clickable { onSelectClip(index) }
                                    .padding(3.dp)
                            ) {
                                Box(Modifier.fillMaxWidth().height(49.dp)) {
                                    ClipThumbnail(clip, Modifier.fillMaxSize())
                                    Text(
                                        "V" + (index + 1),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.TopStart)
                                            .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(2.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                    Text(
                                        formatTime(clip.durationMs),
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        modifier = Modifier.align(Alignment.BottomEnd)
                                            .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(2.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                Text(clip.name, color = if (index == selectedIndex) StudioAccent else StudioText, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Box(
                        Modifier.offset(x = markerX.dp).width(2.dp).height(76.dp)
                            .background(StudioAccent)
                    )
                    Box(
                        Modifier.offset(x = (markerX - 5f).dp).width(12.dp).height(7.dp)
                            .background(StudioAccent, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("AUDIO", color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                Row(Modifier.weight(1f).horizontalScroll(timelineScroll), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                    clips.forEachIndexed { index, clip ->
                        Box(
                            Modifier.width(clipWidths[index].dp).height(32.dp)
                                .background(if (index == selectedIndex) Color(0xFF1B4239) else Color(0xFF172C28), RoundedCornerShape(4.dp))
                                .clickable { onSelectClip(index) }.padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AudioWaveform(clip, Modifier.fillMaxWidth().height(24.dp))
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", color = StudioMuted, fontSize = 9.sp)
            Text(formatTime(totalDuration), color = StudioMuted, fontSize = 9.sp)
        }
    }
}
