## VFR decode-order timestamp normalization
1. Use an H.264/HEVC source with B-frames and verify the extracted PTS sequence is sorted into unique presentation order even when compressed samples arrive in decode order.
2. Trim near a GOP boundary and confirm samples with PTS inside trim OUT that occur after the first out-of-range sample are still included.
3. Confirm the scan stops after two sync samples at/after trim OUT, or fails closed at the 120,000-sample work cap when no bounded boundary is found.
4. Step forward/backward around the trim boundary and compare the displayed frame to source presentation timestamps.
5. Repeat with a source ending before a second post-trim sync sample and with a long-GOP source; verify safe fallback rather than using a partial index.
6. JVM tests validate sorting, de-duplication, input bounds, and sync-lookahead counting. Actual MediaExtractor sample order, codec-specific GOP behaviour, displayed frames, seek latency, and audio continuity require physical Android-device validation.

# Physical-device validation status

## Current status

**Not yet run on a physical Android device.** The available ProductOS coding sandbox is not an Android build or device environment:

- java is not installed (java: command not found).
- No Android SDK is configured (ANDROID_HOME is unset).
- The checkout contains only gradle/wrapper/gradle-wrapper.properties; it has no Gradle wrapper launcher script or installed Gradle.
- No Android emulator or USB-connected device is exposed to this automation.
- The user now has a Linux laptop with 8 GB RAM available for local development, but its Java/Gradle/Android SDK setup and USB-debugging connection have not yet been verified in this workflow. This automation cannot execute commands on that laptop; local build/device evidence must be supplied by the laptop session.
- ProductOS currently classifies this imported native Kotlin/Compose repository as a universal web project. Its browser preview is not an Android app renderer and cannot validate Compose layout or Media3 playback.

GitHub Actions is the available build environment. It provisions JDK 17 and Android SDK packages, runs assembleDebug and testDebugUnitTest, and uploads a debug APK. A green workflow is evidence of compilation and JVM tests only; it is not evidence of real-device playback, codec support, UI geometry, audio synchronization, or a valid end-to-end export.

## Required device pass

Record device model, Android version, source formats, project duration, export duration, and pass/fail notes for each run.

1. **Install and project lifecycle**
   - Install the latest successful CI debug APK on Android 10 / API 29 and one current Android release.
   - Create a project, import at least three local videos, then force-stop and reopen.
   - Verify clip order, source trims, audio lanes, text layers, canvas settings, and preview checkpoint survive restart.
   - Export a JSON backup while the playhead is partway through a trimmed clip, restore it as a new project, and verify the stable clip ID/source timestamp is restored. Version 1–12 backups remain importable; older backups without a checkpoint start at the first clip.
   - Exercise undo/redo, JSON backup/restore, missing-source detection, and relinking while offline.
   - Export a backup at a non-default preview speed, restore it as a new project, and confirm the speed returns; verify older v1-v11 backups default to 1×.

2. **Continuous preview and timeline**
   - Play through at least three clips without touching the controls; verify no freeze, unexpected restart, or black gap at clip boundaries.
   - During uninterrupted playback, confirm the project playhead advances smoothly at roughly 30 updates per second; pause and confirm it settles on the final player position. Check responsiveness and device load on a lower-powered API 29 device.
   - While playing, manually drag the shared horizontal timeline to inspect another region; confirm auto-follow stays suspended instead of pulling the view back. Tap or scrub to seek and confirm follow resumes; alternatively pause and restart playback to re-enable auto-follow. Verify the ruler, video, Music, SFX, and Voiceover lanes retain one shared horizontal offset.
   - Seek by tapping and dragging the timeline across clip boundaries and the exact project end.
   - Use the ±5-second controls, timeline slider, video clip selection, audio clip selection, and text-layer selection; verify the video and Music/SFX/Voiceover players seek to the same project-clock position, including silence gaps and exact clip boundaries.
   - Switch projects and immediately scrub after opening; confirm no audio from the previous project's lane playlists is heard while the new sequences are rebuilding.
   - Reorder, trim, split, duplicate, and delete clips while the selected clip is playing.
   - Background and resume the app; confirm the saved preview position is coherent.

