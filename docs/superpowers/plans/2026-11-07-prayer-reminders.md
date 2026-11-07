# Prayer Vibration Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add offline vibration reminders 15 minutes before each Diyanet prayer, with a 3-pulse standard pattern and a 3 + 10-pulse Güneş pattern.

**Architecture:** A pure `ReminderPlanner` selects the next non-İmsak reminder. `ReminderScheduler` keeps one exact-or-inexact `AlarmManager` alarm. `PrayerReminderReceiver` vibrates and reschedules. `AppContainer` wires reminders into the existing app-open, location, boundary, and district-change paths.

**Tech Stack:** Kotlin, Android SDK 35, minSdk 30, targetSdk 34, AlarmManager, Vibrator/VibrationAttributes, JUnit 4, Robolectric 4.18-beta-1, Gradle 8.13.

**Spec:** `docs/superpowers/specs/2026-11-07-prayer-reminders-design.md`

## Global Constraints

- `compileSdk = 35`, `targetSdk = 34`, `minSdk = 30`. Do not change these.
- All prayer calculations use `Europe/Istanbul`.
- No `INTERNET` permission and no network calls.
- No notification, sound, notification channel, or `POST_NOTIFICATIONS`.
- The reminder lead time is exactly 15 minutes.
- Pulse timing: 300 ms on, 250 ms off; 800 ms pause between the Güneş groups.
- Güneş: 3 pulses, pause, 10 pulses. Other non-İmsak entries: 3 pulses. İmsak: none.
- Exact alarms when `AlarmManager.canScheduleExactAlarms()` allows them; otherwise `setAndAllowWhileIdle`.
- Preserve the existing complication behavior and the existing boundary alarm.
- Robolectric must be at least 4.18-beta-1. Older versions fail on Linux aarch64 before any test runs.
- On this ARM64 machine, prefix Gradle commands with the local toolchain:

```bash
export JAVA_HOME=$HOME/opt/jdk17
export ANDROID_HOME=$HOME/android-sdk
export GRADLE_FLAGS="-Pandroid.aapt2FromMavenOverride=$HOME/android-sdk/build-tools/35.0.0/aapt2"
```

Example: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --console=plain`.

## Review Focus

These are the failure modes most likely to hurt a wearer, with the task that pins each one:

1. **Exact-alarm access is denied or revoked.** The app must still schedule an inexact alarm instead of crashing. Pinned by `ReminderScheduleModeTest` in Task 3 and the device checklist in Task 6.
2. **An alarm fires early, on time, or after the prayer.** It must not vibrate after the prayer, and an early alarm must not schedule itself again. Pinned by `ReminderTimesTest` in Task 1.
3. **No district, a missing timetable row, or no future reminder.** The app must cancel any stale alarm and stay silent. Pinned by `ReminderPlannerTest` null-row cases and the no-district device check in Task 6.
4. **February 29 and December 31 rollover.** The canonical February 28 row and the next January 1 row must be used. Pinned by `AppContainerTest` in Task 3.
5. **Missing or malformed receiver extras.** The receiver must not crash or vibrate and must still reschedule. Pinned by `PrayerReminderReceiverTest` in Task 4 and the broadcast check in Task 6.

---

### Task 0: Make Robolectric tests run on Linux arm64

**Files:**
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Consumes: nothing.
- Produces: a test runtime that executes the existing 44 JVM tests on this ARM64 machine.

- [ ] **Step 1: Confirm the baseline failure**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --console=plain`

Expected: 44 tests, 7 failures. `AppContainerTest` and `TimetableRepositoryTest` fail with `The Robolectric native runtime is not supported on Linux (aarch64)` or a Conscrypt `UnsatisfiedLinkError`.

- [ ] **Step 2: Upgrade Robolectric in the version catalog**

Change the `robolectric` version in `gradle/libs.versions.toml` from `4.14.1` to `4.18-beta-1`. Leave the library coordinates unchanged.

- [ ] **Step 3: Run the full suite to verify it passes**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --console=plain`

Expected: `BUILD SUCCESSFUL`, 44 tests pass. Robolectric 4.18-beta-1 ships the Linux aarch64 native runtime and a Conscrypt version that loads on this platform.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "test: run Robolectric tests on linux arm64"
```

