package com.vyrocut.app

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe cancellation signal shared by the export UI and blocking
 * verification/publication work running on Dispatchers.IO.
 */
internal class ExportCancellationToken {
    private val cancelled = AtomicBoolean(false)

    val isCancelled: Boolean
        get() = cancelled.get()

    fun cancel() {
        cancelled.set(true)
    }

    fun throwIfCancelled() {
        if (isCancelled) throw ExportCancelledException()
    }
}

internal class ExportCancelledException : IllegalStateException("Export cancelled")
