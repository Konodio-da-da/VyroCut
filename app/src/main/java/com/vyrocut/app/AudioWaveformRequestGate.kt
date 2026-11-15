package com.vyrocut.app

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

/**
 * Serializes expensive work for one key while allowing unrelated work to run
 * concurrently. Callers should re-check their persistent cache inside [withKey]:
 * the first cache miss performs the work and later callers reuse its result.
 *
 * Entries are reference-counted so completed keys do not accumulate for the
 * lifetime of the application. A cancelled waiter releases its reference
 * without cancelling the caller that currently owns the key.
 */
internal class AudioWaveformRequestGate {
    private class Entry {
        val mutex = Mutex()
        var references: Int = 0
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    suspend fun <T> withKey(key: String, block: suspend () -> T): T {
        require(key.isNotBlank())

        val entry = entries.compute(key) { _, current ->
            (current ?: Entry()).also { it.references += 1 }
        } ?: error("Unable to allocate waveform request gate")

        var acquired = false
        try {
            entry.mutex.lock()
            acquired = true
            return block()
        } finally {
            if (acquired) entry.mutex.unlock()
            entries.computeIfPresent(key) { _, current ->
                if (current !== entry) {
                    current
                } else {
                    current.references -= 1
                    check(current.references >= 0) { "Waveform request reference count underflow" }
                    if (current.references == 0) null else current
                }
            }
        }
    }

    internal val trackedKeyCount: Int
        get() = entries.size
}
