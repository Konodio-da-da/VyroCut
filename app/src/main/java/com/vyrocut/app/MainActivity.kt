package com.vyrocut.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.os.SystemClock
import android.os.StatFs
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.security.MessageDigest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val StudioBackground = VyroCutPalette.Background
private val StudioPanel = VyroCutPalette.Surface
private val StudioAccent = VyroCutPalette.Accent
private val StudioText = VyroCutPalette.TextPrimary
private val StudioMuted = VyroCutPalette.TextMuted

private fun inspectExportStorageAvailability(
    temporaryDirectory: File,
    silenceCacheDirectory: File,
): ExportStorageAvailability {
    val temporaryAvailableBytes = runCatching {
        StatFs(temporaryDirectory.absolutePath).availableBytes
    }.getOrNull()
    val silenceCacheAvailableBytes = runCatching {
        StatFs(silenceCacheDirectory.absolutePath).availableBytes
    }.getOrNull()
    @Suppress("DEPRECATION")
    val moviesRoot = Environment.getExternalStorageDirectory()
    val moviesAvailableBytes = runCatching {
        StatFs(moviesRoot.absolutePath).availableBytes
    }.getOrNull()
    val temporarySharesMoviesVolume = runCatching {
        isPathWithinStorageRoot(temporaryDirectory.canonicalPath, moviesRoot.canonicalPath)
    }.getOrNull()
    val silenceCacheSharesMoviesVolume = runCatching {
        isPathWithinStorageRoot(silenceCacheDirectory.canonicalPath, moviesRoot.canonicalPath)
    }.getOrNull()
    // The export directory resolves only to app-specific primary external storage
    // or app-private filesDir; cacheDir is app-private, so matching this known
    // primary-external membership identifies whether the two share a volume.
    val silenceCacheSharesTemporaryVolume =
        if (temporarySharesMoviesVolume != null && silenceCacheSharesMoviesVolume != null) {
            temporarySharesMoviesVolume == silenceCacheSharesMoviesVolume
        } else {
            null
        }
    return ExportStorageAvailability(
        temporaryAvailableBytes = temporaryAvailableBytes,
        moviesAvailableBytes = moviesAvailableBytes,
        sameVolume = temporarySharesMoviesVolume,
        silenceCacheAvailableBytes = silenceCacheAvailableBytes,
        silenceCacheSharesTemporaryVolume = silenceCacheSharesTemporaryVolume,
    )
}

@OptIn(UnstableApi::class)
private fun media3SeekParametersFor(intent: PreviewSeekIntent): SeekParameters =
    when (previewSeekPrecisionFor(intent)) {
        PreviewSeekPrecision.EXACT -> SeekParameters.EXACT
        PreviewSeekPrecision.CLOSEST_SYNC -> SeekParameters.CLOSEST_SYNC
    }

private fun previewFrameRateCandidates(tracks: Tracks): List<PreviewTrackFrameRateCandidate> =
    tracks.groups.flatMap { group ->
        (0 until group.length).map { trackIndex ->
            PreviewTrackFrameRateCandidate(
                isVideo = group.type == C.TRACK_TYPE_VIDEO,
                isSelected = group.isTrackSelected(trackIndex),
                frameRate = group.getTrackFormat(trackIndex).frameRate,
            )
        }
    }

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
    val stickerOverlays: List<ClipStickerOverlay> = emptyList(),
    val timedEffectSegments: List<ClipTimedEffectSegment> = emptyList(),
) {
    init {
        require(isValidClipAudioVolume(audioVolume)) { "Clip volume must be between 0 and 1" }
        require(isValidClipAudioFades(fadeInMs, fadeOutMs)) { "Clip fade durations cannot be negative" }
        require((if (textOverlay == null) 0 else 1) + additionalTextOverlays.size <= MAX_CLIP_TEXT_OVERLAYS) {
            "A clip cannot contain more than $MAX_CLIP_TEXT_OVERLAYS text layers"
        }
        require(stickerOverlays.size <= MAX_CLIP_STICKER_OVERLAYS) {
            "A clip cannot contain more than $MAX_CLIP_STICKER_OVERLAYS sticker overlays"
        }
        require(
            validateClipTimedEffectSegments(
                timedEffectSegments,
                (sourceEndMs - sourceStartMs).coerceAtLeast(0L),
            ),
        ) { "A clip has invalid timed effect segments" }
    }
    val allTextOverlays: List<ClipTextOverlay>
        get() = listOfNotNull(textOverlay) + additionalTextOverlays
    val allStickerOverlays: List<ClipStickerOverlay>
        get() = stickerOverlays
    val durationMs: Long get() = (sourceEndMs - sourceStartMs).coerceAtLeast(0L)
}

