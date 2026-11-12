# VyroCut architecture

## Product constraints
- Native Android application; minimum Android 10 / API 29.
- Offline-first: source media, edit decisions, previews, and exports remain on-device.
- No account, cloud storage, or network service is required for core editing.
- UI reference system: matte charcoal surfaces, electric turquoise active state, Inter UI typography, JetBrains Mono timecodes.

## Planned layers

### Presentation
Jetpack Compose screens and state holders. Screens should remain thin and send edit commands to domain use cases.

### Project and timeline domain
A project contains a stable ID, canvas aspect ratio, frame rate, duration, ordered tracks, and edit decision data. Timeline items reference media assets and store source in/out points, timeline position, transform, volume, and effect parameters. All timeline time values should use a consistent microsecond or frame-based representation; avoid floating-point seconds as persisted identity.

### Media engine
Media3 ExoPlayer provides preview playback. Media3 Transformer is the first export pipeline for supported edits. More advanced compositing, transitions, animated overlays, and custom effects can be added behind a renderer interface so the UI and project model do not depend on one rendering implementation.

### Local persistence
Room stores project metadata and edit decision data. MediaStore / Android document-provider URIs reference user-selected media. Persist URI read permissions where available. Never copy or upload source media without an explicit product reason.

### AI capabilities
Captioning and other AI assistance must have an on-device implementation path. Network-hosted inference must not be a dependency for the core product.

## Delivery sequence
1. Android app foundation and document-picker import.
2. Persistent local projects and media library.
3. Video preview and a real single-track timeline with trim/split.
4. Multitrack video, audio, text, and image overlays.
5. Export presets, progress, cancellation, and output verification.
6. Captions, effects, transitions, keyframes, and performance optimization.

## Engineering rules
- A visible control must perform a real operation or be clearly marked as not implemented.
- Keep edits non-destructive in the project model.
- Test on API 29 and a current Android release, including low-memory devices.
- Keep expensive decode/render work off the main thread.
- Verify output files by decoding them after export; do not treat a success toast as proof of a valid render.