3. **Music, SFX, and voiceover**
   - Test leading, internal, and trailing gaps; source trims; lane offsets; fades; and volume.
   - Duplicate a selected Music, SFX, and Voiceover clip; verify it lands in the next available non-overlapping slot, preserves source trim/volume/fades, remains independently selectable, and survives undo/redo plus force-stop/reopen.
   - Ripple-delete a selected Music, SFX, and Voiceover clip; verify only later clips on that lane shift left by the removed duration, source IN/OUT, volume and fades remain unchanged, gaps between later clips are preserved, other lanes do not move, and undo/redo plus force-stop/reopen restore the edit.
   - Use Normal trim and Ripple trim on Music, SFX, and Voiceover clips; verify shortening shifts only later same-lane clips earlier, extending shifts only later same-lane clips later, existing gaps and other lanes remain unchanged, source media is untouched, and undo/redo plus force-stop/reopen restore the edit. Confirm an extension that would exceed project duration is rejected without changing any clip.
   - Split a selected Music, SFX, and Voiceover clip at the playhead; verify source ranges remain contiguous and the original audio is unchanged.
   - Listen across clip transitions and repeated scrubs for drift, clicks, missing audio, or repeated seek glitches.
   - Let a continuous Music/SFX/Voiceover clip span several video cuts; confirm natural video auto-transitions do not cause audible restarts while explicit seeks still realign all lanes.
   - Rapidly cross a video clip boundary and confirm the preview playhead/effects update once without an audible audio restart; repeat with an explicit seek and confirm all audio lanes realign.
   - Test source video with audio, silent video, and all three independent lanes together.
   - Repeat multi-clip preview with playback speed changed mid-clip and across clip boundaries at 0.5×, 0.75×, 1×, 1.25×, 1.5× and 2×; confirm Music, SFX and Voiceover remain aligned with the video clock.
   - Set a non-default preview speed, switch to another project and back, then force-stop/reopen; confirm each project restores its own saved preview speed and a deleted project leaves no speed preference.

4. **MP4 export**
   - Export a short multi-clip project with original audio and separate music/SFX/voiceover.
   - Open the exported MP4 in a separate player; seek near the beginning, middle, every clip boundary, and end.
   - Verify dimensions, duration, clip order, text, source audio, lane audio, fades, and absence of frozen/black frames.
   - Cancel one export and confirm no incomplete MP4 is published.
   - Cancel during a long verification pass and confirm decoder checks stop at the next bounded checkpoint before MediaStore publication begins.
   - Cancel while MediaStore is copying or reading back the MP4; confirm any pending row is deleted and no incomplete video is visible.

5. **Device compatibility**
   - Repeat with mixed resolutions, frame rates, orientations, and common device codecs.
   - Test low-storage and low-memory conditions without losing the saved edit decision list.

Do not mark any of these device-only checks complete from CI results alone.

   - Verify editor-initiated seeks land on the requested preview timestamp rather than the nearest sync sample. Test taps, long-press scrubbing, ±5-second transport, audio-clip selection, and text-layer selection on 23.976, 25, 29.97, and 59.94 fps sources.
   - Compare seek latency and audio continuity on a lower-powered API 29 device; confirm playlist rebuild/project restoration retains closest-sync seeking.


3. **Frame-by-frame preview navigation**
   - Import sources with 24, 23.976, 25, 29.97, 30, and 59.94 fps video tracks. Confirm previous/next-frame controls become available when the active Media3 track or cached source metadata provides a valid frame rate.
   - Step forward and backward repeatedly while paused; compare the displayed frame against the source frame sequence and verify no cumulative 33/34 ms or 41/42 ms drift.
   - Step to both ends of a trimmed clip and confirm navigation crosses into the adjacent clip using that clip's own frame rate (including 24-to-59.94 and 59.94-to-24 cuts). If adjacent metadata is unavailable, confirm the control fails closed rather than guessing a rate.
   - Repeat with audio-only/unknown-frame-rate sources and confirm frame-step controls remain disabled.
   - Verify frame seeks keep Music, SFX, and Voiceover on the same project timestamp; record seek latency on a lower-powered API 29 device.
   - Import adjacent 24 fps and 59.94 fps clips (then reverse their order); step forward from the first clip's last frame and backward from the second clip's first frame. Repeat with 23.976 and 29.97 fps sources.
   - Revoke or relink a source with unavailable frame-rate metadata; confirm the resolver fails closed at that cut rather than guessing, and that other clips remain step-able.
   - Repeat with multiple timeline clips sharing one source URI and verify Music, SFX, and Voiceover remain aligned after crossing the cut.
   - Repeat frame stepping with non-frame-aligned source IN points (for example 100 ms at 24 fps and 210 ms at 30 fps). Confirm stepping uses original-source presentation timestamps, converts back to clip-relative positions, and never seeks before the trimmed source range.
   - Test very short trims that contain no source presentation frame; confirm frame stepping fails closed without moving the playhead.
   - Cross between two trimmed clips with different frame rates in both directions; verify the first/last available source frame is selected rather than assuming clip-relative zero is a frame boundary.
   - Repeat with variable-frame-rate footage and record actual presentation timestamps separately. Nominal FPS metadata alone is not proof of frame-accurate VFR stepping.


