package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPersistenceRulesTest {
    @Test
    fun skipsCorruptSnapshotsWithoutReplacingThemWithEmptyProjects() {
        val decoded = decodeValidHistorySnapshots(
            rawSnapshots = listOf("[]", "broken", "[1]"),
            decode = { raw ->
                when (raw) {
                    "[]" -> emptyList<Int>()
                    "[1]" -> listOf(1)
                    else -> null
                }
            },
        )

        assertEquals(listOf(emptyList<Int>(), listOf(1)), decoded)
    }

    @Test
    fun keepsOnlyTheNewestFiftyValidSnapshots() {
        val rawSnapshots = (0..54).map(Int::toString) + "corrupt"

        val decoded = decodeValidHistorySnapshots(rawSnapshots) { it.toIntOrNull() }

        assertEquals((5..54).toList(), decoded)
    }

    @Test
    fun corruptOnlyHistoryProducesNoUndoEntries() {
        val decoded = decodeValidHistorySnapshots(listOf("bad-1", "bad-2")) {
            it.toIntOrNull()
        }

        assertTrue(decoded.isEmpty())
    }
}
