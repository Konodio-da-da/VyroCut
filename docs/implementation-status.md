## Current export codec-diagnostics milestone — 24 November 2026
- Export failure classification now distinguishes explicit encoder failures, decoder failures, and generic MediaCodec/codec failures instead of treating the substring `codec` inside `decoder` as proof of an encoder problem.
- Verification-stage decode failures are reported as invalid output; the classifier still avoids exposing provider exception text, source URIs, or private file paths.
- Pure Kotlin regression tests cover decoder-versus-encoder classification, neutral generic codec guidance, verification-stage invalid output, cyclic cause chains, and the eight-throwable traversal bound.
- Previous feature head `afd34b798cdf8fd11c77e6ac7f877ceee35f6911` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build #667](https://github.com/Konodio-da-da/VyroCut/actions/runs/45458712144). This diagnostic change requires its own Actions result.
- Physical-device export failure behavior and real codec exception mappings remain unverified.

## Current Android export-sharing handoff milestone — 24 November 2026
- The verified MediaStore MP4 is shared through Android's standard ACTION_SEND chooser with video/mp4, EXTRA_STREAM, ClipData, and temporary read-URI grants on the share and chooser intents.
- The Share MP4 action reports a recoverable message if Android cannot launch a compatible chooser target or denies access to the published URI. Opening the chooser is not described as a completed share.
- Pure Kotlin tests cover the failure-recovery copy. GitHub Actions can verify compilation and JVM tests; actual receiver-app URI access and chooser behavior still require physical-device validation.

