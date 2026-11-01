# Vakit — Wear OS Prayer Complication

Date: 2026-11-01
Status: Approved design
Target device: Samsung Galaxy Watch 4, Wear OS 6.0, Android 16 (API 36), Play services 26

## 1. Problem

Show the next prayer time on the watch face of a Galaxy Watch 4, without opening an app, without a phone companion, and without network access from the watch.

## 2. Goal

A Wear OS app that publishes one watch-face complication. The complication shows the next entry in the Diyanet timetable as `Name HH:mm`, for example `İkindi 14:47`.

Success means all of these hold:

1. The watch face shows the next entry after a one-time setup.
2. A reboot draws the correct value before the app is opened.
3. A reboot starts an immediate location check.
4. Moving to another district changes the displayed times.
5. The app declares no `INTERNET` permission and makes no network calls at runtime.
6. The app survives February 29 without special user action.

## 3. Scope

In scope:

- One Wear OS application, package `io.canavar.vakit`, label `Vakit`.
- One complication data source: `SHORT_TEXT` and `LONG_TEXT`.
- A build-time generator that produces a bundled timetable database.
- A two-screen app: status and district picker.
- Hourly location check, plus an immediate check on boot, app open, and manual refresh.
- Manual district override.

Out of scope:

- Phone companion app.
- Dedicated watch face.
- Tile.
- Adhan notifications or alarms.
- Qibla direction.
- Hijri date display.
- Locations outside Türkiye.
- Play Store distribution.
- Internet updates after install.

## 4. Device and platform

- Samsung Galaxy Watch 4.
- Reported runtime: Wear OS 6.0, Android 16 (API 36), Play services 26.
- `minSdk 30` keeps the app installable on Wear OS 3 and later.
- `targetSdk 34`, `compileSdk 35`. The app opts out of Android 15 and Android 16 behavior changes. This is deliberate. The app needs no API 35 or 36 feature, and the lower target keeps background-location and job-scheduling behavior on the documented path.
- Android 16 may warn that the app targets an older Android version. The warning is harmless for a sideloaded app.
- The watch has Google Play services, so `FusedLocationProviderClient` is available.
- Sideload over wireless debugging with `adb`. No Play Store listing.

## 5. Data source

Diyanet publishes prayer times per district (ilçe), not per coordinate.

Canonical source:

- Provinces: `GET https://ezanvakti.emushaf.net/sehirler/2`
- Districts: `GET https://ezanvakti.emushaf.net/ilceler/{provinceId}`
- Times: `GET https://ezanvakti.imsakiyem.com/api/prayer-times/{districtId}/range?startDate=2026-01-01&endDate=2026-12-31`
- Coordinates: `https://raw.githubusercontent.com/BuNickTamYirmiHarfli/turkey-cities-districts-json/main/cities.json`

Both services republish Diyanet data. They are not official Diyanet endpoints.

The official `awqatsalah.diyanet.gov.tr` API requires a login, bearer tokens, and has a 10-request limit per query. It is not usable for this app.

## 6. Canonical year

The watch stores one year of times: 2026. It reuses that year forever, keyed by month and day.

Why this is acceptable:

- Measured against Diyanet's 2026 and 2027 data for Ankara, İstanbul, Erzurum, Edirne, and Hatay, across all six entries and all 365 dates: mean difference 0.0 minutes, maximum difference 1 minute, zero dates over 1 minute.
- The leap-year phase shift is bounded to roughly one day of solar phase. At Turkish latitudes this is expected to add at most 1–2 minutes, mostly on İmsak and Yatsı.
- Diyanet rounds published times to whole minutes.

Rules:

- Lookup key is `(district_id, month, day)`. The year is never part of the key.
- February 29 uses the February 28 row.
- December 31 uses the January 1 row for the following day.

If Diyanet changes its calculation method, the bundled database must be rebuilt. Nothing else requires a rebuild.

## 7. Prayer sequence

The sequence includes Güneş:

`İmsak → Güneş → Öğle → İkindi → Akşam → Yatsı → next day İmsak`

Güneş is a time marker, not a prayer. The user asked for it in the rotation, so the complication shows it like any other entry.

Display names, with correct Turkish spelling:

| Key | Display |
| --- | --- |
| imsak | İmsak |
| gunes | Güneş |
| ogle | Öğle |
| ikindi | İkindi |
| aksam | Akşam |
| yatsi | Yatsı |

## 8. Next-entry rule

- Build the six instants for today, then the six for tomorrow.
- Return the first instant strictly after the current time.
- If the current time equals an instant exactly, return the following instant.
- Compare in `Europe/Istanbul`.
- If a row is missing, return null. The complication then shows `Veri yok`.

