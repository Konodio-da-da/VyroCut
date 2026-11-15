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