## Current VFR decode-order normalization milestone — 15 November 2026
- MediaExtractor timestamp collection no longer assumes that sample presentation timestamps are monotonically increasing in compressed-sample iteration order. B-frame streams can carry composition timestamps that move backwards while samples remain in decode order.
- The bounded scanner now collects source-range samples through two sync samples at/after trim OUT, so a leading B-frame after the first post-trim sync sample is not discarded by an early PTS cutoff. The scan is capped at 120,000 visited samples and returns no index rather than publishing a partial one when the cap is reached.
- A pure Kotlin normalizer sorts the collected PTS values into presentation order, removes duplicate timestamps, and rejects missing, negative, or oversized input. The existing strict timestamp-index validator and three-entry LRU continue to receive only sorted, unique arrays.
- Regression tests cover decode-order normalization, duplicate removal, defensive ownership, invalid/oversized inputs, and the two-sync lookahead counter.
- Last verified baseline: `ba74f476fed9c68146c9365ed69c6329f0ea9a3a` passed `assembleDebug`, `testDebugUnitTest`, and debug APK upload in [Android build #435](https://github.com/Konodio-da-da/VyroCut/actions/runs/43703543570). This commit requires its own Actions result.
- The two-sync boundary is a bounded open-GOP lookahead, not proof for every codec/container reordering pattern. Long-GOP and device-specific sample ordering remain on the physical-device checklist; the 120,000-sample cap fails closed to nominal-FPS fallback.

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
- Timestamp extraction seeks to the preceding sync sample and collects samples in the clip's original source IN/OUT range on Dispatchers.IO. Decode-order PTS values are sorted and deduplicated into presentation order before caching.
- To avoid truncating reordered samples after a sync boundary, the bounded scan continues through two sync samples at/after trim OUT plus up to 64 subsequent decode-order samples after the most recent qualifying sync. A later qualifying sync restarts that tail. This is a conservative heuristic, not a guarantee for every codec/open-GOP structure; the 120,000-sample total cap still fails closed.
- Each per-trim index is bounded to 120,000 timestamps. The in-memory LRU retains at most three URI/source-IN/source-OUT indexes; unsupported, oversized, or malformed indexes fall back to the existing nominal frame-rate resolver.
- Pure Kotlin tests cover irregular VFR intervals, non-frame-aligned trim boundaries, first/last available frames, VFR cut crossing, decode-order normalization, bounded post-sync lookahead, invalid timestamp sequences, and LRU eviction.
- Media3 still accepts millisecond seek positions, so source PTS values are rounded only when constructing the final seek target. Actual frame presentation, MediaExtractor timestamp ordering across devices, seek latency, and audio continuity remain subject to physical-device validation.


## Continuous VFR-aware timeline playhead (implemented; Android CI pending)
- While preview playback runs, the editor asynchronously warms the active clip and immediately following clip's bounded MediaExtractor PTS indexes on Dispatchers.IO. Existing source-revision keys and the three-entry LRU cache are reused; duplicate URI/revision/trim requests within the warm-up batch are deduplicated.
- The timeline marker is quantized to the latest in-trim presentation timestamp not after Media3's current clip position. When an index is available, the UI sampler uses a 16 ms interval; otherwise it retains the 33 ms playback cadence and nominal clock mapping.
- Audio drift correction, editor seeks, and persisted preview checkpoints continue to use Media3's unquantized project clock. The frame-aligned marker is a UI approximation, not a decoded-frame callback or a claim of verified display timing.
- Starting a manual frame step pauses playback first, cancelling continuous prefetch so the exact frame-step path owns the extractor work. Stale warm-up results are discarded if the project or active playlist index changes.
- Pure Kotlin tests cover floor-to-PTS sampling, non-frame-aligned trims, invalid positions/indexes, and the 16/33/100 ms cadence decisions. Continuous playback, frame display, mixed-rate transitions, and audio sync still require physical Android-device validation.

- VFR timestamp cache writes and successful hits prune superseded revisions for the same URI/source-IN/source-OUT tuple. This keeps the range-based continuous-playhead lookup from selecting an older cached index after a source has been re-read.


## Shared VFR timestamp extraction
- Continuous playback prefetch and manual frame stepping now use one process-wide keyed suspend gate for identical URI/source-IN/source-OUT/source-revision requests. A frame-step request arriving during prefetch waits for the in-flight scan and rechecks the latest in-memory cache before opening another MediaExtractor.
- The shared cache is re-read and updated inside the per-key gate, so simultaneous scans for different clips merge into the latest three-entry LRU state instead of overwriting each other's recency updates from stale local snapshots.
- Unsupported/null PTS indexes are negative-cached for the exact request key. Different trims or source revisions retain independent gates and can run concurrently.
- Project/clip relevance is checked before waiting, after acquiring the gate, and before publishing scan results. Cancellation releases only the waiting request's reference; an active owner is not cancelled by another caller.
- Pure JVM tests cover length-prefixed request-key separation and reuse of a cached null result; the shared keyed gate's existing tests cover unrelated-key concurrency and cancelled waiters. Physical MediaExtractor counts, VFR display timing, and audio synchronization remain device-only checks.


## Recovery mirror reads off the Compose thread
- Project and audio recovery mirror files are now read and checksummed on Dispatchers.IO rather than during initial Compose state construction or a recovery-button callback.
- Initial project/audio state still loads immediately from SharedPreferences. The app then applies a newer valid mirror only if the same project, video edit list, and audio-lane state remain active, preventing a late recovery read from overwriting user edits.
- The explicit Recover action reads its matching audio mirror on IO before rebuilding the shared video/audio checkpoint. The file mirror remains app-private and offline-first.
- Pure JVM tests cover selection of newer mirrored audio, rejection of stale mirrors, and fallback from foreign/corrupt mirror journals. Actual startup responsiveness and force-stop recovery still require physical-device validation.


## Recovery mirror decoding off the UI thread
- The asynchronous project-open effect now performs mirror reads, journal checksum verification, recovery-candidate validation, and audio-track JSON selection together on Dispatchers.IO.
- Initial project state uses only the primary audio preference for immediate rendering; the validated preference/file recovery selection is applied asynchronously only while the project, clip list, and audio list remain unchanged.
- The explicit Recover action now performs mirror reads, matching-checkpoint validation, and audio journal decoding on IO before committing the recovered state on Main.
- Pure JVM selection tests remain independent of Android storage. Device responsiveness and process-death recovery remain physical-device checks.


## First persisted sticker-overlay pipeline — 22 November 2026
- The Stickers tool now adds non-destructive emoji-glyph overlays to the selected video clip, with selectable frame-relative placement and a bounded per-clip layer count.
- Sticker overlays are rendered through Media3 BitmapOverlay in both preview and Transformer export. Their clip-local visibility intervals use inclusive IN and exclusive OUT boundaries.
- Project JSON, undo/redo snapshots, recovery journals, and version-14 project backups carry sticker overlay data. Version-13 and older backups remain readable and default to no stickers.
- Splitting a video clip intersects and rebases each sticker interval onto the two resulting clips. Non-destructive source trimming rebases sticker intervals and drops overlays outside the retained source range.
- The Stickers panel exposes clip-local IN/OUT sliders for each layer. Slider drags commit once on release, use the bounded timing helper, participate in undo/redo, and refresh effects immediately when that clip is loaded in preview; a dedicated sticker timeline lane remains future work.
- Pure JVM tests cover symbol/scale/timing validation, visibility boundaries, split/trim behaviour, bitmap bounds, frame-relative layout, project round trips, legacy backup defaults, and malformed/excessive payload rejection.
- Android CI and device validation are required before treating the rendering path as verified. JVM tests cannot prove platform emoji glyph appearance, preview timing, or exported MP4 playback on physical Android devices.

## Transformer export progress and completion UI — 22 November 2026
- The export panel now polls Media3 Transformer progress on the Transformer's application thread. It displays a determinate percentage only when Media3 reports progress as available; preparation and temporarily unavailable progress remain indeterminate rather than inventing a percentage.
- The UI distinguishes preparation, MP4 rendering, and post-render verification/publication. The verification stage remains visible after rendering reaches 100% until decoded-media validation and the verified MediaStore copy complete.
- The completion state reports the actual output dimensions/resolution and keeps the existing Share action. Cancellation still invalidates the export session, calls Transformer.cancel, and removes its temporary output; late callbacks for an invalidated session remain ignored.
- Pure Kotlin regression tests cover valid percentage bounds, ignored invalid values, and monotonic progress updates. Android CI verifies compilation and JVM tests only; real Transformer progress, cancellation timing, verification, and playback still require physical-device testing.

## Export target frame-rate controls — 22 November 2026
- Export settings now offer Source, 24, 30, and 60 FPS target-rate choices, stored per project in local preferences. Existing projects default to Source so their prior export cadence is not changed.
- Selected targets are applied to each video clip in the Media3 Transformer composition with Media3 1.5.1 FrameDropEffect. Its default strategy compares source presentation timestamps and drops excess frames; it does not interpolate lower-rate clips.
- The target is export-only. Preview playback, the VFR source timestamp index, clip trim points, project clock, and audio-lane timing remain unchanged.
- Pure Kotlin tests cover preference migration/defaults, every supported target, source-cadence no-op behaviour, and truthful completion labels. CI must verify compilation and tests; actual frame cadence, export playback, and audio continuity remain device-validation items.

## Timeline ruler label collision avoidance — 23 November 2026
- Major timecode labels are filtered by an estimated label-width interval that accounts for the longest visible formatted timecode and the user's bounded system font scale.
- The ruler continues to draw its regular major/minor tick marks at the existing absolute project-time positions. Only crowded text labels are omitted; clip widths, project clock mapping, playhead positioning, seek targets, and horizontal scrolling are unchanged.
- The first major label remains visible, and the final major label is shown when enough horizontal space remains after the preceding label. Non-finite tick offsets are ignored by the label selector.
- Pure JVM tests cover crowded and compressed projects, endpoint retention when space allows, minor/non-finite tick filtering, invalid spacing fallbacks, and font-scale/label-width bounds.
- This is a code-level collision-avoidance improvement. Visual label fit across physical screen densities and font configurations still requires Android-device validation.



## Timeline selection and playhead emphasis — 23 November 2026
- The active video clip now has a distinct semantic selection outline, repeated around its text-layer, sticker-layer, and source-audio timeline columns so the selected project clip remains identifiable across tracks.
- A shared playhead marker uses an accent needle with a dark outline and a bordered top cap. The same marker is drawn in the ruler, video, text, sticker, source-audio, and added-audio rows; no timeline positions or seek calculations are changed.
- Pure Kotlin tests verify centered playhead geometry, malformed-offset fallback, selected/unselected border widths, and the contrast of semantic emphasis colors against the timeline's actual track surfaces.
- This is a visual-state implementation only. Touch-target behaviour, perceived contrast, and continuous playhead alignment still require Android-device validation.

## Editable sticker size control — 23 November 2026
- The Stickers panel now exposes a per-layer size slider bounded to the existing 0.5×–2× model range. Changes commit once on release through the shared sticker-overlay mutation path, preserving the layer's symbol, position, and clip-local IN/OUT interval.
- The existing ClipStickerOverlay scale value remains the single source for preview and Media3 export rendering; project serialization and undo/redo continue to store the same overlay model without a schema change.
- Pure JVM tests cover normal scale updates, lower/upper clamping, non-finite rejection, and preservation of the other overlay properties.
- The control is wired to the existing preview/export renderer. Emoji glyph appearance and perceived size still require physical-device and exported-video validation.


## Non-destructive creative colour looks — 23 November 2026
- The Effects tool now exposes Original, Warm, Cool, and Negative looks for the selected clip. Warm and Cool use Media3 1.5.1 `RgbAdjustment` channel scaling; Negative uses `RgbFilter.createInvertedFilter()`. These are real frame effects, not decorative preview-only cards.
- The selected look is stored as a stable ID inside the existing per-clip visual-adjustment payload. Local project saves, recovery journals, undo/redo snapshots, and JSON backups therefore use the same clip model; backup writer version 15 records the new optional field. Older backups without the field default to Original, while unknown effect IDs reject the malformed clip payload.
- The Effects panel retains the existing hue-shift control. Applying a look updates the active preview player's video-effect list while preserving that clip's text and sticker overlays. The same `createClipVisualEffects` builder is used by Transformer export.
- Pure JVM tests cover stable preset IDs, unknown-ID rejection, deterministic effect-spec ordering, local project serialization, version-15 backup round trips, and version-14 backward compatibility.
- The previous feature head `6319016d0ee2c1522b1ba64e667608dc415ab9a9` passed Android compilation, JVM tests, and APK upload in [Android build #539](https://github.com/Konodio-da-da/VyroCut/actions/runs/45105934523). This effect change requires its own Actions result. Visual colour quality, preview appearance, and exported-frame appearance still require physical-device and MP4 validation.

## Creative effect intensity controls — 23 November 2026
- The Effects tool now exposes a per-clip 0–100% strength slider for Warm, Cool, and Negative. The slider commits through the existing non-destructive visual-adjustment mutation path and resets to 100%; zero strength omits the creative effect from the Media3 effect list.
- Warm and Cool interpolate each RGB channel scale from identity to the existing tint values. Negative blends Media3's inverted RGB matrix with the 4×4 identity matrix, so intermediate strengths are genuine partial inversion rather than a preview-only opacity overlay.
- The same effect-spec builder and Media3 effect construction remain shared by preview and Transformer export. The intensity is stored alongside the stable preset ID in the existing per-clip visual-adjustment JSON; version-15 backups created before this field default to 100% when imported, so no backup version bump is required.
- Pure JVM tests cover intensity bounds/defaults, zero-strength no-op behaviour, all three preset strengths, and identity/half/full inversion matrix interpolation. Actual preview appearance, HDR output, and exported MP4 colour still require physical-device validation.


## Categorized grayscale and sepia effects — 23 November 2026
- The Effects browser now groups its non-destructive looks into Color, Neutral & Monochrome, and Vintage categories. Existing Original, Warm, Cool, and Negative identifiers remain stable.
- Added Grayscale using Media3 1.5.1 `RgbFilter.createGrayscaleFilter()`, which selects the library's SDR or HDR luminance matrix. Added Sepia with a dedicated 4×4 RGB matrix. Both use the existing identity-to-effect intensity blending, so the shared 0–100% strength control works consistently.
- Both effects are added to the same ordered `createClipVisualEffects` builder used by preview and Transformer export. Their stable IDs and strength values use the existing per-clip visual-adjustment JSON; project and version-15 backup formats do not change.
- Pure JVM tests cover stable IDs, category grouping/order, effect-spec generation, strength bounds/zero-strength no-op, the Sepia matrix coefficients/alpha channel, and project/backup round trips for both new presets. Android CI is required to verify Media3 integration and compilation; visual quality and exported frames still require physical-device validation.


## Film Fade creative effect — 23 November 2026
- Added a non-destructive Film Fade preset in the existing Vintage category. Its Media3 1.5.1 `RgbMatrix` reduces RGB channel contrast and lifts the shadow toe with a subtle warm bias; the existing 0–100% intensity blend applies the same effect-strength semantics as the other looks.
- The stable `film-fade` identifier is stored in the existing per-clip visual-adjustment payload and uses the same `createClipVisualEffects` builder for preview and Transformer export. No project or version-15 backup schema change is required.
- Pure JVM tests cover the effect specification, category ordering, intensity mapping, matrix coefficients, shadow lift, contrast reduction, alpha preservation, defensive matrix copies, and project/version-15 backup round trips.
- This validates the deterministic matrix and Android compilation only. Perceived film rendering, preview appearance, HDR handling, and exported MP4 output still require physical-device validation.


## Export video bitrate targets — 23 November 2026
- The Export panel now offers per-project Auto, Balanced, High, and Very high video-bitrate choices. Auto remains the default for existing projects and leaves Media3's encoder selection unchanged.
- Manual targets are estimated from output pixel rate and the selected target FPS using bounded bits-per-pixel-per-frame factors. When Source FPS is selected, 30 FPS is used only for the bitrate estimate; source cadence and VFR indexing are not changed.
- Manual modes configure Media3 1.5.1 `DefaultEncoderFactory` with `VideoEncoderSettings.setBitrate` and format fallback enabled. The requested bitrate is best-effort: device encoders may clamp or ignore it to select a supported format. The completion summary reports the selected target preset, not a measured MP4 bitrate.
- Pure Kotlin tests cover preference restoration, Auto no-op behaviour, 720p/1080p estimates, target-FPS scaling, Source-FPS estimation, lower/upper bounds, and truthful completion labels.
- The encoder target is video-only. Existing audio encoding, Media3 composition timing, project clock, VFR frame indexing, preflight, decoded-media validation, cancellation, and MediaStore publication are unchanged. Actual device encoder selection, file size, bitrate, playback quality, and audio continuity still require physical-device export validation.


## Export audio bitrate targets — 23 November 2026
- The Export panel now offers per-project Auto, 96, 128, 192, and 256 kbps audio bitrate requests. Existing projects default to Auto, which leaves Media3's existing audio encoder default unchanged.
- Manual presets use Media3 1.5.1 `DefaultEncoderFactory.Builder.setRequestedAudioEncoderSettings(AudioEncoderSettings)`. The same encoder factory also receives the existing optional video bitrate request, so video and audio quality can be selected independently in one export.
- The completion summary reports the selected requested audio target rather than claiming a measured output bitrate. Media3/device encoders may ignore or adjust the requested value to preserve a supported configuration.
- Pure Kotlin tests cover preference defaults/restoration, exact bits-per-second mappings, Auto no-op behaviour, and truthful requested-target summaries.
- The setting is stored per project in local preferences; no project JSON or backup schema changes are required. Audio mixing, per-clip volume/fades, source audio, Music/SFX/Voiceover composition, project clock, VFR indexing, export preflight, validation, cancellation, and MediaStore publication are unchanged.
- Android CI verifies compilation and JVM rules only. Actual encoder acceptance, resulting MP4 audio bitrate, sound quality, and audio/video sync still require physical-device export validation.


## Export size estimate and storage preflight — 23 November 2026
- The Export panel now displays an approximate MP4 size from project duration, the selected video/audio bitrate requests, and a 5% container allowance. Auto uses explicit planning assumptions (Balanced video bitrate and 128 kbps audio) and is labelled as an estimate; actual size may vary with content and device encoders.
- Before Transformer starts, the app checks free bytes for the app-private temporary output directory and the primary Movies storage volume. When both locations share a volume, preflight budgets for the temporary MP4 and the separate MediaStore publication copy to coexist, plus a 32 MiB reserve. Separate volumes are checked independently with a 16 MiB reserve each.
- A known insufficient-capacity result stops export before creating the MP4. If storage statistics or the volume relationship cannot be read, the check is reported as unknown and export may continue rather than treating unavailable information as a pass.
- Pure JVM tests cover bitrate/duration estimates, automatic assumptions, container overhead, rounding and overflow, same/separate-volume capacity checks, unknown capacity, and canonical path-boundary matching.
- The storage check is a preflight estimate, not a guarantee: concurrent storage use, variable encoder output, filesystem metadata, and device behaviour can change the final space requirement. Physical-device low-storage and MediaStore publication tests remain required.


## Export preflight includes silent-audio gap cache — 23 November 2026
- Export now builds the same Music/SFX/Voiceover gap segments before storage preflight, so it can calculate the longest silent gap before creating the temporary WAV source.
- The 8 kHz mono 16-bit PCM WAV cache size is estimated from the exact writer formula, including the 44-byte RIFF header. A complete matching cache file is detected and is not budgeted as a second allocation; missing or invalid cache files reserve the exact bytes needed for regeneration.
- Preflight checks the cache directory's free space and whether the cache shares the temporary export volume. When it shares the volume, the WAV allocation is added to that volume's temporary-output requirement; when separate, the cache is checked independently with its own reserve. Unknown capacity or volume relationships remain UNKNOWN rather than passing.
- Oversized gap WAVs are rejected before Transformer starts, matching the writer's existing RIFF size limit.
- Pure JVM tests cover exact WAV file-size estimates, cache reuse, same-volume and separate-volume requirements, insufficient cache capacity, and unknown cache relationships. Device-level storage topology and low-space behavior still require Android-device validation.

## Actionable export failure diagnostics
- Media3 render errors, Transformer start failures, export-composition/initialization failures, and post-render verification/publication failures now pass through one pure Kotlin diagnostic classifier.
- The classifier inspects a bounded cause chain and distinguishes storage exhaustion, Android permission denial, unavailable source files, unsupported encoder/codec failures, invalid output verification, and unknown failures.
- User-facing guidance is stage-aware and does not echo raw exception messages, filesystem paths, or content URIs. Unknown failures retain a conservative stage-specific retry/check message rather than guessing a cause.
- JVM regression tests cover nested causes, each recognized category, stage-specific fallback guidance, and suppression of private paths/URIs from the displayed message. Actual encoder/provider failure wording still needs device testing.



## Verified export output metadata — 23 November 2026
- Successful export verification now returns the measured video track summary together with the SHA-256/byte-count fingerprint used for MediaStore publication. The completion card reports actual encoded width/height, container-reported video duration, exact fingerprinted MP4 byte count formatted in MiB, and codec names when the track MIME types are available.
- Requested resolution/FPS/bitrate preset labels remain separate from the measured output fields. The actual file-size label is sourced from the bytes that passed verification and were copied/read back for publication; it is not the preflight estimate or a claimed measured stream bitrate.
- Export track validation now rejects missing or non-positive video dimensions even when no expected canvas size is supplied. Existing exact selected-canvas checks, duration tolerance, decoded video/audio verification, cancellation, fingerprint comparison, and MediaStore publication remain unchanged.
- Pure JVM tests cover invalid dimensions, measured-size formatting, duration formatting, video-only versus audio/video codec reporting, unknown codec preservation, and incomplete metadata rejection. Android CI verifies compilation and JVM rules only; actual encoder metadata and exported playback still require physical-device validation.


## Shared spacing, shape, typography, and control tokens — 23 November 2026
- Extended the central design system beyond color with an explicit dp spacing scale, corner-radius tokens, a small typography scale, and editor tool-dock control metrics.
- Migrated the project-home screen's repeated spacing, card shapes, and title/label sizes to shared tokens. Migrated the editor tool dock's panel/item shape, spacing, icon size, label size, and button geometry without changing its existing dimensions or horizontal scrolling.
- Reused the shared spacing token in the trim dialog. This is a styling-only consolidation: no navigation, state, editing, playback, timeline geometry, persistence, or export logic changed.
- Added JVM assertions for ordered spacing/shape/type scales and for tool-button dimensions remaining at least 48 dp. These tests protect the existing touch geometry; actual touch usability and screen fit still require Android-device validation.


## 2026-11-23 — Project home styling token consolidation

- Added semantic `VyroCutHomeMetrics` for the home screen's remaining one-off dimensions and its search, import, hero, empty-state, project-card, and badge geometry.
- Replaced literal dp/sp values in `VyroCutProjectsHome` with shared design tokens or home-specific semantic metrics.
- Preserved every migrated numeric value; this is a styling-centralization change, not a layout redesign.
- Added an exact-value JVM regression test so future token edits cannot silently change the existing project-home geometry.
- Android compilation, JVM unit tests, and debug APK upload passed in [Android build run #582](https://github.com/Konodio-da-da/VyroCut/actions/runs/45204371892). Physical screen fit remains a device-validation item.


## Effects panel design-token consolidation — 23 November 2026
- Added semantic effects-panel metrics for horizontal/vertical padding, section spacing, reset-button padding, and heading/category letter spacing.
- Replaced repeated dp/sp literals in the Effects panel with shared spacing, shape, typography, and effects-panel tokens while preserving the exact existing values.
- The change is styling-only: creative-effect IDs, Media3 effect construction, preview and Transformer effect lists, slider ranges/commit timing, clip state, undo/redo, and project persistence are unchanged.
- Added an exact-value JVM regression test for the effects-panel metrics and the shared tokens used by the panel. The Android unit-test task exercises this test; actual panel fit and touch behaviour still require Android-device validation.

## Filters and Adjust panel design-token consolidation — 23 November 2026
- Added semantic VyroCutVisualPanelMetrics for the Filters and Adjust panel's existing padding, section spacing, heading tracking, and reset-button padding.
- Replaced the panel's repeated shape, spacing, and type literals with shared design-system tokens while preserving the existing numeric dimensions and control values.
- This is styling-only: filter preset selection, per-clip brightness/contrast/saturation state, slider ranges and commit timing, undo/redo, preview effects, and Transformer export are unchanged.
- Added a pure JVM exact-value regression test for the panel-specific metrics and shared tokens.
- Android CI must verify this commit's compilation and unit tests. Physical-device layout fit and touch behaviour remain unverified.

## Ratio and Canvas panel design-token consolidation — 23 November 2026
- Added semantic `VyroCutCanvasPanelMetrics` for the Ratio and Canvas panel's existing horizontal/vertical padding and section gap.
- Replaced the panel's hardcoded corner radius, padding, heading size, and option spacing with shared shape, spacing, typography, and panel tokens while preserving the existing numeric dimensions.
- The change is styling-only: aspect-ratio selection, canvas-background selection, project settings persistence, preview rendering, and export canvas composition are unchanged.
- Added a pure JVM exact-value regression test for the new panel metrics and shared tokens. Physical-device layout fit remains unverified.


## Text overlay panel design-token consolidation — 23 November 2026
- Added semantic `VyroCutTextPanelMetrics` for the Text panel's existing padding, section spacing, header tracking, helper/caption/chip typography, and chip spacing. Added a reusable 10 dp compact-panel corner token.
- Replaced the Text panel's repeated dp/sp literals with shared design-system tokens while preserving its existing numeric dimensions.
- This is styling-only: text content limits, layer selection, size/position choices, overlay timing, apply/remove actions, split/trim persistence, preview rendering, and MP4 export are unchanged.
- Added a pure JVM exact-value regression test for the Text panel metrics and shared shape/spacing tokens. Physical-device layout fit and text-entry usability remain unverified.
## Sticker overlay panel design-token consolidation — 23 November 2026
- Added semantic `VyroCutStickerPanelMetrics` for the Stickers panel's existing horizontal/vertical padding, chip spacing, layer-card vertical padding and row gap, sticker glyph sizes, and remove-button padding.
- Reused the shared compact/small shapes, spacing scale, and typography tokens for the panel's remaining dimensions. Existing dp/sp values are preserved.
- This is styling-only: sticker symbol choices, per-layer size and clip-local IN/OUT controls, selection/removal, non-destructive overlay model, split/trim persistence, undo/redo, preview rendering, and Media3 Transformer export are unchanged.
- Added a pure JVM exact-value regression test for the sticker-panel metrics and reused shared tokens. Android CI must verify compilation and the unit test; actual glyph rendering and screen fit still require physical-device validation.

## Audio tool panel design-token consolidation — 23 November 2026
- Added semantic `VyroCutAudioPanelMetrics` for the existing lane/clip-volume panel gaps, chip spacing, clip-card geometry, delete controls, action-button padding, and header tracking.
- Reused the shared compact shape, small spacing, and typography scale throughout the Audio tool's lane list and selected-video-clip volume/fade panel without changing the existing dp/sp values.
- This is styling-only: audio import, lane selection, waveform decode/cache/rendering, source IN/OUT, timeline placement, ripple trim/delete, split/duplicate, volume/fades, project clock synchronization, preview mixing, persistence, and Media3 Transformer audio composition are unchanged.
- Added a pure JVM exact-value regression test for the Audio panel metrics and shared tokens. Android CI must verify compilation and unit tests; actual waveform rendering, audio sync, and screen fit still require physical-device validation.

## Compact-width preview transport layout — 23 November 2026
- Extended the pure editor layout rules with a 400 dp compact-width breakpoint. On narrower app windows, clip name/status and any missing-source relink action move above the preview transport controls instead of competing for the same horizontal row.
- The wide layout retains the existing single-row arrangement. Preview transport actions, Media3 player state, frame stepping, project-clock seeking, playback speed, and source relinking behaviour are unchanged.
- The breakpoint uses the current Compose window configuration width, so resized and split-screen windows can receive the compact arrangement. Existing height-based preview sizing and vertical scrolling remain unchanged.
- Added JVM tests for the width breakpoint and invalid-width fallback. Android CI verifies compilation and pure logic only; physical-device layout fit and touch usability still require device validation.


## Tight-height editor spacing — 24 November 2026
- Added a third vertical-density tier below the existing 520 dp tight-window breakpoint: 3 dp between editor sections and 2 dp outer vertical padding, while retaining 4 dp compact spacing from 520–599 dp and 6 dp standard spacing at 600 dp and above.
- The editor remains vertically scrollable. No tools, timeline rows, playback actions, or accessibility touch targets are hidden or reduced; preview sizing, project-clock behaviour, and timeline geometry are unchanged.
- Added pure JVM boundary coverage for the tight-to-compact transition. CI can verify layout-rule calculations and compilation; actual screen fit still requires physical-device validation.


## Timeline selected-audio visibility — 24 November 2026
- Audio clips now receive the same accent selection outline as video, text, sticker, and source clips when their stable ID matches the selected audio clip. The selected outline is visible in the corresponding Music, SFX, or Voiceover lane.
- Selection is presentation-only: tapping an audio clip still selects the same clip, opens the Audio tool, and seeks to its existing timeline start. Audio placement, source/timeline timing, waveform generation/cache, project persistence, preview synchronization, and export composition are unchanged.
- Added a pure JVM test for matching, non-matching, missing, and blank audio selection IDs. Android CI must verify compilation and the regression test; actual outline rendering and touch behaviour still require device validation.


## Timeline ruler hierarchy and edge-safe timecodes — 24 November 2026
- Distinguished major and minor ruler marks with separate widths, heights, and contrast while retaining the existing absolute project-time tick positions and major-label collision filtering.
- Ruler timecodes now use platform monospace digits and a compact surface badge. Their width estimate follows the system font scale, and labels shift inward at the content edges rather than being centered partly outside the scrollable timeline.
- Added pure JVM coverage for tick hierarchy, font-scaled label width, left/center/right label placement, and malformed geometry fallback.
- No ruler tick timestamps, time-to-pixel mapping, playhead geometry, seeking, horizontal scrolling, clip positions, shared project clock, or VFR indexing were changed. CI verifies compilation and pure rules only; visual appearance and touch behaviour still require Android-device validation.


## Timeline track header hierarchy — 24 November 2026
- Replaced plain VIDEO, TEXT, STICKERS, SOURCE, MUSIC, SFX, and VOICEOVER labels with one reusable compact header treatment: a small semantic marker, a tinted label badge, and the existing detail text.
- Reused the existing accent and per-lane colors. Audio header badges use the same Music/SFX/Voiceover lane colors already used by their clips, so the label reinforces the track's existing color identity.
- Badge styling uses horizontal-only padding and retains the existing 11 sp label / 10 sp detail scale; it does not add vertical padding or alter timeline row heights, clip geometry, or timeline-to-time mapping.
- Added a pure JVM regression test for the compact badge geometry. Android CI must verify compilation and unit tests; visual contrast and readability still require physical-device validation.


## Cinematic creative effect — 24 November 2026
- Added a persisted `cinematic` creative-effect preset in its own Effects category. The preset uses a real Media3 `RgbMatrix` rather than a UI-only selection.
- The matrix applies a restrained cool-matte grade with slightly reduced channel gain and a stronger blue lift; its intensity blends against the identity matrix, and alpha remains unchanged.
- Preview and Transformer export both use the shared `createClipVisualEffects` factory, so the same ordered effect specification is used in both paths. Existing clip adjustments remain composed before the creative look.
- The stable preset ID is stored through the existing visual-adjustment JSON field; no project schema or migration change is required. Existing preset IDs and older project defaults are unchanged.
- Added pure JVM coverage for ID decoding, category ordering, full/partial/zero intensity, matrix coefficients, alpha preservation, and defensive matrix copies. CI verifies compilation and JVM logic only; visual grading and playback still require device/export validation.

## Soft Focus lens effect — 24 November 2026
- Added a persisted `soft-focus` creative-effect preset in the Lens category, using Media3 1.5.1 `GaussianBlur` rather than a UI-only look.
- Strength maps linearly from 0–100% to a maximum Gaussian sigma of 5 output pixels. Zero strength remains omitted from the effect list, preserving the no-op path.
- The shared `createClipVisualEffects` factory supplies the effect to both the Media3 preview player and Transformer export composition; existing per-clip adjustments remain ordered before the creative effect.
- Added pure JVM coverage for stable ID decoding, effect specification, category/browser ordering, intensity/no-op behavior, and sigma bounds. Android CI verifies compilation and JVM logic; actual blur quality and playback still require device/export validation.


## Offline built-in vector sticker pack — 24 November 2026
- Added six original Android VectorDrawable assets (Star, Heart, Sparkle, Bolt, Crown, and Flower) to a small bundled sticker pack. The assets render locally and do not require network access or external downloads.
- Sticker overlays retain their existing Unicode `symbol` fallback and add an optional stable `assetId` in the existing `stickerOverlays` JSON entries. Existing emoji-only projects omit the field and decode exactly as before; the project backup format version remains unchanged.
- The Media3 bitmap-overlay renderer draws known vector assets into the same clip-local timed overlay bitmap used by preview and Transformer export. Unknown future asset IDs fall back to the persisted Unicode symbol rather than making a project unreadable.
- The Stickers panel now exposes the bundled vector pack alongside the existing quick-emoji row. Position, size, clip-local IN/OUT timing, split/trim rebasing, selection, removal, undo/redo, and the five-overlay limit are retained.
- Added pure JVM tests for stable asset IDs, resource mappings, unknown-ID fallback, split/trim identity preservation, and project JSON round-tripping including legacy emoji-only entries. CI verifies compilation and JVM persistence rules; actual vector rasterization, visual fit, and MP4 playback still require Android-device/export validation.


## Compose method-size CI fix — 24 November 2026
- Android build run #617 failed in `:app:compileDebugKotlin` with JVM `MethodTooLargeException` for the generated `VyroCutApp$19$1.invoke` method after the built-in sticker panel was added inline to the already-large editor composable. The Kotlin daemon also reported a missing incremental class file, but the decisive compiler failure was the generated method exceeding the JVM method-size limit.
- Extracted the sticker panel into `StickerOverlaysPanel.kt` and split its position picker, vector/emoji pickers, layer list, and individual layer editor into separate composables. `MainActivity.kt` now passes the existing selected clip, layer selection, and editing callbacks; sticker data, timing, scaling, persistence, and Media3 overlay behavior are unchanged.
- This is a UI/code-generation refactor only. Existing pure sticker and project-persistence regression tests remain the relevant behavior checks; a fresh GitHub Actions run must confirm compilation and unit tests before the fix is considered verified.


## Keyboard-safe editor scrolling — 24 November 2026
- Applied Compose IME bottom insets to the vertically scrollable editor content before the scroll modifier. When a text-overlay or project-name field opens the software keyboard, the editor viewport now reserves the keyboard-covered area so lower controls can be reached by scrolling instead of sitting behind the IME.
- The modifier is scoped to the editor branch, not the project-home screen, and is inactive when the keyboard is hidden. Existing preview sizing, timeline geometry, playback controls, text content, project state, and export behavior are unchanged.
- Existing pure JVM editor-layout boundary tests remain unchanged; this is a Compose inset/scroll integration change with no new pure calculation. Android CI must verify compilation and unit tests. Actual keyboard animation, focus visibility, and device-specific inset behavior still require physical-device validation.

## Window-aware editor sizing — 24 November 2026
- Replaced display-wide `LocalConfiguration.screenHeightDp/screenWidthDp` inputs with the editor branch's actual `BoxWithConstraints` bounds. Compact split-screen/resizable windows now feed their available width and height into the existing pure editor layout rules instead of inheriting the physical display dimensions.
- Moved the existing IME bottom inset to the constraints container. The measured editor height therefore reflects the remaining viewport when the software keyboard is visible, while the scrollable editor fills that reduced viewport and retains access to lower controls.
- Added pure JVM boundary coverage for 519/520 dp and 599/600 dp transitions. Preview height and spacing rules are unchanged; only their input now represents the available editor window.
- This is a source-level layout improvement. Actual split-screen sizing, keyboard inset animation, touch reachability, and visual fit still require physical-device validation. Media3 playback, project timing, timeline data, persistence, and export code are untouched.

## Stage-aware export progress — 24 November 2026
- Android build run #637 passed on the preceding head `92f78cf00e72eb45a3831f7c22ebf47e86792ece`, including `assembleDebug`, `testDebugUnitTest`, and debug APK upload.
- Corrected the export progress presentation so Media3's render callback can never display 100% while the output is still being verified or copied into MediaStore. Rendering is capped visually at 99%; the UI switches to an indeterminate indicator during preparation and verification/publication.
- The completion stage alone may display 100%. The existing Transformer listener, decoded MP4 verification, fingerprint/copy integrity checks, cancellation, temporary-file cleanup, and MediaStore publication operations are unchanged.
- Added pure JVM tests for preparing/verifying indeterminate states, missing rendering progress, the 99% render cap, completed-only 100%, and inactive terminal states.
- Android CI verifies compilation and pure JVM rules. Actual export progress cadence, encoder completion timing, saved-file playback, and MediaStore behavior still require physical-device validation.


## Stage-specific export progress copy — 24 November 2026
- Made the export progress card describe the work actually happening in each phase. Preparation now explains source/audio/storage checks, rendering explains on-device rendering and cancellation, and verification explains MP4 validation and Movies publication.
- Replaced the generic “Working…” fallback with phase-specific “Preparing…”, “Rendering…”, and “Verifying…” labels. The displayed percentage policy is unchanged: rendering remains capped at 99%, and only the completed stage may show 100%.
- Kept the Media3 Transformer lifecycle, decoded validation, integrity checks, cancellation, temporary-file cleanup, and MediaStore publication untouched.
- Added JVM assertions for each stage's activity label and explanatory copy, including idle, failure, and cancellation states.
- CI verifies compilation and deterministic presentation rules. Actual export cadence, on-device rendering, and saved MP4 playback still require device validation.


## Export panel design-token consolidation — 24 November 2026
- Added semantic export-panel metrics for settings-row spacing, preset-chip spacing, progress/completion card spacing, and card geometry.
- Reused the shared typography scale for export labels, values, and helper copy.
- Replaced only existing styling literals in the export settings, progress, and completion UI. Numeric values are unchanged; export preferences, bitrate calculations, progress stages, Transformer lifecycle, cancellation, validation, and MediaStore publication are untouched.
- Added a JVM regression assertion for every export-panel metric. CI compilation and unit tests are the available automated checks; compact-window fit and actual export interaction still require Android-device validation.


## Timeline ruler edge-collision correction — 24 November 2026
- Ruler-label selection now measures the actual rendered label-left positions after the same start/end clamping used by Compose, instead of comparing only tick-center distances.
- This prevents the first or last timecode pill from overlapping a neighboring centered label when edge alignment shifts the pill inward.
- The final major timecode is retained only when its rendered bounds leave the configured label gap; tick marks, absolute project-time mapping, playhead position, clip geometry, and timeline scrolling are unchanged.
- Added pure JVM regression cases for colliding start/end labels and for retaining the final edge label when there is enough room. CI is the available automated check; physical-device readability and touch behavior remain unverified.

## Timeline ruler invalid-metric fallback — 24 November 2026
- Invalid/non-positive label widths now conservatively retain only the first major timecode instead of returning every candidate and risking overlap.
- Non-finite or negative minimum gaps fall back to the shared 2 dp ruler-label gap; an explicit zero gap remains supported while label-width collision checks still apply.
- Invalid viewport width continues through the existing placement fallback, which collapses labels to one safe position; added JVM tests cover malformed gap, label width, and viewport width inputs.
- Ruler tick generation, clip geometry, project-time mapping, playhead seeking, scrolling, playback synchronization, and VFR indexing are unchanged. Automated verification is through GitHub Actions; physical-device readability remains unverified.


## Editor unknown-width compact fallback — 24 November 2026
- When the app window width is unavailable or invalid, the editor now defaults to the compact metadata-above-transport layout rather than assuming a 400 dp wide window.
- The measured-width breakpoint is unchanged: widths below 400 dp stack metadata, while 400 dp and above retain the inline layout. Only the unknown-width fallback changes.
- Added JVM coverage for zero/negative widths and the default-argument path. Preview sizing, touch targets, tool availability, timeline geometry, playback, project persistence, and export behavior are unchanged.
- This is a conservative first-measurement/split-window fallback; actual fit still requires physical-device and resized-window validation.

 
## Explicit export retry affordance — 24 November 2026
- The export action now changes to “Retry export” after a failed attempt; active exports retain “Exporting…” and idle, cancelled, or completed states retain “Export MP4”.
- Retry intentionally reuses the existing export action callback, so every attempt reruns source inspection, export preflight, audio validation, storage checks, Media3 composition preparation, output verification, and MediaStore publication. No failed temporary output or stale Transformer session is reused.
- Added pure JVM coverage for failed, active, idle, cancelled, and completed action labels. Export lifecycle, failure classification, cancellation, and publication behavior are unchanged.
- GitHub Actions verifies compilation and pure presentation logic; actual retry after repairing a source/permission/storage issue still requires device validation.

## Structured export completion summary — 24 November 2026
- The completion card now separates metadata measured from the verified MP4 (dimensions, duration, actual file size, and available codecs) from the settings selected before rendering (resolution preset, frame rate, video/audio bitrate, and MP4 format).
- Missing measured output metadata is omitted rather than replaced with an estimate; blank settings are filtered by a pure Kotlin presentation helper.
- Added JVM tests for section separation, trimming, empty sections, and output-only summaries. Export verification, file fingerprinting, sharing URI, and MediaStore publication are unchanged.
- GitHub Actions validates compilation and deterministic summary presentation; actual export/share behavior still requires physical-device validation.

## Muted text contrast on selected surfaces — 24 November 2026
- Raised the shared muted-text token slightly from `#9BAAA5` to `#A0B0A9`. The previous color measured about 4.28:1 against the selected green surface, below the 4.5:1 normal-text contrast target; the new token measures about 4.57:1 there.
- The selected audio-lane clip uses muted text for its time/volume metadata on that selected surface, so this corrects a real low-contrast state without changing layout dimensions or selection behavior.
- Added pure JVM contrast regression coverage for muted, primary, and danger text across every shared studio surface, plus the subtle waveform color's non-text contrast on the unselected clip surface.
- No timeline timing, waveform data/cache, playback, audio synchronization, project persistence, export, or MediaStore behavior changed. Android CI verifies compilation and deterministic color calculations; visual appearance still requires device validation.
