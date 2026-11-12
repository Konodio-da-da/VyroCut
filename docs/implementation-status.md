# Implementation status

## Last verified build
- The Android foundation and initial editing workspace build completed successfully on GitHub Actions.
- The workflow runs `assembleDebug` and `testDebugUnitTest`, then uploads the debug APK artifact.
- The latest verified code revision before this update was `bdf06fb578f7d0f1a912d5dddb7034115f80c45d` (run [43122179138](https://github.com/Konodio-da-da/VyroCut/actions/runs/43122179138)). Both `assembleDebug` and `testDebugUnitTest` completed successfully, and the `vyrocut-debug-apk` artifact was uploaded.
- The revision's 24 timeline-domain unit tests passed as part of `testDebugUnitTest`. CI confirms compilation and unit tests; it does not replace physical-device playback or export testing.

## Editing workspace milestone (in progress)
- Media3 ExoPlayer preview with an ordered playlist of clipped source segments.
- Sequential multi-clip playback; timeline selection seeks to the selected clip.
- Continuous playback advances through imported timeline clips; selecting a timeline card seeks to that clip.
- Project-wide timeline scrubber maps elapsed project time back to the correct clip and clip-relative position.
- Timeline seek mapping is isolated in pure Kotlin helpers and has unit tests for clip boundaries, the exact project end, empty/zero-length timelines, clamping, and duration overflow.
- Timeline cards asynchronously generate frame thumbnails from their source videos; generated frames are cached on disk for reuse.
- Multiple named local projects can be created, renamed, deleted, switched, and autosaved independently; deleting the active project opens another project first and the final remaining project is protected.
- Projects can be exported to a versioned `.vyrocut.json` backup and restored as a new project through Android's document picker; malformed or unsupported backup files are rejected.
- Undo/redo supports up to 50 timeline edit states per project and persists its history across app restarts.
- Timeline audio waveforms are decoded from each clip's source audio on a background dispatcher, bucketed into 48 peak-amplitude samples, and aligned to the clip's trimmed source range.
- Video import through Android's Storage Access Framework with persisted read permission.
- Importing a clip selects the newly appended segment, including when importing into an empty project; the selected index is calculated from the pre-append list size rather than the stale Compose state.
- Timeline segment cards and selection.
- Non-destructive source in/out ranges for segments.
- Split selected segments at their midpoint while preserving left-to-right source order.
- Move the selected clip earlier or later in the ordered timeline without changing its source range.
- Duplicate a selected segment as a separate edit-decision-list entry while retaining the same source URI and in/out points.
- Trim either the selected segment's start or end by one second without modifying source media.
- Video import now rejects sources that cannot grant persistent read access or provide a valid duration, instead of silently saving a temporary or one-millisecond clip.
- Timeline duration uses saturating arithmetic so extremely long projects cannot wrap to a negative slider range.
- Delete selected segment.
- Project edit decisions autosaved locally in app preferences.
- Rebuilding the Media3 playlist after a timeline edit preserves the active clip's source-time playhead when that clip remains in the project; reorders and trims no longer automatically jump playback back to the beginning. If the active clip is removed, playback falls back to the selected remaining clip. Switching projects starts the new project at its beginning.
- Playback restoration is isolated in pure Kotlin and covered by tests for reordering, trim-start/trim-end clamping, deleted clips, and empty projects.

## Implemented in code; awaiting verification
- Media3 Transformer MP4 export wiring for the ordered clip composition, with completion/error handling, safer MediaStore publishing, temporary-file cleanup, and share flow.
- Before publishing, the completed file is checked for a non-empty MP4, a readable video track, and at least one video sample. This is a container/track check, not a substitute for decoding and watching the exported file on an Android device.
- The preview configures Android audio focus, responds to noisy-audio-route changes, and pauses when the app leaves the foreground.
- Media3 preview errors are surfaced in the editor status line instead of failing silently.

## Still to implement
- Automatic recovery snapshots beyond the persisted edit history, including recovery UI and interrupted-export recovery.
- Audio, image, text, transitions, and effects tracks.

A control is only considered complete when its operation is connected to the project state and verified by the Android build. Device playback and rendering still require hands-on testing on an Android device.