private val startupExportCleanupStarted = AtomicBoolean(false)
private val videoFrameTimestampRequestGate = KeyedSuspendRequestGate()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (startupExportCleanupStarted.compareAndSet(false, true)) {
            val cleanupJob = lifecycleScope.launch(Dispatchers.IO) {
                val directory = resolveExportTemporaryDirectory(
                    externalMoviesDirectory = getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    internalFilesDirectory = filesDir,
                )
                cleanupStaleExportTemporaryFiles(directory)
            }
            // A cancelled/failed Activity-scoped attempt may be retried by the next Activity.
            cleanupJob.invokeOnCompletion { cause ->
                if (cause != null) startupExportCleanupStarted.set(false)
            }
        }
        setContent { VyroCutApp() }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VyroCutApp() {
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    val previewScope = rememberCoroutineScope()
    val recoveryDirectory = remember { File(context.filesDir, "project-recovery") }
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember { context.getSharedPreferences("vyrocut_project", 0) }
    val projects = remember { mutableStateListOf<ProjectInfo>().apply { addAll(loadProjectIndex(preferences)) } }
    var activeProjectId by remember {
        mutableStateOf(preferences.getString("active_project_id", projects.first().id) ?: projects.first().id)
    }
    var clips by remember { mutableStateOf(loadProject(preferences, activeProjectId)) }
    var audioTrackClips by remember(activeProjectId) {
        mutableStateOf(
            loadPrimaryProjectAudioTracks(
                preferences,
                activeProjectId,
                calculateTimelineDurationMs(clips.map { it.durationMs }),
            ),
        )
    }
    var selectedAudioTrackType by remember { mutableStateOf(AudioTrackType.MUSIC) }
    var selectedAudioTrackId by remember { mutableStateOf<String?>(null) }
    var rippleAudioTrimEnabled by remember(activeProjectId) { mutableStateOf(false) }
    var audioWaveforms by remember(activeProjectId) { mutableStateOf<Map<String, List<Float>>>(emptyMap()) }
    var audioWaveformSourceRevisions by remember(activeProjectId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var audioWaveformLoadStates by remember(activeProjectId) {
        mutableStateOf<Map<String, AudioWaveformLoadState>>(emptyMap())
    }
    var showHome by remember { mutableStateOf(true) }
    var activeTool by remember { mutableStateOf("Edit") }
    var selectedAdjustment by remember { mutableStateOf("Brightness") }
    var selectedStickerPosition by remember { mutableStateOf(ClipStickerPosition.BOTTOM_RIGHT) }
    var selectedStickerLayerIndex by remember(activeProjectId) { mutableIntStateOf(-1) }
    var selectedTimedEffectSelection by remember(activeProjectId) { mutableStateOf<Pair<String, String>?>(null) }
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
    var exportResolutionPreset by remember(activeProjectId) {
        mutableStateOf(
            ExportResolutionPreset.fromId(
                preferences.getString("project_${activeProjectId}_export_resolution", null),
            ),
        )
    }
    var exportFrameRatePreset by remember(activeProjectId) {
        mutableStateOf(
            ExportFrameRatePreset.fromId(
                preferences.getString("project_${activeProjectId}_export_frame_rate", null),
            ),
        )
    }
    var exportVideoBitratePreset by remember(activeProjectId) {
        mutableStateOf(
            ExportVideoBitratePreset.fromId(
                preferences.getString("project_${activeProjectId}_export_video_bitrate", null),
            ),
        )
    }
    var exportAudioBitratePreset by remember(activeProjectId) {
        mutableStateOf(
            ExportAudioBitratePreset.fromId(
                preferences.getString("project_${activeProjectId}_export_audio_bitrate", null),
            ),
        )
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
        mutableStateOf<ProjectRecoverySnapshot?>(null)
    }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var previewFrameRate by remember { mutableStateOf<Float?>(null) }
    var previewFrameRateUri by remember { mutableStateOf<String?>(null) }
    var previewFrameRatesByUri by remember { mutableStateOf<Map<String, Float?>>(emptyMap()) }
    var previewFrameTimestampIndexes by remember(activeProjectId) {
        mutableStateOf<Map<VideoFrameTimestampCacheKey, LongArray?>>(emptyMap())
    }
    var isResolvingFrameStep by remember(activeProjectId) { mutableStateOf(false) }
    // The playback sampler is a long-lived coroutine; keep its cache view current
    // without restarting the loop (and resetting its checkpoint throttle) on each fill.
    val latestPreviewFrameTimestampIndexes by rememberUpdatedState(previewFrameTimestampIndexes)

    // App-private recovery mirrors can be up to 16 MiB. Never read or checksum
    // them during composition or a click callback; load both copies on IO and
    // publish only if the same project/edit state is still active afterwards.
    LaunchedEffect(activeProjectId) {
        val projectId = activeProjectId
        val clipsAtStart = clips
        val audioAtStart = audioTrackClips
        val projectDurationAtStart = calculateTimelineDurationMs(clipsAtStart.map { it.durationMs })
        val (candidate, recoveredAudioTracks) = withContext(Dispatchers.IO) {
            val mirroredProjectJournal = ProjectRecoveryFileStore.read(
                recoveryDirectory, projectId, ProjectRecoveryFileKind.PROJECT,
            )
            val mirroredAudioJournal = ProjectRecoveryFileStore.read(
                recoveryDirectory, projectId, ProjectRecoveryFileKind.AUDIO,
            )
            // Journal checksums, Base64 decoding, and audio JSON validation can
            // be proportional to the recovery payload size; keep all of it off Main.
            findProjectRecoveryCandidate(
                preferences, projectId, mirroredProjectJournal,
            ) to loadProjectAudioTracks(
                preferences, projectId, projectDurationAtStart, mirroredAudioJournal,
            )
        }
        if (activeProjectId != projectId) return@LaunchedEffect

        recoveryCandidate = candidate
        if (clips == clipsAtStart && audioTrackClips == audioAtStart) {
            audioTrackClips = recoveredAudioTracks
        }
    }

    /**
     * Shares one source/trim/revision scan between continuous prefetch and
     * manual frame stepping. The cache is re-read and updated inside the gate,
     * using the latest Compose state so concurrent scans for different clips
     * cannot overwrite one another's LRU updates from stale local snapshots.
     */
    suspend fun ensureVideoFrameTimestampIndex(
        clip: EditClip,
        key: VideoFrameTimestampCacheKey,
        isStillRelevant: () -> Boolean,
    ) {
        if (!isStillRelevant()) return
        videoFrameTimestampRequestGate.withKey(videoFrameTimestampRequestKey(key)) {
            if (!isStillRelevant()) return@withKey
            if (previewFrameTimestampIndexes.containsKey(key)) {
                previewFrameTimestampIndexes = touchVideoFrameTimestampIndex(
                    previewFrameTimestampIndexes,
                    key,
                )
            } else {
                val timestamps = withContext(Dispatchers.IO) {
                    MediaMetadataReader.videoPresentationTimestampsUs(
                        context = context,
                        uri = Uri.parse(clip.uri),
                        sourceStartMs = clip.sourceStartMs,
                        sourceEndMs = clip.sourceEndMs,
                    )
                }
                if (isStillRelevant()) {
                    previewFrameTimestampIndexes = cacheVideoFrameTimestampIndex(
                        previewFrameTimestampIndexes,
                        key,
                        timestamps,
                    )
                }
            }
        }
    }
    LaunchedEffect(clips.map { it.uri }.distinct()) {
        val sourceUris = clips.map { it.uri }.distinct()
        val retainedRates = previewFrameRatesByUri.filterKeys { it in sourceUris }
        val missingUris = sourceUris.filterNot { retainedRates.containsKey(it) }
        if (missingUris.isNotEmpty()) {
            val inspectedRates = withContext(Dispatchers.IO) {
                missingUris.associateWith { rawUri ->
                    MediaMetadataReader.videoFrameRate(context, Uri.parse(rawUri))
                }
            }
            // Preserve a more authoritative Media3 track rate if onTracksChanged
            // updated the cache while the background metadata scan was running.
            val latestTrackRates = previewFrameRatesByUri.filterKeys { it in sourceUris }
            previewFrameRatesByUri = retainedRates + inspectedRates + latestTrackRates
        } else if (retainedRates != previewFrameRatesByUri) {
            previewFrameRatesByUri = retainedRates
        }
    }
    var previewPlaybackSpeed by remember(activeProjectId) {
        mutableFloatStateOf(loadPreviewPlaybackSpeed(preferences, activeProjectId))
    }
    var isExporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var exportedUri by remember { mutableStateOf<Uri?>(null) }
    var activeTransformer by remember { mutableStateOf<Transformer?>(null) }
    var activeExportFile by remember { mutableStateOf<File?>(null) }
    var exportSessionId by remember { mutableStateOf<String?>(null) }
    var activeExportCancellationToken by remember { mutableStateOf<ExportCancellationToken?>(null) }
    var exportUiStage by remember { mutableStateOf(ExportUiStage.IDLE) }
    var exportProgressPercent by remember { mutableStateOf<Int?>(null) }
    var completedExportSummary by remember { mutableStateOf<ExportCompletionSummary?>(null) }
    var exportStartedAtElapsedRealtimeMs by remember { mutableStateOf(0L) }
    var exportElapsedMs by remember { mutableStateOf(0L) }
    LaunchedEffect(isExporting, exportStartedAtElapsedRealtimeMs) {
        if (exportStartedAtElapsedRealtimeMs <= 0L) {
            exportElapsedMs = 0L
            return@LaunchedEffect
        }
        if (isExporting) {
            while (true) {
                exportElapsedMs = (SystemClock.elapsedRealtime() - exportStartedAtElapsedRealtimeMs)
                    .coerceAtLeast(0L)
                delay(1_000L)
            }
        } else {
            exportElapsedMs = (SystemClock.elapsedRealtime() - exportStartedAtElapsedRealtimeMs)
                .coerceAtLeast(0L)
        }
    }
    var relinkTargetClipId by remember { mutableStateOf<String?>(null) }
    var missingMediaUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isReadingMedia by remember { mutableStateOf(false) }
    val selectedClip = clips.getOrNull(selectedIndex)
    val selectedTimedEffectSegment = selectedClip?.let { clip ->
        selectedTimedEffectSelection?.takeIf { it.first == clip.id }?.let { selection ->
            clip.timedEffectSegments.firstOrNull { it.id == selection.second }
        }
    }
    var timedVignetteIntensityDraft by remember(selectedClip?.id) {
        mutableFloatStateOf(70f)
    }
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
    var clipHueShiftDraft by remember(selectedClip?.id, selectedClip?.visualAdjustments?.hueShiftDegrees) {
        mutableFloatStateOf(selectedClip?.visualAdjustments?.hueShiftDegrees?.toFloat() ?: 0f)
    }
    var clipCreativeEffectIntensityDraft by remember(selectedClip?.id, selectedClip?.visualAdjustments?.creativeEffectIntensity) {
        mutableFloatStateOf(selectedClip?.visualAdjustments?.creativeEffectIntensity?.toFloat() ?: DEFAULT_CREATIVE_EFFECT_INTENSITY.toFloat())
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

    // Keep every audio lane on the same playback rate and pitch as the video clock.
    LaunchedEffect(previewPlaybackSpeed, player, audioPlayers) {
        val parameters = PlaybackParameters(previewPlaybackSpeed, 1f)
        player.playbackParameters = parameters
        audioPlayers.values.forEach { audioPlayer ->
            audioPlayer.playbackParameters = parameters
        }
    }
    var audioTrackSegments by remember {
        mutableStateOf<Map<AudioTrackType, List<AudioTrackTimelineSegment>>>(emptyMap())
    }
    var audioTrackSegmentsProjectId by remember { mutableStateOf<String?>(null) }
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
        previewFrameRate = null
        previewFrameRateUri = null
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
            player.setVideoEffects(
                createClipVisualEffects(restoredClip.visualAdjustments) +
                    createClipTimedVignetteEffects(
                        restoredClip.timedEffectSegments,
                        restoredClip.durationMs,
                        calculateTimelinePositionMs(clipDurationsMs, restoreTarget.clipIndex, 0L),
                    ) +
                    createClipTextOverlayEffects(restoredClip.allTextOverlays) +
                    createClipStickerOverlayEffects(restoredClip.allStickerOverlays, context),
            )
            player.setVolume(
                clipAudioPlaybackGain(
                    restoredClip.audioVolume,
                    restoreTarget.positionMs,
                    restoredClip.durationMs,
                    restoredClip.fadeInMs,
                    restoredClip.fadeOutMs,
                ),
            )
            player.setSeekParameters(media3SeekParametersFor(PreviewSeekIntent.PLAYLIST_REBUILD))
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
    // Warm the active and immediately following clip's source PTS indexes while
    // playback runs. The next clip is ready before a natural playlist transition,
    // so the timeline can switch to its own VFR frame boundaries without waiting
    // for a user to press the frame-step controls.
    LaunchedEffect(player, activeProjectId, isPlaying, selectedIndex, clips) {
        if (!isPlaying || clips.isEmpty()) return@LaunchedEffect
        val projectIdAtRequest = activeProjectId
        val activeClipIndex = player.currentMediaItemIndex
        val activeClip = clips.getOrNull(activeClipIndex) ?: return@LaunchedEffect
        val activeClipId = activeClip.id
        if (player.currentMediaItem?.mediaId != activeClipId) return@LaunchedEffect
        val clipsToWarm = listOfNotNull(
            activeClip,
            clips.getOrNull(activeClipIndex + 1),
        )

        val sourceRevisionsByUri = withContext(Dispatchers.IO) {
            clipsToWarm.map { it.uri }.distinct().associateWith { rawUri ->
                MediaMetadataReader.videoFrameTimestampSourceRevision(
                    context = context,
                    uri = Uri.parse(rawUri),
                )
            }
        }
        val clipsSnapshot = clips
        val isStillRelevant = {
            activeProjectId == projectIdAtRequest &&
                clips == clipsSnapshot &&
                player.currentMediaItemIndex == activeClipIndex &&
                player.currentMediaItem?.mediaId == activeClipId
        }
        val keyedClips = clipsToWarm.map { clip ->
            clip to VideoFrameTimestampCacheKey(
                uri = clip.uri,
                sourceStartMs = clip.sourceStartMs,
                sourceEndMs = clip.sourceEndMs,
                sourceRevision = sourceRevisionsByUri[clip.uri],
            )
        }.distinctBy { it.second }

        for ((clip, key) in keyedClips) {
            if (!isStillRelevant()) return@LaunchedEffect
            ensureVideoFrameTimestampIndex(clip, key, isStillRelevant)
            if (!isStillRelevant()) return@LaunchedEffect
        }
    }

    LaunchedEffect(player, clips, isPlaying, activeProjectId) {
        var lastPersistedAtMs: Long? = null
        while (true) {
            var hasPresentationTimestampIndex = false
            if (!isScrubbing && clips.isNotEmpty() && player.currentMediaItemIndex in clips.indices) {
                val currentIndex = player.currentMediaItemIndex
                val currentPositionMs = player.currentPosition
                val currentClip = clips[currentIndex]
                val timestampsUs = latestPreviewFrameTimestampIndexes.entries
                    .lastOrNull { (key, _) ->
                        key.uri == currentClip.uri &&
                            key.sourceStartMs == currentClip.sourceStartMs &&
                            key.sourceEndMs == currentClip.sourceEndMs
                    }?.value
                hasPresentationTimestampIndex = timestampsUs != null
                val displayedClipPositionMs = timestampsUs?.let { index ->
                    resolvePresentedPreviewFrameOffsetFromTimestampsMs(
                        currentPositionMs = currentPositionMs,
                        sourceStartMs = currentClip.sourceStartMs,
                        clipDurationMs = currentClip.durationMs,
                        timestampsUs = index,
                    )
                } ?: currentPositionMs
                timelinePositionMs = calculateTimelinePositionMs(
                    clipDurationsMs,
                    currentIndex,
                    displayedClipPositionMs,
                ).coerceIn(0L, timelineDurationMs).toFloat()

                val nowElapsedMs = SystemClock.elapsedRealtime()
                if (player.isPlaying && shouldPersistPreviewCheckpoint(nowElapsedMs, lastPersistedAtMs)) {
                    clips.getOrNull(currentIndex)?.let { clip ->
                        savePreviewPosition(preferences, activeProjectId, clip, currentPositionMs)
                    }
                    lastPersistedAtMs = nowElapsedMs
                }
            }
            delay(playbackProgressUpdateIntervalMs(player.isPlaying, hasPresentationTimestampIndex))
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
    // Decode waveform peaks on-device only while the Audio tool is open.
    // The disposable cache is keyed by source URI and source trim range.
    LaunchedEffect(
        activeProjectId,
        activeTool,
        selectedAudioTrackType,
        audioTrackClips.map { audioWaveformMemoryCacheKey(it) },
    ) {
        // Keep Compose's in-memory waveform maps bounded by the current project's
        // edit-decision list. Removed clips and old trim ranges can be restored
        // from the disposable disk cache if the user later undoes the edit.
        val activeWaveformKeys = audioTrackClips
            .map(::audioWaveformMemoryCacheKey)
            .toSet()
        val retainedWaveformCache = retainAudioWaveformUiCache(
            activeMemoryKeys = activeWaveformKeys,
            waveforms = audioWaveforms,
            sourceRevisions = audioWaveformSourceRevisions,
            loadStates = audioWaveformLoadStates,
        )
        if (audioWaveforms != retainedWaveformCache.waveforms) {
            audioWaveforms = retainedWaveformCache.waveforms
        }
        if (audioWaveformSourceRevisions != retainedWaveformCache.sourceRevisions) {
            audioWaveformSourceRevisions = retainedWaveformCache.sourceRevisions
        }
        if (audioWaveformLoadStates != retainedWaveformCache.loadStates) {
            audioWaveformLoadStates = retainedWaveformCache.loadStates
        }

        if (activeTool != "Audio") return@LaunchedEffect
        val selectedLaneClips = audioTrackClips.filter { it.type == selectedAudioTrackType }
        // Re-read provider metadata whenever the Audio tool/lane is opened. A
        // stable content URI can point at media replaced in place by another app.
        val sourceRevisionsByUri = withContext(Dispatchers.IO) {
            selectedLaneClips.map { it.uri }.distinct().associateWith { rawUri ->
                MediaMetadataReader.audioWaveformSourceRevision(context, Uri.parse(rawUri))
            }
        }
        var loadedWaveforms = retainedWaveformCache.waveforms
        var loadedSourceRevisions = retainedWaveformCache.sourceRevisions
        var loadedLoadStates = retainedWaveformCache.loadStates
        for (trackClip in selectedLaneClips) {
            // The in-memory/UI key is stable for this clip's URI and trim range.
            // Provider revisions belong to the disk-cache key, not the UI lookup.
            val memoryKey = audioWaveformMemoryCacheKey(trackClip)
            val sourceRevision = sourceRevisionsByUri[trackClip.uri]
            val sourceRevisionKey = audioWaveformSourceRevisionKey(sourceRevision)
            if (shouldDecodeAudioWaveform(
                    hasCachedWaveform = memoryKey in loadedWaveforms,
                    cachedSourceRevisionKey = loadedSourceRevisions[memoryKey],
                    currentSourceRevisionKey = sourceRevisionKey,
                    loadState = loadedLoadStates[memoryKey],
                )
            ) {
                // Never keep showing peaks from an older version of a URI while
                // the replacement source is being decoded.
                loadedWaveforms = loadedWaveforms - memoryKey
                loadedSourceRevisions = loadedSourceRevisions + (memoryKey to sourceRevisionKey)
                loadedLoadStates = loadedLoadStates + (memoryKey to AudioWaveformLoadState.LOADING)
                audioWaveforms = loadedWaveforms
                audioWaveformSourceRevisions = loadedSourceRevisions
                audioWaveformLoadStates = loadedLoadStates

                val waveform = withContext(Dispatchers.IO) {
                    decodeAudioWaveform(
                        context,
                        trackClip.uri,
                        trackClip.sourceStartMs,
                        trackClip.sourceEndMs,
                        sourceRevision = sourceRevision,
                    )
                }
                if (waveform != null) {
                    loadedWaveforms = loadedWaveforms + (memoryKey to waveform)
                }
                loadedLoadStates = loadedLoadStates + (
                    memoryKey to if (waveform == null) {
                        AudioWaveformLoadState.UNAVAILABLE
                    } else {
                        AudioWaveformLoadState.READY
                    }
                )
                audioWaveforms = loadedWaveforms
                audioWaveformLoadStates = loadedLoadStates
            }
        }
    }

    LaunchedEffect(audioTrackClips, timelineDurationMs, activeProjectId) {
        // Keep the previous project's lane playlists muted while this project's
        // gap-filled Media3 sequences are rebuilt.
        audioTrackSegmentsProjectId = null
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
            audioTrackSegmentsProjectId = activeProjectId
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
        audioTrackSegmentsProjectId = activeProjectId
    }

    LaunchedEffect(audioTrackSegments, audioTrackSegmentsProjectId, clips, player) {
        val lastAudioResyncAtMs = mutableMapOf<AudioTrackType, Long>()
        val lastAudioResyncSegmentIndex = mutableMapOf<AudioTrackType, Int>()
        while (true) {
            if (audioTrackSegmentsProjectId != activeProjectId) {
                audioPlayers.values.forEach { audioPlayer ->
                    audioPlayer.playWhenReady = false
                    audioPlayer.volume = 0f
                }
                delay(if (player.isPlaying) 16L else 100L)
                continue
            }
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
                    val expectedTrackTarget = resolveAudioTrackSeekTarget(segments, projectPositionMs)
                    val nowMs = SystemClock.elapsedRealtime()
                    if (shouldResyncAudioTrack(
                            projectPositionMs = projectPositionMs,
                            trackPositionMs = currentTrackPosition,
                            currentSegmentIndex = audioPlayer.currentMediaItemIndex,
                            expectedSegmentIndex = expectedTrackTarget?.segmentIndex,
                            segmentCount = segments.size,
                            lastResyncAtMs = lastAudioResyncAtMs[type],
                            nowMs = nowMs,
                            lastResyncSegmentIndex = lastAudioResyncSegmentIndex[type],
                        )
                    ) {
                        expectedTrackTarget?.let { target ->
                            audioPlayer.setSeekParameters(media3SeekParametersFor(PreviewSeekIntent.AUDIO_RESYNC))
                            audioPlayer.seekTo(target.segmentIndex, target.positionMs)
                            lastAudioResyncAtMs[type] = nowMs
                            lastAudioResyncSegmentIndex[type] = target.segmentIndex
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

    fun seekAudioPlayersToProjectPosition(projectPositionMs: Long) {
        if (audioTrackSegmentsProjectId != activeProjectId) return
        resolveAudioTrackSeekTargets(audioTrackSegments, projectPositionMs).forEach { (type, target) ->
            val audioPlayer = audioPlayers.getValue(type)
            if (target.segmentIndex in 0 until audioPlayer.mediaItemCount) {
                audioPlayer.setSeekParameters(media3SeekParametersFor(PreviewSeekIntent.USER_EDIT))
                audioPlayer.seekTo(target.segmentIndex, target.positionMs)
            }
        }
    }

    /**
     * Every editor-originated seek uses one project-clock mapping for video,
     * Music, SFX and Voiceover. The periodic drift guard remains as a fallback
     * for decoder startup and Media3 controller interactions.
     */
    fun seekPreviewToTimelinePosition(requestedPositionMs: Long) {
        val target = resolveSynchronizedPreviewSeekTarget(
            clipDurationsMs = clipDurationsMs,
            audioSegmentsByType = audioTrackSegments,
            requestedProjectPositionMs = requestedPositionMs,
        ) ?: return
        selectedIndex = target.videoTarget.clipIndex
        player.setSeekParameters(media3SeekParametersFor(PreviewSeekIntent.USER_EDIT))
        player.seekTo(target.videoTarget.clipIndex, target.videoTarget.positionMs)
        timelinePositionMs = target.projectPositionMs.coerceIn(0L, timelineDurationMs).toFloat()
        seekAudioPlayersToProjectPosition(target.projectPositionMs)
        clips.getOrNull(target.videoTarget.clipIndex)?.let { clip ->
            savePreviewPosition(preferences, activeProjectId, clip, target.videoTarget.positionMs)
        }
    }

    fun stepPreviewFrame(direction: PreviewFrameStepDirection) {
        if (isResolvingFrameStep) return
        // Stop continuous PTS prefetch before starting the user-requested exact
        // frame scan; the frame-step path owns the decoder work from this point.
        player.pause()
        val clipIndex = player.currentMediaItemIndex
        val requestedClips = clips
        val currentClip = requestedClips.getOrNull(clipIndex) ?: return
        val projectIdAtRequest = activeProjectId
        val adjacentClipIndex = clipIndex + if (direction == PreviewFrameStepDirection.PREVIOUS) -1 else 1
        val clipsToIndex = listOfNotNull(
            currentClip,
            requestedClips.getOrNull(adjacentClipIndex),
        )
        isResolvingFrameStep = true
        previewScope.launch {
            try {
                val sourceRevisionsByUri = withContext(Dispatchers.IO) {
                    clipsToIndex.map { it.uri }.distinct().associateWith { rawUri ->
                        MediaMetadataReader.videoFrameTimestampSourceRevision(
                            context = context,
                            uri = Uri.parse(rawUri),
                        )
                    }
                }
                val isStillRelevant = {
                    activeProjectId == projectIdAtRequest &&
                        clips == requestedClips &&
                        player.currentMediaItemIndex == clipIndex &&
                        player.currentMediaItem?.mediaId == currentClip.id
                }
                for (clip in clipsToIndex) {
                    val key = VideoFrameTimestampCacheKey(
                        uri = clip.uri,
                        sourceStartMs = clip.sourceStartMs,
                        sourceEndMs = clip.sourceEndMs,
                        sourceRevision = sourceRevisionsByUri[clip.uri],
                    )
                    ensureVideoFrameTimestampIndex(clip, key, isStillRelevant)
                    if (!isStillRelevant()) return@launch
                }

                // Read the latest shared cache after all gated requests complete;
                // another clip's concurrent prefetch may have updated the LRU meanwhile.
                if (!isStillRelevant()) return@launch
                val timestampCache = previewFrameTimestampIndexes

                // Do not apply a seek computed for a project/clip that changed while
                // the bounded background timestamp scan was running.
                val activeClip = clips.getOrNull(clipIndex) ?: return@launch
                if (activeProjectId != projectIdAtRequest ||
                    clips != requestedClips ||
                    player.currentMediaItemIndex != clipIndex ||
                    activeClip.id != currentClip.id ||
                    activeClip.uri != currentClip.uri ||
                    activeClip.sourceStartMs != currentClip.sourceStartMs ||
                    activeClip.sourceEndMs != currentClip.sourceEndMs
                ) return@launch

                val frameRatesByClip = clips.mapIndexed { index, clip ->
                    if (index == clipIndex) {
                        previewFrameRate?.takeIf { previewFrameRateUri == clip.uri }
                            ?: previewFrameRatesByUri[clip.uri]
                    } else {
                        previewFrameRatesByUri[clip.uri]
                            ?: previewFrameRate?.takeIf { clip.uri == currentClip.uri }
                    }
                }
                val timestampsByClip = clips.map { clip ->
                    timestampCache[VideoFrameTimestampCacheKey(
                        uri = clip.uri,
                        sourceStartMs = clip.sourceStartMs,
                        sourceEndMs = clip.sourceEndMs,
                        sourceRevision = sourceRevisionsByUri[clip.uri],
                    )]
                }
                val target = resolvePreviewFrameStepAcrossClips(
                    clipDurationsMs = clipDurationsMs,
                    frameRatesByClip = frameRatesByClip,
                    currentClipIndex = clipIndex,
                    currentPositionMs = player.currentPosition,
                    direction = direction,
                    sourceStartMsByClip = clips.map { it.sourceStartMs },
                    presentationTimestampsUsByClip = timestampsByClip,
                ) ?: return@launch

                player.pause()
                seekPreviewToTimelinePosition(
                    calculateTimelinePositionMs(clipDurationsMs, target.clipIndex, target.positionMs),
                )
            } finally {
                isResolvingFrameStep = false
            }
        }
    }

    fun syncPlayerPosition(
        clipIndex: Int,
        clipPositionMs: Long,
        forceAudioLaneSeek: Boolean = true,
    ) {
        val position = resolvePlayerTimelinePosition(clipDurationsMs, clipIndex, clipPositionMs) ?: return
        selectedIndex = position.clipIndex
        timelinePositionMs = position.timelinePositionMs.coerceIn(0L, timelineDurationMs).toFloat()
        if (forceAudioLaneSeek) {
            seekAudioPlayersToProjectPosition(position.timelinePositionMs)
        }
        clips.getOrNull(position.clipIndex)?.let { clip ->
            player.setVideoEffects(
                createClipVisualEffects(clip.visualAdjustments) +
                    createClipTimedVignetteEffects(
                        clip.timedEffectSegments,
                        clip.durationMs,
                        position.timelinePositionMs - position.clipPositionMs,
                    ) +
                    createClipTextOverlayEffects(clip.allTextOverlays) +
                    createClipStickerOverlayEffects(clip.allStickerOverlays, context),
            )
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
        fun updatePreviewFrameRate(tracks: Tracks) {
            val frameRate = resolveSelectedPreviewFrameRate(previewFrameRateCandidates(tracks))
            val currentUri = clips.getOrNull(player.currentMediaItemIndex)?.uri
            previewFrameRate = frameRate
            previewFrameRateUri = currentUri
            if (frameRate != null && currentUri != null) {
                previewFrameRatesByUri = previewFrameRatesByUri + (currentUri to frameRate)
            }
        }
        updatePreviewFrameRate(player.currentTracks)
        val listener = object : Player.Listener {
            // Media3 can report one playlist transition through both callbacks
            // below in the same event batch. Collect their reasons and perform
            // one project-clock update from onEvents instead of applying visual
            // effects and writing the preview checkpoint twice.
            var pendingPositionDiscontinuityAutomatic: Boolean? = null
            var pendingMediaItemTransitionAutomatic: Boolean? = null

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                // The built-in PlayerView controller can pause independently of
                // the editor's Play button, so checkpoint that position too.
                if (!playing) {
                    syncPlayerPosition(player.currentMediaItemIndex, player.currentPosition)
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                updatePreviewFrameRate(tracks)
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
                val isAutomatic = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                // If more than one discontinuity is coalesced, any explicit
                // change requires an audio resync.
                pendingPositionDiscontinuityAutomatic =
                    (pendingPositionDiscontinuityAutomatic ?: true) && isAutomatic
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val isAutomatic = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                pendingMediaItemTransitionAutomatic =
                    (pendingMediaItemTransitionAutomatic ?: true) && isAutomatic
            }
            override fun onEvents(player: Player, events: Player.Events) {
                val hasPositionChange = events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                if (!hasPositionChange) return

                val forceAudioLaneSeek = shouldForceAudioLaneSeekForVideoEvents(
                    positionDiscontinuityIsAutomatic = pendingPositionDiscontinuityAutomatic,
                    mediaItemTransitionIsAutomatic = pendingMediaItemTransitionAutomatic,
                ) ?: return
                pendingPositionDiscontinuityAutomatic = null
                pendingMediaItemTransitionAutomatic = null

                // onEvents runs after the individual callbacks, so this reads
                // the final playlist index/position and handles the transition once.
                syncPlayerPosition(
                    player.currentMediaItemIndex,
                    player.currentPosition,
                    forceAudioLaneSeek = forceAudioLaneSeek,
                )
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun mirrorRecoveryJournals(projectId: String, bundle: ProjectRecoveryJournalBundle) {
        // File I/O stays off the Compose thread. The serialized store rejects stale
        // writes, so rapid successive edits cannot replace a newer checkpoint.
        exportScope.launch(Dispatchers.IO) {
            ProjectRecoveryFileStore.write(recoveryDirectory, projectId, ProjectRecoveryFileKind.PROJECT, bundle.projectJournal)
            ProjectRecoveryFileStore.write(recoveryDirectory, projectId, ProjectRecoveryFileKind.AUDIO, bundle.audioTracksJournal)
        }
    }
    fun saveProject(items: List<EditClip>) {
        val projectName = projects.firstOrNull { it.id == activeProjectId }?.name ?: "Untitled project"
        val bundle = persistProjectState(preferences, activeProjectId, projectName, items, audioTrackClips)
        mirrorRecoveryJournals(activeProjectId, bundle)
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
        audioTrackClips = loadPrimaryProjectAudioTracks(
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
        val recoveryBundle = persistProjectState(preferences, project.id, project.name, emptyList(), emptyList())
        mirrorRecoveryJournals(project.id, recoveryBundle)
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
        val deletionEditor = preferences.edit()
        projectPreferenceKeysForDeletion(project.id).forEach { key ->
            deletionEditor.remove(key)
        }
        deletionEditor.apply()
        exportScope.launch(Dispatchers.IO) {
            ProjectRecoveryFileStore.deleteProject(recoveryDirectory, project.id)
        }
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
        val projectIdAtSave = activeProjectId
        val projectNameAtSave = projects.firstOrNull { it.id == projectIdAtSave }?.name ?: "Untitled project"
        val recoveryBundle = persistProjectAudioTracks(
            preferences, projectIdAtSave, projectNameAtSave, clips, items, projectDurationMs,
        )
        mirrorRecoveryJournals(projectIdAtSave, recoveryBundle)
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
    fun duplicateSelectedAudioTrack() {
        val selected = selectedAudioTrack ?: return
        val projectDurationMs = calculateTimelineDurationMs(clips.map { it.durationMs })
        val duplicateId = java.util.UUID.randomUUID().toString()
        val updated = duplicateAudioTrackClip(
            clips = audioTrackClips,
            clipId = selected.id,
            newClipId = duplicateId,
            projectDurationMs = projectDurationMs,
        )
        if (updated == null) {
            exportMessage = when {
                audioTrackClips.size >= MAX_AUDIO_TRACK_CLIPS ->
                    "Audio clip limit reached. Remove an item before duplicating."
                else -> "No free space remains after this clip on its audio lane."
            }
            return
        }
        changedAudioTracks(updated)
        selectedAudioTrackId = duplicateId
        exportMessage = "Duplicated ${selected.name} into the next free lane position. Original source audio remains unchanged."
    }

    fun rippleDeleteSelectedAudioTrack() {
        val selected = selectedAudioTrack ?: return
        val projectDurationMs = calculateTimelineDurationMs(clips.map { it.durationMs })
        val updated = rippleDeleteAudioTrackClip(audioTrackClips, selected.id, projectDurationMs)
        if (updated == null) {
            exportMessage = "Could not ripple-delete this clip because the audio lane state is invalid."
            return
        }
        val nextSelection = updated
            .filter { it.type == selected.type && it.startMs >= selected.startMs }
            .minByOrNull { it.startMs }
            ?: updated
                .filter { it.type == selected.type && it.endMs <= selected.startMs }
                .maxByOrNull { it.endMs }
        changedAudioTracks(updated)
        selectedAudioTrackId = nextSelection?.id
        exportMessage = "Ripple-deleted ${selected.name}; later ${selected.type.name.lowercase()} clips moved earlier. Source audio is unchanged."
    }

    fun splitSelectedAudioTrackAtPlayhead() {
        val selected = selectedAudioTrack ?: return
        val projectDurationMs = calculateTimelineDurationMs(clips.map { it.durationMs })
        val splitPositionMs = timelinePositionMs.toLong().coerceAtLeast(0L)
        val splitId = java.util.UUID.randomUUID().toString()
        val updated = splitAudioTrackClip(
            clips = audioTrackClips,
            clipId = selected.id,
            splitTimelineMs = splitPositionMs,
            newClipId = splitId,
            projectDurationMs = projectDurationMs,
        )
        if (updated == null) {
            val offsetMs = splitPositionMs - selected.startMs
            exportMessage = when {
                audioTrackClips.size >= MAX_AUDIO_TRACK_CLIPS ->
                    "Audio clip limit reached. Remove an item before splitting."
                splitPositionMs <= selected.startMs || splitPositionMs >= selected.endMs ->
                    "Move the playhead inside the selected audio clip to split it."
                offsetMs < selected.fadeInMs || offsetMs > selected.durationMs - selected.fadeOutMs ->
                    "Split outside the existing fade-in and fade-out ramps to preserve the current sound."
                else -> "Could not split this audio clip without changing its source range."
            }
            return
        }
        changedAudioTracks(updated)
        selectedAudioTrackId = splitId
        exportMessage = "Split ${selected.name} at the playhead. Original source audio remains unchanged."
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

    fun rippleTrimSelectedAudioTrack(sourceStartMs: Long, sourceEndMs: Long) {
        val selected = selectedAudioTrack ?: return
        val projectDurationMs = calculateTimelineDurationMs(clips.map { it.durationMs })
        val durationDeltaMs = (sourceEndMs - sourceStartMs) - selected.durationMs
        val updated = rippleTrimAudioTrackClip(
            audioTrackClips,
            selected.id,
            sourceStartMs,
            sourceEndMs,
            projectDurationMs,
        )
        if (updated == null) {
            exportMessage = "Ripple trim cannot fit this source range and shifted clips inside the project. No clips were changed."
        } else {
            changedAudioTracks(updated)
            val movement = when {
                durationDeltaMs < 0L -> "later clips moved earlier by ${-durationDeltaMs} ms"
                durationDeltaMs > 0L -> "later clips moved later by ${durationDeltaMs} ms"
                else -> "later clips kept their timing"
            }
            exportMessage = "Ripple-trimmed ${selected.name}; $movement on this lane. Source audio remains unchanged."
        }
    }
    fun applySelectedAudioTrackTrim(sourceStartMs: Long, sourceEndMs: Long) {
        if (rippleAudioTrimEnabled) {
            rippleTrimSelectedAudioTrack(sourceStartMs, sourceEndMs)
        } else {
            trimSelectedAudioTrack(sourceStartMs, sourceEndMs)
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
        hueShiftDegrees: Int = selectedClip?.visualAdjustments?.hueShiftDegrees ?: 0,
        creativeEffectPreset: ClipCreativeEffectPreset =
            selectedClip?.visualAdjustments?.creativeEffectPreset ?: ClipCreativeEffectPreset.ORIGINAL,
        creativeEffectIntensity: Int =
            selectedClip?.visualAdjustments?.creativeEffectIntensity ?: DEFAULT_CREATIVE_EFFECT_INTENSITY,
    ) {
        val clip = clips.getOrNull(selectedIndex) ?: return
        val next = decodeClipVisualAdjustments(
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            hueShiftDegrees = hueShiftDegrees,
            creativeEffectPresetId = creativeEffectPreset.id,
            creativeEffectIntensity = creativeEffectIntensity,
        ) ?: return
        if (clip.visualAdjustments == next) return
        changed(clips.toMutableList().apply {
            this[selectedIndex] = clip.copy(visualAdjustments = next)
        })
        if (player.currentMediaItem?.mediaId == clip.id) {
            player.setVideoEffects(
                createClipVisualEffects(next) +
                    createClipTimedVignetteEffects(
                        clip.timedEffectSegments,
                        clip.durationMs,
                        calculateTimelinePositionMs(clipDurationsMs, selectedIndex, 0L),
                    ) +
                    createClipTextOverlayEffects(clip.allTextOverlays) +
                    createClipStickerOverlayEffects(clip.allStickerOverlays, context),
            )
        }
        exportMessage = "Clip visual effects updated"
    }
    fun addSelectedClipTimedVignetteSegment() {
        val clip = clips.getOrNull(selectedIndex) ?: return
        if (clip.durationMs <= 0L) {
            exportMessage = "Select a clip with a valid duration before adding an effect."
            return
        }
        val clipStartMs = calculateTimelinePositionMs(clipDurationsMs, selectedIndex, 0L)
        val localStartMs = (timelinePositionMs.toLong() - clipStartMs)
            .coerceIn(0L, clip.durationMs - 1L)
        val segment = ClipTimedEffectSegment(
            id = java.util.UUID.randomUUID().toString(),
            preset = ClipCreativeEffectPreset.VIGNETTE,
            intensity = timedVignetteIntensityDraft.toInt().coerceIn(1, 100),
            startMs = localStartMs,
            endMs = null,
        )
        val nextSegments = appendClipTimedEffectSegment(
            segments = clip.timedEffectSegments,
            clipDurationMs = clip.durationMs,
            segment = segment,
        )
        if (nextSegments == null) {
            exportMessage = "This clip cannot accept another timed effect segment."
            return
        }
        changed(clips.toMutableList().apply {
            this[selectedIndex] = clip.copy(timedEffectSegments = nextSegments)
        })
        selectedTimedEffectSelection = clip.id to segment.id
        activeTool = "Effects"
        seekPreviewToTimelinePosition(
            calculateTimelinePositionMs(clipDurationsMs, selectedIndex, localStartMs),
        )
        exportMessage = "Timed Vignette added from the playhead to the clip end"
    }

    fun removeSelectedClipTimedEffectSegment() {
        val clip = clips.getOrNull(selectedIndex) ?: return
        val selection = selectedTimedEffectSelection?.takeIf { it.first == clip.id } ?: return
        val nextSegments = clip.timedEffectSegments.filterNot { it.id == selection.second }
        if (nextSegments.size == clip.timedEffectSegments.size) {
            selectedTimedEffectSelection = null
            return
        }
        changed(clips.toMutableList().apply {
            this[selectedIndex] = clip.copy(timedEffectSegments = nextSegments)
        })
        selectedTimedEffectSelection = null
        seekPreviewToTimelinePosition(timelinePositionMs.toLong())
        exportMessage = "Timed effect segment removed"
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
    fun commitSelectedClipStickerOverlays(
        clip: EditClip,
        overlays: List<ClipStickerOverlay>,
        message: String,
    ) {
        if (overlays == clip.allStickerOverlays) return
        val updatedClip = clip.copy(stickerOverlays = overlays)
        changed(clips.toMutableList().apply {
            this[selectedIndex] = updatedClip
        })
        if (player.currentMediaItem?.mediaId == clip.id) {
            player.setVideoEffects(
                createClipVisualEffects(updatedClip.visualAdjustments) +
                    createClipTimedVignetteEffects(
                        updatedClip.timedEffectSegments,
                        updatedClip.durationMs,
                        calculateTimelinePositionMs(clipDurationsMs, selectedIndex, 0L),
                    ) +
                    createClipTextOverlayEffects(updatedClip.allTextOverlays) +
                    createClipStickerOverlayEffects(updatedClip.allStickerOverlays, context),
            )
        }
        exportMessage = message
    }
    fun addSelectedClipSticker(symbol: String, assetId: String? = null) {
        val clip = clips.getOrNull(selectedIndex) ?: return
        if (clip.allStickerOverlays.size >= MAX_CLIP_STICKER_OVERLAYS) {
            exportMessage = "A clip can contain up to $MAX_CLIP_STICKER_OVERLAYS stickers."
            return
        }
        val overlay = decodeClipStickerOverlay(
            raw = symbol,
            position = selectedStickerPosition.name,
            assetId = assetId,
        ) ?: return
        val addedIndex = clip.allStickerOverlays.size
        commitSelectedClipStickerOverlays(
            clip,
            clip.allStickerOverlays + overlay,
            "Sticker added to this clip",
        )
        selectedStickerLayerIndex = addedIndex
    }
    fun removeSelectedClipSticker(overlayIndex: Int) {
        val clip = clips.getOrNull(selectedIndex) ?: return
        if (overlayIndex !in clip.allStickerOverlays.indices) return
        val remaining = clip.allStickerOverlays.filterIndexed { index, _ -> index != overlayIndex }
        commitSelectedClipStickerOverlays(
            clip,
            remaining,
            "Sticker removed",
        )
        selectedStickerLayerIndex = if (remaining.isEmpty()) {
            -1
        } else {
            when {
                selectedStickerLayerIndex == overlayIndex ->
                    (overlayIndex - 1).coerceIn(0, remaining.lastIndex)
                selectedStickerLayerIndex > overlayIndex -> selectedStickerLayerIndex - 1
                else -> selectedStickerLayerIndex.coerceIn(0, remaining.lastIndex)
            }
        }
    }
    fun updateSelectedClipStickerTiming(
        overlayIndex: Int,
        startMs: Long? = null,
        endMs: Long? = null,
    ) {
        val clip = clips.getOrNull(selectedIndex) ?: return
        val sticker = clip.allStickerOverlays.getOrNull(overlayIndex) ?: return
        val updated = updateClipStickerOverlayTiming(
            overlay = sticker,
            clipDurationMs = clip.durationMs,
            requestedStartMs = startMs ?: sticker.startMs,
            requestedEndMs = endMs ?: (sticker.endMs ?: clip.durationMs),
        ) ?: return
        if (updated == sticker) return
        commitSelectedClipStickerOverlays(
            clip,
            clip.allStickerOverlays.toMutableList().apply { this[overlayIndex] = updated },
            "Sticker timing updated",
        )
    }
    fun updateSelectedClipStickerScale(overlayIndex: Int, scale: Float) {
        val clip = clips.getOrNull(selectedIndex) ?: return
        val sticker = clip.allStickerOverlays.getOrNull(overlayIndex) ?: return
        val updated = updateClipStickerOverlayScale(sticker, scale) ?: return
        if (updated == sticker) return
        commitSelectedClipStickerOverlays(
            clip,
            clip.allStickerOverlays.toMutableList().apply { this[overlayIndex] = updated },
            "Sticker size updated",
        )
    }
    fun updateSelectedClipStickerRotation(overlayIndex: Int, degrees: Float) {
        val clip = clips.getOrNull(selectedIndex) ?: return
        val sticker = clip.allStickerOverlays.getOrNull(overlayIndex) ?: return
        val updated = updateClipStickerOverlayRotation(sticker, degrees) ?: return
        if (updated == sticker) return
        commitSelectedClipStickerOverlays(
            clip,
            clip.allStickerOverlays.toMutableList().apply { this[overlayIndex] = updated },
            "Sticker rotation updated",
        )
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
            val currentPreviewPosition = clips.getOrNull(player.currentMediaItemIndex)?.let { clip ->
                createSavedPlaybackPosition(
                    ClipPlaybackRange(clip.id, clip.sourceStartMs, clip.sourceEndMs),
                    player.currentPosition,
                )
            } ?: loadSavedPlaybackPosition(preferences, activeProjectId)
            val backup = encodeProjectBackup(
                projectName,
                clips,
                projectCanvasSettingsFor(canvasAspectRatio, canvasBackground),
                audioTrackClips,
                previewPlaybackSpeed,
                currentPreviewPosition,
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
                val recoveryBundle = persistProjectState(
                    preferences,
                    imported.id,
                    imported.name,
                    backup.clips,
                    backup.audioTracks,
                )
                mirrorRecoveryJournals(imported.id, recoveryBundle)
                val importedSettings = preferences.edit()
                    .putString(
                        "project_" + imported.id + "_canvas_settings",
                        encodeProjectCanvasSettings(backup.canvasSettings),
                    )
                    .putFloat(
                        projectPreviewPlaybackSpeedPreferenceKey(imported.id),
                        backup.previewPlaybackSpeed,
                    )
                backup.previewPosition?.let { position ->
                    importedSettings
                        .putString("project_${imported.id}_preview_clip_id", position.clipId)
                        .putLong("project_${imported.id}_preview_source_time_ms", position.sourceTimeMs)
                }
                importedSettings.apply()
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
                        exportMessage = "Could not read an audio track and duration from this file. It was not added."
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

    VyroCutTheme {
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
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().imePadding(),
                ) {
                    val editorMetrics = editorLayoutMetrics(
                        windowHeightDp = maxHeight.value.roundToInt(),
                        windowWidthDp = maxWidth.value.roundToInt(),
                        fontScale = LocalDensity.current.fontScale,
                        hasPreviewRelinkAction = selectedClip?.uri?.let { it in missingMediaUris } == true,
                    )
                    val compactClipActionLabels = editorMetrics.useCompactClipActionLabels
                    val clipActionContentPadding = PaddingValues(
                        horizontal = (
                            if (compactClipActionLabels) {
                                VyroCutClipActionMetrics.CompactButtonHorizontalPaddingDp
                            } else {
                                VyroCutClipActionMetrics.StandardButtonHorizontalPaddingDp
                            }
                        ).dp,
                        vertical = VyroCutClipActionMetrics.ButtonVerticalPaddingDp.dp,
                    )
                    val clipActionIconSize = (
                        if (compactClipActionLabels) {
                            VyroCutClipActionMetrics.CompactIconSizeDp
                        } else {
                            VyroCutClipActionMetrics.StandardIconSizeDp
                        }
                    ).dp
                    val clipActionLabelFontSize = (
                        if (compactClipActionLabels) VyroCutTypography.SmallBodySp
                        else VyroCutTypography.BodySp
                    ).sp
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = VyroCutEditorChromeMetrics.HorizontalContentPaddingDp.dp, vertical = editorMetrics.contentPaddingVerticalDp.dp),
                verticalArrangement = Arrangement.spacedBy(editorMetrics.sectionSpacingDp.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { saveProject(clips); player.pause(); showHome = true },
                        contentPadding = PaddingValues(horizontal = VyroCutEditorChromeMetrics.ProjectNavigationButtonHorizontalPaddingDp.dp, vertical = 0.dp)
                    ) { Text("‹ Projects", color = StudioAccent, fontSize = VyroCutTypography.SmallBodySp.sp) }
                    Icon(Icons.Default.Movie, null, tint = StudioAccent, modifier = Modifier.size(VyroCutEditorChromeMetrics.BrandIconSizeDp.dp))
                    Text("VyroCut", color = StudioText, fontSize = VyroCutTypography.BrandSp.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = VyroCutEditorChromeMetrics.BrandTitleStartPaddingDp.dp))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showProjects = true }) {
                        Text(projects.firstOrNull { it.id == activeProjectId }?.name ?: "Project", color = StudioAccent, maxLines = 1)
                    }
                    IconButton(onClick = { saveProject(clips) }) { Icon(Icons.Default.Save, "Save project", tint = StudioAccent) }
                }
                Box(
                    Modifier.fillMaxWidth().height(editorMetrics.previewHeightDp.dp)
                        .background(canvasBackground, RoundedCornerShape(VyroCutEditorChromeMetrics.PreviewCanvasCornerDp.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedClip == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Movie, null, tint = StudioMuted, modifier = Modifier.size(VyroCutEditorChromeMetrics.EmptyPreviewIconSizeDp.dp))
                            Spacer(Modifier.height(VyroCutEditorChromeMetrics.EmptyPreviewIconGapDp.dp))
                            Text("Import a video to start editing", color = StudioMuted, fontSize = VyroCutTypography.SmallBodySp.sp)
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
                val previewSourceMissing = selectedClip?.uri?.let { it in missingMediaUris } == true
                val previewSubtitle = when {
                    selectedClip == null -> "Your project is stored locally"
                    previewSourceMissing -> "Source unavailable • Relink to keep this edit"
                    else -> "Segment ${selectedIndex + 1} of ${clips.size} • ${formatTime(selectedClip.durationMs)}"
                }
                val previewRelinkAction: (() -> Unit)? = selectedClip
                    ?.takeIf { it.uri in missingMediaUris }
                    ?.let { missingClip ->
                        {
                            relinkTargetClipId = missingClip.id
                            mediaPicker.launch(arrayOf("video/*"))
                        }
                    }
                val previewRelinkLabel = previewRelinkAction?.let {
                    if (isReadingMedia) "Reading…" else "Relink"
                }
                if (editorMetrics.stackPreviewMetadataAboveTransportControls) {
                    EditorPreviewMetadata(
                        title = selectedClip?.name ?: "No media selected",
                        subtitle = previewSubtitle,
                        subtitleColor = if (previewSourceMissing) VyroCutPalette.Danger else StudioMuted,
                        subtitleMaxLines = editorMetrics.previewMetadataSubtitleMaxLines,
                        relinkLabel = previewRelinkLabel,
                        relinkEnabled = !isReadingMedia,
                        onRelink = previewRelinkAction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val transportControlsModifier = if (editorMetrics.scrollTransportControlsHorizontally) {
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                } else {
                    Modifier
                }
                Row(
                    modifier = transportControlsModifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!editorMetrics.stackPreviewMetadataAboveTransportControls) {
                        EditorPreviewMetadata(
                            title = selectedClip?.name ?: "No media selected",
                            subtitle = previewSubtitle,
                            subtitleColor = if (previewSourceMissing) VyroCutPalette.Danger else StudioMuted,
                            subtitleMaxLines = editorMetrics.previewMetadataSubtitleMaxLines,
                            relinkLabel = previewRelinkLabel,
                            relinkEnabled = !isReadingMedia,
                            onRelink = previewRelinkAction,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    IconButton(
                        onClick = { stepPreviewFrame(PreviewFrameStepDirection.PREVIOUS) },
                        enabled = selectedClip != null && !isResolvingFrameStep,
                        modifier = Modifier.size(VyroCutEditorChromeMetrics.CompactIconButtonSizeDp.dp),
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous frame",
                            tint = StudioAccent,
                            modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportIconSizeDp.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            resolveTimelineSeekTargetAfterDelta(
                                clipDurationsMs,
                                timelinePositionMs.toLong(),
                                -5_000L,
                            )?.let { target ->
                                seekPreviewToTimelinePosition(
                                    calculateTimelinePositionMs(clipDurationsMs, target.clipIndex, target.positionMs),
                                )
                            }
                        },
                        enabled = clips.isNotEmpty(),
                        modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportSeekButtonSizeDp.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back 5 seconds",
                            tint = StudioAccent,
                            modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportSeekIconSizeDp.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            resolveTimelineSeekTargetAfterDelta(
                                clipDurationsMs,
                                timelinePositionMs.toLong(),
                                5_000L,
                            )?.let { target ->
                                seekPreviewToTimelinePosition(
                                    calculateTimelinePositionMs(clipDurationsMs, target.clipIndex, target.positionMs),
                                )
                            }
                        },
                        enabled = clips.isNotEmpty(),
                        modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportSeekButtonSizeDp.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Forward 5 seconds",
                            tint = StudioAccent,
                            modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportSeekIconSizeDp.dp),
                        )
                    }
                    IconButton(
                        onClick = { stepPreviewFrame(PreviewFrameStepDirection.NEXT) },
                        enabled = selectedClip != null && !isResolvingFrameStep,
                        modifier = Modifier.size(VyroCutEditorChromeMetrics.CompactIconButtonSizeDp.dp),
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next frame",
                            tint = StudioAccent,
                            modifier = Modifier.size(VyroCutEditorChromeMetrics.TransportIconSizeDp.dp),
                        )
                    }
                    TextButton(
                        onClick = {
                            val nextSpeed = nextPreviewPlaybackSpeed(previewPlaybackSpeed)
                            previewPlaybackSpeed = nextSpeed
                            preferences.edit()
                                .putFloat(
                                    projectPreviewPlaybackSpeedPreferenceKey(activeProjectId),
                                    nextSpeed,
                                )
                                .apply()
                        },
                        enabled = selectedClip != null,
                        contentPadding = PaddingValues(horizontal = VyroCutEditorChromeMetrics.PlaybackSpeedButtonHorizontalPaddingDp.dp, vertical = 0.dp),
                    ) {
                        Text(previewPlaybackSpeedLabel(previewPlaybackSpeed), color = StudioAccent, fontSize = VyroCutTypography.LabelSp.sp)
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
                                seekPreviewToTimelinePosition(0L)
                            }
                            player.play()
                        }
                    }, enabled = selectedClip != null,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = StudioAccent, contentColor = VyroCutPalette.AccentInk)) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(VyroCutEditorChromeMetrics.PlaybackButtonIconSizeDp.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(VyroCutEditorChromeMetrics.ScrubberSectionGapDp.dp)) {
                    Slider(
                        value = timelinePositionMs.coerceIn(0f, timelineDurationMs.toFloat()),
                        onValueChange = {
                            isScrubbing = true
                            timelinePositionMs = it
                        },
                        onValueChangeFinished = {
                            seekPreviewToTimelinePosition(timelinePositionMs.toLong())
                            isScrubbing = false
                        },
                        enabled = clips.isNotEmpty(),
                        colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(timelinePositionMs.toLong()), color = StudioMuted, fontSize = TIMELINE_RULER_LABEL_FONT_SP.sp)
                        Text(formatTime(timelineDurationMs), color = StudioMuted, fontSize = TIMELINE_RULER_LABEL_FONT_SP.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TIMELINE", color = StudioMuted, fontSize = TIMELINE_TRACK_DETAIL_FONT_SP.sp, letterSpacing = VyroCutTypography.TimelineSectionLetterSpacingSp.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { undoEdit() }, enabled = undoStack.isNotEmpty(), contentPadding = PaddingValues(horizontal = VyroCutEditorChromeMetrics.TimelineActionButtonHorizontalPaddingDp.dp, vertical = 0.dp)) {
                        Text("Undo", fontSize = TIMELINE_TRACK_LABEL_FONT_SP.sp)
                    }
                    TextButton(onClick = { redoEdit() }, enabled = redoStack.isNotEmpty(), contentPadding = PaddingValues(horizontal = VyroCutEditorChromeMetrics.TimelineActionButtonHorizontalPaddingDp.dp, vertical = 0.dp)) {
                        Text("Redo", fontSize = TIMELINE_TRACK_LABEL_FONT_SP.sp)
                    }
                    Text("${clips.size} clips", color = StudioMuted, fontSize = TIMELINE_TRACK_LABEL_FONT_SP.sp)
                    Box {
                        IconButton(
                            onClick = { showClipActions = true },
                            enabled = selectedClip != null,
                            modifier = Modifier.size(VyroCutEditorChromeMetrics.CompactIconButtonSizeDp.dp),
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
                    audioWaveforms = audioWaveforms,
                    selectedIndex = selectedIndex,
                    selectedAudioTrackId = selectedAudioTrackId,
                    selectedTimedEffectSelection = selectedTimedEffectSelection,
                    playheadPositionMs = timelinePositionMs.toLong(),
                    isPlaying = isPlaying,
                    onSelectAudioTrack = { trackClip ->
                        selectedAudioTrackId = trackClip.id
                        selectedAudioTrackType = trackClip.type
                        activeTool = "Audio"
                        seekPreviewToTimelinePosition(trackClip.startMs)
                    },
                    onSeek = { target ->
                        seekPreviewToTimelinePosition(
                            calculateTimelinePositionMs(clipDurationsMs, target.clipIndex, target.positionMs),
                        )
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
                            seekPreviewToTimelinePosition(
                                calculateTimelinePositionMs(clipDurationsMs, index, positionMs),
                            )
                        }
                    },
                    onSelectSticker = { index, layerIndex ->
                        clips.getOrNull(index)?.let { clip ->
                            selectedIndex = index
                            selectedStickerLayerIndex = layerIndex.takeIf {
                                it in clip.allStickerOverlays.indices
                            } ?: -1
                            activeTool = "Stickers"
                            val localPositionMs = clip.allStickerOverlays.getOrNull(layerIndex)?.startMs
                                ?.coerceIn(0L, (clip.durationMs - 1L).coerceAtLeast(0L))
                                ?: 0L
                            seekPreviewToTimelinePosition(
                                calculateTimelinePositionMs(clipDurationsMs, index, localPositionMs),
                            )
                        }
                    },
                    onSelectTimedEffect = { index, segmentId ->
                        clips.getOrNull(index)?.let { clip ->
                            val segment = clip.timedEffectSegments.firstOrNull { it.id == segmentId }
                            if (segment != null) {
                                selectedIndex = index
                                selectedTimedEffectSelection = clip.id to segment.id
                                activeTool = "Effects"
                                seekPreviewToTimelinePosition(
                                    calculateTimelinePositionMs(clipDurationsMs, index, segment.startMs),
                                )
                            }
                        }
                    }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(VyroCutClipActionMetrics.ButtonGapDp.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { mediaPicker.launch(arrayOf("video/*")) },
                        enabled = !isReadingMedia,
                        modifier = Modifier.weight(1f),
                        contentPadding = clipActionContentPadding,
                        shape = RoundedCornerShape(VyroCutClipActionMetrics.ButtonCornerDp.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(clipActionIconSize))
                        Text(
                            if (compactClipActionLabels) "Add" else "Import",
                            fontSize = clipActionLabelFontSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                        val (leftStickerOverlays, rightStickerOverlays) = splitClipStickerOverlaysAt(
                            clip.allStickerOverlays,
                            splitOffsetMs,
                            clip.durationMs,
                        )
                        val (leftTimedEffectSegments, rightTimedEffectSegments) =
                            splitClipTimedEffectSegmentsAt(
                                clip.timedEffectSegments,
                                splitOffsetMs,
                                clip.durationMs,
                            )
                        val left = clip.copy(
                            sourceEndMs = halves.first.endMs,
                            fadeOutMs = 0L,
                            textOverlay = leftTextOverlays.firstOrNull(),
                            additionalTextOverlays = leftTextOverlays.drop(1),
                            stickerOverlays = leftStickerOverlays,
                            timedEffectSegments = leftTimedEffectSegments,
                        )
                        val right = clip.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            sourceStartMs = halves.second.startMs,
                            sourceEndMs = halves.second.endMs,
                            fadeInMs = 0L,
                            textOverlay = rightTextOverlays.firstOrNull(),
                            additionalTextOverlays = rightTextOverlays.drop(1),
                            stickerOverlays = rightStickerOverlays,
                            timedEffectSegments = rightTimedEffectSegments,
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
                    } == true, modifier = Modifier.weight(1f), contentPadding = clipActionContentPadding, shape = RoundedCornerShape(VyroCutClipActionMetrics.ButtonCornerDp.dp)) {
                        Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(clipActionIconSize))
                        Text(
                            if (compactClipActionLabels) "Split" else "Split at playhead",
                            fontSize = clipActionLabelFontSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = { selectedClip?.let { openTrimDialog(it) } },
                        enabled = selectedClip != null &&
                            selectedClip.durationMs >= MIN_CLIP_DURATION_MS &&
                            selectedClip.uri !in missingMediaUris &&
                            !isReadingMedia,
                        modifier = Modifier.weight(1f),
                        contentPadding = clipActionContentPadding,
                        shape = RoundedCornerShape(VyroCutClipActionMetrics.ButtonCornerDp.dp)
                    ) {
                        Text(
                            "Trim",
                            fontSize = clipActionLabelFontSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { if (selectedClip != null) changed(clips.toMutableList().apply { removeAt(selectedIndex) }) }, enabled = selectedClip != null) {
                        Icon(Icons.Default.Delete, "Delete selected clip", tint = VyroCutPalette.Danger)
                    }
                }
                VyroCutToolDock(
                    activeTool = activeTool,
                    onToolSelected = { tool ->
                        activeTool = tool
                        if (tool == "Adjust" && selectedAdjustment !in setOf("Brightness", "Contrast", "Saturation")) {
                            selectedAdjustment = "Brightness"
                        }
                    }
                )
                if (activeTool == "Ratio" || activeTool == "Canvas") {
                    Column(
                        Modifier.fillMaxWidth().background(
                            StudioPanel,
                            RoundedCornerShape(VyroCutShapes.MediumCornerDp.dp),
                        ).padding(
                            horizontal = VyroCutCanvasPanelMetrics.HorizontalPaddingDp.dp,
                            vertical = VyroCutCanvasPanelMetrics.VerticalPaddingDp.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(VyroCutCanvasPanelMetrics.SectionGapDp.dp),
                    ) {
                        Text(
                            if (activeTool == "Ratio") "ASPECT RATIO" else "CANVAS BACKGROUND",
                            color = StudioText,
                            fontSize = VyroCutTypography.LabelSp.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(VyroCutSpacing.SmallDp.dp)) {
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
                                listOf("Black" to VyroCutPalette.CanvasBlack, "Dark" to VyroCutPalette.CanvasDark, "White" to VyroCutPalette.CanvasWhite).forEach { (label, color) ->
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
                if (activeTool == "Effects") {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(VyroCutShapes.MediumCornerDp.dp))
                            .padding(horizontal = VyroCutEffectsPanelMetrics.HorizontalPaddingDp.dp, vertical = VyroCutEffectsPanelMetrics.VerticalPaddingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(VyroCutEffectsPanelMetrics.SectionGapDp.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "VIDEO EFFECTS",
                                color = StudioText,
                                fontSize = VyroCutTypography.LabelSp.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = VyroCutEffectsPanelMetrics.HeaderLetterSpacingSp.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            if (selectedClip != null &&
                                selectedClip.visualAdjustments.creativeEffectPreset != ClipCreativeEffectPreset.ORIGINAL
                            ) {
                                TextButton(
                                    onClick = {
                                        selectedClip?.let { clip ->
                                            val reset = resetClipCreativeEffect(clip.visualAdjustments)
                                            clipCreativeEffectIntensityDraft =
                                                reset.creativeEffectIntensity.toFloat()
                                            commitSelectedClipVisualAdjustments(
                                                brightness = clipBrightnessDraft.toInt(),
                                                contrast = clipContrastDraft.toInt(),
                                                saturation = clipSaturationDraft.toInt(),
                                                hueShiftDegrees = clipHueShiftDraft.toInt(),
                                                creativeEffectPreset = reset.creativeEffectPreset,
                                                creativeEffectIntensity = reset.creativeEffectIntensity,
                                            )
                                        }
                                    },
                                    contentPadding = PaddingValues(
                                        horizontal = VyroCutEffectsPanelMetrics.ResetButtonHorizontalPaddingDp.dp,
                                        vertical = VyroCutEffectsPanelMetrics.ResetButtonVerticalPaddingDp.dp,
                                    ),
                                ) {
                                    Text("Original", fontSize = VyroCutTypography.CaptionSp.sp)
                                }
                            }
                        }
                        if (selectedClip == null) {
                            Text("Select a video clip to apply an effect.", color = StudioMuted, fontSize = VyroCutTypography.CaptionSp.sp)
                        } else {
                            ClipCreativeEffectCategory.values().forEach { category ->
                                val presets = clipCreativeEffectPresetsForCategory(category)
                                if (presets.isNotEmpty()) {
                                    Text(
                                        category.label,
                                        color = StudioMuted,
                                        fontSize = VyroCutTypography.CaptionSp.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = VyroCutEffectsPanelMetrics.CategoryLetterSpacingSp.sp,
                                    )
                                    Row(
                                        Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(VyroCutSpacing.SmallDp.dp),
                                    ) {
                                        presets.forEach { preset ->
                                            FilterChip(
                                                selected = selectedClip.visualAdjustments.creativeEffectPreset == preset,
                                                onClick = {
                                                    commitSelectedClipVisualAdjustments(
                                                        brightness = clipBrightnessDraft.toInt(),
                                                        contrast = clipContrastDraft.toInt(),
                                                        saturation = clipSaturationDraft.toInt(),
                                                        hueShiftDegrees = clipHueShiftDraft.toInt(),
                                                        creativeEffectPreset = preset,
                                                        creativeEffectIntensity = clipCreativeEffectIntensityDraft.toInt(),
                                                    )
                                                },
                                                label = { Text(preset.label, fontSize = VyroCutTypography.LabelSp.sp) },
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                selectedClip.visualAdjustments.creativeEffectPreset.description,
                                color = StudioMuted,
                                fontSize = VyroCutTypography.CaptionSp.sp,
                            )
                            if (selectedClip.visualAdjustments.creativeEffectPreset != ClipCreativeEffectPreset.ORIGINAL) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Effect strength", color = StudioMuted, fontSize = VyroCutTypography.LabelSp.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text("${clipCreativeEffectIntensityDraft.toInt()}%", color = StudioAccent, fontSize = VyroCutTypography.LabelSp.sp)
                                    TextButton(
                                        onClick = {
                                            clipCreativeEffectIntensityDraft = DEFAULT_CREATIVE_EFFECT_INTENSITY.toFloat()
                                            commitSelectedClipVisualAdjustments(
                                                brightness = clipBrightnessDraft.toInt(),
                                                contrast = clipContrastDraft.toInt(),
                                                saturation = clipSaturationDraft.toInt(),
                                                hueShiftDegrees = clipHueShiftDraft.toInt(),
                                                creativeEffectIntensity = DEFAULT_CREATIVE_EFFECT_INTENSITY,
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = VyroCutEffectsPanelMetrics.ResetButtonHorizontalPaddingDp.dp, vertical = VyroCutEffectsPanelMetrics.ResetButtonVerticalPaddingDp.dp),
                                    ) { Text("Reset strength", fontSize = VyroCutTypography.CaptionSp.sp) }
                                }
                                Slider(
                                    value = clipCreativeEffectIntensityDraft.coerceIn(0f, 100f),
                                    onValueChange = { clipCreativeEffectIntensityDraft = it },
                                    onValueChangeFinished = {
                                        commitSelectedClipVisualAdjustments(
                                            brightness = clipBrightnessDraft.toInt(),
                                            contrast = clipContrastDraft.toInt(),
                                            saturation = clipSaturationDraft.toInt(),
                                            hueShiftDegrees = clipHueShiftDraft.toInt(),
                                            creativeEffectIntensity = clipCreativeEffectIntensityDraft.toInt(),
                                        )
                                    },
                                    valueRange = 0f..100f,
                                    steps = 99,
                                    colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Hue shift", color = StudioMuted, fontSize = VyroCutTypography.LabelSp.sp)
                                Spacer(Modifier.weight(1f))
                                Text("${clipHueShiftDraft.toInt()}°", color = StudioAccent, fontSize = VyroCutTypography.LabelSp.sp)
                                TextButton(
                                    onClick = {
                                        clipHueShiftDraft = 0f
                                        commitSelectedClipVisualAdjustments(
                                            clipBrightnessDraft.toInt(),
                                            clipContrastDraft.toInt(),
                                            clipSaturationDraft.toInt(),
                                            hueShiftDegrees = 0,
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = VyroCutEffectsPanelMetrics.ResetButtonHorizontalPaddingDp.dp, vertical = VyroCutEffectsPanelMetrics.ResetButtonVerticalPaddingDp.dp),
                                ) { Text("Reset", fontSize = VyroCutTypography.CaptionSp.sp) }
                            }
                            Slider(
                                value = clipHueShiftDraft.coerceIn(-180f, 180f),
                                onValueChange = { clipHueShiftDraft = it },
                                onValueChangeFinished = {
                                    commitSelectedClipVisualAdjustments(
                                        brightness = clipBrightnessDraft.toInt(),
                                        contrast = clipContrastDraft.toInt(),
                                        saturation = clipSaturationDraft.toInt(),
                                        hueShiftDegrees = clipHueShiftDraft.toInt(),
                                    )
                                },
                                valueRange = -180f..180f,
                                steps = 359,
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                            )
                            Text(
                                "Non-destructive • Applied to this clip in preview and MP4 export",
                                color = StudioMuted,
                                fontSize = VyroCutTypography.CaptionSp.sp,
                            )
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(StudioPanel, RoundedCornerShape(VyroCutShapes.MediumCornerDp.dp))
                                    .padding(
                                        horizontal = VyroCutEffectsPanelMetrics.HorizontalPaddingDp.dp,
                                        vertical = VyroCutEffectsPanelMetrics.VerticalPaddingDp.dp,
                                    ),
                                verticalArrangement = Arrangement.spacedBy(VyroCutEffectsPanelMetrics.SectionGapDp.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "TIMED VIGNETTE",
                                        color = StudioText,
                                        fontSize = VyroCutTypography.LabelSp.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${selectedClip.timedEffectSegments.size} / $MAX_CLIP_TIMED_EFFECT_SEGMENTS",
                                        color = StudioMuted,
                                        fontSize = VyroCutTypography.CaptionSp.sp,
                                    )
                                }
                                Text(
                                    "Add a non-destructive Vignette interval from the playhead to this clip's end.",
                                    color = StudioMuted,
                                    fontSize = VyroCutTypography.CaptionSp.sp,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Strength", color = StudioMuted, fontSize = VyroCutTypography.LabelSp.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${timedVignetteIntensityDraft.toInt()}%",
                                        color = StudioAccent,
                                        fontSize = VyroCutTypography.LabelSp.sp,
                                    )
                                }
                                Slider(
                                    value = timedVignetteIntensityDraft.coerceIn(1f, 100f),
                                    onValueChange = { timedVignetteIntensityDraft = it },
                                    valueRange = 1f..100f,
                                    steps = 98,
                                    colors = SliderDefaults.colors(
                                        thumbColor = StudioAccent,
                                        activeTrackColor = StudioAccent,
                                    ),
                                )
                                Button(
                                    onClick = { addSelectedClipTimedVignetteSegment() },
                                    enabled = selectedClip.durationMs > 0L &&
                                        selectedClip.timedEffectSegments.size < MAX_CLIP_TIMED_EFFECT_SEGMENTS,
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(VyroCutSpacing.SmallDp.dp))
                                    Text("Add at playhead", fontSize = VyroCutTypography.LabelSp.sp)
                                }
                                if (selectedTimedEffectSegment != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Selected: ${formatTime(selectedTimedEffectSegment.startMs)} – ${selectedTimedEffectSegment.endMs?.let { formatTime(it) } ?: "clip end"}",
                                            modifier = Modifier.weight(1f),
                                            color = StudioMuted,
                                            fontSize = VyroCutTypography.CaptionSp.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        TextButton(onClick = { removeSelectedClipTimedEffectSegment() }) {
                                            Text("Remove", fontSize = VyroCutTypography.CaptionSp.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (activeTool == "Filters" || activeTool == "Adjust") {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(VyroCutShapes.MediumCornerDp.dp)).padding(horizontal = VyroCutVisualPanelMetrics.HorizontalPaddingDp.dp, vertical = VyroCutVisualPanelMetrics.VerticalPaddingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(VyroCutVisualPanelMetrics.SectionGapDp.dp)
                    ) {
                        Text(
                            if (activeTool == "Filters") "FILTERS" else "CLIP ADJUSTMENTS",
                            color = StudioText,
                            fontSize = VyroCutTypography.LabelSp.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = VyroCutVisualPanelMetrics.HeaderLetterSpacingSp.sp
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(VyroCutSpacing.SmallDp.dp)) {
                            val options = if (activeTool == "Filters") {
                                ClipVisualPreset.values().map { it.label }
                            } else {
                                listOf("Brightness", "Contrast", "Saturation")
                            }
                            options.forEach { item ->
                                FilterChip(
                                    selected = if (activeTool == "Filters") {
                                        selectedClip?.visualAdjustments == clipVisualPresetAdjustments(item)
                                    } else {
                                        selectedAdjustment == item
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
                                            selectedAdjustment = item
                                        }
                                    },
                                    label = { Text(item, fontSize = VyroCutTypography.LabelSp.sp) }
                                )
                            }
                        }
                        if (activeTool == "Adjust") {
                            if (selectedClip == null) {
                                Text("Select a video clip to adjust its colour.", color = StudioMuted, fontSize = VyroCutTypography.CaptionSp.sp)
                            } else {
                                val adjustmentValue = when (selectedAdjustment) {
                                    "Contrast" -> clipContrastDraft
                                    "Saturation" -> clipSaturationDraft
                                    else -> clipBrightnessDraft
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedAdjustment, color = StudioMuted, fontSize = VyroCutTypography.LabelSp.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text("${adjustmentValue.toInt()}%", color = StudioAccent, fontSize = VyroCutTypography.LabelSp.sp)
                                    TextButton(
                                        onClick = {
                                            clipBrightnessDraft = 0f
                                            clipContrastDraft = 0f
                                            clipSaturationDraft = 0f
                                            commitSelectedClipVisualAdjustments(0, 0, 0)
                                        },
                                        contentPadding = PaddingValues(horizontal = VyroCutVisualPanelMetrics.ResetButtonHorizontalPaddingDp.dp, vertical = VyroCutVisualPanelMetrics.ResetButtonVerticalPaddingDp.dp),
                                    ) { Text("Reset", fontSize = VyroCutTypography.CaptionSp.sp) }
                                }
                                Slider(
                                    value = adjustmentValue.coerceIn(-100f, 100f),
                                    onValueChange = { value ->
                                        when (selectedAdjustment) {
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
                                    fontSize = VyroCutTypography.CaptionSp.sp
                                )
                            }
                        } else {
                            if (selectedClip == null) {
                                Text("Select a video clip to apply a tonal preset.", color = StudioMuted, fontSize = VyroCutTypography.CaptionSp.sp)
                            } else {
                                Text(
                                    "Preset applied to this clip • Fine-tune it in Adjust",
                                    color = StudioMuted,
                                    fontSize = VyroCutTypography.CaptionSp.sp,
                                )
                            }
                        }
                    }
                }
                if (activeTool == "Audio" && selectedClip != null) {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(VyroCutShapes.CompactPanelCornerDp.dp)).padding(horizontal = VyroCutAudioPanelMetrics.PanelHorizontalPaddingDp.dp, vertical = VyroCutAudioPanelMetrics.ClipVolumePanelVerticalPaddingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(VyroCutAudioPanelMetrics.ClipVolumeSectionGapDp.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("CLIP VOLUME", color = StudioText, fontSize = VyroCutTypography.CaptionSp.sp, fontWeight = FontWeight.Bold, letterSpacing = VyroCutAudioPanelMetrics.HeaderLetterSpacingSp.sp)
                            Spacer(Modifier.weight(1f))
                            Text("${clipAudioVolumePercent(clipVolumeDraft)}%", color = StudioAccent, fontSize = VyroCutTypography.LabelSp.sp)
                            TextButton(
                                onClick = {
                                    val nextVolume = if (clipVolumeDraft == 0f) 1f else 0f
                                    clipVolumeDraft = nextVolume
                                    commitSelectedClipAudioVolume(nextVolume)
                                },
                                contentPadding = PaddingValues(horizontal = VyroCutAudioPanelMetrics.MuteButtonHorizontalPaddingDp.dp, vertical = 0.dp),
                            ) {
                                Text(if (clipVolumeDraft == 0f) "Unmute" else "Mute", fontSize = VyroCutTypography.CaptionSp.sp)
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
                            Text("FADE IN", color = StudioText, fontSize = VyroCutTypography.CaptionSp.sp, fontWeight = FontWeight.Bold, letterSpacing = VyroCutAudioPanelMetrics.HeaderLetterSpacingSp.sp)
                            Spacer(Modifier.weight(1f))
                            Text(String.format("%.1fs", clipFadeInDraft / 1_000f), color = StudioAccent, fontSize = VyroCutTypography.LabelSp.sp)
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
                            Text("FADE OUT", color = StudioText, fontSize = VyroCutTypography.CaptionSp.sp, fontWeight = FontWeight.Bold, letterSpacing = VyroCutAudioPanelMetrics.HeaderLetterSpacingSp.sp)
                            Spacer(Modifier.weight(1f))
                            Text(String.format("%.1fs", clipFadeOutDraft / 1_000f), color = StudioAccent, fontSize = VyroCutTypography.LabelSp.sp)
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
                        Text("Non-destructive • Smooth fades apply to preview and MP4 export", color = StudioMuted, fontSize = VyroCutTypography.ToolLabelSp.sp)
                    }
                }
                if (activeTool == "Audio") {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(VyroCutShapes.CompactPanelCornerDp.dp)).padding(horizontal = VyroCutAudioPanelMetrics.PanelHorizontalPaddingDp.dp, vertical = VyroCutSpacing.SmallDp.dp),
                        verticalArrangement = Arrangement.spacedBy(VyroCutAudioPanelMetrics.LaneSectionGapDp.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AUDIO LANES", color = StudioText, fontSize = VyroCutTypography.CaptionSp.sp, fontWeight = FontWeight.Bold, letterSpacing = VyroCutAudioPanelMetrics.HeaderLetterSpacingSp.sp)
                            Spacer(Modifier.weight(1f))
                            Text("${audioTrackClips.size}/${MAX_AUDIO_TRACK_CLIPS} clips", color = StudioMuted, fontSize = VyroCutTypography.ToolLabelSp.sp)
                        }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(VyroCutAudioPanelMetrics.LaneChipGapDp.dp),
                        ) {
                            AudioTrackType.values().forEach { type ->
                                FilterChip(
                                    selected = selectedAudioTrackType == type,
                                    onClick = { selectedAudioTrackType = type },
                                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = VyroCutTypography.CaptionSp.sp) },
                                )
                            }
                        }
                        Button(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            enabled = clips.isNotEmpty() && !isReadingMedia && audioTrackClips.size < MAX_AUDIO_TRACK_CLIPS,
                            colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = VyroCutPalette.AccentInk),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, null)
                            Text("Import ${selectedAudioTrackType.name.lowercase()} at playhead")
                        }
                        val laneClips = audioTrackClips.filter { it.type == selectedAudioTrackType }.sortedBy { it.startMs }
                        if (laneClips.isEmpty()) {
                            Text("No ${selectedAudioTrackType.name.lowercase()} clips yet. Imported audio stays local to this project.", color = StudioMuted, fontSize = VyroCutTypography.ToolLabelSp.sp)
                        } else {
                            laneClips.forEach { trackClip ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(
                                            if (trackClip.id == selectedAudioTrackId) VyroCutPalette.SurfaceSelected else VyroCutPalette.SurfaceClip,
                                            RoundedCornerShape(VyroCutAudioPanelMetrics.ClipCardCornerDp.dp),
                                        )
                                        .clickable { selectedAudioTrackId = trackClip.id },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f).padding(horizontal = VyroCutSpacing.SmallDp.dp, vertical = VyroCutAudioPanelMetrics.ClipCardVerticalPaddingDp.dp)) {
                                        Text(trackClip.name, color = StudioText, fontSize = VyroCutTypography.CaptionSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val waveformKey = audioWaveformMemoryCacheKey(trackClip)
                                        val waveformAmplitudes = audioWaveforms[waveformKey]
                                        AudioWaveformStrip(
                                            amplitudes = waveformAmplitudes.orEmpty(),
                                            color = if (trackClip.id == selectedAudioTrackId) StudioAccent else VyroCutPalette.TextSubtle,
                                        )
                                        audioWaveformStatusLabel(
                                            audioWaveformLoadStates[waveformKey],
                                            waveformAmplitudes,
                                        )?.let { status ->
                                            Text(
                                                status,
                                                color = StudioMuted,
                                                fontSize = VyroCutTypography.BadgeSp.sp,
                                            )
                                        }
                                        Text(
                                            "${formatTime(trackClip.startMs)} – ${formatTime(trackClip.endMs)} • ${(trackClip.volume * 100f).toInt()}%",
                                            color = StudioMuted,
                                            fontSize = VyroCutTypography.BadgeSp.sp,
                                        )
                                    }
                                    IconButton(
                                        onClick = { changedAudioTracks(audioTrackClips.filterNot { it.id == trackClip.id }) },
                                        modifier = Modifier.size(VyroCutAudioPanelMetrics.DeleteButtonSizeDp.dp),
                                    ) {
                                        Icon(Icons.Default.Delete, "Remove audio clip", tint = VyroCutPalette.Danger, modifier = Modifier.size(VyroCutAudioPanelMetrics.DeleteIconSizeDp.dp))
                                    }
                                }
                            }
                        }
                        if (selectedAudioTrack != null && selectedAudioTrack.type == selectedAudioTrackType) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("TRACK VOLUME", color = StudioText, fontSize = VyroCutTypography.ToolLabelSp.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text("${(audioTrackVolumeDraft * 100f).toInt()}%", color = StudioAccent, fontSize = VyroCutTypography.CaptionSp.sp)
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
                                Text("FADE IN", color = StudioText, fontSize = VyroCutTypography.ToolLabelSp.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(String.format("%.1fs", audioTrackFadeInDraft / 1_000f), color = StudioAccent, fontSize = VyroCutTypography.CaptionSp.sp)
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
                                Text("FADE OUT", color = StudioText, fontSize = VyroCutTypography.ToolLabelSp.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(String.format("%.1fs", audioTrackFadeOutDraft / 1_000f), color = StudioAccent, fontSize = VyroCutTypography.CaptionSp.sp)
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
                                Text("TRIM MODE", color = StudioText, fontSize = VyroCutTypography.ToolLabelSp.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                FilterChip(
                                    selected = !rippleAudioTrimEnabled,
                                    onClick = { rippleAudioTrimEnabled = false },
                                    label = { Text("Normal", fontSize = VyroCutTypography.ToolLabelSp.sp) },
                                )
                                FilterChip(
                                    selected = rippleAudioTrimEnabled,
                                    onClick = { rippleAudioTrimEnabled = true },
                                    label = { Text("Ripple", fontSize = VyroCutTypography.ToolLabelSp.sp) },
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("SOURCE IN", color = StudioText, fontSize = VyroCutTypography.ToolLabelSp.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(formatTime(audioTrackSourceStartDraft.toLong()), color = StudioAccent, fontSize = VyroCutTypography.CaptionSp.sp)
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
                                    applySelectedAudioTrackTrim(audioTrackSourceStartDraft.toLong(), audioTrackSourceEndDraft.toLong())
                                },
                                valueRange = 0f..(sourceDurationMs - MIN_AUDIO_TRACK_CLIP_DURATION_MS).coerceAtLeast(1L).toFloat(),
                                enabled = sourceDurationMs > MIN_AUDIO_TRACK_CLIP_DURATION_MS,
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("SOURCE OUT", color = StudioText, fontSize = VyroCutTypography.ToolLabelSp.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(formatTime(audioTrackSourceEndDraft.toLong()), color = StudioAccent, fontSize = VyroCutTypography.CaptionSp.sp)
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
                                    applySelectedAudioTrackTrim(audioTrackSourceStartDraft.toLong(), audioTrackSourceEndDraft.toLong())
                                },
                                valueRange = minimumSourceEndMs.toFloat()..sourceDurationMs.toFloat().coerceAtLeast(minimumSourceEndMs.toFloat() + 1f),
                                enabled = sourceDurationMs > MIN_AUDIO_TRACK_CLIP_DURATION_MS,
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("TIMELINE START", color = StudioText, fontSize = VyroCutTypography.ToolLabelSp.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(formatTime(audioTrackStartDraft.toLong()), color = StudioAccent, fontSize = VyroCutTypography.CaptionSp.sp)
                            }
                            Slider(
                                value = audioTrackStartDraft.coerceIn(0f, maximumTimelineStartMs.toFloat()),
                                onValueChange = { audioTrackStartDraft = it },
                                onValueChangeFinished = { moveSelectedAudioTrack(audioTrackStartDraft.toLong()) },
                                valueRange = 0f..maximumTimelineStartMs.toFloat().coerceAtLeast(1f),
                                enabled = maximumTimelineStartMs > 0L,
                                colors = SliderDefaults.colors(thumbColor = StudioAccent, activeTrackColor = StudioAccent),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(VyroCutSpacing.SmallDp.dp)) {
                                TextButton(
                                    onClick = { moveSelectedAudioTrack(timelinePositionMs.toLong()) },
                                    contentPadding = PaddingValues(horizontal = VyroCutAudioPanelMetrics.ActionButtonHorizontalPaddingDp.dp, vertical = 0.dp),
                                ) { Text("Move to playhead", fontSize = VyroCutTypography.CaptionSp.sp) }
                                TextButton(
                                    onClick = { splitSelectedAudioTrackAtPlayhead() },
                                    enabled = timelinePositionMs.toLong() > selectedAudioTrack.startMs &&
                                        timelinePositionMs.toLong() < selectedAudioTrack.endMs &&
                                        audioTrackClips.size < MAX_AUDIO_TRACK_CLIPS,
                                    contentPadding = PaddingValues(horizontal = VyroCutAudioPanelMetrics.ActionButtonHorizontalPaddingDp.dp, vertical = 0.dp),
                                ) { Text("Split at playhead", fontSize = VyroCutTypography.CaptionSp.sp) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(VyroCutSpacing.SmallDp.dp)) {
                                TextButton(
                                    onClick = { duplicateSelectedAudioTrack() },
                                    enabled = audioTrackClips.size < MAX_AUDIO_TRACK_CLIPS,
                                    contentPadding = PaddingValues(horizontal = VyroCutAudioPanelMetrics.ActionButtonHorizontalPaddingDp.dp, vertical = 0.dp),
                                ) { Text("Duplicate", fontSize = VyroCutTypography.CaptionSp.sp) }
                                TextButton(
                                    onClick = { applySelectedAudioTrackTrim(0L, sourceDurationMs) },
                                    contentPadding = PaddingValues(horizontal = VyroCutAudioPanelMetrics.ActionButtonHorizontalPaddingDp.dp, vertical = 0.dp),
                                ) { Text("Restore full source", fontSize = VyroCutTypography.CaptionSp.sp) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(VyroCutSpacing.SmallDp.dp)) {
                                TextButton(
                                    onClick = { rippleDeleteSelectedAudioTrack() },
                                    contentPadding = PaddingValues(horizontal = VyroCutAudioPanelMetrics.ActionButtonHorizontalPaddingDp.dp, vertical = 0.dp),
                                ) {
                                    Text("Ripple delete", color = VyroCutPalette.Danger, fontSize = VyroCutTypography.CaptionSp.sp)
                                }
                            }
                            Text("Ripple delete closes a removed clip duration; ripple trim shifts later clips by the source-range duration change.", color = StudioMuted, fontSize = VyroCutTypography.ToolLabelSp.sp)
                            Text("Non-destructive source trims and lane moves • independent lanes mix in preview and MP4 export", color = StudioMuted, fontSize = VyroCutTypography.ToolLabelSp.sp)
                        }
                    }
                }
                if (activeTool == "Stickers") {
                    StickerOverlaysPanel(
                        selectedClip = selectedClip,
                        selectedStickerPosition = selectedStickerPosition,
                        selectedStickerLayerIndex = selectedStickerLayerIndex,
                        onPositionSelected = { selectedStickerPosition = it },
                        onAddSticker = { symbol, assetId ->
                            addSelectedClipSticker(symbol, assetId)
                        },
                        onRemoveSticker = { index -> removeSelectedClipSticker(index) },
                        onScaleChange = { index, scale ->
                            updateSelectedClipStickerScale(index, scale)
                        },
                        onRotationChange = { index, degrees ->
                            updateSelectedClipStickerRotation(index, degrees)
                        },
                        onTimingChange = { index, startMs, endMs ->
                            updateSelectedClipStickerTiming(index, startMs, endMs)
                        },
                    )
                }
                if (activeTool == "Text") {
                    Column(
                        Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(VyroCutShapes.CompactPanelCornerDp.dp)).padding(horizontal = VyroCutTextPanelMetrics.HorizontalPaddingDp.dp, vertical = VyroCutTextPanelMetrics.VerticalPaddingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(VyroCutTextPanelMetrics.SectionGapDp.dp),
                    ) {
                        Text("TEXT LAYERS", color = StudioText, fontSize = VyroCutTextPanelMetrics.HeaderFontSizeSp.sp, fontWeight = FontWeight.Bold, letterSpacing = VyroCutTextPanelMetrics.HeaderLetterSpacingSp.sp)
                        if (selectedClip == null) {
                            Text("Select a video clip before adding text.", color = StudioMuted, fontSize = VyroCutTextPanelMetrics.HelperFontSizeSp.sp)
                        } else {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(VyroCutTextPanelMetrics.ChipGapDp.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                selectedClip.allTextOverlays.forEachIndexed { index, _ ->
                                    FilterChip(
                                        selected = selectedTextLayerIndex == index,
                                        onClick = { selectedTextLayerIndex = index },
                                        label = { Text("Layer " + (index + 1), fontSize = VyroCutTextPanelMetrics.ChipLabelFontSizeSp.sp) },
                                    )
                                }
                                if (selectedClip.allTextOverlays.size < MAX_CLIP_TEXT_OVERLAYS) {
                                    FilterChip(
                                        selected = selectedTextLayerIndex == selectedClip.allTextOverlays.size,
                                        onClick = { selectedTextLayerIndex = selectedClip.allTextOverlays.size },
                                        label = { Text("+ Add layer", fontSize = VyroCutTextPanelMetrics.ChipLabelFontSizeSp.sp) },
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
                                Text("SIZE", color = StudioMuted, fontSize = VyroCutTextPanelMetrics.CaptionFontSizeSp.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(VyroCutSpacing.SmallDp.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(VyroCutTextPanelMetrics.ChipGapDp.dp)) {
                                    listOf("Small" to 0.8f, "Medium" to 1f, "Large" to 1.25f).forEach { (label, scale) ->
                                        FilterChip(
                                            selected = clipTextScaleDraft == scale,
                                            onClick = { clipTextScaleDraft = scale },
                                            label = { Text(label, fontSize = VyroCutTextPanelMetrics.ChipLabelFontSizeSp.sp) },
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("POSITION", color = StudioMuted, fontSize = VyroCutTextPanelMetrics.CaptionFontSizeSp.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(VyroCutSpacing.SmallDp.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(VyroCutTextPanelMetrics.ChipGapDp.dp)) {
                                    ClipTextPosition.values().forEach { position ->
                                        FilterChip(
                                            selected = clipTextPositionDraft == position,
                                            onClick = { clipTextPositionDraft = position },
                                            label = { Text(position.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = VyroCutTextPanelMetrics.ChipLabelFontSizeSp.sp) },
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
                                    fontSize = VyroCutTextPanelMetrics.CaptionFontSizeSp.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("IN", color = StudioMuted, fontSize = VyroCutTextPanelMetrics.CaptionFontSizeSp.sp, fontWeight = FontWeight.Bold)
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
                                    Text("OUT", color = StudioMuted, fontSize = VyroCutTextPanelMetrics.CaptionFontSizeSp.sp, fontWeight = FontWeight.Bold)
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
                                    fontSize = VyroCutTextPanelMetrics.CaptionFontSizeSp.sp,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(VyroCutSpacing.SmallDp.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = VyroCutPalette.AccentInk),
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
                                fontSize = VyroCutTextPanelMetrics.CaptionFontSizeSp.sp,
                            )
                        }
                    }
                }
                val exportSettingsForDisplay = projectCanvasSettingsFor(canvasAspectRatio, canvasBackground)
                val displayedExportSize = exportCanvasSizeForPreset(
                    exportSettingsForDisplay.aspectRatioPreset,
                    exportResolutionPreset,
                )
                val displayedVideoBitrateTargetBps = exportTargetVideoBitrateBps(
                    exportVideoBitratePreset,
                    displayedExportSize,
                    exportFrameRatePreset,
                )
                val displayedVideoBitrateSummary = displayedVideoBitrateTargetBps?.let { targetBps ->
                    val tenthsOfMbps = (targetBps.toLong() + 50_000L) / 100_000L
                    "Requested target: ${tenthsOfMbps / 10}.${tenthsOfMbps % 10} Mbps • device encoder may adjust to a supported value."
                } ?: "Auto keeps Media3's device-selected video bitrate."
                val displayedAudioBitrateTargetBps = exportRequestedAudioBitrateBps(exportAudioBitratePreset)
                val displayedAutomaticVideoBitrateBps = exportTargetVideoBitrateBps(
                    ExportVideoBitratePreset.BALANCED,
                    displayedExportSize,
                    exportFrameRatePreset,
                )
                val displayedExportSizeEstimate = estimateExportFileSize(
                    durationMs = calculateTimelineDurationMs(clips.map { it.durationMs }),
                    requestedVideoBitrateBps = displayedVideoBitrateTargetBps,
                    requestedAudioBitrateBps = displayedAudioBitrateTargetBps,
                    automaticVideoBitrateBps = displayedAutomaticVideoBitrateBps,
                    automaticAudioBitrateBps = DEFAULT_AUTOMATIC_AUDIO_BITRATE_ESTIMATE_BPS,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VyroCutExportPanelMetrics.SettingsRowGapDp.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(VyroCutExportPanelMetrics.SizeSummaryGapDp.dp),
                    ) {
                        Text(
                            "EXPORT SIZE",
                            color = StudioMuted,
                            fontSize = VyroCutTypography.ToolLabelSp.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${displayedExportSize.widthPx} × ${displayedExportSize.heightPx} • ${exportResolutionPreset.label}",
                            color = StudioText,
                            fontSize = VyroCutTypography.CaptionSp.sp,
                        )
                    }
                    ExportResolutionPreset.values().forEach { preset ->
                        FilterChip(
                            selected = exportResolutionPreset == preset,
                            onClick = {
                                exportResolutionPreset = preset
                                preferences.edit()
                                    .putString("project_${activeProjectId}_export_resolution", preset.id)
                                    .apply()
                            },
                            enabled = !isExporting && !isReadingMedia,
                            label = { Text(preset.label, fontSize = VyroCutTypography.CaptionSp.sp) },
                        )
                    }
                }
                Text(
                    "Resolution changes the MP4 output size without changing your canvas ratio or preview.",
                    color = StudioMuted,
                    fontSize = VyroCutTypography.ToolLabelSp.sp,
                )
                Text(
                    "TARGET FPS",
                    color = StudioMuted,
                    fontSize = VyroCutTypography.ToolLabelSp.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(VyroCutExportPanelMetrics.PresetChipGapDp.dp),
                ) {
                    ExportFrameRatePreset.values().forEach { preset ->
                        FilterChip(
                            selected = exportFrameRatePreset == preset,
                            onClick = {
                                exportFrameRatePreset = preset
                                preferences.edit()
                                    .putString("project_${activeProjectId}_export_frame_rate", preset.id)
                                    .apply()
                            },
                            enabled = !isExporting && !isReadingMedia,
                            label = { Text(preset.label, fontSize = VyroCutTypography.CaptionSp.sp) },
                        )
                    }
                }
                Text(
                    "Media3 targets the selected average frame rate by dropping frames using source timestamps; lower-rate clips are not interpolated.",
                    color = StudioMuted,
                    fontSize = VyroCutTypography.ToolLabelSp.sp,
                )
                Text(
                    "VIDEO BITRATE",
                    color = StudioMuted,
                    fontSize = VyroCutTypography.ToolLabelSp.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(VyroCutExportPanelMetrics.PresetChipGapDp.dp),
                ) {
                    ExportVideoBitratePreset.values().forEach { preset ->
                        FilterChip(
                            selected = exportVideoBitratePreset == preset,
                            onClick = {
                                exportVideoBitratePreset = preset
                                preferences.edit()
                                    .putString("project_${activeProjectId}_export_video_bitrate", preset.id)
                                    .apply()
                            },
                            enabled = !isExporting && !isReadingMedia,
                            label = { Text(preset.label, fontSize = VyroCutTypography.CaptionSp.sp) },
                        )
                    }
                }
                Text(
                    displayedVideoBitrateSummary,
                    color = StudioMuted,
                    fontSize = VyroCutTypography.ToolLabelSp.sp,
                )
                Text(
                    "AUDIO BITRATE",
                    color = StudioMuted,
                    fontSize = VyroCutTypography.ToolLabelSp.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(VyroCutExportPanelMetrics.PresetChipGapDp.dp),
                ) {
                    ExportAudioBitratePreset.values().forEach { preset ->
                        FilterChip(
                            selected = exportAudioBitratePreset == preset,
                            onClick = {
                                exportAudioBitratePreset = preset
                                preferences.edit()
                                    .putString("project_${activeProjectId}_export_audio_bitrate", preset.id)
                                    .apply()
                            },
                            enabled = !isExporting && !isReadingMedia,
                            label = { Text(preset.label, fontSize = VyroCutTypography.CaptionSp.sp) },
                        )
                    }
                }
                Text(
                    exportAudioBitrateSummary(exportAudioBitratePreset),
                    color = StudioMuted,
                    fontSize = VyroCutTypography.ToolLabelSp.sp,
                )
                displayedExportSizeEstimate?.let { estimate ->
                    Text(
                        "Estimated MP4 size: ${formatExportSizeMiB(estimate.estimatedBytes)}",
                        color = StudioText,
                        fontSize = VyroCutTypography.CaptionSp.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val estimateAssumptions = buildList {
                        if (estimate.usesAutomaticVideoBitrate) add("Auto video assumes Balanced")
                        if (estimate.usesAutomaticAudioBitrate) add("Auto audio assumes 128 kbps")
                    }
                    Text(
                        (estimateAssumptions + "Approximate; content and device encoders change actual size.")
                            .joinToString(" • "),
                        color = StudioMuted,
                        fontSize = VyroCutTypography.ToolLabelSp.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VyroCutExportPanelMetrics.SettingsRowGapDp.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (clips.isNotEmpty() && !isExporting && !isReadingMedia) {
                                val exportSession = java.util.UUID.randomUUID().toString()
                                val exportCancellationToken = ExportCancellationToken()
                                val exportClips = clips.toList()
                                val exportAudioTracks = audioTrackClips.toList()
                                val exportCanvasSettings = projectCanvasSettingsFor(canvasAspectRatio, canvasBackground)
                                val exportResolution = exportResolutionPreset
                                val exportFrameRate = exportFrameRatePreset
                                val exportVideoBitrate = exportVideoBitratePreset
                                val exportAudioBitrate = exportAudioBitratePreset
                                val exportFrameDropTargetFps = exportFrameDropTargetRate(exportFrameRate)
                                val exportCanvasSize = exportCanvasSizeForPreset(
                                    exportCanvasSettings.aspectRatioPreset,
                                    exportResolution,
                                )
                                val exportVideoBitrateTargetBps = exportTargetVideoBitrateBps(
                                    exportVideoBitrate,
                                    exportCanvasSize,
                                    exportFrameRate,
                                )
                                val exportAudioBitrateTargetBps = exportRequestedAudioBitrateBps(
                                    exportAudioBitrate,
                                )
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
                                activeExportCancellationToken = exportCancellationToken
                                exportStartedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
                                exportElapsedMs = 0L
                                isExporting = true
                                exportUiStage = ExportUiStage.PREPARING
                                exportProgressPercent = null
                                completedExportSummary = null
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
                                    if (!isExportSessionCurrent(exportSessionId, exportSession)) return@exportPreparation
                                    if (preflightError != null) {
                                        exportUiStage = ExportUiStage.FAILED

                                        isExporting = false
                                        activeExportCancellationToken = null
                                        exportSessionId = null
                                        exportMessage = "Export stopped: $preflightError"
                                        return@exportPreparation
                                    }
                                    if (!validateAudioTrackClips(exportAudioTracks, calculateTimelineDurationMs(exportClips.map { it.durationMs }))) {
                                        exportUiStage = ExportUiStage.FAILED

                                        isExporting = false
                                        activeExportCancellationToken = null
                                        exportSessionId = null
                                        exportMessage = "Export stopped: audio lanes contain an invalid or overlapping segment."
                                        return@exportPreparation
                                    }
                                    val audioSourceDurations = withContext(Dispatchers.IO) {
                                        distinctExportSourceUris(exportAudioTracks.map { it.uri })
                                            .associateWith { rawUri ->
                                                readAudioDurationMs(context, Uri.parse(rawUri))
                                            }
                                    }
                                    if (!isExportSessionCurrent(exportSessionId, exportSession)) return@exportPreparation
                                    val invalidAudioSource = exportAudioTracks.firstOrNull { clip ->
                                        val sourceDuration = audioSourceDurations[clip.uri]
                                        sourceDuration == null || clip.sourceStartMs < 0L ||
                                            clip.sourceEndMs <= clip.sourceStartMs ||
                                            clip.sourceEndMs > sourceDuration
                                    }
                                    if (invalidAudioSource != null) {
                                        exportUiStage = ExportUiStage.FAILED

                                        isExporting = false
                                        activeExportCancellationToken = null
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

                                    val outputDirectory = resolveExportTemporaryDirectory(
                                        externalMoviesDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                                        internalFilesDirectory = context.filesDir,
                                    )
                                    if (!outputDirectory.isDirectory && !outputDirectory.mkdirs()) {
                                        exportUiStage = ExportUiStage.FAILED

                                        isExporting = false
                                        activeExportCancellationToken = null
                                        exportSessionId = null
                                        exportMessage = "Could not create the temporary export directory"
                                        return@exportPreparation
                                    }
                                    val expectedDurationMs = calculateTimelineDurationMs(exportClips.map { it.durationMs })
                                    val exportAudioSegments = AudioTrackType.values().associateWith { type ->
                                        buildAudioTrackTimelineSegments(exportAudioTracks, type, expectedDurationMs)
                                    }
                                    val maximumExportSilenceMs = exportAudioSegments.values.flatten()
                                        .filter { it.clip == null }
                                        .maxOfOrNull { it.durationMs } ?: 0L
                                    val silenceCacheRequiredBytes = when {
                                        maximumExportSilenceMs <= 0L -> 0L
                                        hasReusableSilentAudioWav(context.cacheDir, maximumExportSilenceMs) -> 0L
                                        else -> estimateSilentAudioWavFileBytes(maximumExportSilenceMs)
                                    }
                                    if (silenceCacheRequiredBytes == null) {
                                        exportUiStage = ExportUiStage.FAILED
                                        isExporting = false
                                        activeExportCancellationToken = null
                                        exportSessionId = null
                                        exportMessage = "Audio-track gaps exceed the supported temporary WAV cache size."
                                        return@exportPreparation
                                    }
                                    val automaticVideoBitrateBps = exportTargetVideoBitrateBps(
                                        ExportVideoBitratePreset.BALANCED,
                                        exportCanvasSize,
                                        exportFrameRate,
                                    )
                                    val exportSizeEstimate = estimateExportFileSize(
                                        durationMs = expectedDurationMs,
                                        requestedVideoBitrateBps = exportVideoBitrateTargetBps,
                                        requestedAudioBitrateBps = exportAudioBitrateTargetBps,
                                        automaticVideoBitrateBps = automaticVideoBitrateBps,
                                        automaticAudioBitrateBps = DEFAULT_AUTOMATIC_AUDIO_BITRATE_ESTIMATE_BPS,
                                    )
                                    val storageAvailability = withContext(Dispatchers.IO) {
                                        inspectExportStorageAvailability(outputDirectory, context.cacheDir)
                                    }
                                    if (!isExportSessionCurrent(exportSessionId, exportSession)) return@exportPreparation
                                    val storageCheck = exportSizeEstimate?.let { estimate ->
                                        evaluateExportStoragePreflight(
                                            estimate,
                                            storageAvailability,
                                            silenceCacheRequiredBytes = silenceCacheRequiredBytes,
                                        )
                                    }
                                    if (storageCheck?.status == ExportStoragePreflightStatus.INSUFFICIENT) {
                                        exportUiStage = ExportUiStage.FAILED
                                        isExporting = false
                                        activeExportCancellationToken = null
                                        exportSessionId = null
                                        exportMessage = "Export stopped: ${describeExportStoragePreflight(storageCheck)}"
                                        return@exportPreparation
                                    }
                                    exportMessage = if (storageCheck == null ||
                                        storageCheck.status == ExportStoragePreflightStatus.UNKNOWN
                                    ) {
                                        "Free space could not be verified; continuing with an approximate size estimate."
                                    } else {
                                        "Storage preflight passed; preparing MP4…"
                                    }
                                    val outputFile = File(outputDirectory, "VyroCut_${System.currentTimeMillis()}.mp4")
                                    val sourceUris = exportClips.map { it.uri } + exportAudioTracks.map { it.uri }
                                    val exportSilenceFile = if (maximumExportSilenceMs > 0L) {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                createSilentAudioWav(context.cacheDir, maximumExportSilenceMs)
                                            }
                                        }.getOrElse { error ->
                                            if (isExportSessionCurrent(exportSessionId, exportSession)) {
                                                exportUiStage = ExportUiStage.FAILED
                                                isExporting = false
                                                activeExportCancellationToken = null
                                                exportSessionId = null
                                                exportMessage = "Could not prepare audio-track gaps: ${error.message}"
                                            }
                                            outputFile.delete()
                                            return@exportPreparation
                                        }
                                    } else {
                                        null
                                    }
                                    if (!isExportSessionCurrent(exportSessionId, exportSession)) {
                                        outputFile.delete()
                                        return@exportPreparation
                                    }
                                    val composition = try {
                                        val exportClipDurationsMs = exportClips.map { it.durationMs }
                                        val editedItems = exportClips.mapIndexed { clipIndex, clip ->
                                            val clipPresentationStartMs = calculateTimelinePositionMs(
                                                exportClipDurationsMs,
                                                clipIndex,
                                                0L,
                                            )
                                            val source = MediaItem.Builder()
                                                .setUri(Uri.parse(clip.uri))
                                                .setClippingConfiguration(
                                                    MediaItem.ClippingConfiguration.Builder()
                                                        .setStartPositionMs(clip.sourceStartMs)
                                                        .setEndPositionMs(clip.sourceEndMs)
                                                        .build()
                                                ).build()
                                            val videoEffects = mutableListOf<androidx.media3.common.Effect>()
                                            // FrameDropEffect selects frames by their source presentation
                                            // timestamps, so VFR sources are capped without changing the
                                            // editor's source timestamp index or the shared audio clock.
                                            exportFrameDropTargetFps?.let { targetFps ->
                                                videoEffects += FrameDropEffect.createDefaultFrameDropEffect(targetFps)
                                            }
                                            videoEffects += createClipVisualEffects(clip.visualAdjustments)
                                            videoEffects += createClipTimedVignetteEffects(
                                                clip.timedEffectSegments,
                                                clip.durationMs,
                                                clipPresentationStartMs,
                                            )
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
                                            videoEffects += createClipStickerOverlayEffects(clip.allStickerOverlays, context)
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
                                        exportUiStage = ExportUiStage.FAILED

                                        isExporting = false
                                        activeExportCancellationToken = null
                                        exportSessionId = null
                                        exportMessage = "Export failed: ${diagnoseExportFailure(ExportFailureStage.PREPARATION, error).userMessage}"
                                        return@exportPreparation
                                    }
                                    val transformer = try {
                                        val transformerBuilder = Transformer.Builder(context)
                                        if (exportVideoBitrateTargetBps != null ||
                                            exportAudioBitrateTargetBps != null
                                        ) {
                                            val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
                                            exportVideoBitrateTargetBps?.let { targetBitrateBps ->
                                                encoderFactoryBuilder.setRequestedVideoEncoderSettings(
                                                    VideoEncoderSettings.Builder()
                                                        .setBitrate(targetBitrateBps)
                                                        .build(),
                                                )
                                            }
                                            exportAudioBitrateTargetBps?.let { targetBitrateBps ->
                                                encoderFactoryBuilder.setRequestedAudioEncoderSettings(
                                                    AudioEncoderSettings.Builder()
                                                        .setBitrate(targetBitrateBps)
                                                        .build(),
                                                )
                                            }
                                            // Media3 treats both bitrates as best-effort encoder
                                            // requests; keep its supported-format fallback enabled.
                                            transformerBuilder.setEncoderFactory(
                                                encoderFactoryBuilder.setEnableFallback(true).build(),
                                            )
                                        }
                                        transformerBuilder
                                            .addListener(object : Transformer.Listener {
                                                override fun onCompleted(composition: Composition, result: ExportResult) {
                                                    if (!isExportSessionCurrent(exportSessionId, exportSession)) {
                                                        outputFile.delete()
                                                        return
                                                    }
                                                    exportUiStage = ExportUiStage.VERIFYING
                                                    exportProgressPercent = null
                                                    exportMessage = "Verifying MP4 and saved copy…"
                                                    exportScope.launch exportPublishing@ {
                                                        val publishResult = runCatching {
                                                            withContext(Dispatchers.IO) {
                                                                val verifiedMedia = verifyExportedMp4(
                                                                    context = context,
                                                                    file = outputFile,
                                                                    sourceUris = sourceUris,
                                                                    expectedDurationMs = expectedDurationMs,
                                                                    clipDurationsMs = exportRequirements.map { it.sourceEndMs - it.sourceStartMs },
                                                                    expectedCanvasSize = exportCanvasSize,
                                                                    canvasVisualClips = canvasVisualClips,
                                                                    expectedBackgroundArgb = expectedBackgroundForVerification,
                                                                    cancellationToken = exportCancellationToken,
                                                                )
                                                                exportCancellationToken.throwIfCancelled()
                                                                val uri = publishExportToMovies(
                                                                    context,
                                                                    outputFile,
                                                                    verifiedMedia.fingerprint,
                                                                    cancellationToken = exportCancellationToken,
                                                                )
                                                                uri to verifiedMedia
                                                            }
                                                        }
                                                        if (!isExportSessionCurrent(exportSessionId, exportSession)) {
                                                            publishResult.getOrNull()?.first?.let { uri ->
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
                                                        activeExportCancellationToken = null
                                                        exportSessionId = null
                                                        publishResult.onSuccess { (uri, verifiedMedia) ->
                                                            exportedUri = uri
                                                            val measuredOutputSummary = formatVerifiedExportOutputSummary(
                                                                verifiedMedia.trackSummary,
                                                                verifiedMedia.fingerprint.byteCount,
                                                            )
                                                            completedExportSummary = buildExportCompletionSummary(
                                                                verifiedOutput = measuredOutputSummary,
                                                                exportSettings = listOfNotNull(
                                                                    exportResolution.label,
                                                                    exportFrameRate.summaryLabel,
                                                                    exportVideoBitrate.summaryLabel,
                                                                    exportAudioBitrate.summaryLabel,
                                                                    "MP4",
                                                                ),
                                                            )
                                                            exportUiStage = ExportUiStage.COMPLETE
                                                            exportMessage = "Export complete • Video frames and saved MP4 copy verified; " +
                                                                (if (expectedBackgroundForVerification != null) {
                                                                    "canvas background pixels checked where bars exist; "
                                                                } else {
                                                                    ""
                                                                }) +
                                                                "source audio decoded when present • Saved to Movies/VyroCut"
                                                        }.onFailure { error ->
                                                            exportedUri = null
                                                            completedExportSummary = null
                                                            exportUiStage = ExportUiStage.FAILED
                                                            exportMessage = "Export failed: ${diagnoseExportFailure(ExportFailureStage.VERIFYING_OR_PUBLISHING, error).userMessage}"
                                                        }
                                                        outputFile.delete()
                                                    }
                                                }
                                                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                                                    outputFile.delete()
                                                    if (isExportSessionCurrent(exportSessionId, exportSession)) {
                                                        exportUiStage = ExportUiStage.FAILED
                                                        isExporting = false
                                                        activeTransformer = null
                                                        activeExportFile = null
                                                        activeExportCancellationToken = null
                                                        exportSessionId = null
                                                        exportMessage = "Export failed: ${diagnoseExportFailure(ExportFailureStage.RENDERING, exception).userMessage}"
                                                    }
                                                }
                                            }).build()
                                    } catch (error: Exception) {
                                        outputFile.delete()
                                        exportUiStage = ExportUiStage.FAILED
                                        isExporting = false
                                        activeExportCancellationToken = null
                                        exportSessionId = null
                                        exportMessage = "Export failed: ${diagnoseExportFailure(ExportFailureStage.PREPARATION, error).userMessage}"
                                        return@exportPreparation
                                    }
                                    if (!isExportSessionCurrent(exportSessionId, exportSession)) {
                                        outputFile.delete()
                                        return@exportPreparation
                                    }
                                    activeTransformer = transformer
                                    activeExportFile = outputFile
                                    exportUiStage = ExportUiStage.RENDERING
                                    exportMessage = "Rendering MP4…"
                                    runCatching { transformer.start(composition, outputFile.absolutePath) }
                                        .onSuccess {
                                            exportScope.launch exportProgress@ {
                                                val progressHolder = ProgressHolder()
                                                while (
                                                    isExportSessionCurrent(exportSessionId, exportSession) &&
                                                    isExporting &&
                                                    exportUiStage == ExportUiStage.RENDERING
                                                ) {
                                                    val progressState = runCatching {
                                                        transformer.getProgress(progressHolder)
                                                    }.getOrNull() ?: break
                                                    when (progressState) {
                                                        Transformer.PROGRESS_STATE_AVAILABLE -> {
                                                            exportProgressPercent = nextExportProgressPercent(
                                                                exportProgressPercent,
                                                                progressHolder.progress,
                                                            )
                                                        }
                                                        Transformer.PROGRESS_STATE_UNAVAILABLE -> break
                                                    }
                                                    delay(250L)
                                                }
                                            }
                                        }
                                        .onFailure { error ->
                                            if (isExportSessionCurrent(exportSessionId, exportSession)) {
                                                exportUiStage = ExportUiStage.FAILED
                                                isExporting = false
                                                activeTransformer = null
                                                activeExportFile = null
                                                activeExportCancellationToken = null
                                                exportSessionId = null
                                                exportMessage = "Export failed: ${diagnoseExportFailure(ExportFailureStage.RENDERING, error).userMessage}"
                                            }
                                            outputFile.delete()
                                        }
                                }
                            }
                        },
                        enabled = clips.isNotEmpty() && !isExporting && !isReadingMedia,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = VyroCutPalette.AccentInk)
                    ) {
                        Text(exportActionButtonLabel(exportUiStage, isExporting), fontWeight = FontWeight.Bold)
                    }
                    if (isExporting) {
                        OutlinedButton(onClick = {
                            activeExportCancellationToken?.cancel()
                            activeExportCancellationToken = null
                            exportSessionId = null
                            exportUiStage = ExportUiStage.CANCELLED
                            exportProgressPercent = null
                            activeTransformer?.cancel()
                            activeTransformer = null
                            activeExportFile?.delete()
                            activeExportFile = null
                            isExporting = false
                            completedExportSummary = null
                            exportMessage = "Export cancelled"
                        }) {
                            Text("Cancel")
                        }
                    }
                    exportedUri?.let { uri ->
                        OutlinedButton(onClick = {
                            exportMessage = null
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(
                                    context.contentResolver,
                                    "VyroCut exported video",
                                    uri,
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val chooser = Intent.createChooser(share, "Share exported video").apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(chooser)
                            } catch (_: ActivityNotFoundException) {
                                exportMessage = exportShareFailureMessage(
                                    ExportShareFailureReason.NO_COMPATIBLE_APP,
                                )
                            } catch (_: SecurityException) {
                                exportMessage = exportShareFailureMessage(
                                    ExportShareFailureReason.URI_ACCESS_DENIED,
                                )
                            }
                        }) { Text("Share MP4") }
                    }
                }
                if (shouldShowExportStatusCard(exportUiStage, isExporting)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioPanel, RoundedCornerShape(VyroCutExportPanelMetrics.CardCornerDp.dp))
                            .padding(VyroCutExportPanelMetrics.CardPaddingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(VyroCutExportPanelMetrics.ProgressCardSectionGapDp.dp),
                    ) {
                        val progressPresentation = exportProgressPresentation(
                            stage = exportUiStage,
                            reportedPercent = exportProgressPercent,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                exportUiStage.label,
                                color = StudioText,
                                fontSize = VyroCutTypography.LabelSp.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                progressPresentation.percent?.let { "$it%" } ?: progressPresentation.progressLabel,
                                color = StudioAccent,
                                fontSize = VyroCutTypography.LabelSp.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (isExporting) {
                            if (progressPresentation.isIndeterminate) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(
                                    progress = { (progressPresentation.percent ?: 0) / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Text(
                            progressPresentation.detailText,
                            color = StudioMuted,
                            fontSize = VyroCutTypography.ToolLabelSp.sp,
                        )
                        if (exportStartedAtElapsedRealtimeMs > 0L) {
                            Text(
                                "Elapsed ${formatExportElapsedTime(exportElapsedMs)}",
                                color = StudioMuted,
                                fontSize = VyroCutTypography.CaptionSp.sp,
                            )
                        }
                    }
                }
                completedExportSummary?.let { summary ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioPanel, RoundedCornerShape(VyroCutExportPanelMetrics.CardCornerDp.dp))
                            .padding(VyroCutExportPanelMetrics.CardPaddingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(VyroCutExportPanelMetrics.CompletionCardSectionGapDp.dp),
                    ) {
                        Text(
                            "EXPORT READY",
                            color = StudioAccent,
                            fontSize = VyroCutTypography.CaptionSp.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        summary.verifiedOutput?.let { output ->
                            Text(
                                "VERIFIED OUTPUT",
                                color = StudioMuted,
                                fontSize = VyroCutTypography.CaptionSp.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                output,
                                color = StudioText,
                                fontSize = VyroCutTypography.BodySp.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (summary.exportSettings.isNotEmpty()) {
                            Text(
                                "EXPORT SETTINGS",
                                color = StudioMuted,
                                fontSize = VyroCutTypography.CaptionSp.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                summary.exportSettings.joinToString(" • "),
                                color = StudioText,
                                fontSize = VyroCutTypography.CaptionSp.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            "Saved to Movies/VyroCut. Share MP4 opens Android's app chooser for this saved copy.",
                            color = StudioMuted,
                            fontSize = VyroCutTypography.CaptionSp.sp,
                        )
                        if (exportStartedAtElapsedRealtimeMs > 0L) {
                            Text(
                                "Elapsed ${formatExportElapsedTime(exportElapsedMs)}",
                                color = StudioMuted,
                                fontSize = VyroCutTypography.CaptionSp.sp,
                            )
                        }
                    }
                }
                exportMessage?.let { Text(it, color = StudioAccent, fontSize = VyroCutTypography.LabelSp.sp) }
                Text("Offline editing • Project autosaves on this device", color = StudioMuted, fontSize = VyroCutTypography.CaptionSp.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                if (showProjects) {
                    AlertDialog(
                        onDismissRequest = { showProjects = false },
                        title = { Text("Your projects") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(VyroCutSpacing.SmallDp.dp)) {
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
                                        }) { Text("Rename", fontSize = VyroCutTypography.LabelSp.sp) }
                                        TextButton(
                                            onClick = { projectToDelete = project },
                                            enabled = projects.size > 1
                                        ) { Text("Delete", color = if (projects.size > 1) VyroCutPalette.Danger else StudioMuted, fontSize = VyroCutTypography.LabelSp.sp) }
                                    }
                                }
                                HorizontalDivider()
                                Row(horizontalArrangement = Arrangement.spacedBy(VyroCutSpacing.SmallDp.dp)) {
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
                                    "Backups save your edit list, media references, canvas settings and preview speed. Keep the original video and audio files available; media files are not copied into the backup.",
                                    color = StudioMuted,
                                    fontSize = VyroCutTypography.CaptionSp.sp
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
                                        exportMessage = "Recovering local checkpoint…"
                                        exportScope.launch {
                                            val projectId = snapshot.projectId
                                            val projectWasActiveAtClick = projectId == activeProjectId
                                            val currentAudioTracksAtClick = if (projectWasActiveAtClick) {
                                                audioTrackClips
                                            } else {
                                                null
                                            }
                                            val name = projects.firstOrNull { it.id == projectId }?.name
                                                ?: snapshot.projectName
                                            val restoredDurationMs =
                                                calculateTimelineDurationMs(restored.map { it.durationMs })
                                            val restoredAudioTracks = withContext(Dispatchers.IO) {
                                                val mirroredAudioJournal = ProjectRecoveryFileStore.read(
                                                    recoveryDirectory,
                                                    projectId,
                                                    ProjectRecoveryFileKind.AUDIO,
                                                )
                                                val currentAudioTracks = currentAudioTracksAtClick
                                                    ?: loadProjectAudioTracks(
                                                        preferences, projectId, Long.MAX_VALUE, mirroredAudioJournal,
                                                    )
                                                val matchingAudioJournal = matchingAudioRecoveryJournal(
                                                    projectId = projectId,
                                                    savedAtMs = snapshot.savedAtMs,
                                                    candidates = listOf(
                                                        preferences.getString("project_${snapshot.projectId}_audio_tracks_recovery", null),
                                                        mirroredAudioJournal,
                                                    ),
                                                )
                                                audioTracksForProjectRecovery(
                                                    projectSnapshot = snapshot,
                                                    rawAudioRecoveryJournal = matchingAudioJournal,
                                                    currentAudioTracks = currentAudioTracks,
                                                    projectDurationMs = restoredDurationMs,
                                                )
                                            }
                                            val recoveryBundle = persistProjectState(
                                                preferences,
                                                projectId,
                                                name,
                                                restored,
                                                restoredAudioTracks,
                                                previousSavedAtMsOverride = snapshot.savedAtMs,
                                            )
                                            mirrorRecoveryJournals(snapshot.projectId, recoveryBundle)
                                            if (snapshot.projectId == activeProjectId) {
                                                clips = restored
                                                audioTrackClips = restoredAudioTracks
                                                selectedAudioTrackId = selectedAudioTrackId?.takeIf { id -> audioTrackClips.any { it.id == id } }
                                                selectedIndex = 0
                                                timelinePositionMs = 0f
                                            }
                                            if (recoveryCandidate?.checksum == snapshot.checksum) {
                                                recoveryCandidate = null
                                            }
                                            exportMessage = "Recovered ${restored.size} clips from the local checkpoint"
                                        }
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
                                val rebasedStickerOverlays = rebaseClipStickerOverlaysForTrim(
                                    overlays = trimTargetClip.allStickerOverlays,
                                    oldSourceStartMs = trimTargetClip.sourceStartMs,
                                    oldSourceEndMs = trimTargetClip.sourceEndMs,
                                    newSourceStartMs = range.startMs,
                                    newSourceEndMs = range.endMs,
                                )
                                val rebasedTimedEffectSegments = rebaseClipTimedEffectSegmentsForTrim(
                                    segments = trimTargetClip.timedEffectSegments,
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
                                        stickerOverlays = rebasedStickerOverlays,
                                        timedEffectSegments = rebasedTimedEffectSegments,
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
                                Text("Delete", color = VyroCutPalette.Danger)
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
        modifier = modifier.background(
            VyroCutPalette.SurfaceTrack,
            RoundedCornerShape(VyroCutTimelineVisualMetrics.VideoThumbnailCornerRadiusDp.dp),
        ),
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
            decodeAudioWaveform(
                context,
                clip.uri,
                clip.sourceStartMs,
                clip.sourceEndMs,
                bucketCount = 48,
                maxRangeMs = Long.MAX_VALUE / 1_000L,
            ).orEmpty()
        }
    }
    Canvas(modifier = modifier) {
        if (bars.isEmpty()) {
            drawLine(
                color = StudioMuted.copy(alpha = VyroCutWaveformMetrics.ClipBaselineAlpha),
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                strokeWidth = VyroCutWaveformMetrics.ClipBaselineStrokeWidthDp.dp.toPx()
            )
        } else {
            val gap = VyroCutWaveformMetrics.ClipBarGapDp.dp.toPx()
            val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(VyroCutWaveformMetrics.ClipBarMinimumWidthDp.dp.toPx())
            bars.forEachIndexed { index, amplitude ->
                val barHeight = (amplitude.coerceIn(VyroCutWaveformMetrics.ClipAmplitudeFloor, 1f) * size.height).coerceAtLeast(VyroCutWaveformMetrics.ClipBarMinimumHeightDp.dp.toPx())
                val x = index * (barWidth + gap)
                drawRoundRect(
                    color = StudioAccent.copy(alpha = VyroCutWaveformMetrics.ClipBarBaseAlpha + amplitude.coerceIn(0f, 1f) * VyroCutWaveformMetrics.ClipBarPeakAlpha),
                    topLeft = androidx.compose.ui.geometry.Offset(x, (size.height - barHeight) / 2f),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                )
            }
        }
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
            put("hueShiftDegrees", clip.visualAdjustments.hueShiftDegrees)
            put("creativeEffectPreset", clip.visualAdjustments.creativeEffectPreset.id)
            put("creativeEffectIntensity", clip.visualAdjustments.creativeEffectIntensity)
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
        put("stickerOverlays", JSONArray().apply {
            clip.allStickerOverlays.forEach { overlay ->
                put(JSONObject().apply {
                    put("symbol", overlay.symbol)
                    overlay.assetId?.let { put("assetId", it) }
                    put("scale", overlay.scale.toDouble())
                    if (overlay.rotationDegrees != 0) put("rotationDegrees", overlay.rotationDegrees)
                    put("position", overlay.position.name)
                    put("startMs", overlay.startMs)
                    overlay.endMs?.let { put("endMs", it) }
                })
            }
        })
        put("timedEffectSegments", JSONArray().apply {
            clip.timedEffectSegments.forEach { segment ->
                put(JSONObject().apply {
                    put("id", segment.id)
                    put("presetId", segment.preset.id)
                    put("intensity", segment.intensity)
                    put("startMs", segment.startMs)
                    segment.endMs?.let { put("endMs", it) }
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
    val previewPlaybackSpeed: Float = 1f,
    val previewPosition: SavedPlaybackPosition? = null,
)

internal fun encodeProjectBackup(
    name: String,
    clips: List<EditClip>,
    canvasSettings: ProjectCanvasSettings,
    audioTracks: List<AudioTrackClip> = emptyList(),
    previewPlaybackSpeed: Float = 1f,
    previewPosition: SavedPlaybackPosition? = null,
): String {
    val validPreviewPosition = validateSavedPlaybackPosition(
        clips.map { ClipPlaybackRange(it.id, it.sourceStartMs, it.sourceEndMs) },
        previewPosition,
    )
    return JSONObject().apply {
        put("format", "vyrocut-project")
        put("version", 15)
        put("projectName", name)
        put("createdAt", System.currentTimeMillis())
        put("clips", JSONArray(encodeProject(clips)))
        put("canvasSettings", encodeProjectCanvasSettings(canvasSettings))
        put("audioTracks", JSONArray(encodeAudioTrackClips(audioTracks)))
        put("previewPlaybackSpeed", normalizePreviewPlaybackSpeed(previewPlaybackSpeed).toDouble())
        validPreviewPosition?.let { position ->
            put("previewClipId", position.clipId)
            put("previewSourceTimeMs", position.sourceTimeMs)
        }
    }.toString(2)
}

internal fun decodeProjectBackup(raw: String): ProjectBackup {
    val root = JSONObject(raw)
    require(root.getString("format") == "vyrocut-project") { "This is not a VyroCut project backup" }
    val version = root.getInt("version")
    require(version in 1..15) { "Unsupported backup version" }
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
    val previewPlaybackSpeed = if (version >= 12) {
        normalizePreviewPlaybackSpeed(root.optDouble("previewPlaybackSpeed", 1.0).toFloat())
    } else {
        1f
    }
    val previewPosition = if (version >= 13) {
        val clipId = root.opt("previewClipId") as? String
        val sourceTimeMs = (root.opt("previewSourceTimeMs") as? Number)
            ?.toString()
            ?.toLongOrNull()
        if (clipId != null && sourceTimeMs != null) {
            validateSavedPlaybackPosition(
                clips.map { ClipPlaybackRange(it.id, it.sourceStartMs, it.sourceEndMs) },
                SavedPlaybackPosition(clipId, sourceTimeMs),
            )
        } else {
            null
        }
    } else {
        null
    }
    return ProjectBackup(name, clips, canvasSettings, audioTracks, previewPlaybackSpeed, previewPosition)
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
                hueShiftDegrees = if (visualJson?.has("hueShiftDegrees") == true) visualJson.optInt("hueShiftDegrees", Int.MIN_VALUE) else 0,
                creativeEffectPresetId = visualJson?.takeIf { it.has("creativeEffectPreset") }?.optString("creativeEffectPreset"),
                creativeEffectIntensity = if (visualJson?.has("creativeEffectIntensity") == true) {
                    visualJson.optInt("creativeEffectIntensity", Int.MIN_VALUE)
                } else {
                    DEFAULT_CREATIVE_EFFECT_INTENSITY
                },
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
            val stickerOverlays = if (item.has("stickerOverlays")) {
                val overlaysJson = item.optJSONArray("stickerOverlays")
                    ?: error("A clip has an invalid sticker overlay list")
                require(overlaysJson.length() <= MAX_CLIP_STICKER_OVERLAYS) {
                    "A clip contains too many sticker overlays"
                }
                buildList {
                    for (overlayIndex in 0 until overlaysJson.length()) {
                        val overlayJson = overlaysJson.optJSONObject(overlayIndex)
                            ?: error("A clip has an invalid sticker overlay")
                        val overlay = decodeClipStickerOverlay(
                            raw = overlayJson.optString("symbol", ""),
                            scale = if (overlayJson.has("scale")) {
                                overlayJson.optDouble("scale", Double.NaN).toFloat()
                            } else {
                                1f
                            },
                            position = overlayJson.optString("position", "BOTTOM_RIGHT"),
                            startMs = if (overlayJson.has("startMs")) {
                                overlayJson.optLong("startMs", Long.MIN_VALUE)
                            } else {
                                0L
                            },
                            endMs = if (overlayJson.has("endMs") && !overlayJson.isNull("endMs")) {
                                overlayJson.optLong("endMs", Long.MIN_VALUE)
                            } else {
                                null
                            },
                            assetId = overlayJson.optString("assetId").takeIf {
                                overlayJson.has("assetId") && !overlayJson.isNull("assetId")
                            },
                            rotationDegrees = if (overlayJson.has("rotationDegrees")) {
                                overlayJson.optInt("rotationDegrees", Int.MIN_VALUE)
                            } else {
                                0
                            },
                        ) ?: error("A clip has an invalid sticker overlay")
                        add(overlay)
                    }
                }
            } else {
                emptyList()
            }
            val timedEffectSegments = if (item.has("timedEffectSegments")) {
                val segmentsJson = item.optJSONArray("timedEffectSegments")
                    ?: error("A clip has an invalid timed effect segment list")
                require(segmentsJson.length() <= MAX_CLIP_TIMED_EFFECT_SEGMENTS) {
                    "A clip contains too many timed effect segments"
                }
                buildList {
                    for (segmentIndex in 0 until segmentsJson.length()) {
                        val segmentJson = segmentsJson.optJSONObject(segmentIndex)
                            ?: error("A clip has an invalid timed effect segment")
                        val segment = decodeClipTimedEffectSegment(
                            id = segmentJson.optString("id").takeIf { segmentJson.has("id") },
                            presetId = segmentJson.optString("presetId").takeIf { segmentJson.has("presetId") },
                            intensity = if (segmentJson.has("intensity")) {
                                segmentJson.optInt("intensity", Int.MIN_VALUE)
                            } else {
                                Int.MIN_VALUE
                            },
                            startMs = if (segmentJson.has("startMs")) {
                                segmentJson.optLong("startMs", Long.MIN_VALUE)
                            } else {
                                Long.MIN_VALUE
                            },
                            endMs = if (segmentJson.has("endMs") && !segmentJson.isNull("endMs")) {
                                segmentJson.optLong("endMs", Long.MIN_VALUE)
                            } else {
                                null
                            },
                        ) ?: error("A clip has an invalid timed effect segment")
                        add(segment)
                    }
                }
            } else {
                emptyList()
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
                    stickerOverlays = stickerOverlays,
                    timedEffectSegments = timedEffectSegments,
                ),
            )
        }
    }
}.getOrNull()

internal fun projectPreferenceKeysForDeletion(projectId: String): List<String> {
    require(projectId.isNotBlank()) { "Project ID cannot be blank" }
    return listOf(
        "project_$projectId",
        "project_${projectId}_undo",
        "project_${projectId}_redo",
        "project_${projectId}_recovery",
        "project_${projectId}_recovery_dismissed",
        "project_${projectId}_audio_tracks",
        "project_${projectId}_audio_tracks_recovery",
        "project_${projectId}_canvas_settings",
        "project_${projectId}_preview_clip_id",
        "project_${projectId}_preview_source_time_ms",
        projectPreviewPlaybackSpeedPreferenceKey(projectId),
    )
}

private fun persistProjectState(
    preferences: android.content.SharedPreferences,
    projectId: String,
    projectName: String,
    clips: List<EditClip>,
    audioTracks: List<AudioTrackClip>,
    previousSavedAtMsOverride: Long? = null,
): ProjectRecoveryJournalBundle {
    val projectDurationMs = calculateTimelineDurationMs(clips.map { it.durationMs })
    require(validateAudioTrackClips(audioTracks, projectDurationMs)) {
        "Audio tracks must fit inside the saved video project"
    }
    val projectJson = encodeProject(clips)
    val audioTracksJson = encodeAudioTrackClips(audioTracks)
    val previousSavedAtMs = listOfNotNull(
        ProjectRecoveryJournal.decode(preferences.getString("project_${projectId}_recovery", null), projectId)?.savedAtMs,
        ProjectRecoveryJournal.decode(preferences.getString("project_${projectId}_audio_tracks_recovery", null), projectId)?.savedAtMs,
        previousSavedAtMsOverride,
    ).maxOrNull()
    val recoveryJournals = createProjectRecoveryJournalBundle(
        projectId = projectId,
        projectName = projectName,
        projectJson = projectJson,
        audioTracksJson = audioTracksJson,
        savedAtMs = nextProjectRecoveryTimestampMs(System.currentTimeMillis(), previousSavedAtMs),
    )
    // Video edits, audio lanes, and both recovery journals share one
    // preference transaction instead of four separately scheduled writes.
    preferences.edit()
        .putString("project_$projectId", projectJson)
        .putString("project_${projectId}_recovery", recoveryJournals.projectJournal)
        .putString("project_${projectId}_audio_tracks", audioTracksJson)
        .putString("project_${projectId}_audio_tracks_recovery", recoveryJournals.audioTracksJournal)
        .remove("project_${projectId}_recovery_dismissed")
        .apply()
    return recoveryJournals
}

private fun persistProjectAudioTracks(
    preferences: android.content.SharedPreferences,
    projectId: String,
    projectName: String,
    videoClips: List<EditClip>,
    clips: List<AudioTrackClip>,
    projectDurationMs: Long,
): ProjectRecoveryJournalBundle {
    require(validateAudioTrackClips(clips, projectDurationMs)) {
        "Audio track clips must fit inside the project and cannot overlap on one lane"
    }
    val projectJson = encodeProject(videoClips)
    val encoded = encodeAudioTrackClips(clips)
    val previousSavedAtMs = listOfNotNull(
        ProjectRecoveryJournal.decode(preferences.getString("project_${projectId}_recovery", null), projectId)?.savedAtMs,
        ProjectRecoveryJournal.decode(preferences.getString("project_${projectId}_audio_tracks_recovery", null), projectId)?.savedAtMs,
    ).maxOrNull()
    val recoveryBundle = createProjectRecoveryJournalBundle(
        projectId = projectId,
        projectName = projectName,
        projectJson = projectJson,
        audioTracksJson = encoded,
        savedAtMs = nextProjectRecoveryTimestampMs(System.currentTimeMillis(), previousSavedAtMs),
    )
    preferences.edit()
        .putString("project_${projectId}", projectJson)
        .putString("project_${projectId}_recovery", recoveryBundle.projectJournal)
        .putString("project_${projectId}_audio_tracks", encoded)
        .putString("project_${projectId}_audio_tracks_recovery", recoveryBundle.audioTracksJournal)
        .remove("project_${projectId}_recovery_dismissed")
        .apply()
    return recoveryBundle
}

private fun loadPrimaryProjectAudioTracks(
    preferences: android.content.SharedPreferences,
    projectId: String,
    projectDurationMs: Long,
): List<AudioTrackClip> = preferences
    .getString("project_${projectId}_audio_tracks", null)
    ?.let { decodeAudioTrackClips(it, projectDurationMs) }
    ?: emptyList()

private fun loadProjectAudioTracks(
    preferences: android.content.SharedPreferences,
    projectId: String,
    projectDurationMs: Long,
    mirroredAudioJournal: String? = null,
): List<AudioTrackClip> = selectProjectAudioTracks(
    projectId = projectId,
    projectDurationMs = projectDurationMs,
    primaryAudioTracksJson = preferences.getString("project_${projectId}_audio_tracks", null),
    preferenceAudioJournal = preferences.getString("project_${projectId}_audio_tracks_recovery", null),
    preferenceProjectJournal = preferences.getString("project_${projectId}_recovery", null),
    mirroredAudioJournal = mirroredAudioJournal,
)

private fun findProjectRecoveryCandidate(
    preferences: android.content.SharedPreferences,
    projectId: String,
    mirroredProjectJournal: String?,
): ProjectRecoverySnapshot? {
    val snapshot = newestProjectRecoverySnapshot(
        projectId,
        listOf(
            preferences.getString("project_${projectId}_recovery", null),
            mirroredProjectJournal,
        ),
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

private fun loadPreviewPlaybackSpeed(
    preferences: android.content.SharedPreferences,
    projectId: String,
): Float {
    val storedSpeed = runCatching {
        preferences.getFloat(projectPreviewPlaybackSpeedPreferenceKey(projectId), 1f)
    }.getOrNull()
    return normalizePreviewPlaybackSpeed(storedSpeed)
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
    val extractor = MediaExtractor()
    val hasAudioTrack = try {
        extractor.setDataSource(context, uri, null)
        hasAudioTrackMimeType(
            (0 until extractor.trackCount).map { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            },
        )
    } catch (_: Exception) {
        false
    } finally {
        runCatching { extractor.release() }
    }
    if (!hasAudioTrack) return null

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
    "DARK" -> VyroCutPalette.CanvasDark
    "WHITE" -> VyroCutPalette.CanvasWhite
    else -> VyroCutPalette.CanvasBlack
}

private fun projectCanvasSettingsFor(aspectRatio: Float, background: Color): ProjectCanvasSettings {
    val ratioPreset = when {
        kotlin.math.abs(aspectRatio - (16f / 9f)) < 0.001f -> "16:9"
        kotlin.math.abs(aspectRatio - 1f) < 0.001f -> "1:1"
        else -> "9:16"
    }
    val backgroundPreset = when (background) {
        VyroCutPalette.CanvasWhite -> "WHITE"
        VyroCutPalette.CanvasDark -> "DARK"
        else -> "BLACK"
    }
    return ProjectCanvasSettings(ratioPreset, backgroundPreset)
}


@Composable
private fun TimelinePlayheadMarker(
    offsetDp: Float,
    height: Dp,
    showCap: Boolean = false,
) {
    val metrics = timelinePlayheadVisualMetrics()
    val geometry = timelinePlayheadGeometry(offsetDp)
    Box(
        Modifier.offset(x = geometry.outlineLeftDp.dp)
            .width(metrics.outlineWidthDp.dp)
            .height(height)
            .background(VyroCutPalette.TimelinePlayheadOutline),
    )
    Box(
        Modifier.offset(x = geometry.lineLeftDp.dp)
            .width(metrics.lineWidthDp.dp)
            .height(height)
            .background(VyroCutPalette.TimelinePlayhead),
    )
    if (showCap) {
        Box(
            Modifier.offset(x = geometry.capLeftDp.dp)
                .width(metrics.capWidthDp.dp)
                .height(metrics.capHeightDp.dp)
                .background(VyroCutPalette.TimelinePlayheadOutline, RoundedCornerShape(TIMELINE_PLAYHEAD_CAP_OUTER_CORNER_RADIUS_DP.dp)),
        )
        Box(
            Modifier.offset(x = (geometry.capLeftDp + 1f).dp)
                .width((metrics.capWidthDp - 2).dp)
                .height((metrics.capHeightDp - 2).dp)
                .background(VyroCutPalette.TimelinePlayhead, RoundedCornerShape(TIMELINE_PLAYHEAD_CAP_INNER_CORNER_RADIUS_DP.dp)),
        )
    }
}

@Composable
private fun TimelineEditorStrip(
    clips: List<EditClip>,
    audioTracks: List<AudioTrackClip>,
    audioWaveforms: Map<String, List<Float>>,
    selectedIndex: Int,
    selectedAudioTrackId: String?,
    selectedTimedEffectSelection: Pair<String, String>?,
    playheadPositionMs: Long,
    isPlaying: Boolean,
    onSelectAudioTrack: (AudioTrackClip) -> Unit,
    onSeek: (TimelineSeekTarget) -> Unit,
    onSelectText: (Int, Int) -> Unit,
    onSelectSticker: (Int, Int) -> Unit,
    onSelectTimedEffect: (Int, String) -> Unit,
) {
    // A single horizontal scroll viewport keeps every track and ruler at the
    // same origin and offset. Multiple scroll containers sharing one state
    // can fight over viewport measurements on smaller devices.
    val timelineScroll = rememberScrollState()
    var autoFollowSuppressedByUser by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) autoFollowSuppressedByUser = false
    }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val timelineMetrics = timelineReadabilityMetrics(density.fontScale)
    val pixelsPerSecond = 24f
    val gap = 6f
    val clipWidths = clips.map { (it.durationMs / 1000f * pixelsPerSecond).coerceIn(120f, 320f) }
    val totalWidthPx = (clipWidths.sum() + (clips.size - 1).coerceAtLeast(0) * gap).coerceAtLeast(1f)
    val totalWidth = totalWidthPx.dp
    val timelineClipDurationsMs = clips.map { it.durationMs }
    val clipWidthsPx = with(density) { clipWidths.map { it.dp.toPx() } }
    val gapPx = with(density) { gap.dp.toPx() }
    val totalDuration = calculateTimelineDurationMs(timelineClipDurationsMs).coerceAtLeast(1L)
    val timelineRulerTicks = calculateTimelineRulerTicks(
        clipDurationsMs = timelineClipDurationsMs,
        clipWidthsPx = clipWidths,
        gapPx = gap,
        minimumTickSpacingDp = TIMELINE_RULER_MIN_TICK_SPACING_DP.toFloat(),
    )
    val maxRulerLabelCharacters = timelineRulerTicks
        .asSequence()
        .filter { it.isMajor }
        .maxOfOrNull { formatTime(it.timeMs).length }
        ?: 5
    val rulerLabelWidthDp = timelineRulerLabelWidthDp(
        maxLabelCharacters = maxRulerLabelCharacters,
        fontScale = density.fontScale,
    )
    val visibleRulerLabelTimes = selectTimelineRulerLabelTicks(
        ticks = timelineRulerTicks,
        labelWidthDp = rulerLabelWidthDp,
        contentWidthDp = totalWidthPx,
    ).mapTo(mutableSetOf()) { it.timeMs }
    val markerX = calculateTimelinePlayheadOffsetPx(
        clipDurationsMs = timelineClipDurationsMs,
        clipWidthsPx = clipWidths,
        playheadPositionMs = playheadPositionMs,
        gapPx = gap,
    )

    val markerPositionPx = with(density) { markerX.dp.toPx() }
    LaunchedEffect(
        isPlaying,
        markerPositionPx,
        viewportWidthPx,
        timelineScroll.maxValue,
        autoFollowSuppressedByUser,
    ) {
        val targetScrollPx = calculateTimelineAutoFollowScrollTargetPx(
            currentScrollPx = timelineScroll.value,
            markerPositionPx = markerPositionPx,
            viewportWidthPx = viewportWidthPx,
            maxScrollPx = timelineScroll.maxValue,
            isPlaying = isPlaying,
            isUserScrollOverride = autoFollowSuppressedByUser,
        )
        if (targetScrollPx != null) {
            // Jump only when the playhead leaves the safe viewport band. Manual
            // horizontal drags temporarily suspend auto-follow so the user can
            // inspect another timeline region without being pulled back.
            timelineScroll.scrollTo(targetScrollPx)
        }
    }

    if (clips.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(VyroCutTimelinePanelMetrics.EmptyStateSectionGapDp.dp)) {
            Text("VIDEO", color = StudioMuted, fontSize = TIMELINE_TRACK_LABEL_FONT_SP.sp, fontWeight = FontWeight.Bold)
            Text("Import footage to build your timeline", color = StudioMuted, fontSize = VyroCutTypography.SmallBodySp.sp, modifier = Modifier.padding(vertical = VyroCutTimelinePanelMetrics.EmptyStateVerticalPaddingDp.dp))
        }
        return
    }

    Column(
        Modifier.fillMaxWidth()
            .onSizeChanged { viewportWidthPx = it.width }
            // Observe horizontal drags on the Initial pass without consuming them.
            // horizontalScroll remains the only modifier that owns scrolling.
            .pointerInput(timelineScroll) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    var dragObserved = false
                    while (!dragObserved) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.position - down.position
                        if (
                            shouldSuspendTimelineAutoFollowForDrag(
                                deltaX = delta.x,
                                deltaY = delta.y,
                                touchSlopPx = viewConfiguration.touchSlop,
                            )
                        ) {
                            autoFollowSuppressedByUser = true
                            dragObserved = true
                        }
                    }
                }
            }
            .horizontalScroll(timelineScroll),
        verticalArrangement = Arrangement.spacedBy(VyroCutTimelinePanelMetrics.TrackVerticalGapDp.dp)
    ) {
        TimelineTrackHeader(
            modifier = Modifier.width(totalWidth),
            label = "VIDEO",
            detail = "Main track",
            accentColor = VyroCutPalette.AccentSoft,
            trailingDetail = "Tap to seek • hold to scrub",
        )

        // Ruler ticks share the playhead's absolute project-time mapping, so
        // fractional clip lengths do not reset or drift the time labels.
        Box(Modifier.width(totalWidth).height(timelineMetrics.rulerHeightDp.dp)) {
            timelineRulerTicks.forEach { tick ->
                val tickMetrics = timelineRulerTickVisualMetrics(tick.isMajor)
                val tickOffsetDp = tick.offsetPx.takeIf { it.isFinite() } ?: 0f
                Box(
                    Modifier.offset(x = (tickOffsetDp - tickMetrics.widthDp / 2f).dp)
                        .width(tickMetrics.widthDp.dp)
                        .height(tickMetrics.heightDp.dp)
                        .background(
                            color = if (tick.isMajor) {
                                VyroCutPalette.AccentSoft.copy(alpha = 0.9f)
                            } else {
                                StudioMuted.copy(alpha = 0.45f)
                            },
                            shape = RoundedCornerShape(TIMELINE_RULER_TICK_CORNER_RADIUS_DP.dp),
                        ),
                )
                if (tick.timeMs in visibleRulerLabelTimes) {
                    val labelPlacement = timelineRulerLabelPlacement(
                        tickOffsetDp = tickOffsetDp,
                        labelWidthDp = rulerLabelWidthDp,
                        contentWidthDp = totalWidthPx,
                    )
                    Text(
                        text = formatTime(tick.timeMs),
                        modifier = Modifier.offset(
                            x = labelPlacement.offsetDp.dp,
                            y = TIMELINE_RULER_LABEL_VERTICAL_OFFSET_DP.dp,
                        )
                            .width(rulerLabelWidthDp.dp)
                            .background(
                                VyroCutPalette.SurfaceBadge,
                                RoundedCornerShape(TIMELINE_RULER_LABEL_CORNER_RADIUS_DP.dp),
                            )
                            .padding(
                                horizontal = TIMELINE_RULER_LABEL_HORIZONTAL_PADDING_DP.dp,
                                vertical = TIMELINE_RULER_LABEL_VERTICAL_PADDING_DP.dp,
                            ),
                        color = VyroCutPalette.TextPrimary,
                        fontSize = TIMELINE_RULER_LABEL_FONT_SP.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = when (labelPlacement.alignment) {
                            TimelineRulerLabelAlignment.START -> androidx.compose.ui.text.style.TextAlign.Start
                            TimelineRulerLabelAlignment.CENTER -> androidx.compose.ui.text.style.TextAlign.Center
                            TimelineRulerLabelAlignment.END -> androidx.compose.ui.text.style.TextAlign.End
                        },
                    )
                }
            }
            TimelinePlayheadMarker(markerX, timelineMetrics.rulerHeightDp.dp)
        }

        Box(Modifier.width(totalWidth).height(timelineMetrics.videoTrackHeightDp.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                clips.forEachIndexed { index, clip ->
                    Column(
                        Modifier.width(clipWidths[index].dp)
                            .background(if (index == selectedIndex) VyroCutPalette.SurfaceSelected else VyroCutPalette.SurfaceClip, RoundedCornerShape(TIMELINE_VIDEO_CLIP_CORNER_RADIUS_DP.dp))
                            .border(
                                width = timelineClipSelectionBorderWidthDp(index == selectedIndex).dp,
                                color = VyroCutPalette.TimelineSelectionOutline,
                                shape = RoundedCornerShape(TIMELINE_VIDEO_CLIP_CORNER_RADIUS_DP.dp),
                            )
                            .padding(VyroCutTimelinePanelMetrics.VideoClipContentPaddingDp.dp)
                    ) {
                        Box(Modifier.fillMaxWidth().height(TIMELINE_VIDEO_THUMBNAIL_HEIGHT_DP.dp)) {
                            ClipThumbnail(clip, Modifier.fillMaxSize())
                            Text(
                                "V" + (index + 1), color = VyroCutPalette.MediaOverlayText, fontSize = TIMELINE_CLIP_META_FONT_SP.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopStart).background(VyroCutPalette.MediaOverlayScrim, RoundedCornerShape(TIMELINE_MEDIA_BADGE_CORNER_RADIUS_DP.dp)).padding(
                                    horizontal = VyroCutTimelinePanelMetrics.ClipMetaBadgeHorizontalPaddingDp.dp,
                                    vertical = VyroCutTimelinePanelMetrics.ClipMetaBadgeVerticalPaddingDp.dp,
                                )
                            )
                            Text(
                                formatTime(clip.durationMs), color = VyroCutPalette.MediaOverlayText, fontSize = TIMELINE_CLIP_META_FONT_SP.sp,
                                modifier = Modifier.align(Alignment.BottomEnd).background(VyroCutPalette.MediaOverlayScrim, RoundedCornerShape(TIMELINE_MEDIA_BADGE_CORNER_RADIUS_DP.dp)).padding(
                                    horizontal = VyroCutTimelinePanelMetrics.ClipMetaBadgeHorizontalPaddingDp.dp,
                                    vertical = VyroCutTimelinePanelMetrics.ClipMetaBadgeVerticalPaddingDp.dp,
                                )
                            )
                        }
                        Text(clip.name, color = if (index == selectedIndex) StudioAccent else StudioText, fontSize = TIMELINE_CLIP_LABEL_FONT_SP.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            TimelinePlayheadMarker(markerX, timelineMetrics.videoTrackHeightDp.dp, showCap = true)
            // The overlay is a child inside horizontalScroll, so pointer positions
            // are already expressed in scrolled content coordinates. Adding
            // timelineScroll.value here would apply the scroll displacement twice.
            // Taps and long-press scrubs share the same content-local pixel map.
            val latestOnSeek by rememberUpdatedState(onSeek)
            val seekFromTimeline: (TimelineSeekTarget) -> Unit = { target ->
                autoFollowSuppressedByUser = false
                latestOnSeek(target)
            }
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(timelineClipDurationsMs, clipWidthsPx, gapPx, timelineScroll) {
                        detectTapGestures { position ->
                            resolveTimelineSeekTargetAtOffsetPx(
                                timelineClipDurationsMs,
                                clipWidthsPx,
                                position.x,
                                gapPx,
                            )?.let(seekFromTimeline)
                        }
                    }
                    .pointerInput(timelineClipDurationsMs, clipWidthsPx, gapPx, timelineScroll) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                resolveTimelineSeekTargetAtOffsetPx(
                                    timelineClipDurationsMs,
                                    clipWidthsPx,
                                    position.x,
                                    gapPx,
                                )?.let(seekFromTimeline)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                resolveTimelineSeekTargetAtOffsetPx(
                                    timelineClipDurationsMs,
                                    clipWidthsPx,
                                    change.position.x,
                                    gapPx,
                                )?.let(seekFromTimeline)
                            },
                        )
                    }
            )
        }

        val textLayerCount = maxOf(
            1,
            clips.maxOfOrNull { it.allTextOverlays.size } ?: 0,
        ).coerceAtMost(MAX_CLIP_TEXT_OVERLAYS)
        val textTrackHeight = timelineTextTrackHeightDp(textLayerCount, timelineMetrics.textRowHeightDp).dp
        TimelineTrackHeader(
            modifier = Modifier.width(totalWidth),
            label = "TEXT",
            detail = "Timed layers • tap a row to edit its in/out points",
            accentColor = VyroCutPalette.AccentSecondary,
        )
        Box(Modifier.width(totalWidth).height(textTrackHeight)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                clips.forEachIndexed { index, clip ->
                    Column(
                        Modifier.width(clipWidths[index].dp)
                            .fillMaxHeight()
                            .border(
                                width = timelineClipSelectionBorderWidthDp(index == selectedIndex).dp,
                                color = VyroCutPalette.TimelineSelectionOutline,
                                shape = RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp),
                            ),
                    ) {
                        repeat(textLayerCount) { layerIndex ->
                            val overlay = clip.allTextOverlays.getOrNull(layerIndex)
                            Box(
                                Modifier.fillMaxWidth()
                                    .height(timelineMetrics.textRowHeightDp.dp)
                                    .background(VyroCutPalette.HeroGradientStart, RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp))
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
                                            fontSize = TIMELINE_TRACK_DETAIL_FONT_SP.sp,
                                            maxLines = 1,
                                            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = VyroCutTimelinePanelMetrics.TextOverlaySegmentHorizontalPaddingDp.dp),
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
                                                .background(VyroCutPalette.SurfaceSelected, RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp))
                                                .padding(horizontal = VyroCutTimelinePanelMetrics.TextOverlaySegmentHorizontalPaddingDp.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Text(
                                                overlay.text.replace('\n', ' '),
                                                color = StudioAccent,
                                                fontSize = TIMELINE_CLIP_LABEL_FONT_SP.sp,
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
            TimelinePlayheadMarker(markerX, textTrackHeight)
        }

        val stickerLayerCount = maxOf(
            1,
            clips.maxOfOrNull { it.allStickerOverlays.size } ?: 0,
        ).coerceAtMost(MAX_CLIP_STICKER_OVERLAYS)
        val stickerTrackHeight = timelineStickerTrackHeightDp(stickerLayerCount, timelineMetrics.stickerRowHeightDp).dp
        TimelineTrackHeader(
            modifier = Modifier.width(totalWidth),
            label = "STICKERS",
            detail = "Timed emoji overlays • tap a segment to edit its timing",
            accentColor = VyroCutPalette.Accent,
        )
        Box(Modifier.width(totalWidth).height(stickerTrackHeight)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                clips.forEachIndexed { index, clip ->
                    Column(
                        Modifier.width(clipWidths[index].dp)
                            .fillMaxHeight()
                            .border(
                                width = timelineClipSelectionBorderWidthDp(index == selectedIndex).dp,
                                color = VyroCutPalette.TimelineSelectionOutline,
                                shape = RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp),
                            ),
                    ) {
                        repeat(stickerLayerCount) { layerIndex ->
                            val overlay = clip.allStickerOverlays.getOrNull(layerIndex)
                            Box(
                                Modifier.fillMaxWidth()
                                    .height(timelineMetrics.stickerRowHeightDp.dp)
                                    .background(VyroCutPalette.SurfaceTimeline, RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp)),
                            ) {
                                if (overlay == null) {
                                    if (
                                        layerIndex == clip.allStickerOverlays.size &&
                                        clip.allStickerOverlays.size < MAX_CLIP_STICKER_OVERLAYS
                                    ) {
                                        Text(
                                            "+ Add sticker",
                                            color = StudioMuted,
                                            fontSize = TIMELINE_TRACK_DETAIL_FONT_SP.sp,
                                            maxLines = 1,
                                            modifier = Modifier.fillMaxSize()
                                                .clickable { onSelectSticker(index, layerIndex) }
                                                .padding(
                                                    horizontal = VyroCutTimelinePanelMetrics.StickerAddLabelHorizontalPaddingDp.dp,
                                                    vertical = VyroCutTimelinePanelMetrics.StickerAddLabelVerticalPaddingDp.dp,
                                                ),
                                        )
                                    }
                                } else {
                                    val bounds = calculateClipStickerOverlayTimelineBounds(
                                        trackWidthPx = clipWidths[index],
                                        clipDurationMs = clip.durationMs,
                                        overlay = overlay,
                                    )
                                    bounds?.let { segment ->
                                        Box(
                                            Modifier.offset(x = segment.startOffsetPx.dp)
                                                .width(segment.widthPx.dp)
                                                .fillMaxHeight()
                                                .background(
                                                    VyroCutPalette.SurfaceSelectedTimeline,
                                                    RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp),
                                                )
                                                .clickable { onSelectSticker(index, layerIndex) }
                                                .padding(horizontal = VyroCutTimelinePanelMetrics.StickerSegmentHorizontalPaddingDp.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Text(
                                                overlay.symbol,
                                                color = StudioAccent,
                                                fontSize = TIMELINE_STICKER_SYMBOL_FONT_SP.sp,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            TimelinePlayheadMarker(markerX, stickerTrackHeight)
        }

        TimelineTrackHeader(
            modifier = Modifier.width(totalWidth),
            label = "EFFECTS",
            detail = "Timed Vignette • tap a segment to select",
            accentColor = VyroCutPalette.AccentSecondary,
        )
        val effectTrackHeight = timelineMetrics.stickerRowHeightDp.dp
        Box(Modifier.width(totalWidth).height(effectTrackHeight)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                clips.forEachIndexed { index, clip ->
                    Box(
                        Modifier.width(clipWidths[index].dp)
                            .fillMaxHeight()
                            .background(
                                VyroCutPalette.SurfaceTimeline,
                                RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp),
                            )
                            .border(
                                width = timelineClipSelectionBorderWidthDp(index == selectedIndex).dp,
                                color = VyroCutPalette.TimelineSelectionOutline,
                                shape = RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp),
                            ),
                    ) {
                        clip.timedEffectSegments
                            .filter { it.preset == ClipCreativeEffectPreset.VIGNETTE }
                            .forEach { segment ->
                                val bounds = calculateClipTimedEffectSegmentTimelineBounds(
                                    trackWidthPx = clipWidths[index],
                                    clipDurationMs = clip.durationMs,
                                    segment = segment,
                                )
                                bounds?.let { effectBounds ->
                                    val isSelected = selectedTimedEffectSelection == (clip.id to segment.id)
                                    Box(
                                        Modifier.offset(x = effectBounds.startOffsetPx.dp)
                                            .width(effectBounds.widthPx.dp)
                                            .fillMaxHeight()
                                            .background(
                                                VyroCutPalette.AccentSecondary.copy(alpha = 0.22f),
                                                RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp),
                                            )
                                            .border(
                                                width = if (isSelected) 1.dp else 0.dp,
                                                color = VyroCutPalette.TimelineSelectionOutline,
                                                shape = RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp),
                                            )
                                            .clickable { onSelectTimedEffect(index, segment.id) }
                                            .padding(horizontal = VyroCutTimelinePanelMetrics.StickerSegmentHorizontalPaddingDp.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Text(
                                            "${segment.intensity}%",
                                            color = VyroCutPalette.AccentSecondary,
                                            fontSize = TIMELINE_CLIP_META_FONT_SP.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip,
                                        )
                                    }
                                }
                            }
                    }
                }
            }
            TimelinePlayheadMarker(markerX, effectTrackHeight)
        }

        TimelineTrackHeader(
            modifier = Modifier.width(totalWidth),
            label = "SOURCE",
            detail = "Original clip audio",
            accentColor = VyroCutPalette.TextMuted,
        )
        Box(Modifier.width(totalWidth).height(timelineMetrics.sourceTrackHeightDp.dp)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                clips.forEachIndexed { index, clip ->
                    Box(
                        Modifier.width(clipWidths[index].dp).fillMaxHeight()
                            .background(if (index == selectedIndex) VyroCutPalette.SurfaceSelectedTimeline else VyroCutPalette.SurfaceTimeline, RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp))
                            .border(
                                width = timelineClipSelectionBorderWidthDp(index == selectedIndex).dp,
                                color = VyroCutPalette.TimelineSelectionOutline,
                                shape = RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp),
                            )
                            .padding(horizontal = VyroCutTimelinePanelMetrics.AudioSegmentHorizontalPaddingDp.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AudioWaveform(clip, Modifier.fillMaxWidth().height(timelineMetrics.sourceWaveformHeightDp.dp))
                    }
                }
            }
            TimelinePlayheadMarker(markerX, timelineMetrics.sourceTrackHeightDp.dp)
        }
        AudioTrackType.values().forEach { type ->
            val laneClips = audioTracks.filter { it.type == type }
            val laneColor = when (type) {
                AudioTrackType.MUSIC -> VyroCutPalette.MusicTrack
                AudioTrackType.SFX -> VyroCutPalette.SfxTrack
                AudioTrackType.VOICEOVER -> VyroCutPalette.VoiceoverTrack
            }
            TimelineTrackHeader(
                modifier = Modifier.width(totalWidth),
                label = type.name,
                detail = when (type) {
                    AudioTrackType.MUSIC -> "Music"
                    AudioTrackType.SFX -> "Sound effects"
                    AudioTrackType.VOICEOVER -> "Voiceover"
                },
                accentColor = VyroCutPalette.TextPrimary,
                badgeColor = laneColor.copy(alpha = 0.82f),
            )
            Box(Modifier.width(totalWidth).height(timelineMetrics.audioLaneHeightDp.dp)) {
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
                    Box(
                        Modifier.offset(x = startPx.dp)
                            .width((endPx - startPx).coerceAtLeast(6f).dp)
                            .fillMaxHeight()
                            .background(laneColor, RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp))
                            .border(
                                width = timelineAudioClipSelectionBorderWidthDp(
                                    clipId = trackClip.id,
                                    selectedAudioTrackId = selectedAudioTrackId,
                                ).dp,
                                color = VyroCutPalette.TimelineSelectionOutline,
                                shape = RoundedCornerShape(TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP.dp),
                            )
                            .clickable { onSelectAudioTrack(trackClip) }
                            .padding(horizontal = VyroCutTimelinePanelMetrics.AudioSegmentHorizontalPaddingDp.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                trackClip.name,
                                color = VyroCutPalette.MediaOverlayText,
                                fontSize = TIMELINE_CLIP_LABEL_FONT_SP.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            AudioWaveformStrip(
                                amplitudes = audioWaveforms[audioWaveformMemoryCacheKey(trackClip)].orEmpty(),
                                color = VyroCutPalette.WaveformForeground,
                                height = timelineMetrics.audioWaveformHeightDp.dp,
                            )
                        }
                    }
                }
                TimelinePlayheadMarker(markerX, timelineMetrics.audioLaneHeightDp.dp)
            }
        }
        Row(Modifier.width(totalWidth), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", color = StudioMuted, fontSize = TIMELINE_RULER_LABEL_FONT_SP.sp)
            Text(formatTime(totalDuration), color = StudioMuted, fontSize = TIMELINE_RULER_LABEL_FONT_SP.sp)
        }
    }
}

@Composable
private fun TimelineTrackHeader(
    modifier: Modifier = Modifier,
    label: String,
    detail: String,
    accentColor: Color,
    badgeColor: Color = accentColor.copy(alpha = 0.14f),
    trailingDetail: String? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        val fontScale = LocalDensity.current.fontScale
        val stackDetail = timelineTrackHeaderStacksDetail(
            availableWidthDp = maxWidth.value,
            fontScale = fontScale,
        )
        val visibleTrailingDetail = trailingDetail?.takeIf {
            !stackDetail && timelineTrackHeaderShowsTrailingDetail(
                availableWidthDp = maxWidth.value,
                fontScale = fontScale,
            )
        }

        if (stackDetail) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TIMELINE_TRACK_HEADER_DETAIL_GAP_DP.dp),
            ) {
                TimelineTrackHeaderBadge(
                    label = label,
                    accentColor = accentColor,
                    badgeColor = badgeColor,
                )
                Text(
                    text = detail,
                    modifier = Modifier.fillMaxWidth(),
                    color = StudioMuted,
                    fontSize = TIMELINE_TRACK_DETAIL_FONT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimelineTrackHeaderBadge(
                    label = label,
                    accentColor = accentColor,
                    badgeColor = badgeColor,
                )
                Spacer(Modifier.width(TIMELINE_TRACK_HEADER_DETAIL_GAP_DP.dp))
                Text(
                    text = detail,
                    modifier = Modifier.weight(1f),
                    color = StudioMuted,
                    fontSize = TIMELINE_TRACK_DETAIL_FONT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (visibleTrailingDetail != null) {
                    Spacer(Modifier.width(TIMELINE_TRACK_HEADER_DETAIL_GAP_DP.dp))
                    Text(
                        text = visibleTrailingDetail,
                        color = StudioMuted,
                        fontSize = TIMELINE_TRACK_DETAIL_FONT_SP.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineTrackHeaderBadge(
    label: String,
    accentColor: Color,
    badgeColor: Color,
) {
    Row(
        modifier = Modifier
            .background(
                color = badgeColor,
                shape = RoundedCornerShape(TIMELINE_TRACK_HEADER_CORNER_RADIUS_DP.dp),
            )
            .padding(horizontal = TIMELINE_TRACK_HEADER_BADGE_HORIZONTAL_PADDING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(TIMELINE_TRACK_HEADER_DOT_SIZE_DP.dp)
                .height(TIMELINE_TRACK_HEADER_DOT_SIZE_DP.dp)
                .background(accentColor, RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(TIMELINE_TRACK_HEADER_DOT_GAP_DP.dp))
        Text(
            text = label,
            color = accentColor,
            fontSize = TIMELINE_TRACK_LABEL_FONT_SP.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EditorPreviewMetadata(
    title: String,
    subtitle: String,
    subtitleColor: Color,
    subtitleMaxLines: Int,
    modifier: Modifier = Modifier,
    relinkLabel: String? = null,
    relinkEnabled: Boolean = true,
    onRelink: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = StudioText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = subtitleColor,
                fontSize = VyroCutTypography.LabelSp.sp,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (relinkLabel != null && onRelink != null) {
            TextButton(
                onClick = onRelink,
                enabled = relinkEnabled,
                contentPadding = PaddingValues(
                    horizontal = VyroCutEditorChromeMetrics.RelinkButtonHorizontalPaddingDp.dp,
                    vertical = 0.dp,
                ),
            ) {
                Text(relinkLabel, fontSize = VyroCutTypography.LabelSp.sp)
            }
        }
    }
}