---

### Task 1: Reminder domain, planner, and time helpers

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/reminder/Reminder.kt`
- Create: `app/src/main/java/io/canavar/vakit/reminder/ReminderPlanner.kt`
- Test: `app/src/test/java/io/canavar/vakit/reminder/ReminderPlannerTest.kt`
- Test: `app/src/test/java/io/canavar/vakit/reminder/ReminderTimesTest.kt`

**Interfaces:**
- Consumes: `io.canavar.vakit.domain.Prayer`, `DayTimes`, `NextEntryResolver` conventions.
- Produces:
  - `enum class ReminderKind { STANDARD, GUNES }` with `fun forPrayer(prayer: Prayer): ReminderKind?`.
  - `data class Reminder(val prayer: Prayer, val prayerAt: LocalDateTime, val at: LocalDateTime, val kind: ReminderKind)`.
  - `object ReminderTimes` with:
    - `const val LEAD_MINUTES = 15`
    - `const val RESUME_GUARD_MS = 5_000L`
    - `fun triggerAtMillis(reminder: Reminder, zone: ZoneId): Long`
    - `fun prayerAtMillis(reminder: Reminder, zone: ZoneId): Long`
    - `fun shouldVibrate(nowEpochMs: Long, prayerAtEpochMs: Long): Boolean`
    - `fun resumeAtMillis(nowEpochMs: Long, prayerAtEpochMs: Long): Long`
  - `class ReminderPlanner { fun next(now: LocalDateTime, today: DayTimes?, tomorrow: DayTimes?): Reminder? }`

- [ ] **Step 1: Write the failing planner test**

Create `ReminderPlannerTest.kt` with the June 15 fixture (`DayTimes(346, 430, 757, 927, 1074, 1154)`) and tomorrow (`DayTimes(347, 431, 757, 927, 1073, 1153)`):

```kotlin
class ReminderPlannerTest {
    private val planner = ReminderPlanner()
    private val today = DayTimes(346, 430, 757, 927, 1074, 1154)
    private val tomorrow = DayTimes(347, 431, 757, 927, 1073, 1153)
    private val date = LocalDate.of(2026, 6, 15)

    private fun at(hour: Int, minute: Int) = LocalDateTime.of(date, LocalTime.of(hour, minute))

    @Test
    fun `skips imsak and selects gunes`() {
        val next = planner.next(at(5, 0), today, tomorrow)
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(at(7, 10), next?.prayerAt)
        assertEquals(at(6, 55), next?.at)
        assertEquals(ReminderKind.GUNES, next?.kind)
    }

    @Test
    fun `exactly at a reminder instant selects the following reminder`() {
        val next = planner.next(at(6, 55), today, tomorrow)
        assertEquals(Prayer.OGLE, next?.prayer)
        assertEquals(at(12, 22), next?.at)
        assertEquals(ReminderKind.STANDARD, next?.kind)
    }

    @Test
    fun `between a reminder and its prayer selects the following reminder`() {
        val next = planner.next(at(7, 0), today, tomorrow)
        assertEquals(Prayer.OGLE, next?.prayer)
    }

    @Test
    fun `after yatsi selects tomorrow gunes`() {
        val next = planner.next(at(19, 0), today, tomorrow)
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 16, 7, 11), next?.prayerAt)
        assertEquals(LocalDateTime.of(2026, 6, 16, 6, 56), next?.at)
    }

    @Test
    fun `null today still finds tomorrow`() {
        val next = planner.next(at(21, 0), null, tomorrow)
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 16, 6, 56), next?.at)
    }

    @Test
    fun `null tomorrow still finds today`() {
        val next = planner.next(at(5, 0), today, null)
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(at(6, 55), next?.at)
    }

    @Test
    fun `null rows return null`() {
        assertNull(planner.next(at(5, 0), null, null))
    }

    @Test
    fun `december 31 rolls into january 1`() {
        val next = planner.next(
            LocalDateTime.of(2026, 12, 31, 21, 0),
            today,
            tomorrow,
        )
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(LocalDateTime.of(2027, 1, 1, 7, 11), next?.prayerAt)
        assertEquals(LocalDateTime.of(2027, 1, 1, 6, 56), next?.at)
    }

    @Test
    fun `never returns imsak`() {
        listOf(at(5, 0), at(5, 31), at(5, 45), at(21, 0)).forEach { now ->
            assertNotEquals(Prayer.IMSAK, planner.next(now, today, tomorrow)?.prayer)
        }
    }
}
```

- [ ] **Step 2: Run the planner test to verify it fails**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --tests 'io.canavar.vakit.reminder.ReminderPlannerTest' --console=plain`

