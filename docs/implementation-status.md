## Current VFR timestamp-index ownership milestone — 15 November 2026
- The bounded three-entry VFR timestamp LRU now stores a defensive copy of every valid decoder-produced `LongArray`. Later reuse or mutation of a decoder output buffer cannot silently corrupt a cached presentation-timestamp index.
- Unsupported-source `null` entries and existing LRU recency semantics are unchanged; invalid indexes are still rejected before storage.
- A pure Kotlin regression test mutates the original decoder array after insertion and verifies that the cached snapshot remains ordered and valid.
- Last verified baseline before this change: `8a4b1d8fde3650496b9fab6a403bfb7ab6bb56fa` passed Android compilation, JVM unit tests, and debug APK upload in [run #433](https://github.com/Konodio-da-da/VyroCut/actions/runs/43698718551). This new commit requires its own Actions result.

## Current VFR timestamp-index LRU milestone — 15 November 2026
- Successful timestamp-index cache hits now refresh insertion order, so the three-entry cache behaves as a true least-recently-used cache rather than evicting by original insertion order.
- Both decoded indexes and cached unsupported-source results participate in recency updates. A lookup for a missing key does not insert an entry.
- The frame-step request updates the Compose cache state after all active/adjacent lookups, retaining the most recently used indexes across requests.
- Pure Kotlin regression tests cover hot-entry retention, unsupported-source recency, bounded eviction, and missing-key no-op behaviour.
- Verified baseline: source-revision-aware VFR frame stepping at `fc36396798a74924fa60f8d5731faf78d15f8c66` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build run #431](https://github.com/Konodio-da-da/VyroCut/actions/runs/43693767169).

## Current VFR source-change cache milestone — 15 November 2026
- VFR presentation-timestamp indexes now include a best-effort source revision (provider-reported byte size and last-modified time) in addition to URI and source trim bounds.
- The revision is queried on Dispatchers.IO for the active and adjacent clips before index lookup. Two adjacent clips referencing the same URI share one metadata query for that frame-step request.
- Provider metadata uses OpenableColumns.SIZE, document last-modified time, and MediaStore DATE_MODIFIED fallback. If no usable metadata is available, the cache safely retains URI/range identity; same-URI in-place replacement cannot be detected in that fallback.
- Pure Kotlin tests cover partial/missing metadata normalization and revision-aware VFR cache-key separation. Actual provider metadata and replacement behaviour require physical-device testing.
- Verified source-revision-aware VFR baseline: commit `fc36396798a74924fa60f8d5731faf78d15f8c66` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build run #431](https://github.com/Konodio-da-da/VyroCut/actions/runs/43693767169).

## Current audio waveform lifecycle milestone — 15 November 2026
- Audio waveform extraction is now a cancellable suspend operation. It checks the owning coroutine before opening media, during track inspection, after decoder startup, and on every MediaCodec input/output polling cycle.
- Coroutine cancellation is rethrown rather than converted into a normal decode failure, so closing the Audio tool, switching the selected lane, changing the selected clips, or disposing a waveform composable can stop obsolete work. MediaCodec and MediaExtractor are still released in `finally`.
- The decoder's no-progress watchdog is isolated into pure Kotlin policy helpers. JVM tests cover cancellation/finished/timeout loop decisions, progress resets, invalid limits, and overflow-safe idle counting.
- Both existing call sites run the decoder inside `Dispatchers.IO`; no network access or project/media mutation is introduced. Android CI must verify compilation and unit tests. Actual codec cancellation latency and decoder release still require physical-device testing.

# Implementation status

## Current frame-stepping milestone — 15 November 2026
- Verified baseline fde3fe7bed3a85367b792154bf0b8983a1e2075a passed assembleDebug, testDebugUnitTest, and debug APK upload in [Android build run #386](https://github.com/Konodio-da-da/VyroCut/actions/runs/43647299815).
- The next improvement calculates preview frame positions against each clip's original source timestamp grid, then converts the selected source frame back to the trimmed Media3 item's local position. This addresses non-frame-aligned source IN points without modifying source media or the edit decision list.
- Three pure Kotlin regression tests cover non-aligned trim offsets, mixed-rate transitions between trimmed clips, and trims too short to contain a source presentation frame.
- The new commit's Android build and JVM tests must pass in GitHub Actions before this milestone is considered verified. The ProductOS sandbox has no Java, Gradle, Android SDK, ADB, emulator, or Gradle wrapper launcher; physical-device checks remain outstanding.
- Nominal frame-rate stepping does not establish correct frame navigation for variable-frame-rate sources. VFR presentation timestamps and actual displayed frames still require a dedicated device/source-metadata validation pass.

## Last verified build
- Feature head `9a03acd2ab9226fad34f648b6420b6fa548c76e3` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build run #366](https://github.com/Konodio-da-da/VyroCut/actions/runs/43615113584). This is the verified baseline before manual-scroll-aware timeline auto-follow.
- Feature head `3c3b5bf9d1ed0cd9ce79cfd214ec200b7979be0f` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build run #342](https://github.com/Konodio-da-da/VyroCut/actions/runs/43589993760). This is the verified baseline before adding non-destructive audio-lane ripple delete.
- Feature head `30b8c30a38df9d55dcafc847fbb1ee73360647e3` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build run #346](https://github.com/Konodio-da-da/VyroCut/actions/runs/43594393777). This is the verified baseline before adding non-destructive audio ripple trim.
- Feature head `300c55b9784c53ca8445848ad905b74216dd4406` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build run #324](https://github.com/Konodio-da-da/VyroCut/actions/runs/43565749117). This is the verified baseline before adding the portable preview checkpoint to backup format v13.
- Feature head `68f02c3b4804aaded90bac606650c69e9219b406` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build run #338](https://github.com/Konodio-da-da/VyroCut/actions/runs/43579995000). This is the verified baseline before adding non-destructive audio-clip duplication.
- Feature head `393b7fb204a67ea9de859a17b66cf4a68cf50136` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build run #318](https://github.com/Konodio-da-da/VyroCut/actions/runs/43555985260). This includes the six-rate preview playback control and shared Media3 playback parameters; the next change persists that rate per project.
- The foundation branch remains at `66c1b19decfdb202e79dcfd3817fe65f27b45b2c`; its latest recorded Android CI run [#133](https://github.com/Konodio-da-da/VyroCut/actions/runs/43174174315) passed.
- Feature branch `fix/capcut-workflow-ui` commit `b9b7bb4113b8ef27af74f2c762de7e1295752e82` passed `assembleDebug` and `testDebugUnitTest` in [Android build run #295](https://github.com/Konodio-da-da/VyroCut/actions/runs/43490355930). This fixed the missing `MediaFormat` import exposed by the earlier failed [run #293](https://github.com/Konodio-da-da/VyroCut/actions/runs/43482479845). The debug APK artifact was uploaded.
- Current feature head `857be483f6cc447a19bb805876280c1322b331d4` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build run #300](https://github.com/Konodio-da-da/VyroCut/actions/runs/43500397549). The latest change adds age-based cleanup for abandoned waveform-cache temporary files; it does not affect source media or project data.
- The preceding documentation-only commit `bdde11e267c91d6dae713ee12900cd32f15f1dfd` passed in [run #273](https://github.com/Konodio-da-da/VyroCut/actions/runs/43418323093); the atomic-cache-write and cache-serialization commits passed in [runs #269](https://github.com/Konodio-da-da/VyroCut/actions/runs/43417200959) and #271 (see the corresponding branch run).
- Feature head `483849528e85cf86a5b29de0ca14a5ee5dcda155` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build run #308](https://github.com/Konodio-da-da/VyroCut/actions/runs/43534010417). The ruler endpoint/cap regression tests were included.
- Feature head `c6ff8cc155f8dd4a2ae71120558475a7f5e1c62e` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build run #310](https://github.com/Konodio-da-da/VyroCut/actions/runs/43540144801). Repeated export audio-source inspections are deduplicated in first-use order.
- The app remains `minSdk = 29` and offline-first. GitHub Actions supplies JDK 17 and the Android SDK; the ProductOS sandbox has no Java, Android SDK, Gradle installation/wrapper launcher, or adb.
- CI confirms Android compilation and JVM unit tests only. Physical-device preview, audio synchronization, Compose layout, and end-to-end MP4 playback remain unverified; see [device-validation.md](device-validation.md) for the exact blocker and manual test checklist.

## Editing workspace milestone (in progress)
- Timeline auto-follow now yields after a user-initiated horizontal drag, so playback no longer pulls the shared timeline viewport away while the user inspects another region. Auto-follow resumes after a direct timeline tap/scrub or when playback is restarted. The pure Kotlin target resolver has a regression test for the manual override; real gesture/scroll behaviour remains pending physical-device validation.
- Timeline playhead sampling now runs at 30 Hz while Media3 is playing and 10 Hz while paused, instead of polling every 200 ms. Preview checkpoint throttling uses monotonic `SystemClock.elapsedRealtime()` and writes the first active sample immediately, then at two-second intervals. Pure Kotlin tests cover the cadence, immediate first checkpoint, exact interval boundary, clock regression, and invalid intervals; actual smoothness and device load remain pending physical-device validation.
- Preview playback speed cycles through 0.5×, 0.75×, 1×, 1.25×, 1.5× and 2×; the selected Media3 playback parameters are applied to the video player and all Music/SFX/Voiceover players so their clocks advance at the same rate. The selected rate is stored per project and included in v13 JSON backups, restored when switching projects or importing a backup, normalized to 1× if missing/corrupt/unsupported, and removed with the project. Pure Kotlin tests cover cycling, normalization, project-key isolation, labels and backup migration; physical-device audio-sync and persistence checks remain pending.
- Editor-originated seeks (skip controls, project slider, video/audio/text timeline selection) resolve one canonical project-clock target and immediately seek the video plus each prepared Music/SFX/Voiceover playlist, including silence-gap segments. Media3 position-discontinuity and media-item-transition callbacks are collected and coalesced in `onEvents`, so one callback batch updates the project playhead, clip effects and preview checkpoint once. If all reported reasons are natural auto-transitions, continuous gap-filled audio playlists are not re-seeked; if either callback reports an explicit seek or playlist edit, all lanes are realigned immediately. The periodic drift guard remains as a fallback. Audio playlist rebuilds are project-scoped so stale lane sequences are paused while another project's playlists are prepared. Pure Kotlin tests cover synchronized targets across different lane gaps, the final playable project millisecond, automatic callback batches, mixed automatic/explicit reasons, and batches without timeline events.
- Media3 ExoPlayer preview with an ordered playlist of clipped source segments.
- Sequential multi-clip playback; timeline selection seeks to the selected clip.
- Continuous playback advances through imported timeline clips; selecting a timeline card seeks to that clip.
- Project-wide timeline scrubber maps elapsed project time back to the correct clip and clip-relative position.
- Timeline ruler generation uses the same absolute-time-to-pixel mapping as the playhead, always includes the project-end marker, and is strictly capped at 2,000 Compose ticks even when the duration lands exactly on a regular interval.
- Timeline seek mapping is isolated in pure Kotlin helpers and has unit tests for clip boundaries, the exact project end, empty/zero-length timelines, clamping, and duration overflow.
- Timeline cards asynchronously generate frame thumbnails from their source videos; generated frames are cached on disk for reuse.
- Multiple named local projects can be created, renamed, deleted, switched, and autosaved independently; deleting the active project opens another project first and the final remaining project is protected.
- Projects can be exported to a versioned `.vyrocut.json` backup and restored as a new project through Android's document picker; version 13 preserves preview playback speed plus the current clip's stable ID and absolute source-time checkpoint. Version 1–12 backups remain compatible; v1–v11 default to 1× and v1–v12 have no portable preview checkpoint. Malformed or unsupported backup files are rejected.
- Version 13 project backups also capture the current preview checkpoint by stable clip ID and absolute source timestamp. Export validates that the checkpoint belongs to exactly one exported clip and lies within its non-destructive source range; invalid or absent checkpoints are omitted. Restoring imports the checkpoint into the new project's local preferences so the existing project-open restoration path resumes at the same source frame.
- Undo/redo supports up to 50 timeline edit states per project and persists its history across app restarts.
- Timeline audio waveforms are decoded from each clip's source audio on a background dispatcher, bucketed into 48 peak-amplitude samples, and aligned to the clip's trimmed source range.
- Music, SFX, and voiceover clips support non-destructive ripple delete: removing one item shifts only later items on that same lane left by its duration. Earlier clips, other lanes, source ranges, volume, fades, and gaps between later items are preserved. The operation uses unified audio undo/redo and local persistence; pure Kotlin tests cover lane isolation, preserved metadata, combined pre/post gaps, final-item deletion, and invalid input.
- Music, SFX, and voiceover clips support non-destructive ripple trim from the source IN/OUT controls. Shortening shifts only later clips on the same lane earlier by the duration delta; extending shifts only later clips later. Existing inter-clip gaps, earlier clips, other lanes, source media, volume, and fade metadata are preserved (fade durations are clamped to the new clip duration). The operation is rejected atomically if the source range is invalid or a shifted clip would exceed project duration. It uses unified audio undo/redo and local persistence; pure Kotlin tests cover both directions, gap preservation, metadata, fade clamping, and rejection without mutation.
- Music, SFX, and voiceover clips can be duplicated non-destructively into the first free slot at or after the selected clip's end on the same lane. Duplicates retain the source URI/range, volume and fades, receive a new stable ID, respect same-lane non-overlap and the 24-clip project cap, and use the unified audio undo/redo and local persistence path. Pure Kotlin tests cover first-gap placement, skipping undersized gaps, metadata preservation, and rejection when the project has no room.
- Music, SFX, and voiceover clips can be split at the project playhead without modifying source media. The left half retains the original clip ID and fade-in, the right half gets a new ID and retains the fade-out; splits inside an active fade ramp are rejected because the current fade model cannot represent a non-zero starting gain on the new item. Split edits use the existing unified audio undo/redo and persistence path. Pure Kotlin tests verify contiguous source ranges and sample-by-sample gain-envelope equivalence.
- Waveform cache filenames include the requested bucket count, so compact and detailed resolutions for the same source range cannot overwrite or invalidate each other's cache entries. Successful cache reads refresh their access timestamp, and the bounded 64-entry cache evicts least-recently-used entries with deterministic tie-breaking.
- Waveform cache payloads are versioned and CRC32-checked; each decoder writes through a unique same-directory temporary file and prefers atomic replacement, preventing concurrent requests from clobbering one shared temporary path.
- Waveform cache startup removes only app-owned temporary write files older than 24 hours; recent in-flight writes and completed cache entries are retained. The age/name predicate is covered by JVM unit tests.
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
- Before Transformer starts, export preflight re-opens each distinct video source on an IO dispatcher, checks duration and a readable video track, and rejects duplicate clip IDs, blank source references, invalid trims, unavailable media, trims beyond the current source duration, and timeline-duration overflow. Audio-source duration/MIME checks also run on IO; repeated audio URIs are inspected once per preflight pass. Post-export source-audio detection likewise deduplicates URIs before opening MediaExtractors. A failed preflight stops before rendering; the export action is disabled while import metadata is being read.
- Media3 preview errors are surfaced in the editor status line instead of failing silently.
- The editor listens for position discontinuities from Media3's built-in PlayerView controller, so controller seeks update the selected timeline clip, project scrubber, and saved preview checkpoint; controller pauses also checkpoint the current position. The restored final playable frame is treated as project end so Play restarts from the beginning.
- Controller-position mapping and end-of-project restart behavior are covered by four additional pure Kotlin tests and are included in the latest successful branch-head build.

## Local project recovery milestone (implemented; Android build and unit-test workflow passed)
- Every project save writes the primary edit list and a separate versioned recovery journal in the same SharedPreferences edit.
- Video edit decisions, audio-lane data, and both recovery journals are written together in one SharedPreferences editor transaction for normal saves, new projects, backup imports, checkpoint recovery, and audio-only edits. Both journals share a monotonic checkpoint timestamp, preventing video/audio recovery copies from representing different save moments. A separate app-private file mirror is written on Dispatchers.IO with a synced temporary file and atomic-replace preference.
- Recovery now restores audio lanes from the audio journal only when its project identity and checkpoint timestamp match the video recovery journal; mismatched, corrupt, or legacy audio journals fall back to the current lanes trimmed to the recovered video duration. Three JVM regression tests cover matching timestamps, mismatches, and invalid/foreign journals.
- The journal stores the last persisted clip list, project identity/name, timestamp, and SHA-256 integrity checksum; UTF-8 fields are URL-safe Base64 encoded.
- At startup, a valid journal is offered only when the primary project value is missing or differs. The user can recover the saved clip list or dismiss that exact checkpoint; a later save clears the dismissal.
- Recovery validates every clip entry strictly before replacing project data, preserving the current project if the checkpoint is malformed.
- Five pure Kotlin tests cover Unicode round-tripping, truncation/tampering, project isolation, mismatch detection, and identical-checkpoint suppression. Additional tests verify that video and audio recovery journals share one checkpoint timestamp and reject invalid checkpoint identity/timestamps.
- The SharedPreferences journal protects the last persisted edit list if its primary project value is damaged. A second checksummed copy is mirrored to app-private `filesDir/project-recovery` files, outside SharedPreferences, and can recover project-specific values while the project index remains intact. The mirror does not recover a lost project index or entire app-data directory, media bytes, edits that had not yet been saved, or interrupted Transformer exports.
- Physical-device validation of continuous multi-clip playback, source relinking, and actual MP4 decoding/audio continuity.
- Recovery of unpersisted in-memory edits and interrupted Transformer exports; the SharedPreferences journal and independent file mirror protect only the last persisted edit list and matching audio-lane checkpoint.
- Audio, image, text, transitions, and effects tracks.

A control is only considered complete when its operation is connected to the project state and verified by the Android build. Device playback and rendering still require hands-on testing on an Android device.


## Manual timeline-scroll override CI repair
- The timeline now observes pointer movement on the Initial pass and only suspends auto-follow after horizontal movement exceeds the device touch slop.
- The observer does not consume pointer changes; the existing horizontal scroll modifier remains the sole owner of timeline scrolling.
- Vertical gestures and small incidental movements do not suspend auto-follow. Pure Kotlin tests cover horizontal intent, touch-slop boundaries, vertical movement, and invalid measurements.
- This replaces an unsupported `interactionSource` argument on the pinned Compose `horizontalScroll` API. The previous PR run failed at Kotlin compilation on that argument; the replacement is designed against the project's Compose BOM and must be confirmed by the next Android CI run.


## Exact preview seeking for editor actions
- User-initiated timeline, transport, audio-clip, and text-layer seeks plus audio drift corrections now request Media3 EXACT seek parameters so the preview/audio players decode from the preceding sync sample to the requested timestamp.
- Playlist reconstruction/restoration uses Media3 CLOSEST_SYNC seeking to avoid decoding from a preceding keyframe during project reloads.
- The seek-intent-to-precision decision is pure Kotlin and covered by a JVM regression test. Exact frame presentation, seek latency, mixed-frame-rate behaviour, and audio continuity still require physical-device validation.


## Frame-by-frame preview navigation
- The preview transport exposes previous/next-frame actions when the active Media3 track or cached source metadata provides a valid frame rate.
- Frame stepping uses the active track's selected format and per-source metadata for adjacent clips rather than assuming a project-wide 24/25/30 fps rate. Unknown, audio-only, non-finite, or implausible rates fail closed at the relevant clip boundary.
- Fractional rates such as 23.976, 29.97, and 59.94 are converted through frame indices, avoiding repeated rounded-duration accumulation. Frame steps stay inside the active clip when possible and cross an adjacent cut using that source's cached frame rate; missing adjacent metadata fails closed.
- Frame navigation uses the existing exact-seek path, so video, Music, SFX, and Voiceover remain aligned to the project clock. The action pauses playback before seeking.
- Pure Kotlin tests cover selected-track filtering, integer/fractional rates, repeated frame-step rounding, invalid metadata, within-clip stepping, and cross-clip boundaries. Device-only frame presentation and mixed-rate cut behaviour remain pending physical-device validation.


## Per-source frame-rate metadata and cross-clip frame stepping (implemented; CI pending)
- Frame-rate metadata is now inspected once per distinct video source URI on `Dispatchers.IO`. Capture-frame-rate metadata is preferred to retain fractional rates; the selected video track's `MediaFormat.KEY_FRAME_RATE` is the fallback. Media3's selected active-track rate remains authoritative and refreshes the per-URI cache.
- Previous/next-frame navigation now continues across an adjacent playlist cut instead of stopping at every clip edge. The adjacent clip's own cached frame rate is used, so a 24 fps clip can step into a 59.94 fps clip without applying one clip's rate to the other.
- Unknown or invalid adjacent frame-rate metadata fails closed at the cut; the resolver never guesses a neighbouring clip's rate. The existing exact-seek and synchronized project-clock path is reused for the resulting target.
- Pure Kotlin tests cover forward/backward cut crossing with different frame rates, within-clip stepping, missing adjacent metadata, and mismatched input lists. The background metadata reader and actual displayed-frame/audio behaviour still require Android CI and physical-device validation respectively.


## MediaStore export-copy integrity
- The completed temporary MP4 is fingerprinted only after demux and decoder validation succeeds. Publication is tied to that exact byte count and SHA-256 digest.
- The MediaStore copy is hashed while streaming from the private temporary file, then read back and hashed again while the row is still pending. The row becomes visible only when both fingerprints match the verified temporary MP4; failed copies are deleted.
- Stream processing uses a fixed 64 KiB buffer and never loads the full MP4 into memory. Pure JVM tests cover exact copies, same-length corruption, truncation, empty files, and malformed fingerprints. The MediaStore provider read-back path still requires physical-device validation on API 29 and current Android releases.

## Startup cleanup for abandoned Transformer output
- The app now scans its app-private `Movies/VyroCut` temporary directory once per process at Activity startup, on `Dispatchers.IO`, instead of waiting until another export completes.
- Startup cleanup and new export rendering share one pure path resolver: app-specific external Movies storage is preferred, with `filesDir/Movies/VyroCut` as the fallback.
- The existing 24-hour retention, strict generated-name filter, direct-child restriction, symlink rejection, and fail-closed timestamp handling remain unchanged. The publishing-time cleanup still protects the current verified export.
- JVM tests cover both directory-resolution paths. Physical-device validation must still confirm cleanup across process restarts and storage-volume availability on API 29 and a current Android release.


## Deduplicated audio waveform decoding
- Concurrent requests with the same source URI, source IN/OUT range, and bucket resolution now share a per-key suspend gate. The first cache miss opens the MediaExtractor/MediaCodec pipeline; waiting callers re-check the disk cache after the first request writes its waveform.
- Different source ranges and resolutions use independent keys and can decode concurrently. The gate reference-counts waiters and removes idle keys, so the process does not retain one mutex per waveform ever requested.
- Cancelling a waiter while it is queued releases only that waiter's reference; it does not cancel the active decode owner. Cancelling the owner still propagates through the existing decoder cancellation path and releases MediaCodec/MediaExtractor in `finally`.
- Pure Kotlin tests cover one cache fill for concurrent identical requests, parallel work for different keys, and cancellation of a queued waiter without poisoning subsequent requests. Cache-write failure remains best-effort: a later caller may retry the decode if no valid disk cache was produced.


## Audio waveform source-change invalidation
- Waveform cache keys now include a best-effort source revision built from provider-reported byte size and last-modified time, in addition to URI, source trim, and bucket resolution.
- Document-provider last-modified values are treated as milliseconds; MediaStore DATE_MODIFIED values are converted from seconds. Metadata is queried on Dispatchers.IO whenever the Audio tool/lane effect runs.
- If a provider exposes no usable revision metadata, caching falls back to URI/range identity without blocking waveform generation. In-place replacement cannot be detected reliably for providers that hide both size and modification time.
- Pure Kotlin tests cover changed size/time revisions, stable revisions, partial metadata, and unavailable metadata.


## Variable-frame-rate preview frame stepping (implemented; Android build and JVM unit-test workflow passed)
- Frame navigation can use actual MediaExtractor presentation timestamps for the active clip and its adjacent clip instead of assuming one constant frame rate.
- Timestamp extraction seeks to the preceding sync sample and scans only the clip's original source IN/OUT range on Dispatchers.IO. The scan checks coroutine cancellation on each sample and rejects out-of-order timestamps rather than using a partial index.
- Each per-trim index is bounded to 120,000 timestamps. The in-memory LRU retains at most three URI/source-IN/source-OUT indexes; unsupported, oversized, or malformed indexes fall back to the existing nominal frame-rate resolver.
- Pure Kotlin tests cover irregular VFR intervals, non-frame-aligned trim boundaries, first/last available frames, VFR cut crossing, invalid timestamp sequences, and LRU eviction.
- Media3 still accepts millisecond seek positions, so source PTS values are rounded only when constructing the final seek target. Actual frame presentation, MediaExtractor timestamp ordering across devices, seek latency, and audio continuity remain subject to physical-device validation.
