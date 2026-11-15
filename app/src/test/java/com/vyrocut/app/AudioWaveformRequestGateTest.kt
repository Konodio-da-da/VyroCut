package com.vyrocut.app

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioWaveformRequestGateTest {
    @Test
    fun concurrentSameKeyRequestsPerformOneCacheFill() = runBlocking {
        val gate = AudioWaveformRequestGate()
        val decodeCount = AtomicInteger()
        val decodeStarted = CompletableDeferred<Unit>()
        val finishDecode = CompletableDeferred<Unit>()
        var cachedWaveform: List<Float>? = null

        val requests = List(8) {
            async(start = CoroutineStart.UNDISPATCHED) {
                gate.withKey("same-source-range") {
                    cachedWaveform ?: run {
                        decodeCount.incrementAndGet()
                        decodeStarted.complete(Unit)
                        finishDecode.await()
                        listOf(0f, 0.5f, 1f).also { cachedWaveform = it }
                    }
                }
            }
        }

        decodeStarted.await()
        assertEquals(1, decodeCount.get())
        finishDecode.complete(Unit)

        val results = requests.map { it.await() }
        assertEquals(1, decodeCount.get())
        assertEquals(List(8) { listOf(0f, 0.5f, 1f) }, results)
        assertEquals(0, gate.trackedKeyCount)
    }

    @Test
    fun differentKeysCanDecodeConcurrently() = runBlocking {
        val gate = AudioWaveformRequestGate()
        val enteredCount = AtomicInteger()
        val bothEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        suspend fun enter(key: String) = gate.withKey(key) {
            if (enteredCount.incrementAndGet() == 2) bothEntered.complete(Unit)
            release.await()
            key
        }

        val first = async(start = CoroutineStart.UNDISPATCHED) { enter("source-a") }
        val second = async(start = CoroutineStart.UNDISPATCHED) { enter("source-b") }

        withTimeout(1_000L) { bothEntered.await() }
        release.complete(Unit)

        assertEquals("source-a", first.await())
        assertEquals("source-b", second.await())
        assertEquals(0, gate.trackedKeyCount)
    }

    @Test
    fun cancelledWaiterDoesNotPoisonTheNextRequest() = runBlocking {
        val gate = AudioWaveformRequestGate()
        val ownerStarted = CompletableDeferred<Unit>()
        val releaseOwner = CompletableDeferred<Unit>()

        val owner = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withKey("shared-source") {
                ownerStarted.complete(Unit)
                releaseOwner.await()
            }
        }
        ownerStarted.await()

        val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withKey("shared-source") {
                error("A cancelled waiter must never enter the protected block")
            }
        }
        waiter.cancelAndJoin()
        releaseOwner.complete(Unit)
        owner.join()

        assertEquals("available", gate.withKey("shared-source") { "available" })
        assertEquals(0, gate.trackedKeyCount)
    }
}
