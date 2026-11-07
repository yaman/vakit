# Vakit

A Wear OS watch-face complication that shows the next Diyanet prayer time on a Samsung Galaxy Watch 4.

## Behavior

- The complication shows the next entry as a prayer name and clock time, for example `İkindi` and `14:47`.
- The rotation includes İmsak, Güneş, Öğle, İkindi, Akşam and Yatsı. After Yatsı it shows tomorrow's İmsak.
- Fifteen minutes before each entry the watch vibrates. Güneş loops its pattern with 10 s of rest for up to five minutes, with an ongoing notification whose `Kapat` action stops it early.
- A full-screen prayer screen opens when the overlay permission is available; a silent high-priority notification card is the fallback.
- The app bundles one canonical year of Diyanet times for all 869 Türkiye districts.
- GPS picks the nearest district. The district changes only when a new one is at least 5 km closer.
- Location is checked hourly, on boot, on app open and on demand.
- The app declares no `INTERNET` permission and makes no network calls.
- No phone companion, no watch face, no tile.

## Build

```bash
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0"
./gradlew :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the watch

Enable Developer options and Wireless debugging on the watch, then pair:

```bash
adb pair <watch-ip>:<pair-port>
adb connect <watch-ip>:<debug-port>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Pre-grant location to skip the dialogs:

```bash
adb shell pm grant io.canavar.vakit android.permission.ACCESS_FINE_LOCATION
adb shell pm grant io.canavar.vakit android.permission.ACCESS_BACKGROUND_LOCATION
```

If the background grant fails, open Settings → Apps → Vakit → Permissions → Location and choose "Allow all the time".

Grant exact-alarm access so the 15-minute vibration reminders fire on time:

```bash
adb shell cmd appops set io.canavar.vakit SCHEDULE_EXACT_ALARM allow
```

Without it, reminders fall back to inexact alarms and may be delayed.

Wear OS does not support full-screen intent notifications. The app tries to show a full-screen prayer activity using the "Display over other apps" special access, and falls back to a high-priority notification card. Grant both if the Settings screens are unavailable:

```bash
adb shell appops set io.canavar.vakit SYSTEM_ALERT_WINDOW allow
adb shell pm grant io.canavar.vakit android.permission.POST_NOTIFICATIONS
```

Open Vakit once. It copies the bundled database, takes a location fix, selects the nearest district and shows today's times. Then long-press the watch face → Customize → choose a complication slot → Vakit.

## Data

The timetable comes from Diyanet data republished by `ezanvakti.emushaf.net` and `ezanvakti.imsakiyem.com`. The official Diyanet API needs credentials and is not used.

The bundle holds 2026 times keyed by `(district_id, month, day)`. The year is never part of the key. February 29 uses February 28. Measured drift against Diyanet's 2027 data is at most 1 minute across five cities and all six entries.

Regenerate the bundle:

```bash
python3 scripts/build_timetable.py --report-unmatched
python3 scripts/build_timetable.py
```

The generator caches each district in `scripts/cache/`, so a rerun resumes. It writes `app/src/main/assets/timetable.db` and `timetable.version`.

## Tests

```bash
./gradlew :app:testDebugUnitTest
python3 scripts/test_build_timetable.py
```

## Accuracy

- The canonical year is fixed at build time. A Diyanet correction after the build needs a regeneration and a new APK.
- Times are Türkiye local time (`Europe/Istanbul`).
- District selection is only as precise as the Diyanet district, not the GPS fix.
