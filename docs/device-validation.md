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
   - Exercise undo/redo, JSON backup/restore, missing-source detection, and relinking while offline.

2. **Continuous preview and timeline**
   - Play through at least three clips without touching the controls; verify no freeze, unexpected restart, or black gap at clip boundaries.
   - Seek by tapping and dragging the timeline across clip boundaries and the exact project end.
   - Reorder, trim, split, duplicate, and delete clips while the selected clip is playing.
   - Background and resume the app; confirm the saved preview position is coherent.

3. **Music, SFX, and voiceover**
   - Test leading, internal, and trailing gaps; source trims; lane offsets; fades; and volume.
   - Listen across clip transitions and repeated scrubs for drift, clicks, missing audio, or repeated seek glitches.
   - Test source video with audio, silent video, and all three independent lanes together.

4. **MP4 export**
   - Export a short multi-clip project with original audio and separate music/SFX/voiceover.
   - Open the exported MP4 in a separate player; seek near the beginning, middle, every clip boundary, and end.
   - Verify dimensions, duration, clip order, text, source audio, lane audio, fades, and absence of frozen/black frames.
   - Cancel one export and confirm no incomplete MP4 is published.

5. **Device compatibility**
   - Repeat with mixed resolutions, frame rates, orientations, and common device codecs.
   - Test low-storage and low-memory conditions without losing the saved edit decision list.

Do not mark any of these device-only checks complete from CI results alone.
