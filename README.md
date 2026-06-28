# Inventory Snap

A tiny native Android inventory app:

- Take a photo of an item.
- Add quantity.
- Add notes.
- See all scanned items in a mobile-friendly list.
- Edit quantity/notes.
- Delete items.
- Export/import the inventory JSON.

The app is intentionally simple and uses only Android's built-in Java APIs. There is no Flutter, React Native, Firebase, or server.

## Device support

This version targets Android 10+ (`minSdk 29`) so it can save full-size camera photos through Android's scoped MediaStore API without needing a separate file-provider library.

Photos are saved under your phone gallery's `Pictures/InventorySnap` folder. The item list itself is stored locally on the phone using SharedPreferences.

## Build the APK without installing Android Studio

You can build the APK in GitHub Actions.

1. Create a new GitHub repository.
2. Upload all files from this folder into the repository.
3. Go to the repository's **Actions** tab.
4. Open **Build debug APK**.
5. Click **Run workflow**.
6. When it completes, open the finished workflow run.
7. Download the artifact named **inventory-snap-debug-apk**.
8. Unzip it. Inside you will see `app-debug.apk`.
9. Move `app-debug.apk` to your Android phone and open it.
10. Android may ask you to allow installation from that source. Allow it, install, then open **Inventory Snap**.

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
- Export/import JSON stores inventory text data and image URI references. It is mainly for backup on the same phone/session; moving the JSON to a totally different phone may not restore photo access unless the photos are also moved.