## 9. Bundled database

Asset: `app/src/main/assets/timetable.db`.

```sql
CREATE TABLE districts (
  id       INTEGER PRIMARY KEY,
  name     TEXT NOT NULL,
  province TEXT NOT NULL,
  lat      REAL NOT NULL,
  lon      REAL NOT NULL
) WITHOUT ROWID;

CREATE TABLE times (
  district_id INTEGER NOT NULL,
  month       INTEGER NOT NULL,
  day         INTEGER NOT NULL,
  imsak       INTEGER NOT NULL,
  gunes       INTEGER NOT NULL,
  ogle        INTEGER NOT NULL,
  ikindi      INTEGER NOT NULL,
  aksam       INTEGER NOT NULL,
  yatsi       INTEGER NOT NULL,
  PRIMARY KEY (district_id, month, day)
) WITHOUT ROWID;
```

- Times are minutes since local midnight, range 0–1439.
- Rows: 869 districts × 365 days ≈ 317,000.
- Estimated size: 8 MB.

Asset companion: `app/src/main/assets/timetable.version`, a single line:

```
v1;year=2026;districts=869;rows=317185;sha256=<first 16 hex characters of the file hash>
```

The hash makes the app recopy the database whenever the asset content changes, even if the build date does not.

On first app start, and whenever the version string differs:

1. Copy `timetable.db` from assets to a temporary file in `filesDir`.
2. Rename it to `filesDir/timetable.db`.
3. Write `filesDir/timetable.version`.

The complication service never copies the database. If the file is missing while a district is set, it returns the `Veri yok` state. If no district is set, it returns `Ayarla` or `Konum kapalı` as defined in section 12.

## 10. Runtime architecture

One Gradle module, Kotlin, Compose for Wear OS.

```
LocationWorker (hourly / immediate)
  → FusedLocation
  → DistrictSelector (nearest + hysteresis)
  → SettingsStore

TimetableRepository
  → filesDir/timetable.db (read-only)

NextEntryResolver (pure)
  → (row today, row tomorrow, now) → next entry

NextEntryComplicationService (thin adapter)
  → SettingsStore + TimetableRepository + NextEntryResolver
  → ComplicationData

PrayerBoundaryReceiver
  → requestUpdateAll() at the next entry
```

Units:

- `TimetableRepository` — opens the copied database read-only, returns the six times for a district and a month/day.
- `DistrictSelector` — pure function: current district plus fix plus catalog → next district.
- `NextEntryResolver` — pure function, no Android types.
- `SettingsStore` — DataStore: district id, auto-location flag, last fix, last check, location-blocked flag.
- `LocationScheduler` — owns WorkManager enqueueing for the periodic and immediate workers.
- `ComplicationUpdater` — wraps `ComplicationDataSourceUpdateRequester.requestUpdateAll()`.
- `BoundaryScheduler` — owns the `AlarmManager` alarm.

## 11. Location behavior

Triggers for an immediate check:

- `BOOT_COMPLETED`.
- App open.
- Manual "Konumu yenile".
- Package replaced (`MY_PACKAGE_REPLACED`).

Periodic check: every hour, `ExistingPeriodicWorkPolicy.KEEP`.

Immediate work: one-time, `ExistingWorkPolicy.KEEP`, so duplicates collapse.

Location acquisition:

1. Request `getCurrentLocation(PRIORITY_BALANCED_POWER_ACCURACY)` with a 45-second timeout.
2. On failure or timeout, use `getLastLocation()`.
3. If both fail, keep the current district and set `locationBlocked`.

District selection:

- Compute haversine distance from the fix to every district center.
- Candidate is the nearest center.
- Keep the current district unless the candidate is at least 5 km closer.
- Tie-break on lower district id.
- Manual selection sets `autoLocation = false`. "GPS kullan" sets it back to true.

Permissions:

- `ACCESS_FINE_LOCATION` for foreground fixes.
- `ACCESS_BACKGROUND_LOCATION` for fixes taken by the worker.
- `RECEIVE_BOOT_COMPLETED` for the boot receiver.
- No `INTERNET`.

If background location is unavailable, the worker cannot fix in the background. The app keeps the last district, sets `locationBlocked`, and shows a warning on the status screen.

## 12. Complication behavior

Supported types: `SHORT_TEXT` and `LONG_TEXT`.

`SHORT_TEXT`:

- `text = "14:47"` (the clock, 5 characters)
- `title = "İkindi"` (the name, 6 characters or fewer)
- `contentDescription = "Sonraki vakit: İkindi 14:47"`
- `tapAction` opens `MainActivity`

