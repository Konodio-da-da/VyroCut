# Implementation status

## Last verified build
- The Android foundation and initial editing workspace build completed successfully on GitHub Actions.
- The workflow runs `assembleDebug` and `testDebugUnitTest`, then uploads the debug APK artifact.
- The current branch head `02ba0e5671ea3c7056ffe3ea360c964537e8722f` passed `assembleDebug` and `testDebugUnitTest` in [run 43136495031](https://github.com/Konodio-da-da/VyroCut/actions/runs/43136495031); the debug APK artifact was uploaded.
- All 38 tests passed on that revision (31 timeline-domain tests and 7 export-validation rule tests). The next preview-controller synchronization change is awaiting its own CI run. CI confirms compilation and unit tests; it does not replace physical-device playback or export testing.

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
- Each project now remembers its preview location by stable clip ID and absolute source timestamp. The preview location is checkpointed during playback and on timeline seeks, clip changes, manual pause, and app backgrounding; reopening or switching back to a project restores the saved location and clamps it if the source range was trimmed.
- Saved preview restoration is covered by five additional timeline-domain tests (29 total); the new code is awaiting GitHub Actions verification.

## Implemented in code; awaiting verification
- Media3 Transformer MP4 export wiring for the ordered clip composition, with completion/error handling, safer MediaStore publishing, temporary-file cleanup, and share flow.
- Before publishing, the latest export path demuxes the complete output, counts readable video/audio samples, compares the video duration against the expected project timeline, and requires audio samples when any source clip has an audio track.
- Export validation and MediaStore copying run on an IO dispatcher rather than the Compose UI thread. A per-export session token invalidates late completion callbacks after cancellation, preventing a cancelled render from being published.
- Seven pure Kotlin tests cover missing video/audio samples, duration mismatch, video-only sources, and frame-boundary tolerance.
- These checks validate container demuxing and track/sample presence; they do not prove every frame/audio packet decodes cleanly or that the exported file plays correctly on a physical device.
- The preview configures Android audio focus, responds to noisy-audio-route changes, and pauses when the app leaves the foreground.
- Media3 preview errors are surfaced in the editor status line instead of failing silently.
- The editor listens for position discontinuities from Media3's built-in PlayerView controller, so controller seeks update the selected timeline clip, project scrubber, and saved preview checkpoint; controller pauses also checkpoint the current position. The restored final playable frame is treated as project end so Play restarts from the beginning.
- Controller-position mapping and end-of-project restart behavior are covered by four additional pure Kotlin tests (42 total); the integration is awaiting GitHub Actions verification.

## Still to implement
- Automatic recovery snapshots beyond the persisted edit history, including recovery UI and interrupted-export recovery.
- Audio, image, text, transitions, and effects tracks.

A control is only considered complete when its operation is connected to the project state and verified by the Android build. Device playback and rendering still require hands-on testing on an Android device.