Expected: compilation failure with unresolved `ReminderPlanner`, `ReminderKind`, or `Reminder`.

- [ ] **Step 3: Write the failing time-helper test**

Create `ReminderTimesTest.kt`:

```kotlin
class ReminderTimesTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val reminder = Reminder(
        prayer = Prayer.IKINDI,
        prayerAt = LocalDateTime.of(2026, 6, 15, 15, 27),
        at = LocalDateTime.of(2026, 6, 15, 15, 12),
        kind = ReminderKind.STANDARD,
    )

    @Test
    fun `trigger is exactly fifteen minutes before the prayer`() {
        val expected = LocalDateTime.of(2026, 6, 15, 15, 12)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, ReminderTimes.triggerAtMillis(reminder, zone))
    }

    @Test
    fun `prayer epoch uses the prayer instant`() {
        val expected = LocalDateTime.of(2026, 6, 15, 15, 27)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, ReminderTimes.prayerAtMillis(reminder, zone))
    }

    @Test
    fun `does not vibrate at or after the prayer`() {
        val prayerAt = ReminderTimes.prayerAtMillis(reminder, zone)
        assertTrue(ReminderTimes.shouldVibrate(prayerAt - 1, prayerAt))
        assertFalse(ReminderTimes.shouldVibrate(prayerAt, prayerAt))
        assertFalse(ReminderTimes.shouldVibrate(prayerAt + 1, prayerAt))
        assertFalse(ReminderTimes.shouldVibrate(prayerAt - 1, 0L))
    }

    @Test
    fun `resume skips an early alarm and guards a normal one`() {
        val prayerAt = ReminderTimes.prayerAtMillis(reminder, zone)
        val reminderAt = ReminderTimes.triggerAtMillis(reminder, zone)
        assertEquals(reminderAt + 5_000, ReminderTimes.resumeAtMillis(reminderAt - 10_000, prayerAt))
        assertEquals(reminderAt + 5_000, ReminderTimes.resumeAtMillis(reminderAt, prayerAt))
        assertEquals(reminderAt + 30_000, ReminderTimes.resumeAtMillis(reminderAt + 25_000, prayerAt))
        assertEquals(1_005_000L, ReminderTimes.resumeAtMillis(1_000_000L, 0L))
    }
}
```

- [ ] **Step 4: Run the time-helper test to verify it fails**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --tests 'io.canavar.vakit.reminder.ReminderTimesTest' --console=plain`

Expected: compilation failure with unresolved `Reminder` and `ReminderTimes`.

- [ ] **Step 5: Implement `ReminderKind`, `Reminder`, and `ReminderTimes` in `Reminder.kt`**

Use constants `LEAD_MINUTES = 15` and `RESUME_GUARD_MS = 5_000L`. `forPrayer` returns `null` for `IMSAK`, `GUNES` for `Prayer.GUNES`, and `STANDARD` for all others. `shouldVibrate` requires a positive `prayerAtEpochMs` and `nowEpochMs < prayerAtEpochMs`. `resumeAtMillis` uses `maxOf(nowEpochMs, prayerAtEpochMs - 15 minutes) + RESUME_GUARD_MS`; when `prayerAtEpochMs <= 0`, it returns `nowEpochMs + RESUME_GUARD_MS`.

- [ ] **Step 6: Run the time-helper test to verify it passes**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --tests 'io.canavar.vakit.reminder.ReminderTimesTest' --console=plain`

Expected: PASS.

- [ ] **Step 7: Implement `ReminderPlanner` in `ReminderPlanner.kt`**

