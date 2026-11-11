# Implementation status

## Last verified build
- The Android foundation and initial editing workspace build completed successfully on GitHub Actions.
- The workflow runs `assembleDebug` and `testDebugUnitTest`, then uploads the debug APK artifact.
- The newer playlist and export commits are awaiting their own CI verification.

## Editing workspace milestone (in progress)
- Media3 ExoPlayer preview with an ordered playlist of clipped source segments.
- Sequential multi-clip playback; timeline selection seeks to the selected clip.
- Continuous playback advances through imported timeline clips; selecting a timeline card seeks to that clip.
- Video import through Android's Storage Access Framework with persisted read permission.
- Timeline segment cards and selection.
- Non-destructive source in/out ranges for segments.
- Split selected segment at its midpoint.
- Trim the selected segment's start by one second.
- Delete selected segment.
- Project edit decisions autosaved locally in app preferences.

## Implemented in code; awaiting verification
- Media3 Transformer MP4 export wiring for the ordered clip composition, with completion/error handling and publish-to-Movies/share flow.

## Still to implement
- Timeline scrubbing and waveform/thumbnail generation.
- Undo/redo and named multiple projects.
- Audio, image, text, transitions, and effects tracks.

A control is only considered complete when its operation is connected to the project state and verified by the Android build. Device playback and rendering still require hands-on testing on an Android device.
