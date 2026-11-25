package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCancellationTokenTest {
    @Test
    fun tokenStartsActiveAndCancellationIsIdempotent() {
        val token = ExportCancellationToken()

        assertFalse(token.isCancelled)
        token.throwIfCancelled()

        token.cancel()
        assertTrue(token.isCancelled)
        token.cancel()
        assertTrue(token.isCancelled)
    }

    @Test
    fun cancelledTokenThrowsAnExplicitExportCancellationException() {
        val token = ExportCancellationToken()
        token.cancel()

        val error = try {
            token.throwIfCancelled()
            null
        } catch (caught: ExportCancelledException) {
            caught
        }

        assertTrue(token.isCancelled)
        assertEquals("Export cancelled", error?.message)
    }

    @Test
    fun cancellationIsVisibleAcrossThreads() {
        val token = ExportCancellationToken()
        val cancelThread = Thread { token.cancel() }

        cancelThread.start()
        cancelThread.join()

        assertTrue(token.isCancelled)
    }
}
