# Temporary MP4 cleanup

VyroCut writes Transformer output to the app-specific Movies/VyroCut directory before validating and publishing it through MediaStore. If Android kills the process while Transformer is writing, the incomplete private MP4 can remain on disk because the normal completion/error callbacks never run.

Before publishing the next fully verified export, the app now removes only direct-child files matching the generated `VyroCut_<timestamp>.mp4` naming pattern when their last-modified time is at least 24 hours old.

Safety rules:
- The currently verified export is explicitly protected from cleanup.
- Published videos in the shared `Movies/VyroCut` MediaStore collection are not in this app-private temporary directory and are never considered.
- Other filenames, nested paths, symlinks, directories, unknown timestamps, future-dated files, and files newer than the retention window are retained.
- Cleanup is best-effort; a failed delete does not block a verified export.
- A wall-clock rollback makes files appear future-dated and therefore retains them rather than risking deletion.

The pure age/name rule and filesystem selection/deletion behaviour are covered by JVM tests. Device validation must still confirm the app-specific external-files path and cleanup behaviour on API 29 and a current Android release. This cleanup does not claim to resume an interrupted Transformer job; it only retires abandoned temporary output when a later verified export reaches publication.
