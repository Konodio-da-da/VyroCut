package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectEditHistoryTest {
    private fun videoClip(endMs: Long = 5_000L) = EditClip(
        id = "video-1",
        uri = "content://media/video-1",
        name = "Video",
        sourceStartMs = 0L,
        sourceEndMs = endMs,
    )

    private fun audioClip() = AudioTrackClip(
        id = "music-1",
        type = AudioTrackType.MUSIC,
        uri = "content://media/music-1",
        name = "Music",
        startMs = 1_000L,
        sourceStartMs = 0L,
        sourceEndMs = 4_000L,
        sourceDurationMs = 4_000L,
        volume = 0.6f,
        fadeInMs = 250L,
        fadeOutMs = 500L,
    )

    @Test
    fun historySnapshotRoundTripsVideoAndAudioEditsTogether() {
        val snapshot = ProjectEditSnapshot(
            clips = listOf(videoClip()),
            audioTracks = listOf(audioClip()),
        )

        val restored = decodeProjectEditSnapshot(encodeProjectEditSnapshot(snapshot))

        assertNotNull(restored)
        assertEquals(snapshot.clips, restored!!.clips)
        assertEquals(snapshot.audioTracks, restored.audioTracks)
    }

    @Test
    fun legacyClipOnlyHistoryIsRecognizedWithoutInventingAnEmptyAudioSnapshot() {
        val clips = listOf(videoClip())
        val restored = decodeProjectEditSnapshot(encodeProject(clips))

        assertNotNull(restored)
        assertEquals(clips, restored!!.clips)
        assertNull(restored.audioTracks)
    }

    @Test
    fun explicitEmptyAudioSnapshotRemovesAudioWhenUndoingAnImport() {
        val snapshot = ProjectEditSnapshot(listOf(videoClip()), emptyList())

        val restoredAudio = audioTracksForHistorySnapshot(snapshot, listOf(audioClip()))

        assertEquals(emptyList<AudioTrackClip>(), restoredAudio)
    }

    @Test
    fun legacySnapshotPreservesCurrentAudioButTrimsItToRestoredVideoDuration() {
        val snapshot = ProjectEditSnapshot(listOf(videoClip(endMs = 3_000L)), null)

        val restoredAudio = audioTracksForHistorySnapshot(snapshot, listOf(audioClip()))

        assertEquals(1, restoredAudio.size)
        assertEquals(1_000L, restoredAudio.single().startMs)
        assertEquals(2_000L, restoredAudio.single().durationMs)
        assertEquals(2_000L, restoredAudio.single().sourceEndMs)
    }

    @Test
    fun corruptHistorySnapshotIsRejected() {
        assertNull(decodeProjectEditSnapshot("{not-json"))
        assertNull(decodeProjectEditSnapshot("""{"format":"other","version":1,"clips":[]}"""))
    }
}
