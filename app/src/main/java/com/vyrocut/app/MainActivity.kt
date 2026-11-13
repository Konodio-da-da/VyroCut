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
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.nio.ByteOrder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
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

internal data class EditClip(
    val id: String,
    val uri: String,
    val name: String,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val audioVolume: Float = DEFAULT_CLIP_AUDIO_VOLUME,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val visualAdjustments: ClipVisualAdjustments = ClipVisualAdjustments(),
    val textOverlay: ClipTextOverlay? = null,
    val additionalTextOverlays: List<ClipTextOverlay> = emptyList(),
) {
    init {
        require(isValidClipAudioVolume(audioVolume)) { "Clip volume must be between 0 and 1" }
        require(isValidClipAudioFades(fadeInMs, fadeOutMs)) { "Clip fade durations cannot be negative" }
        require((if (textOverlay == null) 0 else 1) + additionalTextOverlays.size <= MAX_CLIP_TEXT_OVERLAYS) {
            "A clip cannot contain more than $MAX_CLIP_TEXT_OVERLAYS text layers"
        }
    }
    val allTextOverlays: List<ClipTextOverlay>
        get() = listOfNotNull(textOverlay) + additionalTextOverlays
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
    var audioTrackClips by remember(activeProjectId) {
        mutableStateOf(
            loadProjectAudioTracks(
                preferences,
                activeProjectId,
                calculateTimelineDurationMs(clips.map { it.durationMs }),
            ),
        )
    }
    var selectedAudioTrackType by remember { mutableStateOf(AudioTrackType.MUSIC) }
    var selectedAudioTrackId by remember { mutableStateOf<String?>(null) }
    var showHome by remember { mutableStateOf(true) }
    var activeTool by remember { mutableStateOf("Edit") }
    var selectedEffect by remember { mutableStateOf("Edge Glow") }
    var effectIntensity by remember { mutableFloatStateOf(0.65f) }
    val initialCanvasSettings = remember {
        decodeProjectCanvasSettings(
            preferences.getString("project_" + activeProjectId + "_canvas_settings", null)
        ) ?: ProjectCanvasSettings()
    }
    var canvasAspectRatio by remember {
        mutableFloatStateOf(canvasAspectRatioForPreset(initialCanvasSettings.aspectRatioPreset))
    }
    var canvasBackground by remember {
        mutableStateOf(canvasBackgroundForPreset(initialCanvasSettings.backgroundPreset))
    }
    var showProjects by remember { mutableStateOf(false) }
    var showClipActions by remember { mutableStateOf(false) }
    var showTrimDialog by remember { mutableStateOf(false) }
    var trimTargetClipId by remember { mutableStateOf<String?>(null) }
    var trimSourceDurationMs by remember { mutableLongStateOf(1L) }
    var isLoadingTrimSource by remember { mutableStateOf(false) }
    var trimSourceReadFailed by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var projectToRename by remember { mutableStateOf<ProjectInfo?>(null) }
    var renameProjectName by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<ProjectInfo?>(null) }
    val undoStack = remember(activeProjectId) { mutableStateListOf<ProjectEditSnapshot>().apply { addAll(loadProjectHistory(preferences, activeProjectId, "undo")) } }
    val redoStack = remember(activeProjectId) { mutableStateListOf<ProjectEditSnapshot>().apply { addAll(loadProjectHistory(preferences, activeProjectId, "redo")) } }
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
    val selectedAudioTrack = audioTrackClips.firstOrNull { it.id == selectedAudioTrackId }
    var audioTrackVolumeDraft by remember(selectedAudioTrackId, selectedAudioTrack?.volume) {
        mutableFloatStateOf(selectedAudioTrack?.volume ?: 1f)
    }
    var audioTrackFadeInDraft by remember(selectedAudioTrackId, selectedAudioTrack?.fadeInMs) {
        mutableFloatStateOf(selectedAudioTrack?.fadeInMs?.toFloat() ?: 0f)
    }
    var audioTrackFadeOutDraft by remember(selectedAudioTrackId, selectedAudioTrack?.fadeOutMs) {
        mutableFloatStateOf(selectedAudioTrack?.fadeOutMs?.toFloat() ?: 0f)
    }
    var audioTrackStartDraft by remember(selectedAudioTrackId, selectedAudioTrack?.startMs) {
        mutableFloatStateOf(selectedAudioTrack?.startMs?.toFloat() ?: 0f)
    }
    var audioTrackSourceStartDraft by remember(selectedAudioTrackId, selectedAudioTrack?.sourceStartMs) {
        mutableFloatStateOf(selectedAudioTrack?.sourceStartMs?.toFloat() ?: 0f)
    }
    var audioTrackSourceEndDraft by remember(selectedAudioTrackId, selectedAudioTrack?.sourceEndMs) {
        mutableFloatStateOf(selectedAudioTrack?.sourceEndMs?.toFloat() ?: 0f)
    }
    var selectedTextLayerIndex by remember { mutableIntStateOf(0) }
    val selectedTextOverlay = selectedClip?.allTextOverlays?.getOrNull(selectedTextLayerIndex)
    LaunchedEffect(selectedClip?.id, selectedClip?.allTextOverlays?.size) {
        selectedTextLayerIndex = selectedTextLayerIndex.coerceIn(
            0,
            selectedClip?.allTextOverlays?.size ?: 0,
        )
    }
    var clipVolumeDraft by remember(selectedClip?.id, selectedClip?.audioVolume) {
        mutableFloatStateOf(selectedClip?.audioVolume ?: DEFAULT_CLIP_AUDIO_VOLUME)
    }
    var clipFadeInDraft by remember(selectedClip?.id, selectedClip?.fadeInMs, selectedClip?.durationMs) {
        mutableFloatStateOf(selectedClip?.fadeInMs?.toFloat() ?: 0f)
    }
    var clipFadeOutDraft by remember(selectedClip?.id, selectedClip?.fadeOutMs, selectedClip?.durationMs) {
        mutableFloatStateOf(selectedClip?.fadeOutMs?.toFloat() ?: 0f)
    }
    var clipBrightnessDraft by remember(selectedClip?.id, selectedClip?.visualAdjustments?.brightness) {
        mutableFloatStateOf(selectedClip?.visualAdjustments?.brightness?.toFloat() ?: 0f)
    }
    var clipContrastDraft by remember(selectedClip?.id, selectedClip?.visualAdjustments?.contrast) {
        mutableFloatStateOf(selectedClip?.visualAdjustments?.contrast?.toFloat() ?: 0f)
    }
    var clipSaturationDraft by remember(selectedClip?.id, selectedClip?.visualAdjustments?.saturation) {
        mutableFloatStateOf(selectedClip?.visualAdjustments?.saturation?.toFloat() ?: 0f)
    }
    var clipTextDraft by remember(selectedClip?.id, selectedTextLayerIndex, selectedTextOverlay?.text) {
        mutableStateOf(selectedTextOverlay?.text ?: "")
    }
    var clipTextScaleDraft by remember(selectedClip?.id, selectedTextLayerIndex, selectedTextOverlay?.fontScale) {
        mutableFloatStateOf(selectedTextOverlay?.fontScale ?: 1f)
    }
    var clipTextPositionDraft by remember(selectedClip?.id, selectedTextLayerIndex, selectedTextOverlay?.position) {
        mutableStateOf(selectedTextOverlay?.position ?: ClipTextPosition.BOTTOM)
    }
    var clipTextStartDraft by remember(selectedClip?.id, selectedTextLayerIndex, selectedTextOverlay?.startMs) {
        mutableFloatStateOf(selectedTextOverlay?.startMs?.toFloat() ?: 0f)
    }
    var clipTextEndDraft by remember(selectedClip?.id, selectedTextLayerIndex, selectedTextOverlay?.endMs, selectedClip?.durationMs) {
        mutableFloatStateOf(
            (selectedTextOverlay?.endMs ?: selectedClip?.durationMs ?: 0L).toFloat(),
        )
    }
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
    val audioPlayers = remember {
        AudioTrackType.values().associateWith { _ ->
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false,
                )
                playWhenReady = false
                volume = 0f
            }
        }
    }
    DisposableEffect(audioPlayers) {
        onDispose {
            audioPlayers.values.forEach { audioPlayer ->
                audioPlayer.playWhenReady = false
                audioPlayer.release()
            }
        }
    }
    var audioTrackSegments by remember {
        mutableStateOf<Map<AudioTrackType, List<AudioTrackTimelineSegment>>>(emptyMap())
    }
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
            val restoredClip = clips[restoreTarget.clipIndex]
            player.setVideoEffects(createClipVisualEffects(restoredClip.visualAdjustments) + createClipTextOverlayEffects(restoredClip.allTextOverlays))
            player.setVolume(
                clipAudioPlaybackGain(
                    restoredClip.audioVolume,
                    restoreTarget.positionMs,
                    restoredClip.durationMs,
                    restoredClip.fadeInMs,
                    restoredClip.fadeOutMs,
                ),
            )
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
    LaunchedEffect(player, clips) {
        var lastAppliedGain = Float.NaN
        while (true) {
            val currentIndex = player.currentMediaItemIndex
            val clip = clips.getOrNull(currentIndex)
            if (clip != null && clip.durationMs > 0L) {
                val nextGain = clipAudioPlaybackGain(
                    clip.audioVolume,
                    player.currentPosition,
                    clip.durationMs,
                    clip.fadeInMs,
                    clip.fadeOutMs,
                )
                if (lastAppliedGain.isNaN() || kotlin.math.abs(nextGain - lastAppliedGain) > 0.001f) {
                    player.setVolume(nextGain)
                    lastAppliedGain = nextGain
                }
            }
            delay(if (player.isPlaying) 16L else 80L)
        }
    }

    // Each audio lane is an audio-only playlist. Silent WAV segments preserve
    // project-time offsets and gaps, so every lane shares the video project's clock.
    LaunchedEffect(audioTrackClips, timelineDurationMs, activeProjectId) {
        val projectDurationMs = calculateTimelineDurationMs(clipDurationsMs)
        val segmentsByType = AudioTrackType.values().associateWith { type ->
            buildAudioTrackTimelineSegments(audioTrackClips, type, projectDurationMs)
        }
        val maximumGapMs = segmentsByType.values.flatten()
            .filter { it.clip == null }
            .maxOfOrNull { it.durationMs } ?: 0L
        val silenceFile = if (maximumGapMs > 0L) {
            runCatching {
                withContext(Dispatchers.IO) {
                    createSilentAudioWav(context.cacheDir, maximumGapMs)
                }
            }.getOrNull()
        } else {
            null
        }

        if (maximumGapMs > 0L && silenceFile == null) {
            audioTrackSegments = emptyMap()
            audioPlayers.values.forEach { audioPlayer ->
                audioPlayer.clearMediaItems()
                audioPlayer.pause()
            }
            exportMessage = "Could not prepare silent gaps for audio-track preview."
            return@LaunchedEffect
        }

        segmentsByType.forEach { (type, segments) ->
            val audioPlayer = audioPlayers.getValue(type)
            audioPlayer.playWhenReady = false
            audioPlayer.volume = 0f
            if (segments.isEmpty()) {
                audioPlayer.clearMediaItems()
            } else {
                val mediaItems = segments.map { segment ->
                    val clip = segment.clip
                    if (clip != null) {
                        MediaItem.Builder()
                            .setMediaId(clip.id)
                            .setUri(Uri.parse(clip.uri))
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(clip.sourceStartMs)
                                    .setEndPositionMs(clip.sourceEndMs)
                                    .build(),
                            )
                            .build()
                    } else {
                        MediaItem.Builder()
                            .setUri(Uri.fromFile(silenceFile ?: error("Missing silence source")))
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(0L)
                                    .setEndPositionMs(segment.durationMs)
                                    .build(),
                            )
                            .build()
                    }
                }
                audioPlayer.setMediaItems(mediaItems)
                audioPlayer.prepare()
            }
        }
        audioTrackSegments = segmentsByType
    }

    LaunchedEffect(audioTrackSegments, clips, player) {
        while (true) {
            val currentIndex = player.currentMediaItemIndex
            val projectPositionMs = if (currentIndex in clips.indices) {
                calculateTimelinePositionMs(clipDurationsMs, currentIndex, player.currentPosition)
                    .coerceIn(0L, timelineDurationMs)
            } else {
                timelinePositionMs.toLong().coerceIn(0L, timelineDurationMs)
            }
            val mainIsPlaying = player.isPlaying

            AudioTrackType.values().forEach { type ->
                val audioPlayer = audioPlayers.getValue(type)
                val segments = audioTrackSegments[type].orEmpty()
                if (segments.isEmpty() || audioPlayer.mediaItemCount == 0) {
                    audioPlayer.playWhenReady = false
                    audioPlayer.volume = 0f
                } else {
                    val currentTrackPosition = calculateAudioTrackTimelinePositionMs(
                        segments,
                        audioPlayer.currentMediaItemIndex,
                        audioPlayer.currentPosition,
                    )
                    if (kotlin.math.abs(currentTrackPosition - projectPositionMs) > 180L ||
                        audioPlayer.currentMediaItemIndex !in segments.indices
                    ) {
                        resolveAudioTrackSeekTarget(segments, projectPositionMs)?.let { target ->
                            audioPlayer.seekTo(target.segmentIndex, target.positionMs)
                        }
                    }
                    val activeSegment = segments.firstOrNull {
                        projectPositionMs >= it.startMs && projectPositionMs < it.endMs
                    }
                    audioPlayer.volume = activeSegment?.clip?.let {
                        audioTrackGainAtTimelinePosition(it, projectPositionMs)
                    } ?: 0f
                    audioPlayer.playWhenReady = mainIsPlaying
                }
            }
            delay(if (mainIsPlaying) 16L else 100L)
        }
    }

    fun syncPlayerPosition(clipIndex: Int, clipPositionMs: Long) {
        val position = resolvePlayerTimelinePosition(clipDurationsMs, clipIndex, clipPositionMs) ?: return
        selectedIndex = position.clipIndex
        timelinePositionMs = position.timelinePositionMs.coerceIn(0L, timelineDurationMs).toFloat()
        clips.getOrNull(position.clipIndex)?.let { clip ->
            player.setVideoEffects(createClipVisualEffects(clip.visualAdjustments) + createClipTextOverlayEffects(clip.allTextOverlays))
            player.setVolume(
                clipAudioPlaybackGain(
                    clip.audioVolume,
                    position.clipPositionMs,
                    clip.durationMs,
                    clip.fadeInMs,
                    clip.fadeOutMs,
                ),
            )
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
        persistProjectAudioTracks(
            preferences,
            activeProjectId,
            audioTrackClips,
            calculateTimelineDurationMs(items.map { it.durationMs }),
        )
        preferences.edit().putString("active_project_id", activeProjectId).apply()
    }
    fun persistCanvasSettings(aspectRatio: Float, background: Color) {
        val settings = projectCanvasSettingsFor(aspectRatio, background)
        preferences.edit()
            .putString("project_" + activeProjectId + "_canvas_settings", encodeProjectCanvasSettings(settings))
            .apply()
    }
    fun switchProject(project: ProjectInfo) {
        if (project.id == activeProjectId) return
        clips.getOrNull(player.currentMediaItemIndex)?.let { clip ->
            savePreviewPosition(preferences, activeProjectId, clip, player.currentPosition)
        }
        saveProject(clips)
        activeProjectId = project.id
        preferences.edit().putString("active_project_id", project.id).apply()
        val canvasSettings = decodeProjectCanvasSettings(
            preferences.getString("project_" + project.id + "_canvas_settings", null)
        ) ?: ProjectCanvasSettings()
        canvasAspectRatio = canvasAspectRatioForPreset(canvasSettings.aspectRatioPreset)
        canvasBackground = canvasBackgroundForPreset(canvasSettings.backgroundPreset)
        clips = loadProject(preferences, project.id)
        audioTrackClips = loadProjectAudioTracks(
            preferences,
            project.id,
            calculateTimelineDurationMs(clips.map { it.durationMs }),
        )
        selectedAudioTrackId = null
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
        persistProjectAudioTracks(preferences, project.id, emptyList(), 0L)
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
            .remove("project_${project.id}_audio_tracks")
            .remove("project_${project.id}_audio_tracks_recovery")
            .remove("project_" + project.id + "_canvas_settings")
            .apply()
        saveProjectIndex(preferences, projects)
        projectToDelete = null
        exportMessage = "Deleted ${project.name}"
    }
    fun changed(items: List<EditClip>) {
        if (items == clips) return
        undoStack.add(ProjectEditSnapshot(clips.toList(), audioTrackClips.toList()))
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
        clips = items.toList()
        val nextProjectDurationMs = calculateTimelineDurationMs(items.map { it.durationMs })
        val retainedAudioTracks = trimAudioTrackClipsToProjectDuration(audioTrackClips, nextProjectDurationMs)
        if (retainedAudioTracks != audioTrackClips) {
            audioTrackClips = retainedAudioTracks
            selectedAudioTrackId = selectedAudioTrackId?.takeIf { id -> retainedAudioTracks.any { it.id == id } }
                ?: retainedAudioTracks.lastOrNull()?.id
        }
        saveProjectHistory(preferences, activeProjectId, undoStack, redoStack)
        selectedIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        saveProject(items)
    }

    fun changedAudioTracks(items: List<AudioTrackClip>) {
        val projectDurationMs = calculateTimelineDurationMs(clips.map { it.durationMs })
        if (!validateAudioTrackClips(items, projectDurationMs)) {
            exportMessage = "Audio clips must fit inside the project and cannot overlap on the same lane."
            return
        }
        if (items == audioTrackClips) return
        undoStack.add(ProjectEditSnapshot(clips.toList(), audioTrackClips.toList()))
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
        audioTrackClips = items.toList()
        selectedAudioTrackId = selectedAudioTrackId?.takeIf { id -> items.any { it.id == id } }
            ?: items.lastOrNull()?.id
        saveProjectHistory(preferences, activeProjectId, undoStack, redoStack)
        persistProjectAudioTracks(preferences, activeProjectId, items, projectDurationMs)
    }
    fun moveSelectedAudioTrack(requestedStartMs: Long) {
        val selected = selectedAudioTrack ?: return
        val projectDurationMs = calculateTimelineDurationMs(clips.map { it.durationMs })
        val updated = moveAudioTrackClip(audioTrackClips, selected.id, requestedStartMs, projectDurationMs)
        if (updated == null) {
            exportMessage = "No free space remains on this audio lane at or after that position."
        } else {
            changedAudioTracks(updated)
            exportMessage = "Moved " + selected.name + " without changing its source audio."
        }
    }
    fun trimSelectedAudioTrack(sourceStartMs: Long, sourceEndMs: Long) {
        val selected = selectedAudioTrack ?: return
        val projectDurationMs = calculateTimelineDurationMs(clips.map { it.durationMs })
        val updated = trimAudioTrackClip(
            audioTrackClips,
            selected.id,
            sourceStartMs,
            sourceEndMs,
            projectDurationMs,
        )
        if (updated == null) {
            exportMessage = "That trim is too short, exceeds the source, or overlaps another item on this lane."
        } else {
            changedAudioTracks(updated)
            exportMessage = "Updated " + selected.name + " source range. Original audio remains unchanged."
        }
    }
    fun commitSelectedClipAudioVolume(volume: Float) {
        val clip = clips.getOrNull(selectedIndex) ?: return
        if (!isValidClipAudioVolume(volume) || clip.audioVolume == volume) return
        changed(clips.toMutableList().apply {
            this[selectedIndex] = clip.copy(audioVolume = volume)
        })
        val currentPosition = if (player.currentMediaItem?.mediaId == clip.id) player.currentPosition else 0L
        player.setVolume(
            clipAudioPlaybackGain(
                volume,
                currentPosition,
                clip.durationMs,
                clip.fadeInMs,
                clip.fadeOutMs,
            ),
        )
        exportMessage = "Clip volume set to ${clipAudioVolumePercent(volume)}%"
    }
    fun commitSelectedClipAudioFades(fadeInMs: Long, fadeOutMs: Long) {
        val clip = clips.getOrNull(selectedIndex) ?: return
        if (!isValidClipAudioFades(fadeInMs, fadeOutMs)) return
        if (clip.fadeInMs == fadeInMs && clip.fadeOutMs == fadeOutMs) return
        changed(clips.toMutableList().apply {
            this[selectedIndex] = clip.copy(fadeInMs = fadeInMs, fadeOutMs = fadeOutMs)
        })
        exportMessage = "Clip fades updated"
    }
    fun commitSelectedClipVisualAdjustments(
        brightness: Int,
        contrast: Int,
        saturation: Int,
    ) {
        val clip = clips.getOrNull(selectedIndex) ?: return
        val next = decodeClipVisualAdjustments(brightness, contrast, saturation) ?: return
        if (clip.visualAdjustments == next) return
        changed(clips.toMutableList().apply {
            this[selectedIndex] = clip.copy(visualAdjustments = next)
        })
        exportMessage = "Clip colour adjustments updated"
    }
    fun commitSelectedClipTextOverlay(
        layerIndex: Int,
        rawText: String,
        fontScale: Float,
        position: ClipTextPosition,
        startMs: Long,
        endMs: Long,
    ) {
        val clip = clips.getOrNull(selectedIndex) ?: return
        val normalizedText = rawText.trim()
        val overlays = clip.allTextOverlays.toMutableList()
        if (normalizedText.isBlank()) {
            if (layerIndex !in overlays.indices) return
            overlays.removeAt(layerIndex)
        } else {
            val safeTiming = if (clip.durationMs > 0L) {
                val safeStartMs = startMs.coerceIn(0L, clip.durationMs - 1L)
                val safeEndMs = endMs.coerceIn(safeStartMs + 1L, clip.durationMs)
                safeStartMs to safeEndMs
            } else {
                null
            }
            val overlay = safeTiming?.let { (safeStartMs, safeEndMs) ->
                decodeClipTextOverlay(
                    raw = normalizedText,
                    fontScale = fontScale,
                    position = position.name,
                    startMs = safeStartMs,
                    endMs = if (safeEndMs >= clip.durationMs) null else safeEndMs,
                )
            }
            if (overlay == null) {
                exportMessage = "Text timing must be a valid range inside this clip, with at least one millisecond visible."
                return
            }
            when {
                layerIndex in overlays.indices -> overlays[layerIndex] = overlay
                layerIndex == overlays.size && overlays.size < MAX_CLIP_TEXT_OVERLAYS -> overlays.add(overlay)
                else -> {
                    exportMessage = "A clip can contain up to $MAX_CLIP_TEXT_OVERLAYS text layers."
                    return
                }
            }
        }
        val updatedClip = clip.copy(
            textOverlay = overlays.firstOrNull(),
            additionalTextOverlays = overlays.drop(1),
        )
        if (updatedClip == clip) return
        changed(clips.toMutableList().apply { this[selectedIndex] = updatedClip })
        selectedTextLayerIndex = if (overlays.isEmpty()) 0 else {
            if (normalizedText.isBlank()) layerIndex.coerceAtMost(overlays.lastIndex) else layerIndex
        }
        exportMessage = if (normalizedText.isBlank()) "Removed text layer" else "Text layer updated"
    }
    fun openTrimDialog(clip: EditClip) {
        if (clip.uri in missingMediaUris || clip.durationMs < MIN_CLIP_DURATION_MS) return
        trimTargetClipId = clip.id
        trimSourceDurationMs = clip.sourceEndMs.coerceAtLeast(1L)
        isLoadingTrimSource = true
        trimSourceReadFailed = false
        showTrimDialog = true

        exportScope.launch {
            val sourceDuration = withContext(Dispatchers.IO) {
                MediaMetadataReader.videoDurationMs(context, Uri.parse(clip.uri))
            }
            if (trimTargetClipId == clip.id) {
                val sourceRangeIsValid = isClipRangeWithinSourceDuration(
                    sourceDurationMs = sourceDuration,
                    sourceStartMs = clip.sourceStartMs,
                    sourceEndMs = clip.sourceEndMs,
                )
                trimSourceReadFailed = !sourceRangeIsValid
                trimSourceDurationMs = if (sourceRangeIsValid) {
                    sourceDuration ?: clip.sourceEndMs
                } else {
                    clip.sourceEndMs.coerceAtLeast(1L)
                }
                isLoadingTrimSource = false
                if (!sourceRangeIsValid) {
                    exportMessage = "Cannot safely trim this clip: its original source duration could not be verified or is shorter than the saved out point. Relink the original video first."
                }
            }
        }
    }
    fun undoEdit() {
        if (undoStack.isEmpty()) return
        redoStack.add(ProjectEditSnapshot(clips.toList(), audioTrackClips.toList()))
        if (redoStack.size > 50) redoStack.removeAt(0)
        val previous = undoStack.removeAt(undoStack.lastIndex)
        clips = previous.clips
        audioTrackClips = audioTracksForHistorySnapshot(previous, audioTrackClips)
        selectedAudioTrackId = selectedAudioTrackId?.takeIf { id -> audioTrackClips.any { it.id == id } }
            ?: audioTrackClips.lastOrNull()?.id
        selectedIndex = selectedIndex.coerceIn(0, (previous.clips.size - 1).coerceAtLeast(0))
        saveProject(previous.clips)
        saveProjectHistory(preferences, activeProjectId, undoStack, redoStack)
        exportMessage = "Undo completed"
    }
    fun redoEdit() {
        if (redoStack.isEmpty()) return
        undoStack.add(ProjectEditSnapshot(clips.toList(), audioTrackClips.toList()))
        if (undoStack.size > 50) undoStack.removeAt(0)
        val next = redoStack.removeAt(redoStack.lastIndex)
        clips = next.clips
        audioTrackClips = audioTracksForHistorySnapshot(next, audioTrackClips)
        selectedAudioTrackId = selectedAudioTrackId?.takeIf { id -> audioTrackClips.any { it.id == id } }
            ?: audioTrackClips.lastOrNull()?.id
        selectedIndex = selectedIndex.coerceIn(0, (next.clips.size - 1).coerceAtLeast(0))
        saveProject(next.clips)
        saveProjectHistory(preferences, activeProjectId, undoStack, redoStack)
        exportMessage = "Redo completed"
    }

    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val projectName = projects.firstOrNull { it.id == activeProjectId }?.name ?: "VyroCut project"
            val backup = encodeProjectBackup(
                projectName,
                clips,
                projectCanvasSettingsFor(canvasAspectRatio, canvasBackground),
                audioTrackClips,
            )
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
                persistProjectAudioTracks(
                    preferences,
                    imported.id,
                    backup.audioTracks,
                    calculateTimelineDurationMs(backup.clips.map { it.durationMs }),
                )
                preferences.edit()
                    .putString(
                        "project_" + imported.id + "_canvas_settings",
                        encodeProjectCanvasSettings(backup.canvasSettings),
                    )
                    .apply()
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

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val selectedType = selectedAudioTrackType
            val projectIdAtPick = activeProjectId
            val permission = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (permission.isFailure) {
                exportMessage = "Could not keep access to this audio file. Choose a provider that supports persistent access."
            } else {
                isReadingMedia = true
                exportMessage = "Reading audio metadata…"
                exportScope.launch {
                    val metadata = withContext(Dispatchers.IO) {
                        readAudioDurationMs(context, uri)?.let { duration ->
                            duration to runCatching {
                                MediaMetadataReader.displayName(context.contentResolver, uri)
                            }.getOrDefault("Imported audio")
                        }
                    }
                    isReadingMedia = false
                    if (activeProjectId != projectIdAtPick) {
                        exportMessage = "The active project changed while audio was being read. Choose the source again."
                    } else if (metadata == null) {
                        exportMessage = "Could not read this audio file's duration. It was not added."
                    } else {
                        val (sourceDurationMs, name) = metadata
                        val projectDurationMs = calculateTimelineDurationMs(clips.map { it.durationMs })
                        val requestedStartMs = if (
                            selectedType == AudioTrackType.MUSIC &&
                            audioTrackClips.none { it.type == selectedType }
                        ) 0L else timelinePositionMs.toLong().coerceAtLeast(0L)
                        val insertionSlot = resolveAudioTrackInsertionSlot(
                            audioTrackClips,
                            selectedType,
                            requestedStartMs,
                            sourceDurationMs,
                            projectDurationMs,
                        )
                        if (insertionSlot == null) {
                            exportMessage = "There is no free space on the ${selectedType.name.lowercase()} lane for this audio file."
                        } else {
                            val inserted = AudioTrackClip(
                                id = java.util.UUID.randomUUID().toString(),
                                type = selectedType,
                                uri = uri.toString(),
                                name = name.ifBlank { "Imported audio" },
                                startMs = insertionSlot.startMs,
                                sourceStartMs = 0L,
                                sourceEndMs = insertionSlot.durationMs,
                                sourceDurationMs = sourceDurationMs,
                            )
                            changedAudioTracks(audioTrackClips + inserted)
                            selectedAudioTrackType = selectedType
                            selectedAudioTrackId = inserted.id
                            exportMessage = "Added ${selectedType.name.lowercase()} • $name"
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
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                Box(
                    Modifier.fillMaxWidth().height(148.dp)
                        .background(canvasBackground, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedClip == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Movie, null, tint = StudioMuted, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("Import a video to start editing", color = StudioMuted, fontSize = 12.sp)
                        }
                    } else {
                        AndroidView(
                            factory = { viewContext -> PlayerView(viewContext).apply {
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
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
                    IconButton(
                        onClick = {
                            resolveTimelineSeekTargetAfterDelta(
                                clipDurationsMs,
                                timelinePositionMs.toLong(),
                                -5_000L,
                            )?.let { target ->
                                selectedIndex = target.clipIndex
                                player.seekTo(target.clipIndex, target.positionMs)
                                timelinePositionMs = calculateTimelinePositionMs(
                                    clipDurationsMs,
                                    target.clipIndex,
                                    target.positionMs,
                                ).toFloat()
                                clips.getOrNull(target.clipIndex)?.let { clip ->
                                    savePreviewPosition(preferences, activeProjectId, clip, target.positionMs)
                                }
                            }
                        },
                        enabled = clips.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back 5 seconds", tint = StudioAccent)
                    }
                    IconButton(
                        onClick = {
                            resolveTimelineSeekTargetAfterDelta(
                                clipDurationsMs,
                                timelinePositionMs.toLong(),
                                5_000L,
                            )?.let { target ->
                                selectedIndex = target.clipIndex
                                player.seekTo(target.clipIndex, target.positionMs)
                                timelinePositionMs = calculateTimelinePositionMs(
                                    clipDurationsMs,
                                    target.clipIndex,
                                    target.positionMs,
                                ).toFloat()
                                clips.getOrNull(target.clipIndex)?.let { clip ->
                                    savePreviewPosition(preferences, activeProjectId, clip, target.positionMs)
                                }
                            }
                        },
                        enabled = clips.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward 5 seconds", tint = StudioAccent)
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
                    Box {
                        IconButton(
                            onClick = { showClipActions = true },
                            enabled = selectedClip != null,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.MoreHoriz, "Clip actions", tint = StudioMuted)
                        }
                        DropdownMenu(
                            expanded = showClipActions,
                            onDismissRequest = { showClipActions = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Move clip left") },
                                enabled = selectedIndex > 0,
                                onClick = {
                                    showClipActions = false
                                    val destination = selectedIndex - 1
                                    if (destination in clips.indices) {
                                        changed(moveTimelineItem(clips, selectedIndex, destination))
                                        selectedIndex = destination
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate clip") },
                                enabled = selectedClip != null,
                                onClick = {
                                    showClipActions = false
                                    duplicateTimelineItemAfter(clips, selectedIndex) { clip ->
                                        clip.copy(id = java.util.UUID.randomUUID().toString())
                                    }?.let { duplicate ->
                                        changed(duplicate.items)
                                        selectedIndex = duplicate.selectedIndex
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move clip right") },
                                enabled = selectedIndex < clips.lastIndex,
                                onClick = {
                                    showClipActions = false
                                    val destination = selectedIndex + 1
                                    if (destination in clips.indices) {
                                        changed(moveTimelineItem(clips, selectedIndex, destination))
                                        selectedIndex = destination
                                    }
                                },
                            )
                        }
                    }
                }
                TimelineEditorStrip(
                    clips = clips,
                    audioTracks = audioTrackClips,
                    selectedIndex = selectedIndex,
                    playheadPositionMs = timelinePositionMs.toLong(),
                    isPlaying = isPlaying,
                    onSelectAudioTrack = { trackClip ->
                        selectedAudioTrackId = trackClip.id
                        selectedAudioTrackType = trackClip.type
                        activeTool = "Audio"
                        resolveTimelineSeekTarget(clipDurationsMs, trackClip.startMs)?.let { target ->
                            selectedIndex = target.clipIndex
                            player.seekTo(target.clipIndex, target.positionMs)
                            timelinePositionMs = calculateTimelinePositionMs(
                                clipDurationsMs,
                                target.clipIndex,
                                target.positionMs,
                            ).coerceIn(0L, timelineDurationMs).toFloat()
                        }
                    },
                    onSeek = { target ->
                        selectedIndex = target.clipIndex
                        player.seekTo(target.clipIndex, target.positionMs)
                        timelinePositionMs = calculateTimelinePositionMs(
                            clipDurationsMs,
                            target.clipIndex,
                            target.positionMs,
                        ).coerceIn(0L, timelineDurationMs).toFloat()
                        clips.getOrNull(target.clipIndex)?.let { clip ->
                            savePreviewPosition(preferences, activeProjectId, clip, target.positionMs)
                        }
                    },
                    onSelectText = { index, layerIndex ->
                        clips.getOrNull(index)?.let { clip ->
                            selectedIndex = index
                            selectedTextLayerIndex = layerIndex
                            activeTool = "Text"
                            val positionMs = clipTextOverlaySeekPositionMs(
                                clip.allTextOverlays.getOrNull(layerIndex)?.startMs,
                                clip.durationMs,
                            )
                            player.seekTo(index, positionMs)
                            timelinePositionMs = calculateTimelinePositionMs(
                                clipDurationsMs,
                                index,
                                positionMs,
                            ).coerceIn(0L, timelineDurationMs).toFloat()
                            savePreviewPosition(preferences, activeProjectId, clip, positionMs)
                        }
                    }
                )

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
                        val splitOffsetMs = (halves.first.endMs - clip.sourceStartMs)
                            .coerceIn(0L, clip.durationMs)
                        val (leftTextOverlays, rightTextOverlays) = splitClipTextOverlaysAt(
                            clip.allTextOverlays,
                            splitOffsetMs,
                            clip.durationMs,
                        )
                        val left = clip.copy(
                            sourceEndMs = halves.first.endMs,
                            fadeOutMs = 0L,
                            textOverlay = leftTextOverlays.firstOrNull(),
                            additionalTextOverlays = leftTextOverlays.drop(1),
                        )
                        val right = clip.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            sourceStartMs = halves.second.startMs,
                            sourceEndMs = halves.second.endMs,
                            fadeInMs = 0L,
                            textOverlay = rightTextOverlays.firstOrNull(),
                            additionalTextOverlays = rightTextOverlays.drop(1),
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
                    OutlinedButton(
                        onClick = { selectedClip?.let { openTrimDialog(it) } },
                        enabled = selectedClip != null &&
                            selectedClip.durationMs >= MIN_CLIP_DURATION_MS &&
                            selectedClip.uri !in missingMediaUris &&
                            !isReadingMedia,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Trim", maxLines = 1) }
                    IconButton(onClick = { if (selectedClip != null) changed(clips.toMutableList().apply { removeAt(selectedIndex) }) }, enabled = selectedClip != null) {
                        Icon(Icons.Default.Delete, "Delete selected clip", tint = Color(0xFFFF8A80))
                    }
                }
                VyroCutToolDock(
                    activeTool = activeTool,
                    onToolSelected = { tool ->
                        activeTool = tool
                        if (tool == "Adjust" && selectedEffect !in setOf("Brightness", "Contrast", "Saturation")) {
                            selectedEffect = "Brightness"
                        }
                    }
                )
                if (activeTool == "Ratio" || activeTool == "Canvas") {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (activeTool == "Ratio") "ASPECT RATIO" else "CANVAS BACKGROUND", color = StudioText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (activeTool == "Ratio") {
                                listOf("9:16" to (9f / 16f), "16:9" to (16f / 9f), "1:1" to 1f).forEach { (label, ratio) ->
                                    FilterChip(
                                        selected = canvasAspectRatio == ratio,
                                        onClick = {
                                            canvasAspectRatio = ratio
                                            persistCanvasSettings(ratio, canvasBackground)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            } else {
                                listOf("Black" to Color.Black, "Dark" to Color(0xFF202322), "White" to Color.White).forEach { (label, color) ->
                                    FilterChip(
                                        selected = canvasBackground == color,
                                        onClick = {
                                            canvasBackground = color
                                            persistCanvasSettings(canvasAspectRatio, color)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (activeTool == "Effects" || activeTool == "Filters" || activeTool == "Adjust") {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            when (activeTool) {
                                "Effects" -> "VIDEO EFFECTS"
                                "Filters" -> "FILTERS"
                                else -> "CLIP ADJUSTMENTS"
                            },
                            color = StudioText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val options = when (activeTool) {
                                "Effects" -> listOf("Edge Glow", "Blur", "Glitch", "Vignette", "Shake")
                                "Filters" -> ClipVisualPreset.values().map { it.label }
                                else -> listOf("Brightness", "Contrast", "Saturation")
                            }
                            options.forEach { item ->
                                FilterChip(
                                    selected = if (activeTool == "Filters") {
                                        selectedClip?.visualAdjustments == clipVisualPresetAdjustments(item)
                                    } else {
                                        selectedEffect == item
                                    },
                                    onClick = {
                                        if (activeTool == "Filters") {
                                            val preset = clipVisualPresetAdjustments(item)
                                            if (preset != null && selectedClip != null) {
                                                clipBrightnessDraft = preset.brightness.toFloat()
                                                clipContrastDraft = preset.contrast.toFloat()
                                                clipSaturationDraft = preset.saturation.toFloat()
                                                commitSelectedClipVisualAdjustments(
                                                    brightness = preset.brightness,
                                                    contrast = preset.contrast,
                                                    saturation = preset.saturation,
                                                )
                                            }
                                        } else {
                                            selectedEffect = item
                                        }
                                    },
                                    label = { Text(item, fontSize = 11.sp) }
                                )
                            }
                        }
                        if (activeTool == "Adjust") {
                            if (selectedClip == null) {
                                Text("Select a video clip to adjust its colour.", color = StudioMuted, fontSize = 10.sp)
                            } else {
                                val adjustmentValue = when (selectedEffect) {
                                    "Contrast" -> clipContrastDraft
                                    "Saturation" -> clipSaturationDraft
                                    else -> clipBrightnessDraft
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedEffect, color = StudioMuted, fontSize = 11.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text("${adjustmentValue.toInt()}%", color = StudioAccent, fontSize = 11.sp)
                                    TextButton(
                                        onClick = {
                                            clipBrightnessDraft = 0f
                                            clipContrastDraft = 0f
                                            clipSaturationDraft = 0f
                                            commitSelectedClipVisualAdjustments(0, 0, 0)
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    ) { Text("Reset", fontSize = 10.sp) }
                                }
                                Slider(
                                    value = adjustmentValue.coerceIn(-100f, 100f),
                                    onValueChange = { value ->
                                        when (selectedEffect) {
                                            "Contrast" -> clipContrastDraft = value
                                            "Saturation" -> clipSaturationDraft = value
                                            else -> clipBrightnessDraft = value
                                        }
                                    },
                                    onValueChangeFinished = {
                                        commitSelectedClipVisualAdjustments(
                                            brightness = clipBrightnessDraft.toInt(),
                                            contrast = clipContrastDraft.toInt(),
                                            saturation = clipSaturationDraft.toInt(),
                                        )
                                    },
                                    valueRange = -100f..100f,
                                    steps = 199,
                                    colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent)
                                )
                                Text(
                                    "Non-destructive • Applied to this clip in preview and MP4 export",
                                    color = StudioMuted,
                                    fontSize = 10.sp
                                )
                            }
                        } else if (activeTool == "Effects") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Intensity", color = StudioMuted, fontSize = 11.sp)
                                Spacer(Modifier.weight(1f))
                                Text("${(effectIntensity * 100).toInt()}%", color = StudioAccent, fontSize = 11.sp)
                            }
                            Slider(
                                value = effectIntensity,
                                onValueChange = { effectIntensity = it },
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent)
                            )
                            Text("Effect rendering is not implemented yet.", color = StudioMuted, fontSize = 10.sp)
                        } else {
                            if (selectedClip == null) {
                                Text("Select a video clip to apply a tonal preset.", color = StudioMuted, fontSize = 10.sp)
                            } else {
                                Text(
                                    "Preset applied to this clip • Fine-tune it in Adjust",
                                    color = StudioMuted,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
                if (activeTool == "Audio" && selectedClip != null) {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("CLIP VOLUME", color = StudioText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.weight(1f))
                            Text("${clipAudioVolumePercent(clipVolumeDraft)}%", color = StudioAccent, fontSize = 11.sp)
                            TextButton(
                                onClick = {
                                    val nextVolume = if (clipVolumeDraft == 0f) 1f else 0f
                                    clipVolumeDraft = nextVolume
                                    commitSelectedClipAudioVolume(nextVolume)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            ) {
                                Text(if (clipVolumeDraft == 0f) "Unmute" else "Mute", fontSize = 10.sp)
                            }
                        }
                        Slider(
                            value = clipVolumeDraft.coerceIn(0f, 1f),
                            onValueChange = { clipVolumeDraft = it },
                            onValueChangeFinished = { commitSelectedClipAudioVolume(clipVolumeDraft) },
                            valueRange = 0f..1f,
                            steps = 19,
                            colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                        )
                        val fadeRangeMaxMs = selectedClip.durationMs.toFloat().coerceAtLeast(1f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("FADE IN", color = StudioText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.weight(1f))
                            Text(String.format("%.1fs", clipFadeInDraft / 1_000f), color = StudioAccent, fontSize = 11.sp)
                        }
                        Slider(
                            value = clipFadeInDraft.coerceIn(0f, fadeRangeMaxMs),
                            onValueChange = { clipFadeInDraft = it },
                            onValueChangeFinished = {
                                commitSelectedClipAudioFades(clipFadeInDraft.toLong(), clipFadeOutDraft.toLong())
                            },
                            valueRange = 0f..fadeRangeMaxMs,
                            colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("FADE OUT", color = StudioText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.weight(1f))
                            Text(String.format("%.1fs", clipFadeOutDraft / 1_000f), color = StudioAccent, fontSize = 11.sp)
                        }
                        Slider(
                            value = clipFadeOutDraft.coerceIn(0f, fadeRangeMaxMs),
                            onValueChange = { clipFadeOutDraft = it },
                            onValueChangeFinished = {
                                commitSelectedClipAudioFades(clipFadeInDraft.toLong(), clipFadeOutDraft.toLong())
                            },
                            valueRange = 0f..fadeRangeMaxMs,
                            colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                        )
                        Text("Non-destructive • Smooth fades apply to preview and MP4 export", color = StudioMuted, fontSize = 9.sp)
                    }
                }
                if (activeTool == "Audio") {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AUDIO LANES", color = StudioText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.weight(1f))
                            Text("${audioTrackClips.size}/${MAX_AUDIO_TRACK_CLIPS} clips", color = StudioMuted, fontSize = 9.sp)
                        }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AudioTrackType.values().forEach { type ->
                                FilterChip(
                                    selected = selectedAudioTrackType == type,
                                    onClick = { selectedAudioTrackType = type },
                                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 10.sp) },
                                )
                            }
                        }
                        Button(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            enabled = clips.isNotEmpty() && !isReadingMedia && audioTrackClips.size < MAX_AUDIO_TRACK_CLIPS,
                            colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF00382A)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, null)
                            Text("Import ${selectedAudioTrackType.name.lowercase()} at playhead")
                        }
                        val laneClips = audioTrackClips.filter { it.type == selectedAudioTrackType }.sortedBy { it.startMs }
                        if (laneClips.isEmpty()) {
                            Text("No ${selectedAudioTrackType.name.lowercase()} clips yet. Imported audio stays local to this project.", color = StudioMuted, fontSize = 9.sp)
                        } else {
                            laneClips.forEach { trackClip ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(
                                            if (trackClip.id == selectedAudioTrackId) Color(0xFF16483D) else Color(0xFF252A28),
                                            RoundedCornerShape(6.dp),
                                        )
                                        .clickable { selectedAudioTrackId = trackClip.id },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)) {
                                        Text(trackClip.name, color = StudioText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${formatTime(trackClip.startMs)} – ${formatTime(trackClip.endMs)} • ${(trackClip.volume * 100f).toInt()}%",
                                            color = StudioMuted,
                                            fontSize = 8.sp,
                                        )
                                    }
                                    IconButton(
                                        onClick = { changedAudioTracks(audioTrackClips.filterNot { it.id == trackClip.id }) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(Icons.Default.Delete, "Remove audio clip", tint = Color(0xFFFF8A80), modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                        if (selectedAudioTrack != null && selectedAudioTrack.type == selectedAudioTrackType) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("TRACK VOLUME", color = StudioText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text("${(audioTrackVolumeDraft * 100f).toInt()}%", color = StudioAccent, fontSize = 10.sp)
                            }
                            Slider(
                                value = audioTrackVolumeDraft.coerceIn(0f, 1f),
                                onValueChange = { audioTrackVolumeDraft = it },
                                onValueChangeFinished = {
                                    changedAudioTracks(audioTrackClips.map {
                                        if (it.id == selectedAudioTrack.id) it.copy(volume = audioTrackVolumeDraft) else it
                                    })
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("FADE IN", color = StudioText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(String.format("%.1fs", audioTrackFadeInDraft / 1_000f), color = StudioAccent, fontSize = 10.sp)
                            }
                            Slider(
                                value = audioTrackFadeInDraft.coerceIn(0f, selectedAudioTrack.durationMs.toFloat()),
                                onValueChange = { audioTrackFadeInDraft = it },
                                onValueChangeFinished = {
                                    changedAudioTracks(audioTrackClips.map {
                                        if (it.id == selectedAudioTrack.id) it.copy(
                                            fadeInMs = audioTrackFadeInDraft.toLong(),
                                            fadeOutMs = audioTrackFadeOutDraft.toLong(),
                                        ) else it
                                    })
                                },
                                valueRange = 0f..selectedAudioTrack.durationMs.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("FADE OUT", color = StudioText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(String.format("%.1fs", audioTrackFadeOutDraft / 1_000f), color = StudioAccent, fontSize = 10.sp)
                            }
                            Slider(
                                value = audioTrackFadeOutDraft.coerceIn(0f, selectedAudioTrack.durationMs.toFloat()),
                                onValueChange = { audioTrackFadeOutDraft = it },
                                onValueChangeFinished = {
                                    changedAudioTracks(audioTrackClips.map {
                                        if (it.id == selectedAudioTrack.id) it.copy(
                                            fadeInMs = audioTrackFadeInDraft.toLong(),
                                            fadeOutMs = audioTrackFadeOutDraft.toLong(),
                                        ) else it
                                    })
                                },
                                valueRange = 0f..selectedAudioTrack.durationMs.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                            )
                            val sourceDurationMs = selectedAudioTrack.sourceDurationMs
                            val projectDurationMs = calculateTimelineDurationMs(clips.map { it.durationMs })
                            val maximumTimelineStartMs = (projectDurationMs - selectedAudioTrack.durationMs).coerceAtLeast(0L)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("SOURCE IN", color = StudioText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(formatTime(audioTrackSourceStartDraft.toLong()), color = StudioAccent, fontSize = 10.sp)
                            }
                            Slider(
                                value = audioTrackSourceStartDraft.coerceIn(
                                    0f,
                                    (audioTrackSourceEndDraft - MIN_AUDIO_TRACK_CLIP_DURATION_MS).coerceAtLeast(0f),
                                ),
                                onValueChange = {
                                    audioTrackSourceStartDraft = it.coerceAtMost(
                                        (audioTrackSourceEndDraft - MIN_AUDIO_TRACK_CLIP_DURATION_MS).coerceAtLeast(0f),
                                    )
                                },
                                onValueChangeFinished = {
                                    trimSelectedAudioTrack(audioTrackSourceStartDraft.toLong(), audioTrackSourceEndDraft.toLong())
                                },
                                valueRange = 0f..(sourceDurationMs - MIN_AUDIO_TRACK_CLIP_DURATION_MS).coerceAtLeast(1L).toFloat(),
                                enabled = sourceDurationMs > MIN_AUDIO_TRACK_CLIP_DURATION_MS,
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("SOURCE OUT", color = StudioText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(formatTime(audioTrackSourceEndDraft.toLong()), color = StudioAccent, fontSize = 10.sp)
                            }
                            val minimumSourceEndMs = (audioTrackSourceStartDraft.toLong() + MIN_AUDIO_TRACK_CLIP_DURATION_MS)
                                .coerceAtMost(sourceDurationMs)
                            Slider(
                                value = audioTrackSourceEndDraft.coerceIn(minimumSourceEndMs.toFloat(), sourceDurationMs.toFloat()),
                                onValueChange = {
                                    audioTrackSourceEndDraft = it.coerceIn(
                                        minimumSourceEndMs.toFloat(),
                                        sourceDurationMs.toFloat(),
                                    )
                                },
                                onValueChangeFinished = {
                                    trimSelectedAudioTrack(audioTrackSourceStartDraft.toLong(), audioTrackSourceEndDraft.toLong())
                                },
                                valueRange = minimumSourceEndMs.toFloat()..sourceDurationMs.toFloat().coerceAtLeast(minimumSourceEndMs.toFloat() + 1f),
                                enabled = sourceDurationMs > MIN_AUDIO_TRACK_CLIP_DURATION_MS,
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("TIMELINE START", color = StudioText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(formatTime(audioTrackStartDraft.toLong()), color = StudioAccent, fontSize = 10.sp)
                            }
                            Slider(
                                value = audioTrackStartDraft.coerceIn(0f, maximumTimelineStartMs.toFloat()),
                                onValueChange = { audioTrackStartDraft = it },
                                onValueChangeFinished = { moveSelectedAudioTrack(audioTrackStartDraft.toLong()) },
                                valueRange = 0f..maximumTimelineStartMs.toFloat().coerceAtLeast(1f),
                                enabled = maximumTimelineStartMs > 0L,
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { moveSelectedAudioTrack(timelinePositionMs.toLong()) },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                ) { Text("Move to playhead", fontSize = 10.sp) }
                                TextButton(
                                    onClick = { trimSelectedAudioTrack(0L, sourceDurationMs) },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                ) { Text("Restore full source", fontSize = 10.sp) }
                            }
                            Text("Non-destructive source trims and lane moves • independent lanes mix in preview and MP4 export", color = StudioMuted, fontSize = 9.sp)
                        }
                    }
                }
                if (activeTool == "Stickers") {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text("ADD STICKER", color = StudioText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Emoji", "Shapes", "Decorations", "Arrows").forEach { option ->
                                Surface(color = Color(0xFF29312E), shape = RoundedCornerShape(7.dp)) {
                                    Text(option, color = StudioText, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
                                }
                            }
                        }
                        Text("Sticker rendering is not implemented yet.", color = StudioMuted, fontSize = 9.sp)
                    }
                }
                if (activeTool == "Text") {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text("TEXT LAYERS", color = StudioText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        if (selectedClip == null) {
                            Text("Select a video clip before adding text.", color = StudioMuted, fontSize = 10.sp)
                        } else {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                selectedClip.allTextOverlays.forEachIndexed { index, _ ->
                                    FilterChip(
                                        selected = selectedTextLayerIndex == index,
                                        onClick = { selectedTextLayerIndex = index },
                                        label = { Text("Layer " + (index + 1), fontSize = 10.sp) },
                                    )
                                }
                                if (selectedClip.allTextOverlays.size < MAX_CLIP_TEXT_OVERLAYS) {
                                    FilterChip(
                                        selected = selectedTextLayerIndex == selectedClip.allTextOverlays.size,
                                        onClick = { selectedTextLayerIndex = selectedClip.allTextOverlays.size },
                                        label = { Text("+ Add layer", fontSize = 10.sp) },
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = clipTextDraft,
                                onValueChange = { value ->
                                    if (value.length <= MAX_CLIP_TEXT_OVERLAY_LENGTH + 16) clipTextDraft = value
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Text shown over this clip") },
                                placeholder = { Text("Type a title or caption") },
                                minLines = 2,
                                maxLines = 3,
                                supportingText = {
                                    Text(
                                        "${clipTextDraft.trim().length}/${MAX_CLIP_TEXT_OVERLAY_LENGTH} characters • " +
                                            "${clipTextScaleDraft.let { (it * 100).toInt() }}% size • " +
                                            clipTextPositionDraft.name.lowercase(),
                                    )
                                },
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("SIZE", color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("Small" to 0.8f, "Medium" to 1f, "Large" to 1.25f).forEach { (label, scale) ->
                                        FilterChip(
                                            selected = clipTextScaleDraft == scale,
                                            onClick = { clipTextScaleDraft = scale },
                                            label = { Text(label, fontSize = 10.sp) },
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("POSITION", color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ClipTextPosition.values().forEach { position ->
                                        FilterChip(
                                            selected = clipTextPositionDraft == position,
                                            onClick = { clipTextPositionDraft = position },
                                            label = { Text(position.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 10.sp) },
                                        )
                                    }
                                }
                            }
                            if (selectedClip.durationMs > MIN_CLIP_TEXT_OVERLAY_DURATION_MS) {
                                val textDurationMs = selectedClip.durationMs
                                val minimumSegmentMs = MIN_CLIP_TEXT_OVERLAY_DURATION_MS
                                val maximumStartMs = (textDurationMs - minimumSegmentMs).toFloat()
                                val safeStartMs = clipTextStartDraft.coerceIn(0f, maximumStartMs)
                                val minimumEndMs = (safeStartMs.toLong() + minimumSegmentMs).toFloat()
                                val safeEndMs = clipTextEndDraft.coerceIn(minimumEndMs, textDurationMs.toFloat())
                                Text(
                                    "TIMING • ${formatTime(safeStartMs.toLong())} – ${formatTime(safeEndMs.toLong())}",
                                    color = StudioMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("IN", color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = safeStartMs,
                                        onValueChange = { value ->
                                            clipTextStartDraft = value.coerceIn(0f, maximumStartMs)
                                            if (clipTextEndDraft < clipTextStartDraft + minimumSegmentMs) {
                                                clipTextEndDraft = clipTextStartDraft + minimumSegmentMs
                                            }
                                        },
                                        valueRange = 0f..maximumStartMs,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("OUT", color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = safeEndMs,
                                        onValueChange = { value ->
                                            clipTextEndDraft = value.coerceIn(minimumEndMs, textDurationMs.toFloat())
                                        },
                                        valueRange = minimumEndMs..textDurationMs.toFloat(),
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                                    )
                                }
                            } else {
                                Text(
                                    "This short clip uses full-clip text timing.",
                                    color = StudioMuted,
                                    fontSize = 9.sp,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        commitSelectedClipTextOverlay(
                                            selectedTextLayerIndex,
                                            clipTextDraft,
                                            clipTextScaleDraft,
                                            clipTextPositionDraft,
                                            clipTextStartDraft.toLong(),
                                            clipTextEndDraft.toLong(),
                                        )
                                    },
                                    enabled = clipTextDraft.trim().length <= MAX_CLIP_TEXT_OVERLAY_LENGTH,
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF00382A)),
                                    modifier = Modifier.weight(1f),
                                ) { Text("Apply layer") }
                                TextButton(
                                    onClick = {
                                        clipTextDraft = ""
                                        commitSelectedClipTextOverlay(
                                            selectedTextLayerIndex,
                                            "",
                                            clipTextScaleDraft,
                                            clipTextPositionDraft,
                                            clipTextStartDraft.toLong(),
                                            clipTextEndDraft.toLong(),
                                        )
                                    },
                                    enabled = selectedTextOverlay != null || clipTextDraft.isNotBlank(),
                                ) { Text("Remove layer") }
                            }
                            Text(
                                "Non-destructive • Timed overlay is shared by preview and MP4 export",
                                color = StudioMuted,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (clips.isNotEmpty() && !isExporting && !isReadingMedia) {
                                val exportSession = java.util.UUID.randomUUID().toString()
                                val exportClips = clips.toList()
                                val exportAudioTracks = audioTrackClips.toList()
                                val exportCanvasSettings = projectCanvasSettingsFor(canvasAspectRatio, canvasBackground)
                                val exportCanvasSize = exportCanvasSizeForPreset(exportCanvasSettings.aspectRatioPreset)
                                val exportBackgroundArgb = exportCanvasBackgroundArgbForPreset(
                                    exportCanvasSettings.backgroundPreset,
                                )
                                val requiresCustomLetterboxBackground =
                                    exportCanvasSettings.backgroundPreset != "BLACK"
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
                                    val sourceMetadata = withContext(Dispatchers.IO) {
                                        inspectExportSources(context, exportRequirements)
                                    }
                                    val preflightError = validateExportPreflight(
                                        exportRequirements,
                                        sourceMetadata,
                                        requireVideoDimensions = requiresCustomLetterboxBackground,
                                    )
                                    if (exportSessionId != exportSession) return@exportPreparation
                                    if (preflightError != null) {
                                        isExporting = false
                                        exportSessionId = null
                                        exportMessage = "Export stopped: $preflightError"
                                        return@exportPreparation
                                    }
                                    if (!validateAudioTrackClips(exportAudioTracks, calculateTimelineDurationMs(exportClips.map { it.durationMs }))) {
                                        isExporting = false
                                        exportSessionId = null
                                        exportMessage = "Export stopped: audio lanes contain an invalid or overlapping segment."
                                        return@exportPreparation
                                    }
                                    val audioSourceDurations = withContext(Dispatchers.IO) {
                                        exportAudioTracks.associate { clip ->
                                            clip.uri to readAudioDurationMs(context, Uri.parse(clip.uri))
                                        }
                                    }
                                    val invalidAudioSource = exportAudioTracks.firstOrNull { clip ->
                                        val sourceDuration = audioSourceDurations[clip.uri]
                                        sourceDuration == null || clip.sourceStartMs < 0L ||
                                            clip.sourceEndMs <= clip.sourceStartMs ||
                                            clip.sourceEndMs > sourceDuration
                                    }
                                    if (invalidAudioSource != null) {
                                        isExporting = false
                                        exportSessionId = null
                                        exportMessage = "Export stopped: audio source could not be verified for ${invalidAudioSource.name}. Relink it before exporting."
                                        return@exportPreparation
                                    }

                                    val canvasVisualClips = exportRequirements.mapNotNull { requirement ->
                                        val metadata = sourceMetadata[requirement.uri]
                                            ?: return@mapNotNull null
                                        val sourceWidth = metadata.videoWidthPx
                                            ?: return@mapNotNull null
                                        val sourceHeight = metadata.videoHeightPx
                                            ?: return@mapNotNull null
                                        ExportCanvasVisualClip(
                                            durationMs = requirement.sourceEndMs - requirement.sourceStartMs,
                                            sourceWidthPx = sourceWidth,
                                            sourceHeightPx = sourceHeight,
                                            sourceRotationDegrees = metadata.videoRotationDegrees,
                                        )
                                    }
                                    val expectedBackgroundForVerification = when {
                                        canvasVisualClips.size == exportRequirements.size -> exportBackgroundArgb
                                        requiresCustomLetterboxBackground -> error(
                                            "Source dimensions are unavailable for canvas background verification",
                                        )
                                        else -> null
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
                                    val sourceUris = exportClips.map { it.uri } + exportAudioTracks.map { it.uri }
                                    val expectedDurationMs = calculateTimelineDurationMs(exportClips.map { it.durationMs })
                                    val exportAudioSegments = AudioTrackType.values().associateWith { type ->
                                        buildAudioTrackTimelineSegments(exportAudioTracks, type, expectedDurationMs)
                                    }
                                    val maximumExportSilenceMs = exportAudioSegments.values.flatten()
                                        .filter { it.clip == null }
                                        .maxOfOrNull { it.durationMs } ?: 0L
                                    val exportSilenceFile = if (maximumExportSilenceMs > 0L) {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                createSilentAudioWav(context.cacheDir, maximumExportSilenceMs)
                                            }
                                        }.getOrElse { error ->
                                            isExporting = false
                                            exportSessionId = null
                                            exportMessage = "Could not prepare audio-track gaps: ${error.message}"
                                            return@exportPreparation
                                        }
                                    } else {
                                        null
                                    }
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
                                            val videoEffects = mutableListOf<androidx.media3.common.Effect>()
                                            videoEffects += createClipVisualEffects(clip.visualAdjustments)
                                            videoEffects += Presentation.createForWidthAndHeight(
                                                exportCanvasSize.widthPx,
                                                exportCanvasSize.heightPx,
                                                Presentation.LAYOUT_SCALE_TO_FIT,
                                            )
                                            if (requiresCustomLetterboxBackground) {
                                                val metadata = sourceMetadata[clip.uri]
                                                    ?: error("Could not read source dimensions for ${clip.name}")
                                                createLetterboxBackgroundEffect(
                                                    canvasWidthPx = exportCanvasSize.widthPx,
                                                    canvasHeightPx = exportCanvasSize.heightPx,
                                                    sourceWidthPx = metadata.videoWidthPx
                                                        ?: error("Could not read source width for ${clip.name}"),
                                                    sourceHeightPx = metadata.videoHeightPx
                                                        ?: error("Could not read source height for ${clip.name}"),
                                                    sourceRotationDegrees = metadata.videoRotationDegrees,
                                                    backgroundColorArgb = exportBackgroundArgb,
                                                )?.let(videoEffects::add)
                                            }
                                            videoEffects += createClipTextOverlayEffects(clip.allTextOverlays)
                                            EditedMediaItem.Builder(source)
                                                .setEffects(
                                                    Effects(
                                                        createClipAudioProcessors(
                                                            volume = clip.audioVolume,
                                                            clipDurationMs = clip.durationMs,
                                                            fadeInMs = clip.fadeInMs,
                                                            fadeOutMs = clip.fadeOutMs,
                                                        ),
                                                        videoEffects,
                                                    ),
                                                )
                                                .build()
                                        }
                                        // Media3 Composition mixes concurrent sequences. The main
                                        // sequence carries video plus source audio; each independent
                                        // lane contributes an audio-only, gap-filled sequence.
                                        @Suppress("DEPRECATION")
                                        val sequence = EditedMediaItemSequence.Builder(editedItems).build()
                                        val audioSequences = AudioTrackType.values().mapNotNull { type ->
                                            val segments = exportAudioSegments[type].orEmpty()
                                            if (segments.none { it.clip != null }) {
                                                null
                                            } else {
                                                val audioItems = segments.map { segment ->
                                                    val trackClip = segment.clip
                                                    val audioSource = if (trackClip != null) {
                                                        MediaItem.Builder()
                                                            .setUri(Uri.parse(trackClip.uri))
                                                            .setClippingConfiguration(
                                                                MediaItem.ClippingConfiguration.Builder()
                                                                    .setStartPositionMs(trackClip.sourceStartMs)
                                                                    .setEndPositionMs(trackClip.sourceEndMs)
                                                                    .build(),
                                                            )
                                                            .build()
                                                    } else {
                                                        MediaItem.Builder()
                                                            .setUri(Uri.fromFile(exportSilenceFile ?: error("Missing audio silence file")))
                                                            .setClippingConfiguration(
                                                                MediaItem.ClippingConfiguration.Builder()
                                                                    .setStartPositionMs(0L)
                                                                    .setEndPositionMs(segment.durationMs)
                                                                    .build(),
                                                            )
                                                            .build()
                                                    }
                                                    val processors = trackClip?.let {
                                                        createClipAudioProcessors(
                                                            volume = it.volume,
                                                            clipDurationMs = it.durationMs,
                                                            fadeInMs = it.fadeInMs,
                                                            fadeOutMs = it.fadeOutMs,
                                                        )
                                                    } ?: emptyList()
                                                    EditedMediaItem.Builder(audioSource)
                                                        .setRemoveVideo(true)
                                                        .setEffects(Effects(processors, emptyList()))
                                                        .build()
                                                }
                                                // Media3 1.5.1 has no withAudioFrom factory. The
                                                // source is audio-only and video is explicitly removed.
                                                @Suppress("DEPRECATION")
                                                EditedMediaItemSequence.Builder(audioItems).build()
                                            }
                                        }
                                        Composition.Builder(sequence, *audioSequences.toTypedArray()).build()
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
                                                                    clipDurationsMs = exportRequirements.map { it.sourceEndMs - it.sourceStartMs },
                                                                    expectedCanvasSize = exportCanvasSize,
                                                                    canvasVisualClips = canvasVisualClips,
                                                                    expectedBackgroundArgb = expectedBackgroundForVerification,
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
                                                            exportMessage = "Export complete • Video frames verified; " +
                                                                (if (expectedBackgroundForVerification != null) {
                                                                    "canvas background pixels checked where bars exist; "
                                                                } else {
                                                                    ""
                                                                }) +
                                                                "source audio decoded when present • Saved to Movies/VyroCut"
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
                                            audioTrackClips = trimAudioTrackClipsToProjectDuration(
                                                audioTrackClips,
                                                calculateTimelineDurationMs(restored.map { it.durationMs }),
                                            )
                                            selectedAudioTrackId = selectedAudioTrackId?.takeIf { id -> audioTrackClips.any { it.id == id } }
                                            selectedIndex = 0
                                            timelinePositionMs = 0f
                                            persistProjectAudioTracks(
                                                preferences,
                                                snapshot.projectId,
                                                audioTrackClips,
                                                calculateTimelineDurationMs(restored.map { it.durationMs }),
                                            )
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
                val trimTargetClip = clips.firstOrNull { it.id == trimTargetClipId }
                if (showTrimDialog && trimTargetClip != null) {
                    VyroCutTrimDialog(
                        clipId = trimTargetClip.id,
                        clipName = trimTargetClip.name,
                        sourceDurationMs = trimSourceDurationMs,
                        initialRange = ClipRange(trimTargetClip.sourceStartMs, trimTargetClip.sourceEndMs),
                        isLoadingSource = isLoadingTrimSource,
                        sourceReadFailed = trimSourceReadFailed,
                        onDismiss = {
                            showTrimDialog = false
                            trimTargetClipId = null
                            isLoadingTrimSource = false
                        },
                        onApply = { range ->
                            val index = clips.indexOfFirst { it.id == trimTargetClip.id }
                            if (index >= 0) {
                                val rebasedTextOverlays = rebaseClipTextOverlaysForTrim(
                                    overlays = trimTargetClip.allTextOverlays,
                                    oldSourceStartMs = trimTargetClip.sourceStartMs,
                                    oldSourceEndMs = trimTargetClip.sourceEndMs,
                                    newSourceStartMs = range.startMs,
                                    newSourceEndMs = range.endMs,
                                )
                                changed(clips.toMutableList().apply {
                                    this[index] = trimTargetClip.copy(
                                        sourceStartMs = range.startMs,
                                        sourceEndMs = range.endMs,
                                        textOverlay = rebasedTextOverlays.firstOrNull(),
                                        additionalTextOverlays = rebasedTextOverlays.drop(1),
                                    )
                                })
                                selectedIndex = index
                            }
                            showTrimDialog = false
                            trimTargetClipId = null
                            isLoadingTrimSource = false
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

internal fun encodeProject(clips: List<EditClip>): String {
    val array = JSONArray()
    clips.forEach { clip -> array.put(JSONObject().apply {
        put("id", clip.id); put("uri", clip.uri); put("name", clip.name)
        put("start", clip.sourceStartMs); put("end", clip.sourceEndMs)
        put("volume", clip.audioVolume.toDouble())
        put("fadeInMs", clip.fadeInMs)
        put("fadeOutMs", clip.fadeOutMs)
        put("visualAdjustments", JSONObject().apply {
            put("brightness", clip.visualAdjustments.brightness)
            put("contrast", clip.visualAdjustments.contrast)
            put("saturation", clip.visualAdjustments.saturation)
        })
        put("textOverlays", JSONArray().apply {
            clip.allTextOverlays.forEach { overlay ->
                put(JSONObject().apply {
                    put("text", overlay.text)
                    put("fontScale", overlay.fontScale.toDouble())
                    put("position", overlay.position.name)
                    put("startMs", overlay.startMs)
                    overlay.endMs?.let { put("endMs", it) }
                })
            }
        })
    }) }
    return array.toString()
}

internal data class ProjectBackup(
    val name: String,
    val clips: List<EditClip>,
    val canvasSettings: ProjectCanvasSettings,
    val audioTracks: List<AudioTrackClip> = emptyList(),
)

internal fun encodeProjectBackup(
    name: String,
    clips: List<EditClip>,
    canvasSettings: ProjectCanvasSettings,
    audioTracks: List<AudioTrackClip> = emptyList(),
): String =
    JSONObject().apply {
        put("format", "vyrocut-project")
        put("version", 11)
        put("projectName", name)
        put("createdAt", System.currentTimeMillis())
        put("clips", JSONArray(encodeProject(clips)))
        put("canvasSettings", encodeProjectCanvasSettings(canvasSettings))
        put("audioTracks", JSONArray(encodeAudioTrackClips(audioTracks)))
    }.toString(2)

internal fun decodeProjectBackup(raw: String): ProjectBackup {
    val root = JSONObject(raw)
    require(root.getString("format") == "vyrocut-project") { "This is not a VyroCut project backup" }
    val version = root.getInt("version")
    require(version in 1..11) { "Unsupported backup version" }
    val name = root.optString("projectName", "Restored project").ifBlank { "Restored project" }
    val clipsJson = root.getJSONArray("clips")
    val clips = decodeProject(clipsJson.toString())
    require(clips.size == clipsJson.length()) { "The backup contains invalid clip entries" }
    val canvasSettings = decodeProjectCanvasSettings(root.optString("canvasSettings", ""))
        ?: ProjectCanvasSettings()
    val audioTracksRaw = if (root.has("audioTracks")) {
        root.optJSONArray("audioTracks")?.toString()
            ?: error("The backup contains an invalid audio track list")
    } else {
        null
    }
    val audioTracks = decodeAudioTrackClips(
        audioTracksRaw,
        calculateTimelineDurationMs(clips.map { it.durationMs }),
    ) ?: error("The backup contains invalid audio track clips")
    return ProjectBackup(name, clips, canvasSettings, audioTracks)
}

internal fun encodeProjectEditSnapshot(snapshot: ProjectEditSnapshot): String =
    JSONObject().apply {
        put("format", "vyrocut-edit-history")
        put("version", 1)
        put("clips", JSONArray(encodeProject(snapshot.clips)))
        snapshot.audioTracks?.let { put("audioTracks", JSONArray(encodeAudioTrackClips(it))) }
    }.toString()

internal fun decodeProjectEditSnapshot(raw: String): ProjectEditSnapshot? = runCatching {
    val trimmed = raw.trim()
    if (trimmed.startsWith("[")) {
        val clips = decodeProjectStrict(trimmed) ?: error("Invalid legacy clip history")
        ProjectEditSnapshot(clips, null)
    } else {
        val root = JSONObject(trimmed)
        require(root.getString("format") == "vyrocut-edit-history")
        require(root.getInt("version") == 1)
        val clipsJson = root.getJSONArray("clips")
        val clips = decodeProjectStrict(clipsJson.toString()) ?: error("Invalid clip history")
        val projectDurationMs = calculateTimelineDurationMs(clips.map { it.durationMs })
        val audioTracks = if (root.has("audioTracks")) {
            val audioJson = root.optJSONArray("audioTracks")
                ?: error("Invalid audio history list")
            decodeAudioTrackClips(audioJson.toString(), projectDurationMs)
                ?: error("Invalid audio history entries")
        } else {
            null
        }
        ProjectEditSnapshot(clips, audioTracks)
    }
}.getOrNull()

private fun saveProjectHistory(
    preferences: android.content.SharedPreferences,
    projectId: String,
    undo: List<ProjectEditSnapshot>,
    redo: List<ProjectEditSnapshot>
) {
    fun encodeHistory(history: List<ProjectEditSnapshot>): String {
        val array = JSONArray()
        history.takeLast(50).forEach { snapshot -> array.put(encodeProjectEditSnapshot(snapshot)) }
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
): List<ProjectEditSnapshot> = runCatching {
    val raw = preferences.getString("project_${projectId}_${kind}", null) ?: return emptyList()
    val array = JSONArray(raw)
    val snapshots = (0 until array.length()).map { index -> array.getString(index) }
    decodeValidHistorySnapshots(snapshots, ::decodeProjectEditSnapshot)
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

internal fun decodeProjectStrict(raw: String): List<EditClip>? = runCatching {
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
            val audioVolume = decodeClipAudioVolume(
                if (item.has("volume")) item.optDouble("volume", Double.NaN) else null,
            ) ?: error("A clip has an invalid audio volume")
            val fadeInMs = if (item.has("fadeInMs")) item.optLong("fadeInMs", Long.MIN_VALUE) else 0L
            val fadeOutMs = if (item.has("fadeOutMs")) item.optLong("fadeOutMs", Long.MIN_VALUE) else 0L
            require(isValidClipAudioFades(fadeInMs, fadeOutMs)) { "A clip has invalid fade durations" }
            val visualJson = item.optJSONObject("visualAdjustments")
            val visualAdjustments = decodeClipVisualAdjustments(
                brightness = if (visualJson?.has("brightness") == true) visualJson.optInt("brightness", Int.MIN_VALUE) else 0,
                contrast = if (visualJson?.has("contrast") == true) visualJson.optInt("contrast", Int.MIN_VALUE) else 0,
                saturation = if (visualJson?.has("saturation") == true) visualJson.optInt("saturation", Int.MIN_VALUE) else 0,
            ) ?: error("A clip has invalid visual adjustments")
            fun decodeOverlay(overlayJson: JSONObject): ClipTextOverlay =
                decodeClipTextOverlay(
                    raw = overlayJson.optString("text"),
                    fontScale = overlayJson.optDouble("fontScale", 1.0).toFloat(),
                    position = overlayJson.optString("position", "BOTTOM"),
                    startMs = overlayJson.optLong("startMs", 0L),
                    endMs = if (overlayJson.has("endMs") && !overlayJson.isNull("endMs")) {
                        overlayJson.optLong("endMs")
                    } else {
                        null
                    },
                ) ?: error("A clip has an invalid text overlay")

            val textOverlays = if (item.has("textOverlays")) {
                val overlaysJson = item.optJSONArray("textOverlays")
                    ?: error("A clip has an invalid text overlay list")
                require(overlaysJson.length() <= MAX_CLIP_TEXT_OVERLAYS) {
                    "A clip contains too many text layers"
                }
                buildList {
                    for (overlayIndex in 0 until overlaysJson.length()) {
                        val overlayJson = overlaysJson.optJSONObject(overlayIndex)
                            ?: error("A clip has an invalid text overlay")
                        add(decodeOverlay(overlayJson))
                    }
                }
            } else if (!item.has("textOverlay") || item.isNull("textOverlay")) {
                emptyList()
            } else {
                val overlayJson = item.optJSONObject("textOverlay")
                    ?: error("A clip has an invalid text overlay")
                listOf(decodeOverlay(overlayJson))
            }
            add(
                EditClip(
                    id = id,
                    uri = uri,
                    name = item.optString("name", "Imported video"),
                    sourceStartMs = start,
                    sourceEndMs = end,
                    audioVolume = audioVolume,
                    fadeInMs = fadeInMs,
                    fadeOutMs = fadeOutMs,
                    visualAdjustments = visualAdjustments,
                    textOverlay = textOverlays.firstOrNull(),
                    additionalTextOverlays = textOverlays.drop(1),
                ),
            )
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

private fun persistProjectAudioTracks(
    preferences: android.content.SharedPreferences,
    projectId: String,
    clips: List<AudioTrackClip>,
    projectDurationMs: Long,
) {
    require(validateAudioTrackClips(clips, projectDurationMs)) {
        "Audio track clips must fit inside the project and cannot overlap on one lane"
    }
    val encoded = encodeAudioTrackClips(clips)
    preferences.edit()
        .putString("project_${projectId}_audio_tracks", encoded)
        .putString("project_${projectId}_audio_tracks_recovery", encoded)
        .apply()
}

private fun loadProjectAudioTracks(
    preferences: android.content.SharedPreferences,
    projectId: String,
    projectDurationMs: Long,
): List<AudioTrackClip> {
    val primaryRaw = preferences.getString("project_${projectId}_audio_tracks", null)
    val primary = primaryRaw?.let { decodeAudioTrackClips(it, projectDurationMs) }
    if (primary != null) return primary
    val recoveryRaw = preferences.getString("project_${projectId}_audio_tracks_recovery", null)
    return recoveryRaw?.let { decodeAudioTrackClips(it, projectDurationMs) } ?: emptyList()
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

private fun readAudioDurationMs(context: android.content.Context, uri: Uri): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * Writes a small 8 kHz, mono, 16-bit PCM WAV silence source. It is shared by
 * every lane and clipped to individual gaps, avoiding network assets and
 * keeping project-time offsets deterministic in ExoPlayer and Transformer.
 */
private fun createSilentAudioWav(directory: File, durationMs: Long): File {
    require(durationMs > 0L) { "Silence duration must be positive" }
    val sampleRate = 8_000
    val channels = 1
    val bitsPerSample = 16
    val dataBytes = (durationMs.toDouble() * sampleRate * channels * bitsPerSample / 8.0 / 1_000.0)
        .toLong()
        .coerceAtLeast(2L)
    require(dataBytes <= Int.MAX_VALUE.toLong() - 36L) { "Audio gap is too long for a WAV silence source" }
    if (!directory.exists() && !directory.mkdirs()) error("Could not create the audio cache directory")
    val file = File(directory, "vyrocut-silence-${dataBytes}.wav")
    if (file.isFile && file.length() == dataBytes + 44L) return file

    RandomAccessFile(file, "rw").use { output ->
        output.setLength(dataBytes + 44L)
        fun writeAscii(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
        fun writeShortLe(value: Int) {
            output.write(value and 0xff)
            output.write((value shr 8) and 0xff)
        }
        fun writeIntLe(value: Int) {
            output.write(value and 0xff)
            output.write((value shr 8) and 0xff)
            output.write((value shr 16) and 0xff)
            output.write((value shr 24) and 0xff)
        }
        writeAscii("RIFF")
        writeIntLe((dataBytes + 36L).toInt())
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeIntLe(16)
        writeShortLe(1)
        writeShortLe(channels)
        writeIntLe(sampleRate)
        writeIntLe(sampleRate * channels * bitsPerSample / 8)
        writeShortLe(channels * bitsPerSample / 8)
        writeShortLe(bitsPerSample)
        writeAscii("data")
        writeIntLe(dataBytes.toInt())
        output.seek(44L)
        val silence = ByteArray(64 * 1024)
        var remaining = dataBytes
        while (remaining > 0L) {
            val count = minOf(remaining, silence.size.toLong()).toInt()
            output.write(silence, 0, count)
            remaining -= count.toLong()
        }
    }
    return file
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

private fun canvasAspectRatioForPreset(preset: String): Float = when (preset) {
    "16:9" -> 16f / 9f
    "1:1" -> 1f
    else -> 9f / 16f
}

private fun canvasBackgroundForPreset(preset: String): Color = when (preset) {
    "DARK" -> Color(0xFF202322)
    "WHITE" -> Color.White
    else -> Color.Black
}

private fun projectCanvasSettingsFor(aspectRatio: Float, background: Color): ProjectCanvasSettings {
    val ratioPreset = when {
        kotlin.math.abs(aspectRatio - (16f / 9f)) < 0.001f -> "16:9"
        kotlin.math.abs(aspectRatio - 1f) < 0.001f -> "1:1"
        else -> "9:16"
    }
    val backgroundPreset = when (background) {
        Color.White -> "WHITE"
        Color(0xFF202322) -> "DARK"
        else -> "BLACK"
    }
    return ProjectCanvasSettings(ratioPreset, backgroundPreset)
}


@Composable
private fun TimelineEditorStrip(
    clips: List<EditClip>,
    audioTracks: List<AudioTrackClip>,
    selectedIndex: Int,
    playheadPositionMs: Long,
    isPlaying: Boolean,
    onSelectAudioTrack: (AudioTrackClip) -> Unit,
    onSeek: (TimelineSeekTarget) -> Unit,
    onSelectText: (Int, Int) -> Unit,
) {
    // A single horizontal scroll viewport keeps every track and ruler at the
    // same origin and offset. Multiple scroll containers sharing one state
    // can fight over viewport measurements on smaller devices.
    val timelineScroll = rememberScrollState()
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val pixelsPerSecond = 24f
    val gap = 6f
    val clipWidths = clips.map { (it.durationMs / 1000f * pixelsPerSecond).coerceIn(120f, 320f) }
    val totalWidthPx = (clipWidths.sum() + (clips.size - 1).coerceAtLeast(0) * gap).coerceAtLeast(1f)
    val totalWidth = totalWidthPx.dp
    val timelineClipDurationsMs = clips.map { it.durationMs }
    val clipWidthsPx = with(density) { clipWidths.map { it.dp.toPx() } }
    val gapPx = with(density) { gap.dp.toPx() }
    val totalDuration = calculateTimelineDurationMs(timelineClipDurationsMs).coerceAtLeast(1L)
    val markerX = calculateTimelinePlayheadOffsetPx(
        clipDurationsMs = timelineClipDurationsMs,
        clipWidthsPx = clipWidths,
        playheadPositionMs = playheadPositionMs,
        gapPx = gap,
    )

    val markerPositionPx = with(density) { markerX.dp.toPx() }
    LaunchedEffect(isPlaying, markerPositionPx, viewportWidthPx, timelineScroll.maxValue) {
        val targetScrollPx = calculateTimelineAutoFollowScrollTargetPx(
            currentScrollPx = timelineScroll.value,
            markerPositionPx = markerPositionPx,
            viewportWidthPx = viewportWidthPx,
            maxScrollPx = timelineScroll.maxValue,
            isPlaying = isPlaying,
        )
        if (targetScrollPx != null) {
            // Jump only when the playhead leaves the safe viewport band. This
            // avoids a continuous scroll animation fighting the 200 ms player
            // position updates while keeping every timeline track in sync.
            timelineScroll.scrollTo(targetScrollPx)
        }
    }

    if (clips.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("VIDEO", color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("Import footage to build your timeline", color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
        }
        return
    }

    Column(
        Modifier.fillMaxWidth()
            .onSizeChanged { viewportWidthPx = it.width }
            .horizontalScroll(timelineScroll),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.width(totalWidth), verticalAlignment = Alignment.CenterVertically) {
            Text("VIDEO", color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("Main track", color = StudioMuted, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text("Tap to seek • hold to scrub", color = StudioMuted, fontSize = 9.sp)
        }

        // Ruler, video and audio are children of the same scrolling viewport.
        Box(Modifier.width(totalWidth).height(22.dp)) {
            Row(Modifier.fillMaxSize()) {
                var elapsed = 0L
                clips.forEachIndexed { index, clip ->
                    val seconds = (clip.durationMs / 1000L).toInt().coerceAtLeast(1)
                    Box(Modifier.width(clipWidths[index].dp).fillMaxHeight()) {
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

        Box(Modifier.width(totalWidth).height(60.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                clips.forEachIndexed { index, clip ->
                    Column(
                        Modifier.width(clipWidths[index].dp)
                            .background(if (index == selectedIndex) Color(0xFF16483D) else Color(0xFF252A28), RoundedCornerShape(5.dp))
                            .padding(3.dp)
                    ) {
                        Box(Modifier.fillMaxWidth().height(38.dp)) {
                            ClipThumbnail(clip, Modifier.fillMaxSize())
                            Text(
                                "V" + (index + 1), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            Text(
                                formatTime(clip.durationMs), color = Color.White, fontSize = 8.sp,
                                modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Text(clip.name, color = if (index == selectedIndex) StudioAccent else StudioText, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.offset(x = markerX.dp).width(2.dp).height(60.dp).background(StudioAccent))
            Box(Modifier.offset(x = (markerX - 5f).dp).width(12.dp).height(7.dp).background(StudioAccent, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
            // The overlay makes the entire video strip a direct-manipulation
            // scrub surface. Dragging and tapping use the same pixel-to-time map.
            val latestOnSeek by rememberUpdatedState(onSeek)
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(timelineClipDurationsMs, clipWidthsPx, gapPx, timelineScroll) {
                        detectTapGestures { position ->
                            resolveTimelineSeekTargetAtOffsetPx(
                                timelineClipDurationsMs,
                                clipWidthsPx,
                                timelineScroll.value + position.x,
                                gapPx,
                            )?.let(latestOnSeek)
                        }
                    }
                    .pointerInput(timelineClipDurationsMs, clipWidthsPx, gapPx, timelineScroll) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                resolveTimelineSeekTargetAtOffsetPx(
                                    timelineClipDurationsMs,
                                    clipWidthsPx,
                                    timelineScroll.value + position.x,
                                    gapPx,
                                )?.let(latestOnSeek)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                resolveTimelineSeekTargetAtOffsetPx(
                                    timelineClipDurationsMs,
                                    clipWidthsPx,
                                    timelineScroll.value + change.position.x,
                                    gapPx,
                                )?.let(latestOnSeek)
                            },
                        )
                    }
            )
        }

        val textLayerCount = maxOf(
            1,
            clips.maxOfOrNull { it.allTextOverlays.size } ?: 0,
        ).coerceAtMost(MAX_CLIP_TEXT_OVERLAYS)
        val textTrackHeight = (textLayerCount * 24).dp
        Row(Modifier.width(totalWidth), verticalAlignment = Alignment.CenterVertically) {
            Text("TEXT", color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("Timed layers • tap a row to edit its in/out points", color = StudioMuted, fontSize = 9.sp)
        }
        Box(Modifier.width(totalWidth).height(textTrackHeight)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                clips.forEachIndexed { index, clip ->
                    Column(Modifier.width(clipWidths[index].dp)) {
                        repeat(textLayerCount) { layerIndex ->
                            val overlay = clip.allTextOverlays.getOrNull(layerIndex)
                            Box(
                                Modifier.fillMaxWidth()
                                    .height(24.dp)
                                    .background(Color(0xFF202725), RoundedCornerShape(4.dp))
                                    .clickable {
                                        if (overlay != null || layerIndex == clip.allTextOverlays.size) {
                                            onSelectText(index, layerIndex)
                                        }
                                    },
                            ) {
                                if (overlay == null) {
                                    if (layerIndex == clip.allTextOverlays.size) {
                                        Text(
                                            "+ Add layer",
                                            color = StudioMuted,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 6.dp),
                                        )
                                    }
                                } else {
                                    val bounds = calculateClipTextOverlayTimelineBounds(
                                        trackWidthPx = clipWidths[index],
                                        clipDurationMs = clip.durationMs,
                                        overlay = overlay,
                                    )
                                    bounds?.let { segment ->
                                        Box(
                                            Modifier.offset(x = segment.startOffsetPx.dp)
                                                .width(segment.widthPx.dp)
                                                .fillMaxHeight()
                                                .background(Color(0xFF16483D), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Text(
                                                overlay.text.replace('\n', ' '),
                                                color = StudioAccent,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Box(
                Modifier.offset(x = markerX.dp)
                    .width(2.dp)
                    .height(textTrackHeight)
                    .background(StudioAccent),
            )
        }

        Row(Modifier.width(totalWidth), verticalAlignment = Alignment.CenterVertically) {
            Text("SOURCE", color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("Original clip audio", color = StudioMuted, fontSize = 9.sp)
        }
        Row(Modifier.width(totalWidth), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
            clips.forEachIndexed { index, clip ->
                Box(
                    Modifier.width(clipWidths[index].dp).height(26.dp)
                        .background(if (index == selectedIndex) Color(0xFF1B4239) else Color(0xFF172C28), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AudioWaveform(clip, Modifier.fillMaxWidth().height(20.dp))
                }
            }
        }
        AudioTrackType.values().forEach { type ->
            val laneClips = audioTracks.filter { it.type == type }
            Row(Modifier.width(totalWidth), verticalAlignment = Alignment.CenterVertically) {
                Text(type.name, color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (type) {
                        AudioTrackType.MUSIC -> "Music"
                        AudioTrackType.SFX -> "Sound effects"
                        AudioTrackType.VOICEOVER -> "Voiceover"
                    },
                    color = StudioMuted,
                    fontSize = 9.sp,
                )
            }
            Box(Modifier.width(totalWidth).height(25.dp)) {
                laneClips.forEach { trackClip ->
                    val startPx = calculateTimelinePlayheadOffsetPx(
                        timelineClipDurationsMs,
                        clipWidths,
                        trackClip.startMs,
                        gap,
                    )
                    val endPx = calculateTimelinePlayheadOffsetPx(
                        timelineClipDurationsMs,
                        clipWidths,
                        trackClip.endMs,
                        gap,
                    )
                    val laneColor = when (type) {
                        AudioTrackType.MUSIC -> Color(0xFF185C48)
                        AudioTrackType.SFX -> Color(0xFF57452A)
                        AudioTrackType.VOICEOVER -> Color(0xFF344A67)
                    }
                    Box(
                        Modifier.offset(x = startPx.dp)
                            .width((endPx - startPx).coerceAtLeast(6f).dp)
                            .fillMaxHeight()
                            .background(laneColor, RoundedCornerShape(4.dp))
                            .clickable { onSelectAudioTrack(trackClip) }
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            trackClip.name,
                            color = Color.White,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(Modifier.offset(x = markerX.dp).width(2.dp).fillMaxHeight().background(StudioAccent))
            }
        }
        Row(Modifier.width(totalWidth), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", color = StudioMuted, fontSize = 9.sp)
            Text(formatTime(totalDuration), color = StudioMuted, fontSize = 9.sp)
        }
    }
}