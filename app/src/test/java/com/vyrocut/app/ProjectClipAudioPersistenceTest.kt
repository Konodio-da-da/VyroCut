package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun versionEightBackupPreservesClipAudioVisualAdjustmentsAndTimedTextOverlay() {
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

        assertEquals(8, org.json.JSONObject(backup).getInt("version"))
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
}
