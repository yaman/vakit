# Vakit — Prayer Vibration Reminders

Date: 2026-11-07
Status: Approved design
Target device: Samsung Galaxy Watch 4 (SM-R890), Wear OS 6, Android 16 (API 36)

## 1. Problem

The complication shows the next prayer, but the wearer gets no cue before it. The app needs an offline vibration reminder so the watch can be used without checking the face.

## 2. Goal

Fifteen minutes before each Diyanet entry:

| Entry | Vibration |
| --- | --- |
| İmsak | none |
| Güneş | 3 groups of 3 pulses, then 10 pulses (19 total) |
| Öğle, İkindi, Akşam, Yatsı | 3 pulses, 1 s pause, 3 more (6 total) |

Success means all of these hold:

1. The reminder fires with the app closed and after a reboot.
2. It uses the bundled timetable and the selected district; no network is needed.
3. It survives a district change, including an automatic GPS change.
4. An alarm that is delayed past the prayer time does not vibrate.
5. The app still declares no `INTERNET` permission.
6. The existing complication behavior is unchanged.

## 3. Scope

In scope:

- A pure reminder planner for the 15-minute lead time.
- An `AlarmManager` reminder alarm and broadcast receiver.
- Vibration patterns for the standard and Güneş reminders.
- Exact-alarm permission detection, an inexact fallback, and a status-screen grant action.
- Rescheduling on app open, boot/location work, district change, boundary alarms, and after each reminder.
- Unit tests and a device checklist.
- README setup notes for the exact-alarm permission.

Out of scope:

- Notifications, sound, or a notification channel.
- A per-prayer on/off setting or a global reminder setting.
- A reminder for İmsak.
- Snooze or catch-up vibration after a missed reminder.
- Location or timetable changes.
- Repeating alarms with `setRepeating`.

## 4. Reminder behavior

### 4.1 Time

- The reminder instant is the prayer minute minus 15 minutes.
- All calculations use `Europe/Istanbul`, the app's existing zone.
- A reminder is scheduled only when its instant is strictly after "now". If the clock equals the instant exactly, that reminder is skipped and the following one is selected.
- If "now" is after the reminder instant but before the prayer, the reminder is skipped. There is no catch-up vibration.
- February 29 uses the February 28 row through the existing `canonicalMonthDay` lookup.
- December 31 uses January 1 for the following day through the existing lookup.

### 4.2 Vibration patterns

- Standard pulses are 500 ms on followed by 300 ms off. The standard pattern is three pulses, a 1 s pause, then three more: `[0, 500, 300, 500, 300, 500, 1000, 500, 300, 500, 300, 500]`. It has 6 on-pulses and lasts about 5.2 s.
- Güneş starts with three groups of three pulses at 250 ms on / 150 ms off, separated by 300 ms pauses. It then plays ten pulses at 300 ms on / 250 ms off. The full pattern has 19 on-pulses and lasts about 9.3 s, staying inside the receiver's 10-second async budget.
- The waveform requests maximum amplitude (255) on each pulse when the vibrator supports amplitude control; otherwise it uses the default amplitude. The watch's own vibration-intensity setting can still scale or cap the result.
- The vibration uses alarm usage so background delivery is eligible. On API 33 and later it also sets `FLAG_BYPASS_INTERRUPTION_POLICY`. For an unprivileged app that flag is best-effort: the platform may ignore it, and DND behavior still depends on the watch's settings. The device check records what actually happens rather than assuming a bypass.
- On API 30–32 the vibration uses `AudioAttributes.USAGE_ALARM`. Alarm usage is the best available DND behavior on those versions.
- Theater mode and OEM power modes may still stop the motor at the platform or hardware level. The app cannot override that.
- If the device has no vibrator, the receiver skips vibration but still schedules the next reminder.

### 4.3 Late alarms

The alarm carries the prayer instant as an extra. If the receiver runs at or after that instant, it does not vibrate. It still schedules the next reminder. This covers Doze delays in the inexact fallback.

## 5. Architecture

### 5.1 Domain

`ReminderKind` is an enum with:

- `STANDARD` — three pulses.
- `GUNES` — three pulses, pause, ten pulses.

`Reminder` holds:

- `prayer: Prayer`
- `prayerAt: LocalDateTime` — the prayer instant.
- `at: LocalDateTime` — `prayerAt` minus 15 minutes.
- `kind: ReminderKind`

`ReminderPlanner` is pure and has no Android imports:

```kotlin
fun next(
    now: LocalDateTime,
    today: DayTimes?,
    tomorrow: DayTimes?,
): Reminder?
```

Rules:

1. Build today's candidates from İmsak through Yatsı, then tomorrow's candidates in the same order.
2. Skip İmsak.
3. For every other entry, compute `prayerAt` from the day's minute-of-day and `at = prayerAt - 15 minutes`.
4. Keep candidates whose `at` is strictly after `now`.
5. Return the earliest candidate.
6. Return null when there is no future candidate or both rows are null.
7. The `ReminderKind` mapping is: Güneş → `GUNES`; Öğle, İkindi, Akşam, Yatsı → `STANDARD`; İmsak is never returned.

