package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectClipAudioPersistenceTest {
    @Test
    fun clipVolumeAndFadeSettingsSurviveProjectSerialization() {
        val original = EditClip(
            id = "clip-1",
            uri = "content://media/one",
            name = "Interview",
            sourceStartMs = 250L,
            sourceEndMs = 4_250L,
            audioVolume = 0.35f,
            fadeInMs = 750L,
            fadeOutMs = 1_250L,
            visualAdjustments = ClipVisualAdjustments(brightness = 35, contrast = -20, saturation = 55),
            textOverlay = ClipTextOverlay("Interview highlight", startMs = 250L, endMs = 1_750L),
        )

        val restored = decodeProjectStrict(encodeProject(listOf(original)))

        assertNotNull(restored)
        assertEquals(0.35f, restored!!.single().audioVolume, 0.0001f)
        assertEquals(750L, restored.single().fadeInMs)
        assertEquals(1_250L, restored.single().fadeOutMs)
        assertEquals(original.visualAdjustments, restored.single().visualAdjustments)
        assertEquals(original.textOverlay, restored.single().textOverlay)
        assertEquals(250L, restored.single().textOverlay?.startMs)
        assertEquals(1_750L, restored.single().textOverlay?.endMs)
        assertEquals(original.sourceStartMs, restored.single().sourceStartMs)
        assertEquals(original.sourceEndMs, restored.single().sourceEndMs)
    }

    @Test
    fun legacyClipWithoutVolumeOrFadesDefaultsToOriginalAudio() {
        val restored = decodeProjectStrict(
            """[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000}]""",
        )

        assertNotNull(restored)
        assertEquals(1f, restored!!.single().audioVolume, 0f)
        assertEquals(0L, restored.single().fadeInMs)
        assertEquals(0L, restored.single().fadeOutMs)
        assertEquals(ClipVisualAdjustments(), restored.single().visualAdjustments)
        assertNull(restored.single().textOverlay)
    }

    @Test
    fun invalidPersistedClipVolumeOrFadeRejectsTheProjectInsteadOfSilentlyChangingIt() {
        val invalidVolume = decodeProjectStrict(
            """[{"id":"bad-volume","uri":"content://media/bad","name":"Bad clip","start":0,"end":1000,"volume":1.5}]""",
        )
        val invalidFade = decodeProjectStrict(
            """[{"id":"bad-fade","uri":"content://media/bad","name":"Bad clip","start":0,"end":1000,"fadeInMs":-1}]""",
        )
        val invalidTextTiming = decodeProjectStrict(
            """[{"id":"bad-text","uri":"content://media/bad","name":"Bad clip","start":0,"end":1000,"textOverlay":{"text":"Bad timing","startMs":500,"endMs":200}}]""",
        )

        assertNull(invalidVolume)
        assertNull(invalidFade)
        assertNull(invalidTextTiming)
    }

    @Test
    fun versionTwelveBackupPreservesClipAudioVisualAdjustmentsAndTimedTextOverlay() {
        val original = EditClip(
            id = "clip-2",
            uri = "content://media/two",
            name = "Music",
            sourceStartMs = 0L,
            sourceEndMs = 8_000L,
            audioVolume = 0.2f,
            fadeInMs = 1_000L,
            fadeOutMs = 2_000L,
            visualAdjustments = ClipVisualAdjustments(brightness = -45, contrast = 25, saturation = 70),
            textOverlay = ClipTextOverlay("Launch day", 1.25f, ClipTextPosition.TOP, 1_250L, 6_500L),
        )
        val backup = encodeProjectBackup(
            "Volume project",
            listOf(original),
            ProjectCanvasSettings("16:9", "DARK"),
        )
        val restored = decodeProjectBackup(backup)

        assertEquals(12, org.json.JSONObject(backup).getInt("version"))
        assertEquals("Volume project", restored.name)
        assertEquals(0.2f, restored.clips.single().audioVolume, 0.0001f)
        assertEquals(1_000L, restored.clips.single().fadeInMs)
        assertEquals(2_000L, restored.clips.single().fadeOutMs)
        assertEquals(ClipVisualAdjustments(-45, 25, 70), restored.clips.single().visualAdjustments)
        assertEquals(
            ClipTextOverlay("Launch day", 1.25f, ClipTextPosition.TOP, 1_250L, 6_500L),
            restored.clips.single().textOverlay,
        )
        assertEquals(ProjectCanvasSettings("16:9", "DARK"), restored.canvasSettings)
    }


    @Test
    fun versionTwelveBackupRoundTripsEverySupportedPreviewSpeed() {
        val video = EditClip(
            id = "video",
            uri = "content://media/video",
            name = "Video",
            sourceStartMs = 0L,
            sourceEndMs = 5_000L,
        )

        PREVIEW_PLAYBACK_SPEEDS.forEach { speed ->
            val backup = encodeProjectBackup(
                name = "Speed project",
                clips = listOf(video),
                canvasSettings = ProjectCanvasSettings(),
                previewPlaybackSpeed = speed,
            )
            val json = org.json.JSONObject(backup)

            assertEquals(12, json.getInt("version"))
            assertEquals(speed.toDouble(), json.getDouble("previewPlaybackSpeed"), 0.0001)
            assertEquals(speed, decodeProjectBackup(backup).previewPlaybackSpeed, 0f)
        }
    }

    @Test
    fun versionElevenAndOlderBackupsDefaultPreviewSpeedToNormal() {
        val versionEleven = """{
            "format":"vyrocut-project",
            "version":11,
            "projectName":"Legacy speed",
            "clips":[{"id":"video","uri":"content://media/video","name":"Video","start":0,"end":5000}],
            "canvasSettings":"VYROCUT_CANVAS_V1|9:16|BLACK",
            "audioTracks":[]
        }"""
        val versionNine = versionEleven.replace("\"version\":11", "\"version\":9")

        assertEquals(1f, decodeProjectBackup(versionEleven).previewPlaybackSpeed, 0f)
        assertEquals(1f, decodeProjectBackup(versionNine).previewPlaybackSpeed, 0f)
    }

    @Test
    fun unsupportedOrMalformedVersionTwelvePreviewSpeedFallsBackToNormal() {
        val video = EditClip(
            id = "video",
            uri = "content://media/video",
            name = "Video",
            sourceStartMs = 0L,
            sourceEndMs = 5_000L,
        )
        val json = org.json.JSONObject(
            encodeProjectBackup(
                name = "Invalid speed",
                clips = listOf(video),
                canvasSettings = ProjectCanvasSettings(),
                previewPlaybackSpeed = 1.5f,
            ),
        )

        json.put("previewPlaybackSpeed", 1.1)
        assertEquals(1f, decodeProjectBackup(json.toString()).previewPlaybackSpeed, 0f)

        json.put("previewPlaybackSpeed", "NaN")
        assertEquals(1f, decodeProjectBackup(json.toString()).previewPlaybackSpeed, 0f)

        json.put("previewPlaybackSpeed", org.json.JSONObject.NULL)
        assertEquals(1f, decodeProjectBackup(json.toString()).previewPlaybackSpeed, 0f)
    }


    @Test
    fun multipleTextLayersSurviveProjectSerializationAndVersionTwelveBackup() {
        val original = EditClip(
            id = "layered",
            uri = "content://media/layered",
            name = "Layered clip",
            sourceStartMs = 0L,
            sourceEndMs = 10_000L,
            textOverlay = ClipTextOverlay("Opening title", startMs = 500L, endMs = 3_000L),
            additionalTextOverlays = listOf(
                ClipTextOverlay("Lower third", 0.8f, ClipTextPosition.BOTTOM, 2_000L, 8_000L),
                ClipTextOverlay("End card", 1.25f, ClipTextPosition.CENTER, 8_000L, 10_000L),
            ),
        )

        val restoredProject = decodeProjectStrict(encodeProject(listOf(original)))
        assertNotNull(restoredProject)
        assertEquals(original.allTextOverlays, restoredProject!!.single().allTextOverlays)

        val backup = encodeProjectBackup("Layered project", listOf(original), ProjectCanvasSettings())
        assertEquals(12, org.json.JSONObject(backup).getInt("version"))
        val restoredBackup = decodeProjectBackup(backup)
        assertEquals(original.allTextOverlays, restoredBackup.clips.single().allTextOverlays)
    }

    @Test
    fun legacyVersionEightSingleTextOverlayMigratesToTheFirstLayer() {
        val legacy = """{
            "format":"vyrocut-project",
            "version":8,
            "projectName":"Version eight backup",
            "clips":[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000,"textOverlay":{"text":"Legacy title","fontScale":1.25,"position":"TOP","startMs":100,"endMs":900}}],
            "canvasSettings":"VYROCUT_CANVAS_V1|9:16|BLACK"
        }"""

        val restored = decodeProjectBackup(legacy)

        assertEquals(
            listOf(ClipTextOverlay("Legacy title", 1.25f, ClipTextPosition.TOP, 100L, 900L)),
            restored.clips.single().allTextOverlays,
        )
    }

    @Test
    fun rejectsMoreTextLayersThanTheSupportedLimit() {
        val overlays = (0..MAX_CLIP_TEXT_OVERLAYS).joinToString(",") { index ->
            """{"text":"Layer $index"}"""
        }
        val raw = """[{"id":"too-many","uri":"content://media/too-many","name":"Too many","start":0,"end":1000,"textOverlays":[$overlays]}]"""
        assertNull(decodeProjectStrict(raw))
    }

    @Test
    fun versionSevenBackupWithoutTextTimingDefaultsToFullClip() {
        val legacy = """{
            "format":"vyrocut-project",
            "version":7,
            "projectName":"Version seven backup",
            "clips":[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000,"textOverlay":{"text":"Legacy timed title","fontScale":1.25,"position":"TOP"}}],
            "canvasSettings":"VYROCUT_CANVAS_V1|9:16|BLACK"
        }"""

        val restored = decodeProjectBackup(legacy)

        assertEquals(ClipTextOverlay("Legacy timed title", 1.25f, ClipTextPosition.TOP), restored.clips.single().textOverlay)
    }

    @Test
    fun versionSixBackupWithoutTextStyleDefaultsToMediumBottomText() {
        val legacy = """{
            "format":"vyrocut-project",
            "version":6,
            "projectName":"Version six backup",
            "clips":[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000,"textOverlay":{"text":"Legacy title"}}],
            "canvasSettings":"VYROCUT_CANVAS_V1|9:16|BLACK"
        }"""

        val restored = decodeProjectBackup(legacy)

        assertEquals("Version six backup", restored.name)
        assertEquals(ClipTextOverlay("Legacy title"), restored.clips.single().textOverlay)
    }

    @Test
    fun versionFiveBackupWithoutTextOverlayRemainsCompatible() {
        val legacy = """{
            "format":"vyrocut-project",
            "version":5,
            "projectName":"Version five backup",
            "clips":[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000,"volume":0.4,"fadeInMs":100,"fadeOutMs":200,"visualAdjustments":{"brightness":10,"contrast":5,"saturation":-20}}],
            "canvasSettings":"VYROCUT_CANVAS_V1|16:9|DARK"
        }"""

        val restored = decodeProjectBackup(legacy)

        assertEquals("Version five backup", restored.name)
        assertEquals(ClipVisualAdjustments(10, 5, -20), restored.clips.single().visualAdjustments)
        assertNull(restored.clips.single().textOverlay)
    }

    @Test
    fun versionFourBackupWithoutVisualAdjustmentsRemainsCompatible() {
        val legacy = """{
            "format":"vyrocut-project",
            "version":4,
            "projectName":"Version four backup",
            "clips":[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000,"volume":0.4,"fadeInMs":100,"fadeOutMs":200}],
            "canvasSettings":"VYROCUT_CANVAS_V1|16:9|DARK"
        }"""

        val restored = decodeProjectBackup(legacy)

        assertEquals("Version four backup", restored.name)
        assertEquals(0.4f, restored.clips.single().audioVolume, 0.0001f)
        assertEquals(100L, restored.clips.single().fadeInMs)
        assertEquals(200L, restored.clips.single().fadeOutMs)
        assertEquals(ClipVisualAdjustments(), restored.clips.single().visualAdjustments)
    }

    @Test
    fun versionThreeBackupWithClipVolumeRemainsCompatible() {
        val legacy = """{
            "format":"vyrocut-project",
            "version":3,
            "projectName":"Version three backup",
            "clips":[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000,"volume":0.25}],
            "canvasSettings":"VYROCUT_CANVAS_V1|16:9|DARK"
        }"""

        val restored = decodeProjectBackup(legacy)

        assertEquals("Version three backup", restored.name)
        assertEquals(0.25f, restored.clips.single().audioVolume, 0.0001f)
        assertEquals(0L, restored.clips.single().fadeInMs)
        assertEquals(0L, restored.clips.single().fadeOutMs)
        assertEquals(ClipVisualAdjustments(), restored.clips.single().visualAdjustments)
    }

    @Test
    fun versionTwoBackupWithoutClipVolumeRemainsCompatible() {
        val legacy = """{
            "format":"vyrocut-project",
            "version":2,
            "projectName":"Legacy backup",
            "clips":[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000}],
            "canvasSettings":"VYROCUT_CANVAS_V1|9:16|BLACK"
        }"""

        val restored = decodeProjectBackup(legacy)

        assertEquals("Legacy backup", restored.name)
        assertEquals(1f, restored.clips.single().audioVolume, 0f)
        assertEquals(0L, restored.clips.single().fadeInMs)
        assertEquals(0L, restored.clips.single().fadeOutMs)
        assertEquals(ClipVisualAdjustments(), restored.clips.single().visualAdjustments)
        assertEquals(ProjectCanvasSettings("9:16", "BLACK"), restored.canvasSettings)
    }

    @Test
    fun versionTwelveBackupPreservesIndependentMusicSfxAndVoiceoverTracks() {
        val video = EditClip(
            id = "video",
            uri = "content://media/video",
            name = "Video",
            sourceStartMs = 0L,
            sourceEndMs = 12_000L,
        )
        val tracks = listOf(
            AudioTrackClip(
                id = "music",
                type = AudioTrackType.MUSIC,
                uri = "content://media/music",
                name = "Music bed",
                startMs = 0L,
                sourceStartMs = 1_000L,
                sourceEndMs = 9_000L,
                sourceDurationMs = 12_000L,
                volume = 0.65f,
                fadeInMs = 800L,
                fadeOutMs = 1_200L,
            ),
            AudioTrackClip(
                id = "sfx",
                type = AudioTrackType.SFX,
                uri = "content://media/sfx",
                name = "Impact",
                startMs = 4_000L,
                sourceStartMs = 0L,
                sourceEndMs = 1_500L,
                volume = 0.9f,
            ),
            AudioTrackClip(
                id = "voice",
                type = AudioTrackType.VOICEOVER,
                uri = "content://media/voice",
                name = "Narration",
                startMs = 2_000L,
                sourceStartMs = 500L,
                sourceEndMs = 6_500L,
                volume = 0.8f,
                fadeInMs = 250L,
            ),
        )
        val backup = encodeProjectBackup(
            "Audio mix",
            listOf(video),
            ProjectCanvasSettings("9:16", "BLACK"),
            tracks,
        )
        val restored = decodeProjectBackup(backup)

        assertEquals(12, org.json.JSONObject(backup).getInt("version"))
        assertEquals(tracks, restored.audioTracks)
        assertTrue(validateAudioTrackClips(restored.audioTracks, 12_000L))
    }

    @Test
    fun versionTenAudioTrackBackupDefaultsOriginalSourceDurationToSavedOutPoint() {
        val legacy = """{
            "format":"vyrocut-project",
            "version":10,
            "projectName":"Legacy audio trim",
            "clips":[{"id":"video","uri":"content://media/video","name":"Video","start":0,"end":5000}],
            "canvasSettings":"VYROCUT_CANVAS_V1|9:16|BLACK",
            "audioTracks":[{"id":"music","type":"MUSIC","uri":"content://media/music","name":"Music","startMs":0,"sourceStartMs":500,"sourceEndMs":2500,"volume":0.75,"fadeInMs":100,"fadeOutMs":200}]
        }"""

        val restored = decodeProjectBackup(legacy)

        assertEquals(10, org.json.JSONObject(legacy).getInt("version"))
        assertEquals(500L, restored.audioTracks.single().sourceStartMs)
        assertEquals(2_500L, restored.audioTracks.single().sourceEndMs)
        assertEquals(2_500L, restored.audioTracks.single().sourceDurationMs)
    }

    @Test
    fun versionNineBackupWithoutSeparateAudioTracksRemainsCompatible() {
        val legacy = """{
            "format":"vyrocut-project",
            "version":9,
            "projectName":"Version nine backup",
            "clips":[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000}],
            "canvasSettings":"VYROCUT_CANVAS_V1|9:16|BLACK"
        }"""

        val restored = decodeProjectBackup(legacy)

        assertEquals("Version nine backup", restored.name)
        assertEquals(emptyList<AudioTrackClip>(), restored.audioTracks)
    }


    @Test
    fun versionTenBackupRejectsMalformedAudioTrackListInsteadOfDroppingIt() {
        val malformed = """{
            "format":"vyrocut-project",
            "version":10,
            "projectName":"Malformed audio backup",
            "clips":[{"id":"video","uri":"content://media/video","name":"Video","start":0,"end":1000}],
            "canvasSettings":"VYROCUT_CANVAS_V1|9:16|BLACK",
            "audioTracks":{"not":"an array"}
        }"""
        assertNull(runCatching { decodeProjectBackup(malformed) }.getOrNull())
    }

}