Build today's candidates from İmsak through Yatsı, then tomorrow's in the same order. Skip `Prayer.IMSAK`. Set `prayerAt` from the day's `minuteOf(prayer)` and `at = prayerAt.minusMinutes(15)`. Keep candidates where `at.isAfter(now)` and return the earliest. Return `null` when there are none. Use `now.toLocalDate()` and `now.toLocalDate().plusDays(1)` for the dates.

- [ ] **Step 8: Run the planner test to verify it passes**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --tests 'io.canavar.vakit.reminder.ReminderPlannerTest' --console=plain`

Expected: PASS, 9 tests.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/reminder/Reminder.kt \
  app/src/main/java/io/canavar/vakit/reminder/ReminderPlanner.kt \
  app/src/test/java/io/canavar/vakit/reminder/ReminderPlannerTest.kt \
  app/src/test/java/io/canavar/vakit/reminder/ReminderTimesTest.kt
git commit -m "feat: add prayer reminder planner"
```

---

### Task 2: Vibration patterns

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/reminder/ReminderPattern.kt`
- Test: `app/src/test/java/io/canavar/vakit/reminder/ReminderPatternTest.kt`

**Interfaces:**
- Consumes: `ReminderKind` from Task 1.
- Produces: `object ReminderPattern` with `PULSE_MS = 300L`, `GAP_MS = 250L`, `GROUP_GAP_MS = 800L`, and `fun forKind(kind: ReminderKind): LongArray`.

- [ ] **Step 1: Write the failing pattern test**

```kotlin
class ReminderPatternTest {
    @Test
    fun `standard is three pulses`() {
        assertArrayEquals(
            longArrayOf(0, 300, 250, 300, 250, 300),
            ReminderPattern.forKind(ReminderKind.STANDARD),
        )
    }

    @Test
    fun `gunes is three pulses paused then ten`() {
        assertArrayEquals(
            longArrayOf(
                0,
                300, 250, 300, 250, 300,
                800,
                300, 250, 300, 250, 300, 250, 300, 250, 300, 250,
                300, 250, 300, 250, 300, 250, 300, 250, 300,
            ),
            ReminderPattern.forKind(ReminderKind.GUNES),
        )
    }
}
```

- [ ] **Step 2: Run the pattern test to verify it fails**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --tests 'io.canavar.vakit.reminder.ReminderPatternTest' --console=plain`

Expected: compilation failure with unresolved `ReminderPattern`.

- [ ] **Step 3: Implement `ReminderPattern` in `ReminderPattern.kt`**

Build a pulse body as a list of `on, off, on, off, ...` values where the final off-gap is omitted. Prefix it with a single `0L` initial delay. The Güneş body is the three-pulse body, one `GROUP_GAP_MS`, then the ten-pulse body.

- [ ] **Step 4: Run the pattern test to verify it passes**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --tests 'io.canavar.vakit.reminder.ReminderPatternTest' --console=plain`

Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/reminder/ReminderPattern.kt \
  app/src/test/java/io/canavar/vakit/reminder/ReminderPatternTest.kt
git commit -m "feat: add prayer reminder vibration patterns"
```

---

### Task 3: Schedule decision and next-reminder resolution

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/reminder/ReminderScheduleMode.kt`
- Modify: `app/src/main/java/io/canavar/vakit/AppContainer.kt`
- Test: `app/src/test/java/io/canavar/vakit/reminder/ReminderScheduleModeTest.kt`
- Modify: `app/src/test/java/io/canavar/vakit/AppContainerTest.kt`

**Interfaces:**
- Consumes: `ReminderPlanner`, `Reminder`, `ReminderTimes`, `canonicalMonthDay`, `TimetableRepository`.
- Produces:
  - `object ReminderScheduleMode { fun useExact(apiLevel: Int, canScheduleExact: Boolean): Boolean }`.
  - `AppContainer.reminderPlanner: ReminderPlanner`.
  - `AppContainer.nextReminderAt(districtId: Int, now: LocalDateTime): Reminder?`.

- [ ] **Step 1: Write the failing schedule-mode test**

```kotlin
class ReminderScheduleModeTest {
    @Test
    fun `api 30 always uses exact alarms`() {
        assertTrue(ReminderScheduleMode.useExact(apiLevel = 30, canScheduleExact = false))
    }

