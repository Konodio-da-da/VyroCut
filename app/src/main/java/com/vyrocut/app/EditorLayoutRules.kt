package com.vyrocut.app

internal const val MIN_EDITOR_PREVIEW_HEIGHT_DP = 112
internal const val MAX_EDITOR_PREVIEW_HEIGHT_DP = 168

/**
 * Keeps the editor preview proportional on short windows while capping it on
 * tall windows. The surrounding editor column scrolls so controls remain reachable.
 */
internal fun editorPreviewHeightDp(windowHeightDp: Int): Int =
    (windowHeightDp.coerceAtLeast(0) / 5)
        .coerceIn(MIN_EDITOR_PREVIEW_HEIGHT_DP, MAX_EDITOR_PREVIEW_HEIGHT_DP)
