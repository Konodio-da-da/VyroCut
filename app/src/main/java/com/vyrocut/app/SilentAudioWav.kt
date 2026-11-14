package com.vyrocut.app

import java.io.File
import java.io.RandomAccessFile

private const val SILENT_AUDIO_SAMPLE_RATE_HZ = 8_000
private const val SILENT_AUDIO_CHANNELS = 1
private const val SILENT_AUDIO_BITS_PER_SAMPLE = 16
private const val SILENT_AUDIO_HEADER_BYTES = 44L

/**
 * Builds the canonical 8 kHz, mono, 16-bit PCM RIFF/WAVE header used for
 * project-timeline gaps. Keeping this pure makes the cache format testable.
 */
internal fun buildSilentAudioWavHeader(dataBytes: Long): ByteArray {
    val blockAlign = SILENT_AUDIO_CHANNELS * SILENT_AUDIO_BITS_PER_SAMPLE / 8
    require(dataBytes >= blockAlign.toLong() && dataBytes % blockAlign == 0L) {
        "WAV audio data must contain complete PCM samples"
    }
    require(dataBytes <= Int.MAX_VALUE.toLong() - 36L) {
        "Audio gap is too long for a WAV silence source"
    }

    val header = ByteArray(SILENT_AUDIO_HEADER_BYTES.toInt())
    fun writeAscii(offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).copyInto(header, offset)
    }
    fun writeShortLe(offset: Int, value: Int) {
        header[offset] = (value and 0xff).toByte()
        header[offset + 1] = ((value shr 8) and 0xff).toByte()
    }
    fun writeIntLe(offset: Int, value: Int) {
        for (byteIndex in 0 until 4) {
            header[offset + byteIndex] = ((value shr (byteIndex * 8)) and 0xff).toByte()
        }
    }

    writeAscii(0, "RIFF")
    writeIntLe(4, (dataBytes + 36L).toInt())
    writeAscii(8, "WAVE")
    writeAscii(12, "fmt ")
    writeIntLe(16, 16)
    writeShortLe(20, 1)
    writeShortLe(22, SILENT_AUDIO_CHANNELS)
    writeIntLe(24, SILENT_AUDIO_SAMPLE_RATE_HZ)
    writeIntLe(28, SILENT_AUDIO_SAMPLE_RATE_HZ * blockAlign)
    writeShortLe(32, blockAlign)
    writeShortLe(34, SILENT_AUDIO_BITS_PER_SAMPLE)
    writeAscii(36, "data")
    writeIntLe(40, dataBytes.toInt())
    return header
}

/** Rejects truncated or incompatible headers before a cached silence file is reused. */
internal fun isValidSilentAudioWavHeader(header: ByteArray, expectedDataBytes: Long): Boolean =
    runCatching { header.contentEquals(buildSilentAudioWavHeader(expectedDataBytes)) }
        .getOrDefault(false)

/**
 * Writes a local silence source atomically. A killed write can leave a temporary
 * file, but it can never leave a correctly-sized, half-written target that the
 * next editor session mistakes for a valid WAV.
 */
@Synchronized
internal fun createSilentAudioWav(directory: File, durationMs: Long): File {
    require(durationMs > 0L) { "Silence duration must be positive" }
    val dataBytes = (
        durationMs.toDouble() *
            SILENT_AUDIO_SAMPLE_RATE_HZ *
            SILENT_AUDIO_CHANNELS *
            SILENT_AUDIO_BITS_PER_SAMPLE / 8.0 / 1_000.0
        ).toLong().coerceAtLeast(2L)
    require(dataBytes <= Int.MAX_VALUE.toLong() - 36L) {
        "Audio gap is too long for a WAV silence source"
    }
    if (!directory.exists() && !directory.mkdirs()) {
        error("Could not create the audio cache directory")
    }

    val file = File(directory, "vyrocut-silence-v2-$dataBytes.wav")
    if (isValidSilentAudioWavFile(file, dataBytes)) return file

    val temporaryFile = File(directory, "${file.name}.tmp")
    if (temporaryFile.exists() && !temporaryFile.delete()) {
        error("Could not replace an incomplete audio silence cache file")
    }

    RandomAccessFile(temporaryFile, "rw").use { output ->
        output.setLength(dataBytes + SILENT_AUDIO_HEADER_BYTES)
        output.write(buildSilentAudioWavHeader(dataBytes))
        output.seek(SILENT_AUDIO_HEADER_BYTES)
        val silence = ByteArray(64 * 1024)
        var remaining = dataBytes
        while (remaining > 0L) {
            val count = minOf(remaining, silence.size.toLong()).toInt()
            output.write(silence, 0, count)
            remaining -= count.toLong()
        }
        output.fd.sync()
    }

    if (file.exists() && !file.delete()) {
        temporaryFile.delete()
        error("Could not replace an invalid audio silence cache file")
    }
    if (!temporaryFile.renameTo(file)) {
        temporaryFile.delete()
        error("Could not publish the audio silence cache file")
    }
    return file
}

private fun isValidSilentAudioWavFile(file: File, expectedDataBytes: Long): Boolean {
    if (!file.isFile || file.length() != expectedDataBytes + SILENT_AUDIO_HEADER_BYTES) return false
    return runCatching {
        RandomAccessFile(file, "r").use { input ->
            val header = ByteArray(SILENT_AUDIO_HEADER_BYTES.toInt())
            input.readFully(header)
            isValidSilentAudioWavHeader(header, expectedDataBytes)
        }
    }.getOrDefault(false)
}
