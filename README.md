# InventorySnap — one-photo-at-a-time UI

## Replace this file

`app/src/main/java/com/example/inventorysnap/MainActivity.java`

This replacement is based on the public `RP28/InventorySnap` repository's `main` branch inspected on 17 November 2026. No Gradle dependency, layout XML, manifest, or resources need to change.

## New workflow

1. Open a list. The screen shows **Capture mode** with a photo count, without rendering any image thumbnails.
2. Tap **Take photo**. The standard camera opens and a successful capture is immediately saved with quantity 1 and blank notes.
3. Tap **Find mode**. Exactly one photo is decoded in a worker thread to a display-sized bitmap.
4. Tap **Found**, **Skip**, or **Not found** to move on. Found/Not found persist to JSON; Skip is session-only and stays pending.
5. At the end, use **Review again** or **Back to capture**. Pending photos are prioritized on each new review.
6. **Edit details** adjusts quantity and notes; **Delete photo** deletes the record and the underlying gallery photo.

## Compatibility

- Reads original SharedPreferences `inventory_snap_store`, `lists_json_v2`, and legacy `items_json`.
- Reads old `done: true` as `status: found` and `done: false` as `status: pending`.
- Writes new `status` as `pending`, `found`, or `not_found`, plus a compatible `done` boolean.
- Keeps original MediaStore location `Pictures/InventorySnap`.
- Keeps JSON export/import (exports photo URI references, not binary image bytes).
- No network or external libraries used.

## Before installing

Back up your original source and export your data using the current app. Install a debug build as an **update** over the original app (same applicationId), not by uninstalling the original; uninstalling usually clears local inventory data. Photos may remain in the gallery but the index would be lost.

## Build

Use the existing GitHub Actions workflow: push the replaced file, run **Build debug APK**, download `inventory-snap-debug-apk`, then install `app-debug.apk`.

## Limitations

- This implementation hasn't been compiled or tested on an Android device in this environment. Build/test it on a copy or branch first.
- Existing photo URI-only backups are not portable to another phone without the photo files.
- Photos are saved to the gallery, exactly as in the original app; automatic discard of camera photos is intentionally not implemented.
- Skipping doesn't change persistent status. Starting a new review includes skipped items again.