    @Test
    fun `api 31 uses exact only when allowed`() {
        assertFalse(ReminderScheduleMode.useExact(apiLevel = 31, canScheduleExact = false))
        assertTrue(ReminderScheduleMode.useExact(apiLevel = 31, canScheduleExact = true))
    }
}
```

- [ ] **Step 2: Add failing container tests**

Add to `AppContainerTest.kt`:

```kotlin
@Test
fun `resolves the next reminder from the bundled database`() = runTest {
    val district = container.timetable.districts().first()
    val times = container.timetable.times(district.id, 6, 15)!!
    val next = container.nextReminderAt(
        districtId = district.id,
        now = LocalDateTime.of(2026, 6, 15, 0, 0),
    )
    assertEquals(Prayer.GUNES, next?.prayer)
    assertEquals(
        LocalDateTime.of(2026, 6, 15, 0, 0).plusMinutes(times.gunes.toLong()),
        next?.prayerAt,
    )
    assertEquals(next?.prayerAt?.minusMinutes(15), next?.at)
}

@Test
fun `february 29 uses the february 28 row`() = runTest {
    val district = container.timetable.districts().first()
    val times = container.timetable.times(district.id, 2, 28)!!
    val next = container.nextReminderAt(
        districtId = district.id,
        now = LocalDateTime.of(2028, 2, 29, 0, 0),
    )
    assertEquals(Prayer.GUNES, next?.prayer)
    assertEquals(
        LocalDateTime.of(2028, 2, 29, 0, 0).plusMinutes(times.gunes.toLong()),
        next?.prayerAt,
    )
}

