# Implementation status

## Last verified build
- The foundation branch remains at `66c1b19decfdb202e79dcfd3817fe65f27b45b2c`; its latest recorded Android CI run [#133](https://github.com/Konodio-da-da/VyroCut/actions/runs/43174174315) passed.
- Feature branch `fix/capcut-workflow-ui` commit `ea9d1ed02b372832adfadfb2d7e3b9263af46fda` passed `gradle assembleDebug testDebugUnitTest` in [Android build run #275](https://github.com/Konodio-da-da/VyroCut/actions/runs/43424885660). The debug APK artifact was uploaded.
- The preceding documentation-only commit `bdde11e267c91d6dae713ee12900cd32f15f1dfd` passed in [run #273](https://github.com/Konodio-da-da/VyroCut/actions/runs/43418323093); the atomic-cache-write and cache-serialization commits passed in [runs #269](https://github.com/Konodio-da-da/VyroCut/actions/runs/43417200959) and #271 (see the corresponding branch run).
- The app remains `minSdk = 29` and offline-first. GitHub Actions supplies JDK 17 and the Android SDK; the ProductOS sandbox has no Java, Android SDK, Gradle installation/wrapper launcher, or adb.
- CI confirms Android compilation and JVM unit tests only. Physical-device preview, audio synchronization, Compose layout, and end-to-end MP4 playback remain unverified; see [device-validation.md](device-validation.md) for the exact blocker and manual test checklist.

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
- Persisted source URIs are checked when a project opens; unavailable or too-short sources are flagged in the selected-clip header.
- A missing clip can be relinked to another video through the document picker. The replacement must cover the existing source in/out range, and relinking preserves the clip ID and trim points so undo/redo and preview restoration remain coherent.
- Video duration and display-name metadata are read on an IO dispatcher instead of blocking the Compose UI thread.
- Importing a clip selects the newly appended segment, including when importing into an empty project; the selected index is calculated from the pre-append list size rather than the stale Compose state.
- Timeline segment cards and selection.
- Non-destructive source in/out ranges for segments.
- Split the selected segment at the active Media3 playhead, converting clip-relative playback time to source time; the left segment retains its stable ID and the right segment becomes selected at the split boundary.
- Project and undo/redo snapshots are decoded strictly. Corrupt history entries are skipped instead of being interpreted as valid empty projects; an intentionally empty project remains a valid undo state.
- History decoding retains the newest 50 valid snapshots.
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
- Saved preview restoration is covered by five additional timeline-domain tests and is included in the latest successful branch-head build.

## Implemented in code; awaiting verification
- Media3 Transformer MP4 export wiring for the ordered clip composition, with completion/error handling, safer MediaStore publishing, temporary-file cleanup, and share flow.
- Before publishing, the latest export path demuxes the complete output, counts readable video/audio samples, compares the video duration against the expected project timeline, and requires audio samples when any source clip has an audio track.
- Export validation and MediaStore copying run on an IO dispatcher rather than the Compose UI thread. A per-export session token invalidates late completion callbacks after cancellation, preventing a cancelled render from being published.
- Seven pure Kotlin tests cover missing video/audio samples, duration mismatch, video-only sources, and frame-boundary tolerance.
- These checks validate container demuxing and track/sample presence; they do not prove every frame/audio packet decodes cleanly or that the exported file plays correctly on a physical device.
- Export audio verification maps supported 8-bit, 16-bit, packed 24-bit, 32-bit and floating-point PCM formats to their actual sample widths; unknown decoder encodings now fail closed instead of being silently treated as 16-bit. Two JVM tests cover the supported widths and unsupported values.
- The preview configures Android audio focus, responds to noisy-audio-route changes, and pauses when the app leaves the foreground.
- Before Transformer starts, export preflight re-opens each distinct source on an IO dispatcher, checks duration and a readable video track, and rejects duplicate clip IDs, blank source references, invalid trims, unavailable media, trims beyond the current source duration, and timeline-duration overflow. A failed preflight stops before rendering; the export action is disabled while import metadata is being read.
- Media3 preview errors are surfaced in the editor status line instead of failing silently.
- The editor listens for position discontinuities from Media3's built-in PlayerView controller, so controller seeks update the selected timeline clip, project scrubber, and saved preview checkpoint; controller pauses also checkpoint the current position. The restored final playable frame is treated as project end so Play restarts from the beginning.
- Controller-position mapping and end-of-project restart behavior are covered by four additional pure Kotlin tests and are included in the latest successful branch-head build.

## Local project recovery milestone (implemented; Android build and unit-test workflow passed)
- Every project save writes the primary edit list and a separate versioned recovery journal in the same SharedPreferences edit.
- The journal stores the last persisted clip list, project identity/name, timestamp, and SHA-256 integrity checksum; UTF-8 fields are URL-safe Base64 encoded.
- At startup, a valid journal is offered only when the primary project value is missing or differs. The user can recover the saved clip list or dismiss that exact checkpoint; a later save clears the dismissal.
- Recovery validates every clip entry strictly before replacing project data, preserving the current project if the checkpoint is malformed.
- Five pure Kotlin tests cover Unicode round-tripping, truncation/tampering, project isolation, mismatch detection, and identical-checkpoint suppression.
- This protects the last persisted edit list if the primary project preference value is damaged or lost. The journal is stored as a separate key in the same SharedPreferences file, so it does not survive loss of the entire preference file. It does not recover edits that had not yet been saved, source media bytes, or interrupted Transformer exports.
- Physical-device validation of continuous multi-clip playback, source relinking, and actual MP4 decoding/audio continuity.
- Recovery of unpersisted in-memory edits and interrupted Transformer exports; the current journal protects the last persisted project edit list only.
- Audio, image, text, transitions, and effects tracks.

A control is only considered complete when its operation is connected to the project state and verified by the Android build. Device playback and rendering still require hands-on testing on an Android device.
