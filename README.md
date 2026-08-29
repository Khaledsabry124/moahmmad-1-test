# سجل الفوائت — Qada' (Missed Prayer) Tracker

An open-source, offline, privacy-respecting Android app that helps you track
daily prayers and the qada' (make-up) prayers you owe. Prayer times are
calculated on-device using standard astronomical formulas — no ads, no
analytics, no network access needed at runtime, no accounts.

## What this is

This is a lightweight native Android wrapper (Kotlin + WebView) around a
self-contained HTML/JS app (`app/src/main/assets/index.html`). All logic —
prayer time calculation, tracking, and storage — runs locally on the device
via the WebView's local storage. Nothing is sent anywhere.

## Building the APK yourself

You need [Android Studio](https://developer.android.com/studio) (free) or
the Android command-line SDK + JDK 17.

**Option A — Android Studio (easiest):**
1. Open Android Studio → "Open" → select this `QadaTracker` folder.
2. Let it sync Gradle (it will download the Android Gradle Plugin/SDK
   the first time — this needs internet once).
3. Build → Generate Signed Bundle / APK → APK → follow the signing wizard
   (create a new keystore if you don't have one).
4. Install the resulting APK on your phone, or `adb install app-release.apk`.

**Option B — command line (if you already have the Android SDK + JDK 17):**
```
./gradlew assembleRelease
```
(You'll need to run `gradle wrapper` once first if `gradlew` isn't present,
or use your own local `gradle` install.)

The debug build (`assembleDebug`) doesn't need signing and installs directly
for testing:
```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — to calculate prayer
  times for your location.
- `POST_NOTIFICATIONS` — to show prayer-time and reminder alerts.
- `SCHEDULE_EXACT_ALARM` — for precisely-timed alerts (falls back to
  approximate timing if not granted; no extra permission dialog is forced).
- `INTERNET` / `ACCESS_NETWORK_STATE` — used opportunistically, never
  required: when online, the app fetches official prayer times from
  aladhan.com and your location's place name from OpenStreetMap's Nominatim,
  as an enhancement over its own built-in astronomical calculation. If
  there's no connection, everything still works entirely offline using that
  built-in calculation and a generic "your current location" label — nothing
  breaks either way.

No ads, no analytics, no accounts, no tracking of any kind.

## Publishing to F-Droid

F-Droid builds apps from source itself — you don't upload an APK. Roughly:
1. Push this project to a public git repo (GitHub, GitLab, Codeberg…).
2. Add a `LICENSE` file (included — MIT).
3. Submit a merge request to
   [fdroiddata](https://gitlab.com/fdroid/fdroiddata) with a metadata file
   describing how to build your app from that repo (`Categories`,
   `RepoType: git`, `Build:` block with your version/commit, etc.) — see
   F-Droid's ["Submitting to F-Droid Quick Start Guide"](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/).
4. F-Droid's build server checks out your tagged commit and builds it
   itself from source, reproducibly — which is why the project has no
   proprietary dependencies (no Firebase, no Google Play services, no ads
   SDKs) and no pre-built binaries committed.

## Project layout

```
QadaTracker/
├── app/
│   ├── src/main/
│   │   ├── java/com/mmusa/qadatracker/MainActivity.kt   (WebView host + geolocation permission bridge)
│   │   ├── assets/index.html                             (the actual app: UI, logic, storage)
│   │   ├── res/                                          (adaptive icon, app name, theme)
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── LICENSE
```

## License

MIT — see `LICENSE`. Use, modify, and redistribute freely.
