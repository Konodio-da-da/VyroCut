package com.vyrocut.app

import android.content.ContentResolver
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object MediaMetadataReader {
    fun displayName(contentResolver: ContentResolver, uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0 && cursor.moveToFirst()) return cursor.getString(nameColumn)
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Imported media"
    }

    fun mimeType(contentResolver: ContentResolver, uri: Uri): String? =
        contentResolver.getType(uri)

    /**
     * Reads duration without retaining a native retriever. Call from Dispatchers.IO:
     * document providers may perform disk or cloud-backed work while opening a URI.
     */
    fun videoDurationMs(context: Context, uri: Uri): Long? {
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
     * Reads the source video's nominal frame rate for cross-clip frame stepping.
     * Capture metadata is preferred because it can retain fractional rates such
     * as 23.976/29.97; the extractor track format is a fallback for providers
     * that do not expose capture-frame-rate metadata. Call from Dispatchers.IO.
     */
    fun videoFrameRate(context: Context, uri: Uri): Float? {
        val retriever = MediaMetadataRetriever()
        val captureRate = try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
        if (captureRate != null && isValidPreviewFrameRate(captureRate)) return captureRate

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var resolvedRate: Float? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") != true) continue
                val candidate = runCatching {
                    format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                }.getOrNull()
                if (candidate != null && isValidPreviewFrameRate(candidate)) {
                    resolvedRate = candidate
                    break
                }
            }
            resolvedRate
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }
    
    /**
     * Reads presentation timestamps inside one clip's original source trim.
     * MediaExtractor enumerates compressed samples in decode order, which can
     * produce non-monotonic PTS around B-frames. The returned index is normalized
     * into presentation order before frame navigation uses it.
     *
     * The scan seeks to the preceding sync sample and continues through two
     * sync samples at/after trim OUT so leading B-frames in the following GOP
     * are not lost. Work is bounded by the per-clip sample cap and checks
     * coroutine cancellation on every sample. Call from Dispatchers.IO.
     *
     * Null means the provider/codec did not expose a complete bounded index,
     * the trim contained no video samples, or the scan exceeded the safety cap.
     */
    suspend fun videoPresentationTimestampsUs(
        context: Context,
        uri: Uri,
        sourceStartMs: Long,
        sourceEndMs: Long,
    ): LongArray? {
        if (sourceStartMs < 0L || sourceEndMs <= sourceStartMs ||
            sourceEndMs > Long.MAX_VALUE / 1_000L
        ) return null
        val startUs = sourceStartMs * 1_000L
        val endUs = sourceEndMs * 1_000L
        val extractor = MediaExtractor()
        return try {
            currentCoroutineContext().ensureActive()
            extractor.setDataSource(context, uri, null)
            val videoTrackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                runCatching {
                    extractor.getTrackFormat(index)
                        .getString(MediaFormat.KEY_MIME)
                        ?.startsWith("video/") == true
                }.getOrDefault(false)
            } ?: return null

            extractor.selectTrack(videoTrackIndex)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val timestamps = LongArray(MAX_VIDEO_FRAME_TIMESTAMPS_PER_CLIP)
            var count = 0
            var scannedSampleCount = 0
            var endSyncSampleCount = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) break
                if (scannedSampleCount >= MAX_VIDEO_FRAME_TIMESTAMPS_PER_CLIP) return null
                scannedSampleCount++

                val isSyncSample = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                if (sampleTimeUs >= startUs && sampleTimeUs < endUs) {
                    if (count >= MAX_VIDEO_FRAME_TIMESTAMPS_PER_CLIP) return null
                    timestamps[count++] = sampleTimeUs
                }

                endSyncSampleCount = nextVideoPtsEndSyncSampleCount(
                    currentCount = endSyncSampleCount,
                    sampleTimeUs = sampleTimeUs,
                    endUs = endUs,
                    isSyncSample = isSyncSample,
                )
                if (endSyncSampleCount >= VIDEO_PTS_END_SYNC_LOOKAHEAD_COUNT) break
                if (!extractor.advance()) break
            }
            if (count == 0) null else normalizeVideoPresentationTimestampsUs(timestamps.copyOf(count))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /**
     * Reads a best-effort revision for disposable media-derived caches.
     * Document providers expose last-modified in milliseconds; MediaStore's
     * DATE_MODIFIED is in seconds. Call from Dispatchers.IO.
     *
     * Null means neither a stable size nor modification time was available.
     */
    fun videoFrameTimestampSourceRevision(context: Context, uri: Uri): String? =
        mediaSourceRevisionFor(context, uri)

    /** Kept as the audio-specific entry point; both caches share provider metadata. */
    fun audioWaveformSourceRevision(context: Context, uri: Uri): String? =
        mediaSourceRevisionFor(context, uri)

    private fun mediaSourceRevisionFor(context: Context, uri: Uri): String? {
        fun queryLong(column: String): Long? = runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(column)
                if (index < 0 || !cursor.moveToFirst() || cursor.isNull(index)) {
                    null
                } else {
                    cursor.getLong(index)
                }
            }
        }.getOrNull()

        val sizeBytes = queryLong(OpenableColumns.SIZE)?.takeIf { it >= 0L }
        val documentModifiedMs = queryLong(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            ?.takeIf { it >= 0L }
        val mediaModifiedSeconds = queryLong(MediaStore.MediaColumns.DATE_MODIFIED)
            ?.takeIf { it >= 0L }
        val mediaModifiedMs = mediaModifiedSeconds
            ?.takeIf { it <= Long.MAX_VALUE / 1_000L }
            ?.times(1_000L)

        return mediaSourceRevision(
            sizeBytes = sizeBytes,
            lastModifiedMs = documentModifiedMs ?: mediaModifiedMs,
        )
    }
}
