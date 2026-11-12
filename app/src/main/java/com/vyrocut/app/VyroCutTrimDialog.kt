package com.vyrocut.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun VyroCutTrimDialog(
    clipId: String,
    clipName: String,
    sourceDurationMs: Long,
    initialRange: ClipRange,
    isLoadingSource: Boolean,
    sourceReadFailed: Boolean,
    onDismiss: () -> Unit,
    onApply: (ClipRange) -> Unit,
) {
    val maximumSourceMs = maxOf(sourceDurationMs, initialRange.endMs, 1L)
    var selectedRange by remember(clipId, initialRange, maximumSourceMs) {
        mutableStateOf(initialRange)
    }
    val validRange = clampTrimRange(
        sourceDurationMs = maximumSourceMs,
        requestedStartMs = selectedRange.startMs,
        requestedEndMs = selectedRange.endMs,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trim clip") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(clipName, fontWeight = FontWeight.SemiBold)
                if (isLoadingSource) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Reading original video duration…")
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("IN", fontWeight = FontWeight.Bold)
                            Text(formatTrimTime(selectedRange.startMs))
                        }
                        Column {
                            Text("OUT", fontWeight = FontWeight.Bold)
                            Text(formatTrimTime(selectedRange.endMs))
                        }
                    }
                    RangeSlider(
                        value = selectedRange.startMs.toFloat()..selectedRange.endMs.toFloat(),
                        onValueChange = { values ->
                            clampTrimRange(
                                sourceDurationMs = maximumSourceMs,
                                requestedStartMs = values.start.toLong(),
                                requestedEndMs = values.endInclusive.toLong(),
                            )?.let { selectedRange = it }
                        },
                        valueRange = 0f..maximumSourceMs.toFloat().coerceAtLeast(1f),
                        steps = 0,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoadingSource,
                    )
                    Text(
                        "Selected length  ${formatTrimTime((selectedRange.endMs - selectedRange.startMs).coerceAtLeast(0L))}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (sourceReadFailed) {
                            "The full source length could not be read. The current out point is the available limit."
                        } else {
                            "Drag either handle to adjust the source in and out points. The original video stays untouched."
                        },
                    )
                    Text("Minimum clip length: 0.1 seconds")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { validRange?.let(onApply) },
                enabled = !isLoadingSource && validRange != null && validRange != initialRange,
            ) { Text("Apply trim") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatTrimTime(milliseconds: Long): String {
    val safeMs = milliseconds.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1_000L
    val tenths = (safeMs % 1_000L) / 100L
    return "%02d:%02d.%d".format(totalSeconds / 60L, totalSeconds % 60L, tenths)
}
