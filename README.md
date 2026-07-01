# Inventory Snap

A tiny native Android inventory app.

## Current UI

- First screen: scan lists.
- Tap a list to open its scans.
- `+` on the list screen creates a new list.
- `×` on a list deletes the whole list.
- Second screen: scans for the selected list.
- `+` on the scan screen takes a new scan.
- Scans are shown in a two-column grid.
- Images use `FIT_CENTER`, so photos are not cropped.
- Tap an image to view it full screen.
- `✓` marks a scan done. Done scans move to the bottom and are greyed/struck-through.
- `↺` undoes done.
- `✎` edits quantity/notes.
- `×` deletes a scan.
- `⋮` on the list screen contains export/import.
- The main screens now apply status/navigation bar safe-area padding so content does not overlap the phone notification bar or bottom gesture/navigation area.

The app uses only Android's built-in Java APIs. There is no Flutter, React Native, Firebase, or server.

## Device support

This version targets Android 10+ (`minSdk 29`) so it can save full-size camera photos through Android's scoped MediaStore API without needing a separate file-provider library.

Photos are saved under your phone gallery's `Pictures/InventorySnap` folder. The item lists are stored locally on the phone using SharedPreferences.

## Build the APK without installing Android Studio

You can build the APK in GitHub Actions.

1. Push these files to your GitHub repository.
2. Go to the repository's **Actions** tab.
3. Open **Build debug APK**.
4. Click **Run workflow**.
5. When it completes, open the finished workflow run.
6. Download the artifact named `inventory-snap-debug-apk`.
7. Unzip it. Inside you will see `app-debug.apk`.
8. Move `app-debug.apk` to your Android phone and open it.
9. Android may ask you to allow installation from that source. Allow it, install, then open Inventory Snap.

## Build locally, optional

Only do this if you already have Java, Gradle, and the Android SDK installed:

```bash
gradle :app:assembleDebug
```

The APK will be at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- The debug APK is enough for your own phone/testing.
- For Play Store release, you would need a signed release APK/AAB.
- Export/import stores inventory text data and image URI references. It is mainly for backup on the same phone/session; moving the JSON to a different phone may not restore photo access unless the photos are also moved.