## Independent recovery-file mirror
- Save a project with at least one video clip and Music/SFX/Voiceover items, then force-stop. In a disposable test install, remove the project's primary/recovery preference values while leaving the project index and app-private files directory intact.
- Reopen the project and confirm the recovery prompt reads `filesDir/project-recovery`, restores the video edit list, and restores audio only when its checkpoint timestamp matches.
- Make several rapid edits, then force-stop after the save UI returns. Verify the newest checkpoint is offered rather than a delayed older file write.
- Delete a project and verify both recovery journal files are removed and a tombstone prevents a delayed write from recreating them. Confirm no recovery file is created outside the app-private files directory.
- This mirror does not recover a lost project index, survive app-data clearing/uninstall/device loss, or include media bytes and edits that were never persisted.


## MediaStore export-copy verification
- Export a short and a long MP4, then confirm the UI reports completion only after the Movies/VyroCut copy has been read back and matched to the verified temporary file by byte count and SHA-256.
- Repeat on Android 10 / API 29 and a current Android release. Confirm the pending MediaStore item is readable by the owning app before its pending flag is cleared, and that a read-back failure removes the pending row rather than exposing an incomplete video.
- Open and seek the published MP4 in a separate player after publication. CI unit tests validate the streaming fingerprint logic, not provider-specific read-back behaviour.

### Abandoned export cleanup at startup
1. Create a stale app-private `Movies/VyroCut/VyroCut_<timestamp>.mp4` file with a last-modified time older than 24 hours, then force-stop and relaunch VyroCut.
2. Confirm the stale generated file is removed without starting a new export and that startup remains responsive while cleanup runs on IO.
3. Confirm recent generated files, unrelated MP4s, directories, symlinks, and published MediaStore videos remain untouched.
4. Repeat with app-specific external Movies storage unavailable to exercise the `filesDir/Movies/VyroCut` fallback.
5. Repeat on API 29 and a current Android release; verify that an Activity recreation in the same process does not trigger a second cleanup pass.

## Audio waveform decode cancellation
1. Open the Audio tool with a long music, SFX, or voiceover source and switch lanes while its waveform is decoding. Confirm the obsolete decode stops and the newly selected lane can decode without waiting for the old request.
2. Close the Audio tool during waveform extraction, then reopen it. Confirm the cancelled request does not populate stale UI state and a fresh request can complete.
3. Scroll a video timeline clip with waveform extraction active, then remove the clip or leave the composable. Confirm the decode is cancelled and the MediaCodec/MediaExtractor resources are released.
4. Repeat while switching projects and while a decoder is slow or unable to produce output. Confirm the no-progress timeout still returns a normal unavailable waveform, while coroutine cancellation propagates to the Compose caller.
5. CI tests validate the pure decode-loop policy. Cancellation latency, codec release, UI responsiveness, and stale-state behaviour require physical Android-device validation.


