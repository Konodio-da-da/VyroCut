package com.vyrocut.app

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SilentAudioWavTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createsValidRiffWaveSilenceAndReusesTheCompletedCache() {
        val directory = temporaryFolder.newFolder("silence")
        val file = createSilentAudioWav(directory, 250L)
        val expectedDataBytes = 4_000L

        assertEquals(expectedDataBytes + 44L, file.length())
        val header = RandomAccessFile(file, "r").use { input ->
            ByteArray(44).also { input.readFully(it) }
        }
        assertTrue(isValidSilentAudioWavHeader(header, expectedDataBytes))
        val payload = file.readBytes().copyOfRange(44, file.length().toInt())
        assertTrue(payload.all { it == 0.toByte() })

        val reused = createSilentAudioWav(directory, 250L)
        assertEquals(file.absolutePath, reused.absolutePath)
        assertEquals(expectedDataBytes + 44L, reused.length())
    }

    @Test
    fun rejectsAndRebuildsACorrectlySizedButCorruptCache() {
        val directory = temporaryFolder.newFolder("corrupt")
        val file = createSilentAudioWav(directory, 500L)
        RandomAccessFile(file, "rw").use {
            it.seek(0L)
            it.write("BROK".toByteArray(Charsets.US_ASCII))
        }

        val rebuilt = createSilentAudioWav(directory, 500L)
        val header = RandomAccessFile(rebuilt, "r").use { input ->
            ByteArray(44).also { input.readFully(it) }
        }

        assertTrue(isValidSilentAudioWavHeader(header, 8_000L))
        assertTrue(rebuilt.readBytes().copyOfRange(44, rebuilt.length().toInt()).all { it == 0.toByte() })
    }

    @Test
    fun ignoresAndReplacesAnInterruptedTemporaryWrite() {
        val directory = temporaryFolder.newFolder("interrupted")
        val temporaryFile = File(directory, "vyrocut-silence-v2-16000.wav.tmp")
        temporaryFile.writeBytes(ByteArray(128))

        val file = createSilentAudioWav(directory, 1_000L)
        val header = RandomAccessFile(file, "r").use { input ->
            ByteArray(44).also { input.readFully(it) }
        }

        assertFalse(temporaryFile.exists())
        assertEquals(16_044L, file.length())
        assertTrue(isValidSilentAudioWavHeader(header, 16_000L))
    }

    @Test
    fun concurrentPreviewAndExportRequestsShareOneCompleteCache() {
        val directory = temporaryFolder.newFolder("concurrent")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val requests = (0 until 2).map {
                executor.submit<File> { createSilentAudioWav(directory, 750L) }
            }
            val files = requests.map { it.get(5, TimeUnit.SECONDS) }
            val header = RandomAccessFile(files.first(), "r").use { input ->
                ByteArray(44).also { input.readFully(it) }
            }

            assertEquals(files.first().absolutePath, files.last().absolutePath)
            assertTrue(isValidSilentAudioWavHeader(header, 12_000L))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun rejectsTruncatedOrMismatchedWaveHeaders() {
        val header = buildSilentAudioWavHeader(2_000L)

        assertTrue(isValidSilentAudioWavHeader(header, 2_000L))
        assertFalse(isValidSilentAudioWavHeader(header.copyOf(43), 2_000L))
        assertFalse(isValidSilentAudioWavHeader(header.copyOf().also { it[0] = 0 }, 2_000L))
        assertFalse(isValidSilentAudioWavHeader(header, 2_001L))
        assertFalse(isValidSilentAudioWavHeader(header, Long.MAX_VALUE))
    }
}
