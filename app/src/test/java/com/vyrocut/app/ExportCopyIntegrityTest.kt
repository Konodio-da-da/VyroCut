package com.vyrocut.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCopyIntegrityTest {
    @Test
    fun copiesBytesAndComputesTheirSha256Fingerprint() {
        val source = "abc".toByteArray(Charsets.UTF_8)
        val destination = ByteArrayOutputStream()

        val copied = copyAndFingerprintExport(ByteArrayInputStream(source), destination)

        assertArrayEquals(source, destination.toByteArray())
        assertEquals(3L, copied.byteCount)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            copied.sha256Hex,
        )
        assertTrue(exportFileFingerprintsMatch(copied, fingerprintExportStream(ByteArrayInputStream(source))))
    }

    @Test
    fun rejectsSameLengthMediaStoreCopyWithDifferentBytes() {
        val expected = fingerprintExportStream(ByteArrayInputStream("verified-mp4".toByteArray()))
        val corrupted = fingerprintExportStream(ByteArrayInputStream("verified-mpX".toByteArray()))

        assertEquals(expected.byteCount, corrupted.byteCount)
        assertFalse(exportFileFingerprintsMatch(expected, corrupted))
    }

    @Test
    fun rejectsTruncatedMediaStoreCopy() {
        val expected = fingerprintExportStream(ByteArrayInputStream(ByteArray(256) { it.toByte() }))
        val truncated = fingerprintExportStream(ByteArrayInputStream(ByteArray(255) { it.toByte() }))

        assertFalse(exportFileFingerprintsMatch(expected, truncated))
    }

    @Test
    fun rejectsEmptyAndMalformedFingerprints() {
        val empty = fingerprintExportStream(ByteArrayInputStream(byteArrayOf()))
        val valid = fingerprintExportStream(ByteArrayInputStream(byteArrayOf(1, 2, 3)))

        assertFalse(exportFileFingerprintsMatch(empty, empty))
        assertFalse(exportFileFingerprintsMatch(valid, valid.copy(sha256Hex = "not-a-sha256")))
        assertFalse(exportFileFingerprintsMatch(valid, valid.copy(byteCount = Long.MAX_VALUE)))
    }
}