@Test
fun `december 31 resolves january 1 for tomorrow`() = runTest {
    val district = container.timetable.districts().first()
    val times = container.timetable.times(district.id, 1, 1)!!
    val next = container.nextReminderAt(
        districtId = district.id,
        now = LocalDateTime.of(2026, 12, 31, 21, 0),
    )
    assertEquals(Prayer.GUNES, next?.prayer)
    assertEquals(
        LocalDateTime.of(2027, 1, 1, 0, 0).plusMinutes(times.gunes.toLong()),
        next?.prayerAt,
    )
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --tests 'io.canavar.vakit.reminder.ReminderScheduleModeTest' --tests 'io.canavar.vakit.AppContainerTest' --console=plain`

Expected: compilation failure with unresolved `ReminderScheduleMode` and `nextReminderAt`.

- [ ] **Step 4: Implement `ReminderScheduleMode`**

Return `true` when `apiLevel < 31`; otherwise return `canScheduleExact`. Use `Build.VERSION_CODES.S` rather than a magic number in production code if desired.

- [ ] **Step 5: Add `reminderPlanner` and `nextReminderAt` to `AppContainer`**

`nextReminderAt` mirrors `nextEntryAt`: load today's and tomorrow's times through `canonicalMonthDay`, then call `reminderPlanner.next(now, today, tomorrow)` inside `withContext(Dispatchers.IO)`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --tests 'io.canavar.vakit.reminder.ReminderScheduleModeTest' --tests 'io.canavar.vakit.AppContainerTest' --console=plain`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/reminder/ReminderScheduleMode.kt \
  app/src/main/java/io/canavar/vakit/AppContainer.kt \
  app/src/test/java/io/canavar/vakit/reminder/ReminderScheduleModeTest.kt \
  app/src/test/java/io/canavar/vakit/AppContainerTest.kt
git commit -m "feat: resolve the next prayer reminder"
```

---

### Task 4: Alarm delivery, vibration, and scheduling wiring

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/reminder/ReminderScheduler.kt`
- Create: `app/src/main/java/io/canavar/vakit/reminder/ReminderVibrator.kt`
- Create: `app/src/main/java/io/canavar/vakit/reminder/PrayerReminderReceiver.kt`
- Modify: `app/src/main/java/io/canavar/vakit/AppContainer.kt`
- Modify: `app/src/main/java/io/canavar/vakit/location/LocationWorker.kt`
- Modify: `app/src/main/java/io/canavar/vakit/ui/DistrictPickerActivity.kt`
- Modify: `app/src/main/java/io/canavar/vakit/complication/PrayerBoundaryReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/io/canavar/vakit/reminder/PrayerReminderReceiverTest.kt`

**Interfaces:**
- Consumes: `Reminder`, `ReminderKind`, `ReminderTimes`, `ReminderPattern`, `ReminderScheduleMode`, `AppContainer.nextReminderAt`.
- Produces:
  - `internal fun parsePrayer(raw: String?): Prayer?` in `PrayerReminderReceiver.kt`.
  - `PrayerReminderReceiver.EXTRA_PRAYER = "prayer"`.
  - `PrayerReminderReceiver.EXTRA_PRAYER_AT_EPOCH_MS = "prayer_at_epoch_ms"`.
  - `class ReminderScheduler(context: Context, zone: ZoneId) { fun schedule(reminder: Reminder?) }`.
  - `class ReminderVibrator(context: Context) { fun vibrate(kind: ReminderKind) }`.
  - `AppContainer.scheduleNextReminder(now: LocalDateTime = LocalDateTime.now(zone))`.
  - `AppContainer.scheduleNextReminderAfter(epochMs: Long)`.
  - `AppContainer.scheduleNextEvents()`.

- [ ] **Step 1: Write the failing receiver parsing test**

Create `PrayerReminderReceiverTest.kt`:

```kotlin
class PrayerReminderReceiverTest {
    @Test
    fun `parses a known prayer`() {
        assertEquals(Prayer.IKINDI, parsePrayer("IKINDI"))
    }

    @Test
    fun `unknown or null names return null`() {
        assertNull(parsePrayer("NOT_A_PRAYER"))
        assertNull(parsePrayer(null))
    }
}
```

- [ ] **Step 2: Run the parsing test to verify it fails**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --tests 'io.canavar.vakit.reminder.PrayerReminderReceiverTest' --console=plain`

Expected: compilation failure with unresolved `parsePrayer`.

- [ ] **Step 3: Implement `parsePrayer`**

Add a top-level `internal fun parsePrayer(raw: String?): Prayer? = raw?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }` to `PrayerReminderReceiver.kt`.

- [ ] **Step 4: Run the parsing test to verify it passes**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest --tests 'io.canavar.vakit.reminder.PrayerReminderReceiverTest' --console=plain`

Expected: PASS, 2 tests.

- [ ] **Step 5: Implement `ReminderScheduler`**

- Use request code `1`, `PendingIntent.getBroadcast` to `PrayerReminderReceiver`, `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`.
- Add `EXTRA_PRAYER` and `EXTRA_PRAYER_AT_EPOCH_MS` from the reminder.
- Cancel the pending intent first. A null reminder returns after the cancel.
- Compute the trigger with `ReminderTimes.triggerAtMillis(reminder, zone)`.
- Use `ReminderScheduleMode.useExact(Build.VERSION.SDK_INT, canScheduleExact(alarmManager))`.
- Exact path: `setExactAndAllowWhileIdle(RTC_WAKEUP, trigger, pendingIntent)`, catching `SecurityException`.
- Fallback and inexact path: `setAndAllowWhileIdle(RTC_WAKEUP, trigger, pendingIntent)`.
- `canScheduleExact` is `Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()`.

- [ ] **Step 6: Implement `ReminderVibrator`**

- Resolve `VibratorManager.defaultVibrator` on API 31+ and `Vibrator` on API 30.
- Return when the service is null or `!hasVibrator()`.
- Build `VibrationEffect.createWaveform(ReminderPattern.forKind(kind), -1)`, or the amplitude variant with `ReminderPattern.amplitudesForKind(kind)` when `hasAmplitudeControl()` is true.
- API 33+: `VibrationAttributes.Builder().setUsage(USAGE_ALARM).setFlags(FLAG_BYPASS_INTERRUPTION_POLICY, FLAG_BYPASS_INTERRUPTION_POLICY).build()`, then the `VibrationAttributes` overload.
- API 30–32: `AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()`, then the deprecated `AudioAttributes` overload.