## Audio waveform request deduplication
1. Display the same source URI, source IN/OUT range, and bucket resolution in more than one waveform consumer. Confirm both consumers receive the same normalized bars while only one decode is needed for the cache miss.
2. Repeat with different trim ranges and with 48-bucket versus 96-bucket requests; confirm distinct cache keys do not block or reuse the wrong resolution.
3. Cancel one consumer while it is waiting behind another request for the same key. Confirm the active request can finish and a later consumer can still acquire the key.
4. Request two different source ranges at the same time. Confirm the per-key gate does not serialize unrelated decodes globally.
5. Repeat while switching lanes, closing the Audio tool, removing a clip, and switching projects. Confirm obsolete Compose callers remain cancellable and decoder resources are released by the active owner.
6. CI tests validate request coordination and cancellation. Actual decoder-session counts, codec latency, and UI responsiveness require physical Android-device validation.


## Audio waveform cache source-change invalidation
1. Open a Music, SFX, or Voiceover waveform, then replace/update the same provider item while retaining its content URI. Close and reopen the Audio tool; verify changed size or last-modified metadata produces a new cache key and waveform decode.
2. Repeat with the same byte size but a changed last-modified timestamp. Confirm the old waveform is not reused.
3. Repeat with a provider that exposes size but not modified time, then with one that exposes only modified time. Confirm each available field participates in the revision.
4. Repeat with a provider exposing neither field. Confirm waveform generation still works using URI/range identity and does not fail because metadata is unavailable; stale-cache detection is not guaranteed in this fallback case.
5. Repeat after switching projects and lanes. Confirm the source revision is queried on IO and identical clips still share the same revision-aware request gate/cache key.
6. CI tests validate revision-string/key logic. Actual provider metadata behaviour and same-URI replacement require physical-device testing with document and MediaStore providers.


## Variable-frame-rate source-change cache invalidation
1. Build a VFR timestamp index for a source URI, then replace the underlying document/media item while retaining the same URI.
2. Change the file size and verify the next frame-step request queries provider metadata and builds a new timestamp index rather than reusing the old one.
3. Repeat with unchanged size but a changed last-modified timestamp.
4. Repeat with size-only metadata, modified-time-only metadata, and providers exposing neither field. The last case must retain URI/range fallback behaviour and must not fail frame stepping solely because revision metadata is unavailable.
5. Confirm two adjacent clips referencing the same URI share one provider revision query during a single frame-step request.
6. CI tests cover revision identity and cache-key separation. Actual document-provider/MediaStore metadata and same-URI replacement require physical-device validation.
7. Step repeatedly between three recently used clips, then index a fourth; revisit the first three and record whether hot indexes remain responsive while the least-recently-used index is the one that needs rebuilding. JVM tests verify the cache recency order.

## Variable-frame-rate preview frame stepping
0. Repeat frame stepping after rapidly switching between source clips and reopening the Audio/Preview tools; verify the selected clip still resolves against its own source timestamps. JVM coverage confirms later mutation of the caller-owned timestamp array cannot alter the cached snapshot; actual MediaExtractor output and displayed-frame behaviour still require device validation.
1. Import a VFR source with visibly irregular frame intervals and use Previous/Next Frame at several points. Confirm the target follows actual sample presentation timestamps rather than a constant nominal cadence.
2. Trim the source at a timestamp between frames. Confirm the first seek displays the first available frame at/after trim IN, Next advances once, and Previous does not step outside the trim.
3. Step across cuts between VFR, CFR, and mixed-rate clips. Confirm each clip uses its own source PTS index and the project playhead/audio lanes continue to use the existing synchronized project-clock seek path.
4. Repeat with a long clip exceeding the 120,000-timestamp safety cap and with a provider/codec that cannot expose a monotonic sample index. Confirm the editor falls back to nominal frame-rate stepping instead of using a partial index.
5. Switch projects, trim a clip, reorder clips, and repeat. Confirm timestamp indexes are keyed by URI plus source IN/OUT and the in-memory cache remains bounded to three entries.
6. Use open-GOP or B-frame-heavy footage and verify samples whose PTS falls inside trim OUT are not omitted merely because they appear after a qualifying sync sample in decode order. The scanner reads up to 64 samples after the most recent qualifying boundary sync, subject to the 120,000-sample total cap.
7. JVM tests validate irregular timestamp resolution, trim boundaries, cross-clip mapping, invalid-index rejection, LRU eviction, and the bounded post-sync lookahead policy. Actual MediaExtractor sample ordering, codec-specific reorder depth, seek presentation, VFR frame display, decoder latency, and audio continuity require physical Android-device validation.


