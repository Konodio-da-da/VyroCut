package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRecoveryJournalTest {
    @Test
    fun roundTripsUnicodeNamesAndProjectJson() {
        val projectJson = """[{"id":"clip-1","name":"夜の街 🎬","start":0,"end":4200}]"""
        val encoded = ProjectRecoveryJournal.encode(
            projectId = "project-α",
            projectName = "夜の街 🎬",
            savedAtMs = 1_800_000_000_000L,
            projectJson = projectJson,
        )

        val decoded = ProjectRecoveryJournal.decode(encoded, "project-α")

        assertNotNull(decoded)
        assertEquals("project-α", decoded?.projectId)
        assertEquals("夜の街 🎬", decoded?.projectName)
        assertEquals(1_800_000_000_000L, decoded?.savedAtMs)
        assertEquals(projectJson, decoded?.projectJson)
    }

    @Test
    fun rejectsTruncatedOrModifiedRecoveryData() {
        val encoded = ProjectRecoveryJournal.encode("p1", "Project", 1234L, "[]")
        val modified = encoded.replace("Project", "Changed")

        assertNull(ProjectRecoveryJournal.decode(encoded.substringBeforeLast('\n'), "p1"))
        assertNull(ProjectRecoveryJournal.decode(modified, "p1"))
        assertNull(ProjectRecoveryJournal.decode("not-a-journal", "p1"))
    }

    @Test
    fun rejectsSnapshotBelongingToAnotherProject() {
        val encoded = ProjectRecoveryJournal.encode("p1", "Project", 1234L, "[]")

        assertNull(ProjectRecoveryJournal.decode(encoded, "p2"))
    }

    @Test
    fun offersRecoveryWhenPrimaryDataIsMissingOrDifferent() {
        val encoded = ProjectRecoveryJournal.encode("p1", "Project", 1234L, "[1]")
        val snapshot = ProjectRecoveryJournal.decode(encoded, "p1")!!

        assertTrue(ProjectRecoveryJournal.shouldOfferRecovery(null, snapshot))
        assertTrue(ProjectRecoveryJournal.shouldOfferRecovery("{broken", snapshot))
        assertTrue(ProjectRecoveryJournal.shouldOfferRecovery("[]", snapshot))
    }

    @Test
    fun doesNotOfferRecoveryWhenPrimaryMatchesTheCheckpoint() {
        val projectJson = """[{"id":"clip-1","start":0,"end":1000}]"""
        val encoded = ProjectRecoveryJournal.encode("p1", "Project", 1234L, projectJson)
        val snapshot = ProjectRecoveryJournal.decode(encoded, "p1")!!

        assertFalse(ProjectRecoveryJournal.shouldOfferRecovery(projectJson, snapshot))
    }
}