- [ ] **Step 7: Implement `PrayerReminderReceiver`**

- Parse the prayer with `parsePrayer`.
- Read `EXTRA_PRAYER_AT_EPOCH_MS`, defaulting to `0L`.
- Compute `now = System.currentTimeMillis()`.
- Resolve `kind = prayer?.let { ReminderKind.forPrayer(it) }`.
- Vibrate only when `kind != null && ReminderTimes.shouldVibrate(now, prayerAtEpochMs)`; wrap the vibration call in `runCatching` so a vendor vibration failure cannot stop rescheduling.
- Always call `goAsync()` and, on `Dispatchers.IO`, call `app.container.scheduleNextReminderAfter(ReminderTimes.resumeAtMillis(now, prayerAtEpochMs))`.
- Finish the pending result in `finally`; swallow exceptions so the next event can retry.

- [ ] **Step 8: Add the manifest entries**

Add:

```xml
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

Add the receiver:

```xml
<receiver
    android:name=".reminder.PrayerReminderReceiver"
    android:exported="false" />
```

- [ ] **Step 9: Wire the container**

- Add `val reminderScheduler = ReminderScheduler(context, zone)`.
- Add `scheduleNextReminder(now: LocalDateTime = LocalDateTime.now(zone))`: read settings; call `reminderScheduler.schedule(districtId?.let { nextReminderAt(it, now) })` so a missing district cancels the alarm.
- Add `scheduleNextReminderAfter(epochMs: Long)`: convert with `Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime()` and call `scheduleNextReminder`.
- Add `scheduleNextEvents()`: call `scheduleNextBoundary()` then `scheduleNextReminder()`.
- Change the `LocationCheck` callback to `{ scheduleNextEvents() }`.
- Change `start()` to `launchSafely { scheduleNextEvents() }`.

- [ ] **Step 10: Update the scheduling call sites**

Replace `scheduleNextBoundary()` with `scheduleNextEvents()` in `LocationWorker`, `DistrictPickerActivity`, and `PrayerBoundaryReceiver`.

- [ ] **Step 11: Run tests and assemble**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest :app:assembleDebug --console=plain`

Expected: `BUILD SUCCESSFUL`; all unit tests pass.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/reminder/ReminderScheduler.kt \
  app/src/main/java/io/canavar/vakit/reminder/ReminderVibrator.kt \
  app/src/main/java/io/canavar/vakit/reminder/PrayerReminderReceiver.kt \
  app/src/main/java/io/canavar/vakit/AppContainer.kt \
  app/src/main/java/io/canavar/vakit/location/LocationWorker.kt \
  app/src/main/java/io/canavar/vakit/ui/DistrictPickerActivity.kt \
  app/src/main/java/io/canavar/vakit/complication/PrayerBoundaryReceiver.kt \
  app/src/main/AndroidManifest.xml \
  app/src/test/java/io/canavar/vakit/reminder/PrayerReminderReceiverTest.kt
git commit -m "feat: deliver prayer vibration reminders"
```

---

### Task 5: Exact-alarm permission status UI

**Files:**
- Modify: `app/src/main/java/io/canavar/vakit/ui/StatusUiState.kt`
- Modify: `app/src/main/java/io/canavar/vakit/AppContainer.kt`
- Modify: `app/src/main/java/io/canavar/vakit/ui/StatusScreen.kt`
- Modify: `README.md`

**Interfaces:**
- Consumes: `AppContainer.scheduleNextReminder`, `AlarmManager.canScheduleExactAlarms`.
- Produces: `StatusUiState.hasExactAlarmPermission: Boolean` and `StatusScreen(..., onGrantExactAlarms: () -> Unit)`.

- [ ] **Step 1: Add the permission flag**

Add `hasExactAlarmPermission: Boolean` to `StatusUiState`. In `AppContainer.loadStatus`, set it from a private helper:

```kotlin
private fun canScheduleExactAlarms(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
```

- [ ] **Step 2: Add the status action**

In `StatusRoute`, add an `exactAlarmLauncher` with `ActivityResultContracts.StartActivityForResult()`. Its callback calls `container.scheduleNextReminder()` and increments `reload`.

Add `onGrantExactAlarms` to `StatusScreen`. The action:

- Builds `Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))`.
- Launches it through the launcher.
- On any launch exception, opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` instead.