`ReminderTimes` converts a reminder to an alarm trigger:

```kotlin
fun triggerAtMillis(reminder: Reminder, zone: ZoneId): Long =
    reminder.at.atZone(zone).toInstant().toEpochMilli()
```

`ReminderPattern` builds the waveform as a pure `LongArray` so it is testable without Android:

- Standard: initial `0`, then three `300, 250` pairs with the final `250` omitted.
- Güneş: the standard three-pulse body, then `800`, then ten `300, 250` pairs with the final `250` omitted.

The leading `0` is the waveform's initial delay. In a waveform array, index 0 is off, odd indices are on, and even indices after 0 are off.

### 5.2 Alarm scheduling

`ReminderScheduler(context, zone)`:

- Uses a distinct `PendingIntent` request code, separate from the boundary alarm.
- Targets `PrayerReminderReceiver`.
- Cancels the previous reminder alarm before scheduling the next one.
- Adds extras for the prayer name and the prayer instant in epoch milliseconds.
- On API 30, calls `setExactAndAllowWhileIdle(RTC_WAKEUP, trigger, pendingIntent)`.
- On API 31 and later, calls `setExactAndAllowWhileIdle` only when `AlarmManager.canScheduleExactAlarms()` is true.
- Otherwise calls `setAndAllowWhileIdle(RTC_WAKEUP, trigger, pendingIntent)`.
- Catches `SecurityException` from the exact call and retries with `setAndAllowWhileIdle`.
- A null reminder cancels the existing alarm and schedules nothing.

`PrayerReminderReceiver`:

1. Reads the prayer name and prayer epoch from the intent.
2. If the current time is before the prayer instant, plays the prayer's pattern through `ReminderVibrator`.
3. If the extras are missing or invalid, skips vibration.
4. Calls `goAsync()` and reschedules the next reminder on `Dispatchers.IO`.
5. Reschedules from `max(now, prayerAt - 15 minutes) + 5 seconds`, so an alarm that fires early cannot schedule itself again.
6. Always finishes the pending result.

`ReminderVibrator` is the only Android vibration adapter:

- Uses `VibratorManager.defaultVibrator` on API 31 and later, and `Vibrator` on API 30.
- Checks `hasVibrator()`.
- API 33 and later: `Vibrator.vibrate(effect, VibrationAttributes)` with `USAGE_ALARM` and `FLAG_BYPASS_INTERRUPTION_POLICY`.
- API 30–32: `Vibrator.vibrate(effect, AudioAttributes)` with `USAGE_ALARM`.

### 5.3 Container and wiring

`AppContainer` gains:

- `reminderPlanner = ReminderPlanner()`
- `reminderScheduler = ReminderScheduler(context, zone)`
- `suspend fun nextReminderAt(districtId: Int, now: LocalDateTime): Reminder?`
  - Loads today's and tomorrow's `DayTimes` through the existing canonical-date lookups.
  - Calls `reminderPlanner.next`.
- `suspend fun scheduleNextReminder(now: LocalDateTime = LocalDateTime.now(zone))`
  - Reads settings.
  - If no district is set, calls `reminderScheduler.schedule(null)` to cancel any existing alarm.
  - Otherwise calls `reminderScheduler.schedule(nextReminderAt(districtId, now))`.
- `suspend fun scheduleNextEvents()`
  - Calls `scheduleNextBoundary()` and then `scheduleNextReminder()`.

`scheduleNextEvents()` replaces `scheduleNextBoundary()` at these call sites:

- `AppContainer.start()`
- the `LocationCheck` district-change callback
- `LocationWorker.doWork()`
- `DistrictPickerActivity` after a manual selection
- `PrayerBoundaryReceiver` after a boundary

`PrayerReminderReceiver` calls `scheduleNextReminder()` only; the boundary alarm has its own chain.

Boot does not need a new receiver. `BootReceiver` already enqueues the immediate location worker, and that worker now calls `scheduleNextEvents()`. The hourly worker and every boundary alarm recover a lost reminder reschedule.

## 6. Permissions and status UI

Manifest additions:

