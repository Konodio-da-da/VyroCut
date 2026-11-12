package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectClipAudioPersistenceTest {
    @Test
    fun clipVolumeSurvivesProjectSerialization() {
        val original = EditClip(
            id = "clip-1",
            uri = "content://media/one",
            name = "Interview",
            sourceStartMs = 250L,
            sourceEndMs = 4_250L,
            audioVolume = 0.35f,
        )

        val restored = decodeProjectStrict(encodeProject(listOf(original)))

        assertNotNull(restored)
        assertEquals(0.35f, restored!!.single().audioVolume, 0.0001f)
        assertEquals(original.sourceStartMs, restored.single().sourceStartMs)
        assertEquals(original.sourceEndMs, restored.single().sourceEndMs)
    }

    @Test
    fun legacyClipWithoutVolumeDefaultsToUnity() {
        val restored = decodeProjectStrict(
            """[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000}]""",
        )

        assertNotNull(restored)
        assertEquals(1f, restored!!.single().audioVolume, 0f)
    }

    @Test
    fun invalidPersistedClipVolumeRejectsTheProjectInsteadOfSilentlyChangingIt() {
        val restored = decodeProjectStrict(
            """[{"id":"bad","uri":"content://media/bad","name":"Bad clip","start":0,"end":1000,"volume":1.5}]""",
        )

        assertNull(restored)
    }

    @Test
    fun versionThreeBackupPreservesClipVolume() {
        val original = EditClip(
            id = "clip-2",
            uri = "content://media/two",
            name = "Music",
            sourceStartMs = 0L,
            sourceEndMs = 8_000L,
            audioVolume = 0.2f,
        )
        val backup = encodeProjectBackup(
            "Volume project",
            listOf(original),
            ProjectCanvasSettings("16:9", "DARK"),
        )
        val restored = decodeProjectBackup(backup)

        assertEquals("Volume project", restored.name)
        assertEquals(0.2f, restored.clips.single().audioVolume, 0.0001f)
        assertEquals(ProjectCanvasSettings("16:9", "DARK"), restored.canvasSettings)
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
        assertEquals(ProjectCanvasSettings("9:16", "BLACK"), restored.canvasSettings)
    }
}
