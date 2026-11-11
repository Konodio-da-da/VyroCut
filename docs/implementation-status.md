# Implementation status

## Last verified build
- The Android foundation and initial editing workspace build completed successfully on GitHub Actions.
- The workflow runs `assembleDebug` and `testDebugUnitTest`, then uploads the debug APK artifact.
- The ordered-playlist, timeline-scrubbing, thumbnail-cache, undo/redo, and export-publishing changes are verified by GitHub Actions when the corresponding run succeeds. Waveform decoding, project rename/delete, JSON backup/restore, and persistent undo/redo history are implemented in code but are awaiting a successful build run.

## Editing workspace milestone (in progress)
- Media3 ExoPlayer preview with an ordered playlist of clipped source segments.
- Sequential multi-clip playback; timeline selection seeks to the selected clip.
- Continuous playback advances through imported timeline clips; selecting a timeline card seeks to that clip.
- Project-wide timeline scrubber maps elapsed project time back to the correct clip and clip-relative position.
- Timeline cards asynchronously generate frame thumbnails from their source videos; generated frames are cached on disk for reuse.
- Multiple named local projects can be created, renamed, deleted, switched, and autosaved independently; deleting the active project opens another project first and the final remaining project is protected.
- Projects can be exported to a versioned `.vyrocut.json` backup and restored as a new project through Android's document picker; malformed or unsupported backup files are rejected.
- Undo/redo supports up to 50 timeline edit states per project and persists its history across app restarts.
- Timeline audio waveforms are decoded from each clip's source audio on a background dispatcher, bucketed into 48 peak-amplitude samples, and aligned to the clip's trimmed source range.
- Video import through Android's Storage Access Framework with persisted read permission.
- Timeline segment cards and selection.
- Non-destructive source in/out ranges for segments.
- Split selected segment at its midpoint.
- Trim the selected segment's start by one second.
- Delete selected segment.
- Project edit decisions autosaved locally in app preferences.

## Implemented in code; awaiting verification
- Media3 Transformer MP4 export wiring for the ordered clip composition, with completion/error handling, safer MediaStore publishing, temporary-file cleanup, and share flow.

## Still to implement
- Automatic recovery snapshots beyond the persisted edit history, including recovery UI and interrupted-export recovery.
- Audio, image, text, transitions, and effects tracks.

A control is only considered complete when its operation is connected to the project state and verified by the Android build. Device playback and rendering still require hands-on testing on an Android device.
