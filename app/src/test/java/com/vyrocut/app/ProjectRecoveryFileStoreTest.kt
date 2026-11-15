package com.vyrocut.app

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProjectRecoveryFileStoreTest {
    private lateinit var directory: java.io.File
    @Before fun setUp() { directory = Files.createTempDirectory("vyrocut-recovery-test").toFile() }
    @After fun tearDown() { directory.deleteRecursively() }

    @Test
    fun writesAndReadsIndependentProjectAndAudioJournals() {
        val project = ProjectRecoveryJournal.encode("project-1", "Project", 100L, "[1]")
        val audio = encodeAudioTrackRecoveryJournal("project-1", "[]", 100L)
        assertTrue(ProjectRecoveryFileStore.write(directory, "project-1", ProjectRecoveryFileKind.PROJECT, project))
        assertTrue(ProjectRecoveryFileStore.write(directory, "project-1", ProjectRecoveryFileKind.AUDIO, audio))
        assertEquals(project, ProjectRecoveryFileStore.read(directory, "project-1", ProjectRecoveryFileKind.PROJECT))
        assertEquals(audio, ProjectRecoveryFileStore.read(directory, "project-1", ProjectRecoveryFileKind.AUDIO))
    }

    @Test
    fun rejectsForeignMalformedAndWrongKindJournals() {
        val foreign = ProjectRecoveryJournal.encode("project-2", "Project", 100L, "[]")
        val wrongKind = ProjectRecoveryJournal.encode("project-1", "Project", 100L, "[]")
        assertFalse(ProjectRecoveryFileStore.write(directory, "project-1", ProjectRecoveryFileKind.PROJECT, foreign))
        assertFalse(ProjectRecoveryFileStore.write(directory, "project-1", ProjectRecoveryFileKind.AUDIO, wrongKind))
        assertFalse(ProjectRecoveryFileStore.write(directory, "project-1", ProjectRecoveryFileKind.PROJECT, "broken"))
        assertNull(ProjectRecoveryFileStore.read(directory, "project-1", ProjectRecoveryFileKind.PROJECT))
    }

    @Test
    fun rejectsUnsafeProjectIdsAndNeverCreatesPathsOutsideTheRecoveryDirectory() {
        val journal = ProjectRecoveryJournal.encode("../outside", "Project", 100L, "[]")
        assertFalse(ProjectRecoveryFileStore.write(directory, "../outside", ProjectRecoveryFileKind.PROJECT, journal))
        assertNull(ProjectRecoveryFileStore.read(directory, "../outside", ProjectRecoveryFileKind.PROJECT))
        assertFalse(ProjectRecoveryFileStore.deleteProject(directory, "../outside"))
        assertEquals(0, directory.listFiles()?.size ?: 0)
    }

    @Test
    fun staleAsyncWritesCannotReplaceANewerCheckpoint() {
        val newer = ProjectRecoveryJournal.encode("project-1", "Project", 200L, "[new]")
        val older = ProjectRecoveryJournal.encode("project-1", "Project", 199L, "[old]")
        assertTrue(ProjectRecoveryFileStore.write(directory, "project-1", ProjectRecoveryFileKind.PROJECT, newer))
        assertFalse(ProjectRecoveryFileStore.write(directory, "project-1", ProjectRecoveryFileKind.PROJECT, older))
        assertEquals(newer, ProjectRecoveryFileStore.read(directory, "project-1", ProjectRecoveryFileKind.PROJECT))
    }

    @Test
    fun corruptFilesAreIgnoredAndProjectDeletionBlocksLateWrites() {
        val project = ProjectRecoveryJournal.encode("project-1", "Project", 100L, "[]")
        val audio = encodeAudioTrackRecoveryJournal("project-1", "[]", 100L)
        assertTrue(ProjectRecoveryFileStore.write(directory, "project-1", ProjectRecoveryFileKind.PROJECT, project))
        assertTrue(ProjectRecoveryFileStore.write(directory, "project-1", ProjectRecoveryFileKind.AUDIO, audio))
        directory.resolve("vyrocut_project-1_project.journal").writeText("corrupt")
        assertNull(ProjectRecoveryFileStore.read(directory, "project-1", ProjectRecoveryFileKind.PROJECT))
        assertTrue(ProjectRecoveryFileStore.deleteProject(directory, "project-1"))
        assertNull(ProjectRecoveryFileStore.read(directory, "project-1", ProjectRecoveryFileKind.AUDIO))
        assertFalse(ProjectRecoveryFileStore.write(directory, "project-1", ProjectRecoveryFileKind.PROJECT, project))
    }

    @Test
    fun choosesNewestValidRecoveryAndMatchesAudioByProjectAndTimestamp() {
        val old = ProjectRecoveryJournal.encode("project-1", "Old", 100L, "[]")
        val current = ProjectRecoveryJournal.encode("project-1", "Current", 200L, "[]")
        val foreign = ProjectRecoveryJournal.encode("project-2", "Foreign", 300L, "[]")
        assertEquals("Current", newestProjectRecoverySnapshot("project-1", listOf("bad", old, foreign, current))?.projectName)
        val audio = encodeAudioTrackRecoveryJournal("project-1", "[]", 200L)
        assertEquals(audio, matchingAudioRecoveryJournal("project-1", 200L, listOf(old, audio)))
        assertNull(matchingAudioRecoveryJournal("project-1", 201L, listOf(audio)))
    }

    @Test
    fun recoveryTimestampRemainsMonotonicWhenClockRepeatsOrRegresses() {
        assertEquals(1_000L, nextProjectRecoveryTimestampMs(1_000L, null))
        assertEquals(1_001L, nextProjectRecoveryTimestampMs(1_000L, 1_000L))
        assertEquals(1_002L, nextProjectRecoveryTimestampMs(999L, 1_001L))
    }
}


    @Test
    fun prefersMirroredAudioOnlyWhenPrimaryIsMissingOrMirrorCheckpointIsNewer() {
        assertTrue(shouldPreferMirroredAudioRecovery(false, null, null))
        assertTrue(shouldPreferMirroredAudioRecovery(false, 200L, 100L))
        assertTrue(shouldPreferMirroredAudioRecovery(true, 100L, 101L))
        assertFalse(shouldPreferMirroredAudioRecovery(true, 100L, 100L))
        assertFalse(shouldPreferMirroredAudioRecovery(true, 100L, 99L))
        assertFalse(shouldPreferMirroredAudioRecovery(true, null, null))
    }