```xml
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

No notification permission is added. The app remains without `INTERNET`.

`StatusUiState` gains `hasExactAlarmPermission: Boolean`. `AppContainer.loadStatus` computes it:

- API 30: true.
- API 31 and later: `AlarmManager.canScheduleExactAlarms()`.

`StatusScreen` gains an `onGrantExactAlarms` action and shows a `Tam hatırlatma izni` chip when:

- a district is set (`state.districtName != null`), and
- `hasExactAlarmPermission` is false.

The chip opens `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` with the package URI through an activity-result launcher. If the settings activity is unavailable, it falls back to the app details screen. On return it calls `scheduleNextReminder()` and reloads the status.

If the permission is granted or revoked outside the app, the next app open, hourly worker, or prayer-boundary alarm reschedules the reminder. Exact alarms are canceled by the system when the permission is revoked.

README gains the adb fallback:

```bash
adb shell cmd appops set io.canavar.vakit SCHEDULE_EXACT_ALARM allow
```

## 7. Error handling

| Condition | Behavior |
| --- | --- |
| No district | Cancel the reminder alarm; no vibration. |
| Missing timetable row | Cancel the reminder alarm; no vibration. |
| No future reminder | Cancel the alarm. |
| Scheduler has no `AlarmManager` | No-op; the next worker retries. |
| Exact permission missing | Schedule inexact. |
| Exact call throws `SecurityException` | Retry inexact. |
| Receiver extras invalid | Skip vibration; reschedule. |
| Alarm fires at or after prayer | Skip vibration; reschedule. |
| Reschedule fails | Hourly worker and boundary alarm retry. |
| No vibrator | Skip vibration; reschedule. |

## 8. Testing

JVM unit tests:

- `ReminderPlannerTest`
  - Before İmsak selects Güneş, not İmsak.
  - Exactly at a reminder instant selects the following reminder.
  - Between a reminder and its prayer selects the following reminder.
  - After Yatsı selects tomorrow's Güneş.
  - Null today still finds tomorrow's reminder.
  - Null tomorrow still finds today's reminder.
  - Null rows return null.
  - December 31 rolls into January 1.
  - İmsak is never returned.
- `ReminderTimesTest`
  - Trigger is exactly 15 minutes before the prayer.
  - The epoch conversion uses `Europe/Istanbul`.
- `ReminderPatternTest`
  - Standard pattern has 3 on-pulses and the expected 300/250 timing.
  - Güneş pattern has 13 on-pulses, an 800 ms pause, and the expected length.
  - The waveform starts with the initial delay.
- `AppContainerTest`
  - `nextReminderAt` resolves a reminder from the bundled database.
  - The reminder is 15 minutes before the resolved prayer.
  - February 29 uses the February 28 row.

Device checklist:

1. Install the debug APK and set a district.
2. Grant exact-alarm access through the chip or the adb command.
3. Open the app, then run `adb shell dumpsys alarm | grep io.canavar.vakit` and confirm one reminder alarm plus the boundary alarm.
4. Wait for a reminder and confirm the 3-pulse pattern for a standard prayer.
5. Confirm the 3 + 10 pattern before Güneş.
6. Confirm no vibration before İmsak.
7. Reboot before a reminder and confirm the alarm is restored.
8. Change the district and confirm the alarm time changes.
9. Revoke exact-alarm access and confirm the reminder still fires approximately.
10. Leave the watch in Do Not Disturb and record whether the vibration occurs. Alarm usage keeps it eligible, but the bypass flag is not guaranteed for an unprivileged app.
11. Confirm the complication still updates and `INTERNET` is still absent.

Build verification:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## 9. Risks and fallbacks

| Risk | Impact | Fallback |
| --- | --- | --- |
| Exact-alarm permission denied by default on Android 14+ | Reminder may be delayed | Inexact fallback, status chip, adb command |
| Doze delays an inexact alarm past the prayer | Reminder is useless | Suppress late alarms; next reminder is still scheduled |
| Samsung Bedtime/theater mode suppresses vibration | No physical vibration | Platform limitation; cannot bypass from the app |
| Exact-alarm settings screen missing on a Wear OS build | Chip cannot grant access | Open app details; document adb appops |
| Background vibration ignored by the platform | No vibration | Use alarm usage and bypass flag; device test required |
| Watch face/other alarm interference | Vibration feels different | Accepted; no sound or notification is added |
| Time-zone change | Alarm times use the old schedule | Out of scope; the existing hourly and boundary rescheduling re-arms with the same `Europe/Istanbul` data |

## 10. Acceptance criteria

1. With a district set, the next non-İmsak entry has one reminder alarm at prayer minus 15 minutes.
2. Öğle, İkindi, Akşam, and Yatsı vibrate two groups of 3 pulses (6 total) 15 minutes before.
3. Güneş vibrates three groups of 3 pulses, then 10 pulses, 15 minutes before.
4. İmsak has no reminder.
5. A reboot before a reminder restores the reminder.
6. A district change reschedules the reminder for the new district.
7. An alarm that fires after the prayer does not vibrate and the following reminder is scheduled.
8. Without exact-alarm permission the reminder still fires approximately; with permission it is exact.
9. The app still declares no `INTERNET` permission.
10. The complication continues to show the next entry as before.

## 11. Future work

- A notification or full-screen cue naming the prayer.
- Per-prayer or global reminder toggles.
- Adhan sound.
- Snooze or catch-up behavior.
- Rescheduling automatically when the exact-alarm permission is granted outside the app.