The library caps `ShortTextComplicationData.text` at 7 characters (`MAX_TEXT_LENGTH`). The name therefore goes in `title`, which the API documents as expected to be displayed. All five prayer names are 6 characters or fewer.

`LONG_TEXT`:

- `title = "Sonraki vakit"`
- `text = "İkindi 14:47"`
- `contentDescription = "Sonraki vakit: İkindi 14:47"`
- `tapAction` opens `MainActivity`

Both types use the `ShortTextComplicationData.Builder` and `LongTextComplicationData.Builder` classes. The old `ComplicationData(...)` constructor no longer exists in version 1.3.0.

States:

| Condition | Text |
| --- | --- |
| District set and row found | `Name HH:mm` |
| No district, permission denied | `Konum kapalı` |
| No district, no permission result yet | `Ayarla` |
| District set, row missing | `Veri yok` |

Update triggers, all calling `requestUpdateAll()`:

- Database copied or district changed.
- Immediate worker finished.
- Hourly worker finished.
- Alarm at the next entry, plus 5 seconds.
- System-requested update (`UPDATE_PERIOD_SECONDS = 900` as a safety net).

The complication service must return within its binder budget. It performs one indexed database read and one pure calculation.

## 13. Boundary alarm

- Schedule at the next entry instant plus 5 seconds.
- `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, ...)`.
- PendingIntent is an immutable broadcast to `PrayerBoundaryReceiver`.
- Reschedule on boot, app open, district change, database copy, and each boundary.
- No exact-alarm permission. Doze may delay the alarm.
- The hourly worker and the 900-second system update period bound visible staleness.

## 14. App screens

1. Status screen:
   - Current district and province.
   - Next entry.
   - Today's six times.
   - Last fix age and last check time.
   - Permission state.
   - Buttons: `Konumu yenile`, `İlçe seç`, `GPS kullan` (when manual), `İzin ver` (when missing).
2. District picker:
   - Province list, then district list.
   - Selecting a district stores it and disables auto-location.
   - No keyboard search.

## 15. First run

1. Install the APK over `adb`.
2. Optionally pre-grant permissions over `adb`:
   `pm grant io.canavar.vakit android.permission.ACCESS_FINE_LOCATION`
   `pm grant io.canavar.vakit android.permission.ACCESS_BACKGROUND_LOCATION`
3. Open the app once. It copies the database, requests a fix, selects the nearest district, and shows today's times.
4. Add the complication to a watch-face slot.

Android does not deliver `BOOT_COMPLETED` or start background work for an app that has never been launched. The one manual launch is unavoidable.

After this setup, everything is automatic: boot, hourly checks, district changes, boundary updates.

## 16. Build

- Gradle wrapper 8.13.
- Android Gradle Plugin 8.9.3.
- Kotlin 2.1.20, Compose compiler plugin 2.1.20.
- JDK 17 toolchain.
- `compileSdk 35`, `targetSdk 34`, `minSdk 30`.
- SDK packages to install: `platforms;android-35`, `build-tools;35.0.0`.
- Dependencies:
  - `androidx.wear.compose:compose-material:1.6.1`
  - `androidx.wear.compose:compose-foundation:1.6.1`
  - `androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0`
  - `androidx.work:work-runtime-ktx:2.10.1`
  - `androidx.datastore:datastore-preferences:1.1.7`
  - `com.google.android.gms:play-services-location:21.3.0`
  - `androidx.activity:activity-compose:1.10.1`
  - `androidx.core:core-ktx:1.15.0`

The newest Wear Compose release requires `compileSdk 37`. The pinned set above is the newest combination that builds against `compileSdk 35`.

The location client version is independent of the Play services 26 runtime on the watch. The client library talks to whatever Play services APK is installed.

21.4.0 is compiled with Kotlin 2.3 metadata and cannot be consumed by the pinned Kotlin 2.1.20 compiler. 21.3.0 is the newest release that builds with this toolchain.

`targetSdk 34` on an API 36 device is supported. If Android 16 blocks location or background work during device testing, record the exact block and test the fallbacks in section 19. The data path does not change.

## 17. Build-time generator

Script: `scripts/build_timetable.py`. Python 3, standard library only.

Steps:

1. Fetch the 81 provinces.
2. Fetch districts per province. Throttle to 1.2 seconds between requests. Retry 429 and 5xx up to 5 times with exponential backoff. Send `User-Agent: vakit-timetable-generator/1.0`.
3. Fetch 2026 times per district.
4. Fetch the coordinate dataset once.
5. Match each Diyanet district to a coordinate.

