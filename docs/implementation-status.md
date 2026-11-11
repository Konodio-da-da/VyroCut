# Implementation status

## Verified build
- The Android foundation build completed successfully on GitHub Actions.
- The workflow runs `assembleDebug` and `testDebugUnitTest`, then uploads the debug APK artifact.

## Editing workspace milestone (in progress)
- Media3 ExoPlayer preview with an ordered playlist of clipped source segments.
- Continuous playback advances through imported timeline clips; selecting a timeline card seeks to that clip.
- Video import through Android's Storage Access Framework with persisted read permission.
- Timeline segment cards and selection.
- Non-destructive source in/out ranges for segments.
- Split selected segment at its midpoint.
- Trim the selected segment's start by one second.
- Delete selected segment.
- Project edit decisions autosaved locally in app preferences.

## Not yet implemented
- Multi-clip export / composition (not yet implemented).
- Timeline scrubbing and waveform/thumbnail generation.
- Undo/redo and named multiple projects.
- Media3 Transformer export.
- Audio, image, text, transitions, and effects tracks.

A control is only considered complete when its operation is connected to the project state and verified by the Android build. Device playback and rendering still require hands-on testing on an Android device.
