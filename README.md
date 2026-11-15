# Drift

A minimal wallpaper rotator for Android. Pick photos, group them into collections, and Drift quietly changes your home and lock screen through the day. It doesn't run in the background between changes.

## What it does

- **Collections** of photos from your gallery, chosen with the system photo picker. Drift needs no storage permission.
- **Home, lock, or both**, set per collection.
- **Rotation interval**: 15 min, 30 min, 1, 3, 6 or 12 hours, or daily. Photos are shuffled without repeats until each one has been shown.
- **Schedules**: limit a collection to certain days and a time range, which can cross midnight. A scheduled collection takes over from always-on ones while its schedule is active.

## How it stays light

- **No resident process.** One inexact `AlarmManager.setWindow` alarm (10-minute window) wakes a receiver. The receiver sets whatever is due, books the next alarm, and exits. It skips the 60-second polling loop, foreground service and WorkManager that apps like this often use.
- **Wallpapers are decoded once, at screen size.** `ImageDecoder` applies EXIF rotation, then Drift centre-crops and hands the system a compact JPEG.
- **Two permissions, both granted automatically:** `SET_WALLPAPER` and `RECEIVE_BOOT_COMPLETED`. It has no network access, notifications or exact-alarm permission.
- **No libraries beyond Compose Material 3 and Activity.** State is one JSON file, read with Android's built-in `org.json`. The release APK is about 2.3 MB.

## Known limits

- Changes land within about 10 minutes of the target time. Exact timing would need the "Alarms & reminders" permission, which Drift deliberately doesn't ask for. In Doze, Android may defer changes further, until the phone wakes.
- "New wallpaper on every unlock" is not possible without a process that runs all the time, so Drift doesn't offer it.
- Android caps persisted photo grants at 512 per app. Drift releases a grant when a photo is removed from every collection.
- Force-stopping the app cancels its alarm until the app is opened again. This is standard Android behaviour.

## Building

Requirements: JDK 17 or newer (Android Studio's JBR works) and Android SDK 37.

```sh
./gradlew :app:testDebugUnitTest   # schedule logic tests
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease     # signed if keystore/keystore.properties exists
```

Release signing reads `keystore/keystore.properties` (gitignored) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

## Code map

| File | Role |
|---|---|
| `Model.kt` | Collections, schedules, active-collection rules, JSON store |
| `Engine.kt` | Decides what's due, sets wallpapers, books the next alarm; boot/time receiver |
| `MainActivity.kt` | Activity, navigation, photo-grant bookkeeping |
| `Home.kt` / `Editor.kt` | The two screens |
| `Ui.kt` | Theme, icons, shared controls, thumbnail loader |