Name normalization:

1. Replace `İ` with `I` and `ı` with `i`.
2. Uppercase.
3. Decompose Unicode and strip combining marks.
4. Remove every character outside `A-Z0-9`.

Matching order:

1. Exact normalized district name inside the normalized province.
2. Same, after stripping a trailing parenthetical such as `(A)`.
3. District name equal to province name: use the province coordinates.
4. Manual entry in `scripts/district-overrides.json`.

Validation gates, all fatal:

- 81 provinces.
- At least 850 districts.
- Exactly 365 rows per district.
- Every time parses as `HH:MM` and lies in 00:00–23:59.
- Every coordinate lies inside 35.8–42.2 N and 25.6–44.9 E.
- No duplicate `(district_id, month, day)`.
- Every district has coordinates.

Output:

- `app/src/main/assets/timetable.db`
- `app/src/main/assets/timetable.version`

The generated files are committed. Normal builds do not run the generator and need no network.

Generation takes about 20 minutes and downloads about 255 MB.

## 18. Tests

JVM unit tests:

- `NextEntryResolverTest`
  - Before İmsak.
  - Exactly at an instant.
  - Between İmsak and Güneş.
  - After Yatsı returns tomorrow's İmsak.
  - February 29 uses February 28.
  - December 31 rolls into January 1.
- `DistrictSelectorTest`
  - Nearest district.
  - Hysteresis keeps the current district at 4 km advantage.
  - Hysteresis switches at 6 km advantage.
  - Tie-break on lower id.
- `TimeMappingTest`
  - Minutes encode and decode correctly.
  - DB row maps to the six domain entries.
- `TimetableRepositoryTest`
  - Fixture database with two districts and two dates.
  - Missing row returns null.
- `ComplicationTextTest`
  - `İkindi 14:47`.
  - `Ayarla`.
  - `Konum kapalı`.
  - `Veri yok`.

Python tests:

- Normalization handles Turkish characters.
- Override table wins over name matching.
- Validation rejects a short district and a malformed time.

Device checklist, run on the Wear OS 6.0 / Android 16 watch with Play services 26:

1. Sideload and open once. Confirm the app installs and runs with `targetSdk 34` on API 36.
2. Grant fine location. Confirm the settings screen offers "Allow all the time". Grant it if present. If absent, record the exact screen and continue.
3. Confirm Play services 26 returns a fix and the district is selected.
4. Confirm six times appear.
5. Add the complication, confirm the text.
6. Reboot. Confirm the face is correct before opening the app, and confirm a location check starts.
7. Revoke location. Confirm the app warns and keeps the last district.
8. Pick a district manually. Confirm the override and the complication update.
9. Set a mock location in another city, run the worker, confirm the district switches with hysteresis.
10. Enable airplane mode. Confirm the complication still changes at the next entry.
11. Check `dumpsys batterystats` after 24 hours.

## 19. Risks and fallbacks

| Risk | Impact | Fallback |
| --- | --- | --- |
| Wear OS 6 does not offer "Allow all the time" location to a sideloaded app | Hourly checks fail in the background | Keep the last district, warn in the app, refresh on app open |
| Android 16 blocks background work for a `targetSdk 34` app | Hourly checks do not run | Test a short foreground service, or refresh only on app open; the data path is unchanged |
| Samsung face truncates short text | Name or time clipped | Long-text variant, or choose a wider slot |
| Doze delays the boundary alarm | Stale label for minutes | Hourly worker and 900-second system update period |
| Generator source disappears | Cannot rebuild | The committed database keeps working; swap the source behind the generator |
| Canonical-year drift | 1–2 minute error in some years | Accepted; rebuild with a newer year if desired |

## 20. Acceptance criteria

1. With a district set, the complication shows the next of the six entries as `Name HH:mm`.
2. At 14:48 with İkindi at 14:47, the complication shows Akşam.
3. After Yatsı, the complication shows tomorrow's İmsak.
4. On February 29, the app uses the February 28 row.
5. After a reboot, the face draws correctly before the app opens, and an immediate location check runs.
6. A manual district pick disables auto-location until "GPS kullan" is tapped.
7. With no district and no location permission, the complication shows `Konum kapalı`.
8. The APK declares no `INTERNET` permission.
9. Airplane mode changes nothing about the displayed schedule.

## 21. Future work

- Locations outside Türkiye. This needs a data source per country, not a bigger bundle.
- Local computation with Diyanet angles as an offline fallback.
- Hijri date and remaining-time display.
- Adhan notifications.
