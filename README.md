# Drift

![Drift on a Pixel 9: home screen in light and dark, and the collection editor](docs/hero.jpg)

**A minimal wallpaper rotator for Android that quietly changes your home and lock screen through the day.**

Pick photos, group them into collections, and choose when each one rotates. In its default timed mode Drift has no background process between changes.

## Screenshots

<p align="center">
  <img src="docs/home-dark.png" width="30%" alt="Home screen">
  <img src="docs/editor-dark.png" width="30%" alt="Collection editor">
  <img src="docs/photos-dark.png" width="30%" alt="Photos in a collection">
</p>

<p align="center">
  <img src="docs/launcher.png" width="30%" alt="A Drift wallpaper on the launcher">
  <img src="docs/drift.gif" width="30%" alt="Tapping a preview to move the wallpaper on">
</p>

## What it does

- **Collections** of photos from your gallery, chosen with the system photo picker. Drift needs no storage permission.
- **Home, lock, or both**, set per collection.
- **Rotation interval**: 15 min, 30 min, 1, 3, 6 or 12 hours, or daily. Photos are shuffled without repeats until each one has been shown.
- **Random**: a different gap every time, from 30 minutes to 4 hours.
- **Each wake**: a new photo is ready every time the screen turns off. This needs Drift set as your live wallpaper (one-time setup from the editor).
- **Schedules**: limit a collection to certain days and a time range, which can cross midnight. A scheduled collection takes over from always-on ones while its schedule is active.

## How it stays light

- **No resident process in timed mode.** One inexact `AlarmManager.setWindow` alarm (10-minute window) wakes a receiver. The receiver sets whatever is due, books the next alarm, and exits. It skips the 60-second polling loop, foreground service and WorkManager that apps like this often use.
- **Each wake is the exception.** Hearing the screen turn off needs a receiver that is always registered, so Drift stays loaded as your live wallpaper while that mode is in use. It draws only when the photo changes. Static timed mode (any other interval) remains the lightest.
- **Wallpapers are decoded once, at screen size.** `ImageDecoder` applies EXIF rotation, then Drift centre-crops and hands the system a compact JPEG.
- **Two permissions, both granted automatically:** `SET_WALLPAPER` and `RECEIVE_BOOT_COMPLETED`. It has no network access, notifications or exact-alarm permission.
- **No libraries beyond Compose Material 3 and Activity.** State is one JSON file, read with Android's built-in `org.json`. The release APK is about 2.3 MB.

## Measured on a Pixel 9 (Android 17)

- Release APK: 2.3 MB. Cold start: about 370 ms.
- One background change: 1 wake-up alarm, about 0.7 s of wake lock and about 0.5 s of CPU, after which Android freezes the process.
- Verified with the app process killed and the screen off:
  - an interval rotation happened;
  - a schedule edge handed the lock screen back from a scheduled collection to the always-on one;
  - the receiver cold-started from the alarm.
- Photos with EXIF rotation and landscape photos come out upright and centre-cropped.

Debug builds add a "1 min" interval so background behaviour can be checked quickly. Release builds don't show it.

## Battery

Measured on a Pixel 9 (Android 17) with Android's own battery accounting (`dumpsys batterystats`), plus CPU timers inside the app. Daily figures assume a 4,700 mAh battery.

| Mode | Cost per change | Typical day | Share of battery per day |
|---|---|---|---|
| **Daily** | ~0.6 s CPU | 1 change, ~0.03 mAh | **< 0.001%** |
| **1–12 hours** | ~0.6 s CPU, 1 wake-up alarm | hourly: 24 changes, ~0.7 mAh | **~0.015%** |
| **Random** (30 min–4 h) | same as timed | ~11 changes, ~0.3 mAh | **~0.007%** |
| **15 min** | same as timed | 96 changes, ~2.8 mAh | **~0.06%** |
| **Each wake** | ~0.2 s CPU, no extra wake-up | 100 screen-offs, ~1 mAh | **~0.02%** |

What the numbers mean:

- **Timed modes** (any interval, and Random) cost nothing between changes. The process is dead or frozen, with no wake locks and no polling. Each change costs one alarm wake-up, a process start, one decode at screen size and one JPEG hand-off: about 0.5–0.7 s of CPU and 0.7 s of wake lock. The system's wallpaper renderer then does a small amount of work of its own, not counted above.
- **Each wake** costs about 0.2 s of CPU per screen-off: about 176 ms to decode the photo, the rest bookkeeping. It never wakes the phone, because the screen turning off has already done that. Kept loaded as the wallpaper and idle, it used 0.17 s of CPU in 5 minutes of screen-off time, and most of that was the one change triggered by turning the screen off. Its real cost is memory: about 56 MB stays loaded while it's your wallpaper.
- **Most battery-friendly:** Daily, then longer intervals, then Random. Every mode stays well under a tenth of a percent of battery per day. The phone's screen uses more energy in a few seconds than Drift uses in a day.

## Known limits

- Changes land within about 10 minutes of the target time. Exact timing would need the "Alarms & reminders" permission, which Drift deliberately doesn't ask for. In Doze, Android may defer changes further, until the phone wakes.
- "Each wake" changes the photo when the screen turns off, not at the moment of unlock, and requires Drift to be the live wallpaper for the screens it targets.
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
| `DriftWallpaper.kt` | Optional live wallpaper: draws the current photo and rotates "Each wake" collections on screen-off |
| `Wording.kt` | Interval, target, day and schedule labels shared by the list and editor |
| `MainActivity.kt` | Activity, navigation, photo-grant bookkeeping |
| `Home.kt` / `Editor.kt` | The two screens |
| `Ui.kt` | Theme, icons, shared controls, thumbnail loader |
