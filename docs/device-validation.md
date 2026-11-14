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
   - Seek by tapping and dragging the timeline across clip boundaries and the exact project end.
   - Use the ±5-second controls, timeline slider, video clip selection, audio clip selection, and text-layer selection; verify the video and Music/SFX/Voiceover players seek to the same project-clock position, including silence gaps and exact clip boundaries.
   - Switch projects and immediately scrub after opening; confirm no audio from the previous project's lane playlists is heard while the new sequences are rebuilding.
   - Reorder, trim, split, duplicate, and delete clips while the selected clip is playing.
   - Background and resume the app; confirm the saved preview position is coherent.

3. **Music, SFX, and voiceover**
   - Test leading, internal, and trailing gaps; source trims; lane offsets; fades; and volume.
   - Duplicate a selected Music, SFX, and Voiceover clip; verify it lands in the next available non-overlapping slot, preserves source trim/volume/fades, remains independently selectable, and survives undo/redo plus force-stop/reopen.
   - Listen across clip transitions and repeated scrubs for drift, clicks, missing audio, or repeated seek glitches.
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
