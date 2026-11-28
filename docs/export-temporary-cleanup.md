# Temporary MP4 cleanup

VyroCut writes Transformer output to the app-specific Movies/VyroCut directory before validating and publishing it through MediaStore. If Android kills the process while Transformer is writing, the incomplete private MP4 can remain on disk because the normal completion/error callbacks never run.

The app now performs best-effort cleanup once per process at Activity startup, and again before publishing a fully verified export. It removes only direct-child files matching the generated `VyroCut_<timestamp>.mp4` naming pattern when their last-modified time is at least 24 hours old. Both startup cleanup and Transformer output use one shared path resolver, preferring the app-specific external Movies directory and falling back to `filesDir/Movies/VyroCut`. The startup pass runs on `Dispatchers.IO` and does not delay the editor UI.

Safety rules:
- The currently verified export is explicitly protected from cleanup.
- Published videos in the shared `Movies/VyroCut` MediaStore collection are not in this app-private temporary directory and are never considered.
- Other filenames, nested paths, symlinks, directories, unknown timestamps, future-dated files, and files newer than the retention window are retained.
- Cleanup is best-effort; a failed delete does not block a verified export.
- A wall-clock rollback makes files appear future-dated and therefore retains them rather than risking deletion.

The pure age/name rule, external/internal directory selection, and filesystem selection/deletion behaviour are covered by JVM tests. Device validation must still confirm the app-specific external-files path and cleanup behaviour on API 29 and a current Android release. This cleanup does not claim to resume an interrupted Transformer job; it only retires abandoned temporary output when a later verified export reaches publication.
