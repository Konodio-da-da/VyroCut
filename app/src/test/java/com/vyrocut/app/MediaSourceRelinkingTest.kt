package com.vyrocut.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSourceRelinkingTest {
    @Test
    fun replacementPreservesExistingTrimWhenItCoversTheWholeSourceWindow() {
        assertTrue(isClipSourceCompatible(1_500L, 7_500L, 7_500L))
        assertTrue(isClipSourceCompatible(1_500L, 7_500L, 12_000L))
    }

    @Test
    fun rejectsReplacementThatIsShorterThanTheExistingOutPoint() {
        assertFalse(isClipSourceCompatible(1_500L, 7_500L, 7_499L))
    }

    @Test
    fun rejectsInvalidExistingSourceRanges() {
        assertFalse(isClipSourceCompatible(-1L, 1_000L, 2_000L))
        assertFalse(isClipSourceCompatible(1_000L, 1_000L, 2_000L))
        assertFalse(isClipSourceCompatible(2_000L, 1_000L, 3_000L))
    }

    @Test
    fun rejectsUnknownOrEmptyReplacementDuration() {
        assertFalse(isClipSourceCompatible(0L, 1_000L, 0L))
        assertFalse(isClipSourceCompatible(0L, 1_000L, -1L))
    }
}