## Continuous VFR playhead sampling
1. Start playback on a VFR clip and confirm the active clip's timestamp index is loaded on Dispatchers.IO; the immediately following clip should be warmed before the natural transition when the scan completes in time.
2. Confirm the timeline marker advances on source presentation-frame boundaries using the latest PTS not after Media3's current position. The project clock used for audio drift correction and persisted checkpoints must remain the unquantized Media3 position.
3. Cross from VFR to CFR and mixed-rate clips. Confirm the marker switches to the destination clip's own source trim/index and never carries the previous clip's timestamps across the cut.
4. Switch projects, trim or reorder clips during prefetch, and press previous/next-frame while playback is active. Confirm stale scans are cancelled or ignored and the frame-step request owns its decoder work.
5. Check 30/60 Hz displays and long clips for UI responsiveness, battery/CPU impact, and marker jitter. This PTS-based marker is a presentation-timeline approximation, not proof of the exact frame currently displayed by the decoder; only physical-device playback can establish that.

## VFR timestamp cache revision replacement
1. Build a VFR timestamp index, then update the underlying source while retaining the same content URI and source IN/OUT range.
2. Resume playback or request a frame step so the source revision is re-read. Confirm the refreshed index replaces the previous revision for that exact URI/trim instead of leaving two competing indexes.
3. Repeat with a second trim of the same URI and with another clip source. Confirm unrelated trim/source entries remain cached and the three-entry LRU bound is preserved.
4. Repeat with providers exposing size only, modified time only, and neither field. Confirm the fallback revision remains usable and never throws.
5. JVM tests validate superseded-revision pruning on cache insertion and successful cache access. Same-URI provider replacement and visible playback behaviour require physical Android-device validation.


## Shared VFR timestamp extraction requests
1. Start playback so active/next clip prefetch begins, then immediately press Previous/Next Frame while the active source index is still warming.
2. Repeat with the same URI, source IN/OUT and provider revision. Confirm prefetch and frame-step consumers share one MediaExtractor scan rather than opening duplicate extractors.
3. Repeat with a different trim and with a changed source revision. Confirm those are separate request keys and can proceed independently.
4. Use a source/codec that produces no usable PTS index. Confirm the null result is negative-cached for that exact revision so concurrent waiters do not repeatedly scan it.
5. Switch projects or reorder/trim clips while a request waits on the gate. Confirm stale requests do not publish results into the new project state, and a cancelled waiter does not cancel the active owner.
6. JVM tests cover request-key separation, same-key null-result reuse, unrelated-key concurrency, and cancellation-safe reference cleanup. Actual MediaExtractor counts, playback responsiveness, and displayed-frame behaviour still require physical Android-device validation.


## Recovery mirror IO and late-result protection
1. Open a project with large video and audio recovery journals while monitoring startup responsiveness; the Compose thread must not perform file reads or SHA-256 journal validation.
2. Switch projects while recovery mirror reads are in flight. Confirm a result from the previous project is discarded.
3. Edit a video clip or audio lane before a mirror read completes. Confirm the late mirror result does not overwrite the in-memory edit.
4. Open a project whose primary preferences are current but whose mirrored audio checkpoint is newer. Confirm only the matching newer audio checkpoint is applied.
5. Use the Recover action for a project other than the currently open project. Confirm its audio mirror is read on IO and restored only with the selected video's exact checkpoint timestamp.
6. JVM tests cover pure audio-source selection; startup jank, disk latency, and process-death recovery still require physical Android-device validation.


## Recovery journal validation thread affinity
1. Open a project with large recovery journals and verify file reads, SHA-256 checks, Base64 decoding, and recovered audio JSON selection occur on Dispatchers.IO.
2. Start recovery for another project, switch the active project while its journal is being decoded, and confirm the recovered project is persisted without replacing the newly active project's UI state.
3. Edit the active project's audio or video list while its mirror is loading; confirm the late result is discarded.
4. Repeat with a corrupt primary journal and a valid mirrored journal, then with a valid primary and newer mirrored audio checkpoint.
5. CI covers the pure recovery-source selection rules. Thread affinity, startup responsiveness, and force-stop recovery require physical Android-device validation.