Show the chip when `!state.hasExactAlarmPermission && state.districtName != null`:

```kotlin
Chip(
    modifier = Modifier.fillMaxWidth(),
    label = { Text("Tam hatırlatma izni") },
    onClick = onGrantExactAlarms,
)
```

- [ ] **Step 3: Update README**

Under the install instructions, document the special-access grant:

```bash
adb shell cmd appops set io.canavar.vakit SCHEDULE_EXACT_ALARM allow
```

Explain that without it, reminders fall back to inexact delivery.

- [ ] **Step 4: Build and run the unit tests**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest :app:assembleDebug --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/ui/StatusUiState.kt \
  app/src/main/java/io/canavar/vakit/AppContainer.kt \
  app/src/main/java/io/canavar/vakit/ui/StatusScreen.kt \
  README.md
git commit -m "feat: surface exact reminder alarm permission"
```

---

### Task 6: Full verification and device acceptance

**Files:** none.

- [ ] **Step 1: Run the complete JVM suite and build**

Run: `./gradlew $GRADLE_FLAGS :app:testDebugUnitTest :app:assembleDebug --console=plain`

Expected: `BUILD SUCCESSFUL`; no failing tests.

- [ ] **Step 2: Install on the connected watch**

Run: `adb -s 192.168.1.10:39915 install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`.

- [ ] **Step 3: Grant exact-alarm access and open the app**

Run:

```bash
adb -s 192.168.1.10:39915 shell cmd appops set io.canavar.vakit SCHEDULE_EXACT_ALARM allow
adb -s 192.168.1.10:39915 shell am start -n io.canavar.vakit/.MainActivity
```

Wait two seconds, then run:

```bash
adb -s 192.168.1.10:39915 shell dumpsys alarm | grep -A6 -i vakit
```

Expected: one boundary alarm and one reminder alarm. The reminder trigger is the next non-İmsak prayer minus 15 minutes.

- [ ] **Step 4: Trigger the 3-pulse receiver manually**

If the broadcast is accepted, trigger a standard reminder with a prayer instant 15 minutes in the future:

```bash
WATCH_SECONDS=$(adb -s 192.168.1.10:39915 shell date +%s | tr -d '\r')
PRAYER_AT=$(( WATCH_SECONDS * 1000 + 900000 ))
adb -s 192.168.1.10:39915 shell am broadcast \
  -n io.canavar.vakit/.reminder.PrayerReminderReceiver \
  --es prayer IKINDI --el prayer_at_epoch_ms "$PRAYER_AT"
```

Expected: the watch vibrates 3 pulses. If Android rejects the broadcast because the receiver is not exported, record that and use a real reminder instead. Reopen the app afterward so the fake schedule is replaced.

- [ ] **Step 5: Trigger the Güneş pattern manually**

Repeat Step 4 with `--es prayer GUNES`. Expected: 3 pulses, a pause, then 10 pulses. Reopen the app afterward.

- [ ] **Step 6: Verify no vibration for İmsak and malformed extras**

Repeat the broadcast with `--es prayer IMSAK`; expected: no vibration. Repeat with no extras; expected: no crash and no vibration. Reopen the app afterward.

- [ ] **Step 7: Check the permission surface**

Run: `adb -s 192.168.1.10:39915 shell dumpsys package io.canavar.vakit | grep -i internet`

Expected: no output. Confirm the APK permissions with `$ANDROID_HOME/build-tools/35.0.0/aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk`; expected: no `INTERNET`.

- [ ] **Step 8: Real reminder acceptance**

Wait for the next real reminder (or set a temporary district/time that puts one within the test window) and confirm:

- A standard prayer gives 3 pulses.
- Güneş gives 3 + pause + 10 pulses.
- İmsak gives none.
- The complication still shows the next entry.
- A district change moves the reminder time.
- A reboot restores the reminder alarm.

- [ ] **Step 9: Final status**

Run: `git status --short --branch`

Expected: clean working tree, with the implementation commits on `main`.
