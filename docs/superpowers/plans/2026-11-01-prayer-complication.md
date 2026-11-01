# Vakit Wear OS Prayer Complication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Wear OS app that publishes a watch-face complication showing the next Diyanet prayer time, fully offline, with hourly GPS district selection.

**Architecture:** One Android application module. A bundled SQLite database holds one canonical year of Diyanet times for 869 Türkiye districts, keyed by month and day. A pure resolver picks the next entry from the cache and the clock. A WorkManager job reads location hourly and switches districts with hysteresis. A complication data source renders the result. No network permission.

**Tech Stack:** Kotlin 2.1, Compose for Wear OS 1.6.1, AndroidX complications 1.3.0, WorkManager 2.10.1, DataStore 1.1.7, Play services location 21.4.0, SQLite, Python 3 generator.

**Spec:** `docs/superpowers/specs/2026-11-01-prayer-complication-design.md`

## Global Constraints

- Package `io.canavar.vakit`, app label `Vakit`.
- `minSdk 30`, `targetSdk 34`, `compileSdk 35`.
- Android Gradle Plugin 8.9.3, Gradle 8.13, Kotlin 2.1.20, JDK 17 toolchain.
- Manifest declares no `android.permission.INTERNET`.
- Prayer order is `İmsak, Güneş, Öğle, İkindi, Akşam, Yatsı`.
- Canonical year is 2026. Lookup key is `(district_id, month, day)`. February 29 uses February 28.
- Times are minutes since midnight in `Europe/Istanbul`.
- UI and complication labels are Turkish.
- No phone companion, no watch face, no tile, no notifications.
- Every task ends with a git commit.

## Review Focus

1. **Year rollover and leap years.** A user in 2027, 2028, or 2029 expects the same next-prayer behavior. February 29 must map to February 28, and December 31 must roll into January 1. Pinned by `CanonicalDateTest` and `NextEntryResolverTest` in Task 2.
2. **The exact prayer instant.** At 14:47:00 with İkindi at 14:47, the user expects the *next* entry, not the one that just arrived. Pinned by the exact-instant test in Task 2.
3. **District borders.** A user standing on a border expects the face to stop flapping. Pinned by the hysteresis tests in Task 3.
4. **No district, no fix, no row.** A user expects a readable state, never a crash or a wrong time. Pinned by `ComplicationRendererTest` in Task 8.
5. **Turkish characters and leading zeros.** A user expects `İmsak 05:46`, not `Imsak 5:46`. Pinned by `TimeFormatTest` in Task 2 and the renderer tests in Task 8.

---

## Task 1: Scaffold the Wear OS project

**Files:**
- Create: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `local.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/io/canavar/vakit/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_launcher.xml`

**Interfaces:**
- Consumes: nothing.
- Produces: a buildable `:app` module with the pinned toolchain.

- [ ] **Step 1: Install SDK 35 and build tools 35**

Run:

```bash
yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0"
```

Expected: `done` for both packages.

- [ ] **Step 2: Generate the Gradle wrapper**

Run:

```bash
cd /home/canavar/projects/prayer
gradle wrapper --gradle-version 8.13
```

Expected: `gradle/wrapper/gradle-wrapper.properties` contains `distributionUrl=...gradle-8.13-bin.zip`.

- [ ] **Step 3: Write the build files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Vakit"
include(":app")
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.9.3"
kotlin = "2.1.20"
coreKtx = "1.15.0"
activityCompose = "1.10.1"
wearCompose = "1.6.1"
wearComplications = "1.3.0"
work = "2.10.1"
datastore = "1.1.7"
playServicesLocation = "21.4.0"
coroutines = "1.9.0"
junit = "4.13.2"
robolectric = "4.14.1"
androidxTestCore = "1.6.1"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
wear-compose-material = { module = "androidx.wear.compose:compose-material", version.ref = "wearCompose" }
wear-compose-foundation = { module = "androidx.wear.compose:compose-foundation", version.ref = "wearCompose" }
wear-complications-data-source-ktx = { module = "androidx.wear.watchface:watchface-complications-data-source-ktx", version.ref = "wearComplications" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "playServicesLocation" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

`local.properties`:

```properties
sdk.dir=/opt/android-sdk
```

`.gitignore`:

```text
.gradle/
build/
local.properties
.idea/
*.iml
.kotlin/
scripts/cache/
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.canavar.vakit"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.canavar.vakit"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
```

- [ ] **Step 4: Write the minimal app**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature
        android:name="android.hardware.type.watch"
        android:required="true" />

    <application
        android:allowBackup="false"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/java/io/canavar/vakit/MainActivity.kt`:

```kotlin
package io.canavar.vakit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text("Vakit")
            }
        }
    }
}
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Vakit</string>
</resources>
```

`app/src/main/res/drawable/ic_launcher.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="48"
    android:viewportHeight="48">
    <path
        android:fillColor="#FF1B5E20"
        android:pathData="M24,2a22,22 0,1 0,0 44a22,22 0,1 0,0 -44z" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M24,12l3,7h7l-6,4l2,7l-6,-4l-6,4l2,-7l-6,-4h7z" />
</vector>
```

- [ ] **Step 5: Build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml app gradlew gradlew.bat gradle/wrapper
git commit -m "chore: scaffold Wear OS app"
```

---

## Task 2: Domain model and next-entry resolver

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/domain/Prayer.kt`
- Create: `app/src/main/java/io/canavar/vakit/domain/DayTimes.kt`
- Create: `app/src/main/java/io/canavar/vakit/domain/NextEntry.kt`
- Create: `app/src/main/java/io/canavar/vakit/domain/CanonicalDate.kt`
- Create: `app/src/main/java/io/canavar/vakit/domain/TimeFormat.kt`
- Create: `app/src/main/java/io/canavar/vakit/domain/NextEntryResolver.kt`
- Test: `app/src/test/java/io/canavar/vakit/domain/NextEntryResolverTest.kt`
- Test: `app/src/test/java/io/canavar/vakit/domain/CanonicalDateTest.kt`
- Test: `app/src/test/java/io/canavar/vakit/domain/TimeFormatTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class Prayer(val displayName: String)` with `IMSAK, GUNES, OGLE, IKINDI, AKSAM, YATSI`.
  - `data class DayTimes(imsak, gunes, ogle, ikindi, aksam, yatsi: Int)` and `minuteOf(prayer: Prayer): Int`.
  - `data class NextEntry(val prayer: Prayer, val at: LocalDateTime)`.
  - `fun canonicalMonthDay(date: LocalDate): MonthDay`.
  - `object TimeFormat { fun hhmm(minutes: Int): String; fun hhmm(at: LocalDateTime): String }`.
  - `class NextEntryResolver { fun resolve(now: LocalDateTime, today: DayTimes?, tomorrow: DayTimes?): NextEntry? }`.

- [ ] **Step 1: Write the failing tests**

`NextEntryResolverTest.kt`:

```kotlin
package io.canavar.vakit.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEntryResolverTest {
    private val resolver = NextEntryResolver()
    private val today = DayTimes(346, 430, 757, 927, 1074, 1154)
    private val tomorrow = DayTimes(347, 431, 757, 927, 1073, 1153)

    @Test
    fun `before imsak returns imsak`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 5, 0), today, tomorrow)
        assertEquals(Prayer.IMSAK, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 15, 5, 46), next?.at)
    }

    @Test
    fun `exactly at imsak returns gunes`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 5, 46), today, tomorrow)
        assertEquals(Prayer.GUNES, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 15, 7, 10), next?.at)
    }

    @Test
    fun `between imsak and gunes returns gunes`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 6, 0), today, tomorrow)
        assertEquals(Prayer.GUNES, next?.prayer)
    }

    @Test
    fun `exactly at yatsi returns tomorrow imsak`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 19, 14), today, tomorrow)
        assertEquals(Prayer.IMSAK, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 16, 5, 47), next?.at)
    }

    @Test
    fun `after yatsi returns tomorrow imsak`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 21, 0), today, tomorrow)
        assertEquals(Prayer.IMSAK, next?.prayer)
        assertEquals(LocalDateTime.of(2026, 6, 16, 5, 47), next?.at)
    }

    @Test
    fun `null today still finds tomorrow`() {
        val next = resolver.resolve(LocalDateTime.of(2026, 6, 15, 21, 0), null, tomorrow)
        assertEquals(Prayer.IMSAK, next?.prayer)
    }

    @Test
    fun `december 31 rolls into january 1`() {
        val next = resolver.resolve(
            LocalDateTime.of(2026, 12, 31, 21, 0),
            today,
            tomorrow,
        )
        assertEquals(LocalDateTime.of(2027, 1, 1, 5, 47), next?.at)
    }

    @Test
    fun `null rows return null`() {
        assertNull(resolver.resolve(LocalDateTime.of(2026, 6, 15, 12, 0), null, null))
    }
}
```

`CanonicalDateTest.kt`:

```kotlin
package io.canavar.vakit.domain

import java.time.LocalDate
import java.time.MonthDay
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalDateTest {
    @Test
    fun `february 29 maps to february 28`() {
        assertEquals(MonthDay.of(2, 28), canonicalMonthDay(LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun `ordinary dates keep month and day`() {
        assertEquals(MonthDay.of(3, 1), canonicalMonthDay(LocalDate.of(2028, 3, 1)))
        assertEquals(MonthDay.of(12, 31), canonicalMonthDay(LocalDate.of(2026, 12, 31)))
    }
}
```

`TimeFormatTest.kt`:

```kotlin
package io.canavar.vakit.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun `formats minutes with leading zeros`() {
        assertEquals("00:00", TimeFormat.hhmm(0))
        assertEquals("05:46", TimeFormat.hhmm(346))
        assertEquals("23:59", TimeFormat.hhmm(1439))
    }

    @Test
    fun `formats a local date time`() {
        assertEquals("14:47", TimeFormat.hhmm(LocalDateTime.of(2026, 6, 15, 14, 47)))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.domain.*'
```

Expected: FAIL, compilation errors for unresolved `Prayer`, `DayTimes`, `NextEntryResolver`, `canonicalMonthDay`, and `TimeFormat`.

- [ ] **Step 3: Write the implementation**

`Prayer.kt`:

```kotlin
package io.canavar.vakit.domain

enum class Prayer(val displayName: String) {
    IMSAK("İmsak"),
    GUNES("Güneş"),
    OGLE("Öğle"),
    IKINDI("İkindi"),
    AKSAM("Akşam"),
    YATSI("Yatsı"),
}
```

`DayTimes.kt`:

```kotlin
package io.canavar.vakit.domain

data class DayTimes(
    val imsak: Int,
    val gunes: Int,
    val ogle: Int,
    val ikindi: Int,
    val aksam: Int,
    val yatsi: Int,
) {
    fun minuteOf(prayer: Prayer): Int = when (prayer) {
        Prayer.IMSAK -> imsak
        Prayer.GUNES -> gunes
        Prayer.OGLE -> ogle
        Prayer.IKINDI -> ikindi
        Prayer.AKSAM -> aksam
        Prayer.YATSI -> yatsi
    }
}
```

`NextEntry.kt`:

```kotlin
package io.canavar.vakit.domain

import java.time.LocalDateTime

data class NextEntry(val prayer: Prayer, val at: LocalDateTime)
```

`CanonicalDate.kt`:

```kotlin
package io.canavar.vakit.domain

import java.time.LocalDate
import java.time.MonthDay

fun canonicalMonthDay(date: LocalDate): MonthDay =
    if (date.monthValue == 2 && date.dayOfMonth == 29) {
        MonthDay.of(2, 28)
    } else {
        MonthDay.of(date.monthValue, date.dayOfMonth)
    }
```

`TimeFormat.kt`:

```kotlin
package io.canavar.vakit.domain

import java.time.LocalDateTime
import java.util.Locale

object TimeFormat {
    fun hhmm(minutes: Int): String =
        String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

    fun hhmm(at: LocalDateTime): String = hhmm(at.hour * 60 + at.minute)
}
```

`NextEntryResolver.kt`:

```kotlin
package io.canavar.vakit.domain

import java.time.LocalDate
import java.time.LocalDateTime

class NextEntryResolver {
    fun resolve(
        now: LocalDateTime,
        today: DayTimes?,
        tomorrow: DayTimes?,
    ): NextEntry? {
        val todayDate = now.toLocalDate()
        val tomorrowDate = todayDate.plusDays(1)
        val candidates = buildList {
            if (today != null) {
                Prayer.entries.forEach { prayer ->
                    add(NextEntry(prayer, at(todayDate, today.minuteOf(prayer))))
                }
            }
            if (tomorrow != null) {
                Prayer.entries.forEach { prayer ->
                    add(NextEntry(prayer, at(tomorrowDate, tomorrow.minuteOf(prayer))))
                }
            }
        }
        return candidates.firstOrNull { it.at.isAfter(now) }
    }

    private fun at(date: LocalDate, minuteOfDay: Int): LocalDateTime =
        date.atStartOfDay().plusMinutes(minuteOfDay.toLong())
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.domain.*'
```

Expected: `BUILD SUCCESSFUL`, 12 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/domain app/src/test/java/io/canavar/vakit/domain
git commit -m "feat: add prayer domain and next-entry resolver"
```

---

## Task 3: District selector with hysteresis

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/domain/District.kt`
- Create: `app/src/main/java/io/canavar/vakit/domain/Haversine.kt`
- Create: `app/src/main/java/io/canavar/vakit/domain/DistrictSelector.kt`
- Test: `app/src/test/java/io/canavar/vakit/domain/DistrictSelectorTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class District(val id: Int, val name: String, val province: String, val lat: Double, val lon: Double)`.
  - `fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double`.
  - `class DistrictSelector(distance: (Double, Double, Double, Double) -> Double = ::haversineMeters, hysteresisMeters: Double = 5_000.0) { fun select(current: District?, lat: Double, lon: Double, districts: List<District>): District? }`.

- [ ] **Step 1: Write the failing test**

`DistrictSelectorTest.kt`:

```kotlin
package io.canavar.vakit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistrictSelectorTest {
    private val selector = DistrictSelector(
        distance = { _, _, _, lon2 -> lon2 * 1_000.0 },
        hysteresisMeters = 5_000.0,
    )

    private fun district(id: Int, lon: Double) = District(id, "D$id", "P", 0.0, lon)

    @Test
    fun `empty catalog returns null`() {
        assertNull(selector.select(null, 0.0, 0.0, emptyList()))
    }

    @Test
    fun `picks the nearest district`() {
        val result = selector.select(null, 0.0, 0.0, listOf(district(1, 10.0), district(2, 2.0)))
        assertEquals(2, result?.id)
    }

    @Test
    fun `keeps current district when the gain is under five kilometres`() {
        val current = district(1, 4.0)
        val nearest = district(2, 1.0)
        assertEquals(1, selector.select(current, 0.0, 0.0, listOf(current, nearest))?.id)
    }

    @Test
    fun `keeps current district at exactly five kilometres`() {
        val current = district(1, 6.0)
        val nearest = district(2, 1.0)
        assertEquals(1, selector.select(current, 0.0, 0.0, listOf(current, nearest))?.id)
    }

    @Test
    fun `switches when the gain is over five kilometres`() {
        val current = district(1, 7.0)
        val nearest = district(2, 1.0)
        assertEquals(2, selector.select(current, 0.0, 0.0, listOf(current, nearest))?.id)
    }

    @Test
    fun `breaks distance ties on the lower id`() {
        val result = selector.select(null, 0.0, 0.0, listOf(district(9, 1.0), district(3, 1.0)))
        assertEquals(3, result?.id)
    }

    @Test
    fun `haversine returns a realistic Ankara Konya distance`() {
        val meters = haversineMeters(39.9334, 32.8597, 37.8746, 32.4921)
        assertTrue("was $meters", meters in 225_000.0..240_000.0)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.domain.DistrictSelectorTest'
```

Expected: FAIL, unresolved `District`, `DistrictSelector`, and `haversineMeters`.

- [ ] **Step 3: Write the implementation**

`District.kt`:

```kotlin
package io.canavar.vakit.domain

data class District(
    val id: Int,
    val name: String,
    val province: String,
    val lat: Double,
    val lon: Double,
)
```

`Haversine.kt`:

```kotlin
package io.canavar.vakit.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * earthRadius * asin(sqrt(a))
}
```

`DistrictSelector.kt`:

```kotlin
package io.canavar.vakit.domain

class DistrictSelector(
    private val distance: (Double, Double, Double, Double) -> Double = ::haversineMeters,
    private val hysteresisMeters: Double = 5_000.0,
) {
    fun select(
        current: District?,
        lat: Double,
        lon: Double,
        districts: List<District>,
    ): District? {
        val nearest = districts.minWithOrNull(
            compareBy<District> { distance(lat, lon, it.lat, it.lon) }.thenBy { it.id },
        ) ?: return null
        if (current == null) return nearest
        val currentDistance = distance(lat, lon, current.lat, current.lon)
        val nearestDistance = distance(lat, lon, nearest.lat, nearest.lon)
        return if (currentDistance - nearestDistance > hysteresisMeters) nearest else current
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.domain.DistrictSelectorTest'
```

Expected: `BUILD SUCCESSFUL`, 7 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/domain app/src/test/java/io/canavar/vakit/domain
git commit -m "feat: add district selector with hysteresis"
```

---

## Task 4: Build-time timetable generator

**Files:**
- Create: `scripts/build_timetable.py`
- Create: `scripts/test_build_timetable.py`
- Create: `scripts/district-overrides.json`
- Create: `scripts/cities.json` (downloaded)
- Output (later task): `app/src/main/assets/timetable.db`
- Output (later task): `app/src/main/assets/timetable.version`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `normalize(value: str) -> str`
  - `parse_minutes(value: str) -> int`
  - `match_coordinate(province, district, provinces, overrides) -> tuple[float, float] | None`
  - `write_db(path, districts, records) -> None`
  - `validate(districts, records) -> None`
  - CLI: `--fixture`, `--report-unmatched`, `--out`, `--cache-dir`, `--coords`, `--overrides`.

- [ ] **Step 1: Download the coordinate dataset**

Run:

```bash
mkdir -p scripts
curl -L -o scripts/cities.json https://raw.githubusercontent.com/BuNickTamYirmiHarfli/turkey-cities-districts-json/main/cities.json
python3 -c "import json; d=json.load(open('scripts/cities.json')); print(len(d), sum(len(c['towns']) for c in d))"
```

Expected: `81 973`.

- [ ] **Step 2: Write the override table**

`scripts/district-overrides.json`:

```json
{
  "AFYONKARAHİSAR/İHSANİYE": {"lat": 39.032, "lon": 30.4168},
  "AĞRI/DOĞUBEYAZIT": {"lat": 39.5484, "lon": 44.0811},
  "AYDIN/BOZDOĞAN": {"lat": 37.6711, "lon": 28.3087},
  "TEKİRDAĞ/M.EREĞLİSİ": {"lat": 40.9693, "lon": 27.9549},
  "ZONGULDAK/KARADENİZ EREĞLİ": {"lat": 41.2842, "lon": 31.4133}
}
```

- [ ] **Step 3: Write the failing Python tests**

`scripts/test_build_timetable.py`:

```python
import os
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import build_timetable as bt


class BuildTimetableTest(unittest.TestCase):
    def test_normalize_turkish_characters(self):
        self.assertEqual(bt.normalize("İKİNDİ"), "IKINDI")
        self.assertEqual(bt.normalize("Güneş"), "GUNES")
        self.assertEqual(bt.normalize("YENİPAZAR (A)"), "YENIPAZARA")

    def test_parse_minutes(self):
        self.assertEqual(bt.parse_minutes("05:46"), 346)
        self.assertEqual(bt.parse_minutes("23:59"), 1439)
        with self.assertRaises(ValueError):
            bt.parse_minutes("24:00")
        with self.assertRaises(ValueError):
            bt.parse_minutes("5:46")

    def test_match_exact_name(self):
        provinces = {
            "ANKARA": {
                "name": "Ankara",
                "latitude": 39.9,
                "longitude": 32.8,
                "towns": [{"name": "Çankaya", "latitude": 39.8, "longitude": 32.7}],
            }
        }
        self.assertEqual(
            bt.match_coordinate("ANKARA", "ÇANKAYA", provinces, {}),
            (39.8, 32.7),
        )

    def test_match_central_district_uses_province(self):
        provinces = {
            "ANKARA": {
                "name": "Ankara",
                "latitude": 39.9,
                "longitude": 32.8,
                "towns": [],
            }
        }
        self.assertEqual(
            bt.match_coordinate("ANKARA", "ANKARA", provinces, {}),
            (39.9, 32.8),
        )

    def test_match_strips_parenthetical(self):
        provinces = {
            "AYDIN": {
                "name": "Aydın",
                "latitude": 37.8,
                "longitude": 27.8,
                "towns": [{"name": "Yenipazar", "latitude": 37.8, "longitude": 28.2}],
            }
        }
        self.assertEqual(
            bt.match_coordinate("AYDIN", "YENİPAZAR (A)", provinces, {}),
            (37.8, 28.2),
        )

    def test_override_wins(self):
        provinces = {
            "ANKARA": {
                "name": "Ankara",
                "latitude": 39.9,
                "longitude": 32.8,
                "towns": [{"name": "Çankaya", "latitude": 39.8, "longitude": 32.7}],
            }
        }
        overrides = {"ANKARA/ÇANKAYA": {"lat": 1.0, "lon": 2.0}}
        self.assertEqual(
            bt.match_coordinate("ANKARA", "ÇANKAYA", provinces, overrides),
            (1.0, 2.0),
        )

    def test_write_db_round_trip(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "t.db")
            districts = [
                {"id": 1, "name": "A", "province": "A", "lat": 39.0, "lon": 32.0}
            ]
            records = [(1, 1, 1, 346, 430, 757, 927, 1074, 1154)]
            bt.write_db(path, districts, records)
            connection = sqlite3.connect(path)
            try:
                self.assertEqual(
                    connection.execute("SELECT count(*) FROM times").fetchone()[0], 1
                )
                self.assertEqual(
                    connection.execute("SELECT imsak FROM times").fetchone()[0], 346
                )
            finally:
                connection.close()

    def test_validate_rejects_missing_days(self):
        districts = [
            {"id": 1, "name": "A", "province": "A", "lat": 39.0, "lon": 32.0}
        ]
        records = [(1, 1, 1, 346, 430, 757, 927, 1074, 1154)]
        with self.assertRaises(ValueError):
            bt.validate(districts, records)

    def test_fetch_times_uses_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            payload = {
                "data": [
                    {
                        "date": f"2026-01-{day:02d}T00:00:00.000Z",
                        "times": {
                            "imsak": "05:46",
                            "gunes": "07:10",
                            "ogle": "12:37",
                            "ikindi": "15:27",
                            "aksam": "17:54",
                            "yatsi": "19:14",
                        },
                    }
                    for day in range(1, 32)
                ]
            }
            # Only 31 rows, so this must fail the 365-row check.
            calls = []

            def fake_fetch(url):
                calls.append(url)
                return payload

            with self.assertRaises(ValueError):
                bt.fetch_times(1, tmp, fetch=fake_fetch)
            self.assertEqual(len(calls), 1)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 4: Run the tests to verify they fail**

Run:

```bash
python3 scripts/test_build_timetable.py
```

Expected: FAIL with `ModuleNotFoundError: No module named 'build_timetable'`.

- [ ] **Step 5: Write the generator**

`scripts/build_timetable.py`:

```python
#!/usr/bin/env python3
"""Build the bundled Diyanet timetable database."""

import argparse
import calendar
import datetime as dt
import hashlib
import json
import os
import re
import sqlite3
import time
import unicodedata
import urllib.error
import urllib.request

CANONICAL_YEAR = 2026
BASE_DISTRICTS = "https://ezanvakti.emushaf.net"
BASE_TIMES = "https://ezanvakti.imsakiyem.com"
COORDS_URL = (
    "https://raw.githubusercontent.com/BuNickTamYirmiHarfli/"
    "turkey-cities-districts-json/main/cities.json"
)
USER_AGENT = "vakit-timetable-generator/1.0"
PRAYERS = ("imsak", "gunes", "ogle", "ikindi", "aksam", "yatsi")
LAT_MIN, LON_MIN, LAT_MAX, LON_MAX = 35.8, 25.6, 42.2, 44.9

SCHEMA = """
CREATE TABLE districts (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  province TEXT NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL
) WITHOUT ROWID;

CREATE TABLE times (
  district_id INTEGER NOT NULL,
  month INTEGER NOT NULL,
  day INTEGER NOT NULL,
  imsak INTEGER NOT NULL,
  gunes INTEGER NOT NULL,
  ogle INTEGER NOT NULL,
  ikindi INTEGER NOT NULL,
  aksam INTEGER NOT NULL,
  yatsi INTEGER NOT NULL,
  PRIMARY KEY (district_id, month, day)
) WITHOUT ROWID;
"""


def normalize(value):
    value = value.replace("İ", "I").replace("ı", "i").upper()
    value = unicodedata.normalize("NFKD", value)
    value = "".join(ch for ch in value if not unicodedata.combining(ch))
    return re.sub(r"[^A-Z0-9]", "", value)


def parse_minutes(value):
    if not re.fullmatch(r"\d{2}:\d{2}", value):
        raise ValueError(f"bad time: {value!r}")
    hours, minutes = (int(part) for part in value.split(":"))
    if hours > 23 or minutes > 59:
        raise ValueError(f"time out of range: {value!r}")
    return hours * 60 + minutes


def match_coordinate(province, district, provinces, overrides):
    key = f"{province}/{district}"
    if key in overrides:
        value = overrides[key]
        if value == "province":
            return _province_coordinate(province, provinces)
        return float(value["lat"]), float(value["lon"])

    province_row = provinces.get(normalize(province))
    if province_row is None:
        return None

    towns = {normalize(town["name"]): town for town in province_row["towns"]}
    candidate = towns.get(normalize(district))
    if candidate is None:
        stripped = re.sub(r"\s*\(.*\)\s*$", "", district)
        candidate = towns.get(normalize(stripped))
    if candidate is not None:
        return float(candidate["latitude"]), float(candidate["longitude"])

    if normalize(district) == normalize(province):
        return float(province_row["latitude"]), float(province_row["longitude"])
    return None


def _province_coordinate(province, provinces):
    row = provinces.get(normalize(province))
    if row is None:
        return None
    return float(row["latitude"]), float(row["longitude"])


def get_json(url, attempts=5):
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code in (429, 500, 502, 503, 504) and attempt < attempts - 1:
                time.sleep(2 ** attempt)
                continue
            raise
        except (urllib.error.URLError, TimeoutError):
            if attempt < attempts - 1:
                time.sleep(2 ** attempt)
                continue
            raise
    raise RuntimeError("unreachable")


def fetch_times(district_id, cache_dir, fetch=get_json):
    cache_path = os.path.join(cache_dir, f"{district_id}.json")
    if os.path.exists(cache_path):
        with open(cache_path, encoding="utf-8") as handle:
            payload = json.load(handle)
    else:
        start = f"{CANONICAL_YEAR}-01-01"
        end = f"{CANONICAL_YEAR}-12-31"
        url = (
            f"{BASE_TIMES}/api/prayer-times/{district_id}/range"
            f"?startDate={start}&endDate={end}"
        )
        payload = fetch(url)
        os.makedirs(cache_dir, exist_ok=True)
        with open(cache_path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle)

    rows = payload.get("data", [])
    if len(rows) != 365:
        raise ValueError(f"district {district_id}: expected 365 rows, got {len(rows)}")
    return rows


def build_records(districts, times_by_district):
    records = []
    for district in districts:
        for row in times_by_district[district["id"]]:
            date = dt.date.fromisoformat(row["date"][:10])
            minutes = [parse_minutes(row["times"][prayer]) for prayer in PRAYERS]
            records.append((district["id"], date.month, date.day, *minutes))
    return records


def validate(districts, records):
    counts = {}
    keys = set()
    for record in records:
        district_id = record[0]
        counts[district_id] = counts.get(district_id, 0) + 1
        key = (record[0], record[1], record[2])
        if key in keys:
            raise ValueError(f"duplicate row: {key}")
        keys.add(key)
        for value in record[3:]:
            if not 0 <= value <= 1439:
                raise ValueError(f"minute out of range: {value}")

    for district in districts:
        if counts.get(district["id"], 0) != 365:
            raise ValueError(
                f"district {district['id']} has {counts.get(district['id'], 0)} rows"
            )
        if not (LAT_MIN <= district["lat"] <= LAT_MAX):
            raise ValueError(f"latitude out of Turkey: {district}")
        if not (LON_MIN <= district["lon"] <= LON_MAX):
            raise ValueError(f"longitude out of Turkey: {district}")


def write_db(path, districts, records):
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    if os.path.exists(path):
        os.remove(path)
    connection = sqlite3.connect(path)
    try:
        connection.executescript(SCHEMA)
        connection.executemany(
            "INSERT INTO districts (id, name, province, lat, lon) VALUES (?, ?, ?, ?, ?)",
            [
                (d["id"], d["name"], d["province"], d["lat"], d["lon"])
                for d in districts
            ],
        )
        connection.executemany(
            "INSERT INTO times "
            "(district_id, month, day, imsak, gunes, ogle, ikindi, aksam, yatsi) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            records,
        )
        connection.commit()
    finally:
        connection.close()


def write_version(db_path, districts, records):
    with open(db_path, "rb") as handle:
        digest = hashlib.sha256(handle.read()).hexdigest()[:16]
    version = (
        f"v1;year={CANONICAL_YEAR};districts={len(districts)};"
        f"rows={len(records)};sha256={digest}"
    )
    version_path = os.path.join(os.path.dirname(db_path), "timetable.version")
    with open(version_path, "w", encoding="utf-8") as handle:
        handle.write(version + "\n")
    return version


def write_fixture(out_path):
    districts = [
        {
            "id": 9206,
            "name": "ANKARA",
            "province": "ANKARA",
            "lat": 39.9334,
            "lon": 32.8597,
        },
        {
            "id": 9541,
            "name": "İSTANBUL",
            "province": "İSTANBUL",
            "lat": 41.0082,
            "lon": 28.9784,
        },
    ]
    records = []
    for district in districts:
        for month in range(1, 13):
            for day in range(1, calendar.monthrange(CANONICAL_YEAR, month)[1] + 1):
                records.append(
                    (district["id"], month, day, 346, 430, 757, 927, 1074, 1154)
                )
    validate(districts, records)
    write_db(out_path, districts, records)
    version = write_version(out_path, districts, records)
    print(f"fixture: {len(districts)} districts, {len(records)} rows, {version}")


def fetch_catalog():
    provinces = get_json(f"{BASE_DISTRICTS}/sehirler/2")
    districts = []
    for province in provinces:
        rows = get_json(f"{BASE_DISTRICTS}/ilceler/{province['SehirID']}")
        for row in rows:
            districts.append(
                {
                    "id": int(row["IlceID"]),
                    "name": row["IlceAdi"],
                    "province": province["SehirAdi"],
                }
            )
        time.sleep(1.2)
    return provinces, districts


def load_overrides(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def load_coordinates(path, url=COORDS_URL):
    if not os.path.exists(path):
        payload = get_json(url)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle)
        return payload
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="app/src/main/assets/timetable.db")
    parser.add_argument("--coords", default="scripts/cities.json")
    parser.add_argument("--overrides", default="scripts/district-overrides.json")
    parser.add_argument("--cache-dir", default="scripts/cache")
    parser.add_argument("--fixture", action="store_true")
    parser.add_argument("--report-unmatched", action="store_true")
    args = parser.parse_args(argv)

    if args.fixture:
        write_fixture(args.out)
        return 0

    coordinates = load_coordinates(args.coords)
    provinces = {normalize(row["name"]): row for row in coordinates}
    overrides = load_overrides(args.overrides)

    api_provinces, catalog = fetch_catalog()
    unmatched = []
    matched = []
    for district in catalog:
        coordinate = match_coordinate(
            district["province"], district["name"], provinces, overrides
        )
        if coordinate is None:
            unmatched.append(district)
        else:
            district["lat"], district["lon"] = coordinate
            matched.append(district)

    if unmatched:
        print(f"unmatched: {len(unmatched)}")
        for district in unmatched:
            print(f"  {district['province']}/{district['name']} ({district['id']})")
        return 0 if args.report_unmatched else 1

    print(f"matched: {len(matched)} districts, {len(api_provinces)} provinces")
    if args.report_unmatched:
        return 0
    if len(api_provinces) != 81:
        raise SystemExit(f"expected 81 provinces, got {len(api_provinces)}")
    if len(matched) < 850:
        raise SystemExit(f"expected at least 850 districts, got {len(matched)}")

    times_by_district = {}
    for index, district in enumerate(matched, start=1):
        times_by_district[district["id"]] = fetch_times(district["id"], args.cache_dir)
        if index % 25 == 0:
            print(f"  fetched {index}/{len(matched)}")
        time.sleep(1.2)

    records = build_records(matched, times_by_district)
    validate(matched, records)
    write_db(args.out, matched, records)
    version = write_version(args.out, matched, records)
    print(f"wrote {args.out}: {len(matched)} districts, {len(records)} rows")
    print(version)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 6: Run the tests to verify they pass**

Run:

```bash
python3 scripts/test_build_timetable.py
```

Expected: `OK`, 9 tests passed.

- [ ] **Step 7: Generate the development fixture**

Run:

```bash
python3 scripts/build_timetable.py --fixture
ls -l app/src/main/assets/timetable.db app/src/main/assets/timetable.version
cat app/src/main/assets/timetable.version
```

Expected: two files, and a version line starting with `v1;year=2026;districts=2;rows=730;`.

- [ ] **Step 8: Commit**

```bash
git add scripts/build_timetable.py scripts/test_build_timetable.py scripts/district-overrides.json scripts/cities.json app/src/main/assets
git commit -m "feat: add timetable generator and development fixture"
```

---

## Task 5: Timetable database and repository

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/data/TimetableDb.kt`
- Create: `app/src/main/java/io/canavar/vakit/data/TimetableRepository.kt`
- Test: `app/src/test/java/io/canavar/vakit/data/TimetableRepositoryTest.kt`

**Interfaces:**
- Consumes: `DayTimes`, `District`.
- Produces:
  - `class TimetableDb(context: Context) { fun ensureReady(); fun open(): SQLiteDatabase }`.
  - `interface TimetableRepository { fun districts(): List<District>; fun times(districtId: Int, month: Int, day: Int): DayTimes? }`.
  - `class SqliteTimetableRepository(db: SQLiteDatabase) : TimetableRepository`.

- [ ] **Step 1: Write the failing test**

`TimetableRepositoryTest.kt`:

```kotlin
package io.canavar.vakit.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.canavar.vakit.domain.DayTimes
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableRepositoryTest {
    private lateinit var db: SQLiteDatabase
    private lateinit var repository: SqliteTimetableRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "timetable.db").delete()
        File(context.filesDir, "timetable.version").delete()
        File(context.filesDir, "timetable.db.tmp").delete()
        TimetableDb(context).ensureReady()
        db = SQLiteDatabase.openDatabase(
            File(context.filesDir, "timetable.db").path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        repository = SqliteTimetableRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `lists both fixture districts`() {
        assertEquals(listOf(9206, 9541), repository.districts().map { it.id })
    }

    @Test
    fun `returns six times for Ankara in June`() {
        val times = repository.times(9206, 6, 15)
        assertEquals(DayTimes(346, 430, 757, 927, 1074, 1154), times)
    }

    @Test
    fun `returns null for February 29 in the canonical year`() {
        assertNull(repository.times(9206, 2, 29))
    }

    @Test
    fun `copies the asset to internal storage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "timetable.db")
        assertEquals(true, file.exists())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.data.TimetableRepositoryTest'
```

Expected: FAIL, unresolved `TimetableDb` and `SqliteTimetableRepository`.

- [ ] **Step 3: Write the implementation**

`TimetableDb.kt`:

```kotlin
package io.canavar.vakit.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

class TimetableDb(private val context: Context) {
    private val dbFile = File(context.filesDir, "timetable.db")
    private val versionFile = File(context.filesDir, "timetable.version")

    @Synchronized
    fun ensureReady() {
        val assetVersion = context.assets
            .open("timetable.version")
            .bufferedReader()
            .use { it.readText() }
            .trim()
        val installedVersion = if (versionFile.exists()) {
            versionFile.readText().trim()
        } else {
            ""
        }
        if (installedVersion == assetVersion && dbFile.exists()) return

        val temporary = File(context.filesDir, "timetable.db.tmp")
        context.assets.open("timetable.db").use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        if (!temporary.renameTo(dbFile)) {
            temporary.copyTo(dbFile, overwrite = true)
            temporary.delete()
        }
        versionFile.writeText(assetVersion)
    }

    fun open(): SQLiteDatabase {
        ensureReady()
        return SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }
}
```

`TimetableRepository.kt`:

```kotlin
package io.canavar.vakit.data

import android.database.sqlite.SQLiteDatabase
import io.canavar.vakit.domain.DayTimes
import io.canavar.vakit.domain.District

interface TimetableRepository {
    fun districts(): List<District>
    fun times(districtId: Int, month: Int, day: Int): DayTimes?
}

class SqliteTimetableRepository(
    private val db: SQLiteDatabase,
) : TimetableRepository {
    override fun districts(): List<District> =
        db.rawQuery(
            "SELECT id, name, province, lat, lon FROM districts ORDER BY id",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        District(
                            id = cursor.getInt(0),
                            name = cursor.getString(1),
                            province = cursor.getString(2),
                            lat = cursor.getDouble(3),
                            lon = cursor.getDouble(4),
                        ),
                    )
                }
            }
        }

    override fun times(districtId: Int, month: Int, day: Int): DayTimes? =
        db.rawQuery(
            "SELECT imsak, gunes, ogle, ikindi, aksam, yatsi FROM times " +
                "WHERE district_id = ? AND month = ? AND day = ?",
            arrayOf(districtId.toString(), month.toString(), day.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                DayTimes(
                    imsak = cursor.getInt(0),
                    gunes = cursor.getInt(1),
                    ogle = cursor.getInt(2),
                    ikindi = cursor.getInt(3),
                    aksam = cursor.getInt(4),
                    yatsi = cursor.getInt(5),
                )
            }
        }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.data.TimetableRepositoryTest'
```

Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/data app/src/test/java/io/canavar/vakit/data
git commit -m "feat: read the bundled timetable database"
```

---

## Task 6: Settings storage

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/data/Settings.kt`
- Create: `app/src/main/java/io/canavar/vakit/data/SettingsRepository.kt`
- Test: `app/src/test/java/io/canavar/vakit/data/DataStoreSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `District`.
- Produces:
  - `data class LocationFix(val lat: Double, val lon: Double, val atEpochMs: Long)`.
  - `data class Settings(districtId: Int?, districtName: String?, districtProvince: String?, autoLocation: Boolean, lastFix: LocationFix?, locationBlocked: Boolean, permissionAsked: Boolean)`.
  - `interface SettingsRepository { suspend fun read(): Settings; suspend fun setDistrict(district: District, auto: Boolean); suspend fun setFix(fix: LocationFix); suspend fun setAutoLocation(enabled: Boolean); suspend fun setLocationBlocked(blocked: Boolean); suspend fun setPermissionAsked() }`.
  - `class DataStoreSettingsRepository(dataStore: DataStore<Preferences>) : SettingsRepository`.
  - `val Context.settingsDataStore: DataStore<Preferences>`.

- [ ] **Step 1: Write the failing test**

`DataStoreSettingsRepositoryTest.kt`:

```kotlin
package io.canavar.vakit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.canavar.vakit.domain.District
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataStoreSettingsRepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var repository: DataStoreSettingsRepository
    private lateinit var file: File

    @Before
    fun setUp() {
        file = File.createTempFile("settings", ".preferences_pb")
        file.delete()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        repository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun `defaults to automatic location with no district`() = runTest {
        val settings = repository.read()
        assertEquals(null, settings.districtId)
        assertTrue(settings.autoLocation)
        assertFalse(settings.locationBlocked)
    }

    @Test
    fun `stores a manual district and disables automatic location`() = runTest {
        repository.setDistrict(
            District(9206, "ANKARA", "ANKARA", 39.9334, 32.8597),
            auto = false,
        )
        val settings = repository.read()
        assertEquals(9206, settings.districtId)
        assertEquals("ANKARA", settings.districtName)
        assertFalse(settings.autoLocation)
    }

    @Test
    fun `stores a fix and a blocked flag`() = runTest {
        repository.setFix(LocationFix(39.9, 32.8, 123L))
        repository.setLocationBlocked(true)
        val settings = repository.read()
        assertEquals(123L, settings.lastFix?.atEpochMs)
        assertTrue(settings.locationBlocked)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.data.DataStoreSettingsRepositoryTest'
```

Expected: FAIL, unresolved `DataStoreSettingsRepository`, `LocationFix`, and `Settings`.

- [ ] **Step 3: Write the implementation**

`Settings.kt`:

```kotlin
package io.canavar.vakit.data

data class LocationFix(
    val lat: Double,
    val lon: Double,
    val atEpochMs: Long,
)

data class Settings(
    val districtId: Int? = null,
    val districtName: String? = null,
    val districtProvince: String? = null,
    val autoLocation: Boolean = true,
    val lastFix: LocationFix? = null,
    val locationBlocked: Boolean = false,
    val permissionAsked: Boolean = false,
)
```

`SettingsRepository.kt`:

```kotlin
package io.canavar.vakit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.canavar.vakit.domain.District
import kotlinx.coroutines.flow.first

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

interface SettingsRepository {
    suspend fun read(): Settings
    suspend fun setDistrict(district: District, auto: Boolean)
    suspend fun setFix(fix: LocationFix)
    suspend fun setAutoLocation(enabled: Boolean)
    suspend fun setLocationBlocked(blocked: Boolean)
    suspend fun setPermissionAsked()
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override suspend fun read(): Settings {
        val values = dataStore.data.first()
        val lat = values[Keys.LAST_LAT]
        val lon = values[Keys.LAST_LON]
        val fix = if (lat != null && lon != null) {
            LocationFix(lat, lon, values[Keys.LAST_FIX_AT] ?: 0L)
        } else {
            null
        }
        return Settings(
            districtId = values[Keys.DISTRICT_ID],
            districtName = values[Keys.DISTRICT_NAME],
            districtProvince = values[Keys.DISTRICT_PROVINCE],
            autoLocation = values[Keys.AUTO_LOCATION] ?: true,
            lastFix = fix,
            locationBlocked = values[Keys.LOCATION_BLOCKED] ?: false,
            permissionAsked = values[Keys.PERMISSION_ASKED] ?: false,
        )
    }

    override suspend fun setDistrict(district: District, auto: Boolean) {
        dataStore.edit { values ->
            values[Keys.DISTRICT_ID] = district.id
            values[Keys.DISTRICT_NAME] = district.name
            values[Keys.DISTRICT_PROVINCE] = district.province
            values[Keys.AUTO_LOCATION] = auto
        }
    }

    override suspend fun setFix(fix: LocationFix) {
        dataStore.edit { values ->
            values[Keys.LAST_LAT] = fix.lat
            values[Keys.LAST_LON] = fix.lon
            values[Keys.LAST_FIX_AT] = fix.atEpochMs
        }
    }

    override suspend fun setAutoLocation(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.AUTO_LOCATION] = enabled }
    }

    override suspend fun setLocationBlocked(blocked: Boolean) {
        dataStore.edit { values -> values[Keys.LOCATION_BLOCKED] = blocked }
    }

    override suspend fun setPermissionAsked() {
        dataStore.edit { values -> values[Keys.PERMISSION_ASKED] = true }
    }

    private object Keys {
        val DISTRICT_ID = intPreferencesKey("district_id")
        val DISTRICT_NAME = stringPreferencesKey("district_name")
        val DISTRICT_PROVINCE = stringPreferencesKey("district_province")
        val AUTO_LOCATION = booleanPreferencesKey("auto_location")
        val LAST_LAT = doublePreferencesKey("last_lat")
        val LAST_LON = doublePreferencesKey("last_lon")
        val LAST_FIX_AT = longPreferencesKey("last_fix_at")
        val LOCATION_BLOCKED = booleanPreferencesKey("location_blocked")
        val PERMISSION_ASKED = booleanPreferencesKey("permission_asked")
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.data.DataStoreSettingsRepositoryTest'
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/data app/src/test/java/io/canavar/vakit/data
git commit -m "feat: persist district and location settings"
```

---

## Task 7: Application container

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/VakitApp.kt`
- Create: `app/src/main/java/io/canavar/vakit/AppContainer.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/io/canavar/vakit/AppContainerTest.kt`

**Interfaces:**
- Consumes: `TimetableDb`, `SqliteTimetableRepository`, `DataStoreSettingsRepository`, `NextEntryResolver`, `DistrictSelector`.
- Produces:
  - `class VakitApp : Application { val container: AppContainer }`.
  - `class AppContainer(context: Context)` with `zone`, `db`, `timetable`, `settings`, `resolver`, `selector`, `nextEntry(districtId: Int): NextEntry?`.

- [ ] **Step 1: Write the failing test**

`AppContainerTest.kt`:

```kotlin
package io.canavar.vakit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppContainerTest {
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "timetable.db").delete()
        File(context.filesDir, "timetable.version").delete()
        File(context.filesDir, "timetable.db.tmp").delete()
        container = AppContainer(context)
    }

    @Test
    fun `resolves the next entry from the fixture database`() = runTest {
        val next = container.nextEntryAt(
            districtId = 9206,
            now = LocalDateTime.of(2026, 6, 15, 13, 0),
        )
        assertNotNull(next)
        assertEquals("İkindi", next?.prayer?.displayName)
    }

    @Test
    fun `uses the Istanbul zone`() {
        assertEquals(ZoneId.of("Europe/Istanbul"), container.zone)
    }

    @Test
    fun `copies the database before the first query`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        container.timetable.districts()
        assertEquals(true, File(context.filesDir, "timetable.db").exists())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.AppContainerTest'
```

Expected: FAIL, unresolved `AppContainer`.

- [ ] **Step 3: Write the implementation**

`AppContainer.kt`:

```kotlin
package io.canavar.vakit

import android.content.Context
import io.canavar.vakit.data.DataStoreSettingsRepository
import io.canavar.vakit.data.SettingsRepository
import io.canavar.vakit.data.SqliteTimetableRepository
import io.canavar.vakit.data.TimetableDb
import io.canavar.vakit.data.TimetableRepository
import io.canavar.vakit.data.settingsDataStore
import io.canavar.vakit.domain.DistrictSelector
import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.NextEntryResolver
import io.canavar.vakit.domain.canonicalMonthDay
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppContainer(private val context: Context) {
    val zone: ZoneId = ZoneId.of("Europe/Istanbul")
    val db: TimetableDb = TimetableDb(context)
    val timetable: TimetableRepository by lazy { SqliteTimetableRepository(db.open()) }
    val settings: SettingsRepository = DataStoreSettingsRepository(context.settingsDataStore)
    val resolver = NextEntryResolver()
    val selector = DistrictSelector()

    suspend fun nextEntry(districtId: Int): NextEntry? =
        nextEntryAt(districtId, LocalDateTime.now(zone))

    suspend fun nextEntryAt(districtId: Int, now: LocalDateTime): NextEntry? =
        withContext(Dispatchers.IO) {
            val today = now.toLocalDate()
            val tomorrow = today.plusDays(1)
            val todayMonthDay = canonicalMonthDay(today)
            val tomorrowMonthDay = canonicalMonthDay(tomorrow)
            resolver.resolve(
                now = now,
                today = timetable.times(
                    districtId,
                    todayMonthDay.monthValue,
                    todayMonthDay.dayOfMonth,
                ),
                tomorrow = timetable.times(
                    districtId,
                    tomorrowMonthDay.monthValue,
                    tomorrowMonthDay.dayOfMonth,
                ),
            )
        }
}
```

`VakitApp.kt`:

```kotlin
package io.canavar.vakit

import android.app.Application

class VakitApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
```

Add only the `android:name=".VakitApp"` attribute to the existing `<application>` tag in `AndroidManifest.xml`. Keep every other attribute unchanged, including `android:theme="@android:style/Theme.DeviceDefault"`.

```xml
<application
    android:name=".VakitApp"
    android:allowBackup="false"
    android:icon="@drawable/ic_launcher"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@android:style/Theme.DeviceDefault">
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.AppContainerTest'
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/VakitApp.kt app/src/main/java/io/canavar/vakit/AppContainer.kt app/src/main/AndroidManifest.xml app/src/test/java/io/canavar/vakit/AppContainerTest.kt
git commit -m "feat: add application container"
```

---

## Task 8: Complication data source

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/complication/ComplicationRenderer.kt`
- Create: `app/src/main/java/io/canavar/vakit/complication/ComplicationRefresher.kt`
- Create: `app/src/main/java/io/canavar/vakit/complication/ComplicationUpdater.kt`
- Create: `app/src/main/java/io/canavar/vakit/complication/NextEntryComplicationService.kt`
- Modify: `app/src/main/java/io/canavar/vakit/AppContainer.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_complication.xml`
- Test: `app/src/test/java/io/canavar/vakit/complication/ComplicationRendererTest.kt`

**Interfaces:**
- Consumes: `NextEntry`, `TimeFormat`, `AppContainer`.
- Produces:
  - `data class ComplicationContent(val title: String?, val text: String, val description: String)`.
  - `object ComplicationRenderer { fun render(districtId: Int?, next: NextEntry?, locationBlocked: Boolean): ComplicationContent }`.
  - `interface ComplicationRefresher { fun requestUpdate() }`.
  - `class ComplicationUpdater(context: Context) : ComplicationRefresher`.
  - `class NextEntryComplicationService : SuspendingComplicationDataSourceService()`.

- [ ] **Step 1: Write the failing test**

`ComplicationRendererTest.kt`:

```kotlin
package io.canavar.vakit.complication

import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.Prayer
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComplicationRendererTest {
    private val next = NextEntry(Prayer.IKINDI, LocalDateTime.of(2026, 6, 15, 14, 47))

    @Test
    fun `renders the prayer name as title and the clock as text`() {
        val content = ComplicationRenderer.render(9206, next, locationBlocked = false)
        assertEquals("İkindi", content.title)
        assertEquals("14:47", content.text)
        assertEquals("İkindi 14:47", content.description)
    }

    @Test
    fun `renders setup when no district is selected`() {
        val content = ComplicationRenderer.render(null, null, locationBlocked = false)
        assertEquals("Ayarla", content.text)
        assertNull(content.title)
    }

    @Test
    fun `renders location off when no district and location is blocked`() {
        val content = ComplicationRenderer.render(null, null, locationBlocked = true)
        assertEquals("Konum kapalı", content.text)
    }

    @Test
    fun `renders no data when a district has no row`() {
        val content = ComplicationRenderer.render(9206, null, locationBlocked = false)
        assertEquals("Veri yok", content.text)
    }

    @Test
    fun `formats single digit hours with a leading zero`() {
        val early = NextEntry(Prayer.IMSAK, LocalDateTime.of(2026, 6, 15, 5, 6))
        val content = ComplicationRenderer.render(9206, early, locationBlocked = false)
        assertEquals("05:06", content.text)
        assertEquals("İmsak", content.title)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.complication.ComplicationRendererTest'
```

Expected: FAIL, unresolved `ComplicationRenderer`.

- [ ] **Step 3: Write the renderer**

`ComplicationRenderer.kt`:

```kotlin
package io.canavar.vakit.complication

import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.TimeFormat

data class ComplicationContent(
    val title: String?,
    val text: String,
    val description: String,
)

object ComplicationRenderer {
    const val SETUP = "Ayarla"
    const val LOCATION_OFF = "Konum kapalı"
    const val NO_DATA = "Veri yok"

    fun render(
        districtId: Int?,
        next: NextEntry?,
        locationBlocked: Boolean,
    ): ComplicationContent {
        if (districtId == null) {
            val label = if (locationBlocked) LOCATION_OFF else SETUP
            return ComplicationContent(title = null, text = label, description = label)
        }
        if (next == null) {
            return ComplicationContent(title = null, text = NO_DATA, description = NO_DATA)
        }
        val name = next.prayer.displayName
        val time = TimeFormat.hhmm(next.at)
        return ComplicationContent(title = name, text = time, description = "$name $time")
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.complication.ComplicationRendererTest'
```

Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 5: Write the refresher and service**

`ComplicationRefresher.kt`:

```kotlin
package io.canavar.vakit.complication

interface ComplicationRefresher {
    fun requestUpdate()
}
```

`ComplicationUpdater.kt`:

```kotlin
package io.canavar.vakit.complication

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

class ComplicationUpdater(
    private val context: Context,
) : ComplicationRefresher {
    override fun requestUpdate() {
        ComplicationDataSourceUpdateRequester.create(
            context,
            ComponentName(context, NextEntryComplicationService::class.java),
        ).requestUpdateAll()
    }
}
```

`NextEntryComplicationService.kt`:

```kotlin
package io.canavar.vakit.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import io.canavar.vakit.MainActivity
import io.canavar.vakit.VakitApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NextEntryComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        withContext(Dispatchers.IO) {
            val container = (applicationContext as VakitApp).container
            val content = try {
                val settings = container.settings.read()
                val next = settings.districtId?.let { container.nextEntry(it) }
                ComplicationRenderer.render(
                    districtId = settings.districtId,
                    next = next,
                    locationBlocked = settings.locationBlocked,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ComplicationContent(
                    title = null,
                    text = ComplicationRenderer.NO_DATA,
                    description = ComplicationRenderer.NO_DATA,
                )
            }
            val tapAction = PendingIntent.getActivity(
                this@NextEntryComplicationService,
                0,
                Intent(this@NextEntryComplicationService, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                    text = text(content.text),
                    contentDescription = text(content.description),
                )
                    .setTitle(content.title?.let { text(it) })
                    .setTapAction(tapAction)
                    .build()

                ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                    text = text(content.description),
                    contentDescription = text(content.description),
                )
                    .setTitle(
                        if (content.title != null) text("Sonraki vakit") else null,
                    )
                    .setTapAction(tapAction)
                    .build()

                else -> null
            }
        }

    private fun text(value: String): ComplicationText =
        PlainComplicationText.Builder(value).build()
}
```

- [ ] **Step 6: Register the service and wire the container**

Add to `AndroidManifest.xml` inside `<application>`:

```xml
<service
    android:name=".complication.NextEntryComplicationService"
    android:exported="true"
    android:icon="@drawable/ic_complication"
    android:label="@string/complication_label"
    android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER">
    <intent-filter>
        <action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST" />
    </intent-filter>
    <meta-data
        android:name="android.support.wearable.complications.SUPPORTED_TYPES"
        android:value="SHORT_TEXT,LONG_TEXT" />
    <meta-data
        android:name="android.support.wearable.complications.UPDATE_PERIOD_SECONDS"
        android:value="900" />
</service>
```

Add to `app/src/main/res/values/strings.xml`:

```xml
<string name="complication_label">Vakit</string>
<string name="complication_description">Sonraki namaz vakti</string>
```

`app/src/main/res/drawable/ic_complication.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2a10,10 0,1 0,0 20a10,10 0,1 0,0 -20zM12,6a6,6 0,1 1,0 12a6,6 0,1 1,0 -12z" />
</vector>
```

Add to `AppContainer.kt`:

```kotlin
val complicationRefresher: ComplicationRefresher = ComplicationUpdater(context)
```

Import `io.canavar.vakit.complication.ComplicationRefresher` and `io.canavar.vakit.complication.ComplicationUpdater`.

- [ ] **Step 7: Build and run the tests**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/complication app/src/main/java/io/canavar/vakit/AppContainer.kt app/src/main/AndroidManifest.xml app/src/main/res app/src/test/java/io/canavar/vakit/complication
git commit -m "feat: publish the next prayer complication"
```

---

## Task 9: Location check, worker, and boot receiver

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/location/LocationSource.kt`
- Create: `app/src/main/java/io/canavar/vakit/location/FusedLocationSource.kt`
- Create: `app/src/main/java/io/canavar/vakit/location/LocationCheck.kt`
- Create: `app/src/main/java/io/canavar/vakit/location/LocationWorker.kt`
- Create: `app/src/main/java/io/canavar/vakit/location/LocationScheduler.kt`
- Create: `app/src/main/java/io/canavar/vakit/location/BootReceiver.kt`
- Modify: `app/src/main/java/io/canavar/vakit/AppContainer.kt`
- Modify: `app/src/main/java/io/canavar/vakit/VakitApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/io/canavar/vakit/location/LocationCheckTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository`, `TimetableRepository`, `DistrictSelector`, `ComplicationRefresher`, `LocationFix`.
- Produces:
  - `interface LocationSource { suspend fun current(): LocationFix? }`.
  - `class FusedLocationSource(context: Context) : LocationSource`.
  - `class LocationCheck(locationSource, timetable, settings, selector, refresher) { suspend fun run() }`.
  - `class LocationWorker : CoroutineWorker`.
  - `object LocationScheduler { fun ensurePeriodic(context); fun runNow(context) }`.
  - `class BootReceiver : BroadcastReceiver`.

- [ ] **Step 1: Write the failing test**

`LocationCheckTest.kt`:

```kotlin
package io.canavar.vakit.location

import io.canavar.vakit.data.LocationFix
import io.canavar.vakit.data.Settings
import io.canavar.vakit.data.SettingsRepository
import io.canavar.vakit.data.TimetableRepository
import io.canavar.vakit.domain.DayTimes
import io.canavar.vakit.domain.District
import io.canavar.vakit.domain.DistrictSelector
import io.canavar.vakit.complication.ComplicationRefresher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationCheckTest {
    private val ankara = District(9206, "ANKARA", "ANKARA", 0.0, 10.0)
    private val istanbul = District(9541, "İSTANBUL", "İSTANBUL", 0.0, 100.0)

    private class FakeLocation(var fix: LocationFix?) : LocationSource {
        override suspend fun current(): LocationFix? = fix
    }

    private class FakeSettings(var current: Settings) : SettingsRepository {
        var district: District? = null
        var blocked: Boolean? = null
        var auto: Boolean? = null
        override suspend fun read(): Settings = current
        override suspend fun setDistrict(district: District, auto: Boolean) {
            this.district = district
            this.auto = auto
            current = current.copy(districtId = district.id, autoLocation = auto)
        }
        override suspend fun setFix(fix: LocationFix) {
            current = current.copy(lastFix = fix)
        }
        override suspend fun setAutoLocation(enabled: Boolean) {
            current = current.copy(autoLocation = enabled)
        }
        override suspend fun setLocationBlocked(blocked: Boolean) {
            this.blocked = blocked
            current = current.copy(locationBlocked = blocked)
        }
        override suspend fun setPermissionAsked() = Unit
    }

    private class FakeTimetable : TimetableRepository {
        override fun districts(): List<District> = emptyList()
        override fun times(districtId: Int, month: Int, day: Int): DayTimes? = null
    }

    private class FakeRefresher : ComplicationRefresher {
        var count = 0
        override fun requestUpdate() { count++ }
    }

    private fun check(
        location: LocationSource,
        settings: FakeSettings,
        refresher: FakeRefresher,
        timetable: TimetableRepository,
    ): LocationCheck = LocationCheck(
        locationSource = location,
        timetable = timetable,
        settings = settings,
        selector = DistrictSelector(
            distance = { _, _, _, lon2 -> lon2 * 1_000.0 },
            hysteresisMeters = 5_000.0,
        ),
        refresher = refresher,
    )

    @Test
    fun `a null fix blocks location and keeps the district`() = runTest {
        val settings = FakeSettings(Settings(districtId = 9206))
        val refresher = FakeRefresher()
        check(FakeLocation(null), settings, refresher, FakeTimetable()).run()
        assertEquals(true, settings.blocked)
        assertEquals(null, settings.district)
        assertEquals(1, refresher.count)
    }

    @Test
    fun `a nearby fix keeps the current district`() = runTest {
        val settings = FakeSettings(Settings(districtId = 9206))
        val refresher = FakeRefresher()
        val timetable = object : TimetableRepository {
            override fun districts(): List<District> = listOf(ankara, istanbul)
            override fun times(districtId: Int, month: Int, day: Int): DayTimes? = null
        }
        check(FakeLocation(LocationFix(0.0, 12.0, 1L)), settings, refresher, timetable).run()
        assertEquals(false, settings.blocked)
        assertEquals(null, settings.district)
    }

    @Test
    fun `a distant fix switches the district`() = runTest {
        val settings = FakeSettings(Settings(districtId = 9206))
        val refresher = FakeRefresher()
        val timetable = object : TimetableRepository {
            override fun districts(): List<District> = listOf(ankara, istanbul)
            override fun times(districtId: Int, month: Int, day: Int): DayTimes? = null
        }
        check(FakeLocation(LocationFix(0.0, 98.0, 1L)), settings, refresher, timetable).run()
        assertEquals(9541, settings.district?.id)
        assertEquals(true, settings.auto)
        assertFalse(settings.current.locationBlocked)
    }

    @Test
    fun `manual mode skips the location call`() = runTest {
        val settings = FakeSettings(Settings(districtId = 9206, autoLocation = false))
        val refresher = FakeRefresher()
        var called = false
        val location = object : LocationSource {
            override suspend fun current(): LocationFix? {
                called = true
                return null
            }
        }
        check(location, settings, refresher, FakeTimetable()).run()
        assertFalse(called)
        assertEquals(0, refresher.count)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.location.LocationCheckTest'
```

Expected: FAIL, unresolved `LocationCheck`.

- [ ] **Step 3: Write the location source and check**

`LocationSource.kt`:

```kotlin
package io.canavar.vakit.location

import io.canavar.vakit.data.LocationFix

interface LocationSource {
    suspend fun current(): LocationFix?
}
```

`FusedLocationSource.kt`:

```kotlin
package io.canavar.vakit.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.canavar.vakit.data.LocationFix
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class FusedLocationSource(private val context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun current(): LocationFix? {
        if (!hasFinePermission()) return null
        val token = CancellationTokenSource()
        val fresh = try {
            withTimeoutOrNull(45_000) {
                client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    token.token,
                ).await()
            }
        } catch (error: Exception) {
            null
        }
        val location = fresh ?: try {
            client.lastLocation.await()
        } catch (error: Exception) {
            null
        }
        return location?.let {
            LocationFix(it.latitude, it.longitude, System.currentTimeMillis())
        }
    }

    private fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
}
```

`LocationCheck.kt`:

```kotlin
package io.canavar.vakit.location

import io.canavar.vakit.complication.ComplicationRefresher
import io.canavar.vakit.data.SettingsRepository
import io.canavar.vakit.data.TimetableRepository
import io.canavar.vakit.domain.DistrictSelector

class LocationCheck(
    private val locationSource: LocationSource,
    private val timetable: TimetableRepository,
    private val settings: SettingsRepository,
    private val selector: DistrictSelector,
    private val refresher: ComplicationRefresher,
) {
    suspend fun run() {
        val current = settings.read()
        if (!current.autoLocation) return

        val fix = locationSource.current()
        if (fix == null) {
            settings.setLocationBlocked(true)
            refresher.requestUpdate()
            return
        }

        settings.setLocationBlocked(false)
        settings.setFix(fix)

        val catalog = timetable.districts()
        val currentDistrict = current.districtId?.let { id ->
            catalog.firstOrNull { it.id == id }
        }
        val selected = selector.select(currentDistrict, fix.lat, fix.lon, catalog)
        if (selected != null && selected.id != currentDistrict?.id) {
            settings.setDistrict(selected, auto = true)
        }
        refresher.requestUpdate()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.location.LocationCheckTest'
```

Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Write the worker, scheduler, and boot receiver**

`LocationWorker.kt`:

```kotlin
package io.canavar.vakit.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.canavar.vakit.VakitApp
import kotlinx.coroutines.CancellationException

class LocationWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as VakitApp
        return try {
            app.container.locationCheck.run()
            app.container.scheduleNextBoundary()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
```

This worker references `container.locationCheck` and `container.scheduleNextBoundary()`. Add `locationCheck` now. Add `scheduleNextBoundary()` in Task 10. To keep Task 9 compiling, add a temporary no-op `scheduleNextBoundary()` to `AppContainer` now, and replace it in Task 10.

Add to `AppContainer.kt`:

```kotlin
val locationCheck: LocationCheck by lazy {
    LocationCheck(
        locationSource = FusedLocationSource(context),
        timetable = timetable,
        settings = settings,
        selector = selector,
        refresher = complicationRefresher,
    )
}

suspend fun scheduleNextBoundary() {
    // Replaced in Task 10 with the AlarmManager implementation.
}
```

Imports: `io.canavar.vakit.location.FusedLocationSource`, `io.canavar.vakit.location.LocationCheck`.

`LocationScheduler.kt`:

```kotlin
package io.canavar.vakit.location

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object LocationScheduler {
    private const val PERIODIC = "location-hourly"
    private const val NOW = "location-now"

    fun ensurePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LocationWorker>(1, TimeUnit.HOURS).build(),
        )
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LocationWorker>().build(),
        )
    }
}
```

`BootReceiver.kt`:

```kotlin
package io.canavar.vakit.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            LocationScheduler.ensurePeriodic(context)
            LocationScheduler.runNow(context)
        }
    }
}
```

- [ ] **Step 6: Wire the application and manifest**

Add to `VakitApp.kt`:

```kotlin
override fun onCreate() {
    super.onCreate()
    container.start()
}
```

Add to `AppContainer.kt`:

```kotlin
fun start() {
    LocationScheduler.ensurePeriodic(context)
    LocationScheduler.runNow(context)
    complicationRefresher.requestUpdate()
}
```

Imports: `io.canavar.vakit.location.LocationScheduler`.

Add to `AndroidManifest.xml` before `<application>`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Add inside `<application>`:

```xml
<receiver
    android:name=".location.BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
    </intent-filter>
</receiver>
```

- [ ] **Step 7: Verify no INTERNET permission and build**

Run:

```bash
grep -c "android.permission.INTERNET" app/src/main/AndroidManifest.xml || true
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: `0` for the grep, then `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/location app/src/main/java/io/canavar/vakit/AppContainer.kt app/src/main/java/io/canavar/vakit/VakitApp.kt app/src/main/AndroidManifest.xml app/src/test/java/io/canavar/vakit/location
git commit -m "feat: check location hourly and on boot"
```

---

## Task 10: Boundary alarm

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/complication/BoundaryScheduler.kt`
- Create: `app/src/main/java/io/canavar/vakit/complication/PrayerBoundaryReceiver.kt`
- Modify: `app/src/main/java/io/canavar/vakit/AppContainer.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/io/canavar/vakit/complication/BoundarySchedulerTest.kt`

**Interfaces:**
- Consumes: `NextEntry`, `AppContainer`.
- Produces:
  - `class BoundaryScheduler(context: Context, zone: ZoneId) { fun schedule(next: NextEntry?) }`.
  - `class PrayerBoundaryReceiver : BroadcastReceiver`.

- [ ] **Step 1: Write the failing test**

The alarm itself needs Android. Test the pure delay calculation instead. Add a helper to `BoundaryScheduler.kt`:

```kotlin
object BoundaryTimes {
    fun triggerAtMillis(next: NextEntry, zone: ZoneId): Long =
        next.at.atZone(zone).toInstant().toEpochMilli() + 5_000
}
```

`BoundarySchedulerTest.kt`:

```kotlin
package io.canavar.vakit.complication

import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.Prayer
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundarySchedulerTest {
    @Test
    fun `adds five seconds to the next entry`() {
        val next = NextEntry(Prayer.IKINDI, LocalDateTime.of(2026, 6, 15, 14, 47))
        val expected = LocalDateTime.of(2026, 6, 15, 14, 47, 5)
            .atZone(ZoneId.of("Europe/Istanbul"))
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, BoundaryTimes.triggerAtMillis(next, ZoneId.of("Europe/Istanbul")))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.complication.BoundarySchedulerTest'
```

Expected: FAIL, unresolved `BoundaryTimes`.

- [ ] **Step 3: Write the scheduler and receiver**

`BoundaryScheduler.kt`:

```kotlin
package io.canavar.vakit.complication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.canavar.vakit.domain.NextEntry
import java.time.ZoneId

object BoundaryTimes {
    fun triggerAtMillis(next: NextEntry, zone: ZoneId): Long =
        next.at.atZone(zone).toInstant().toEpochMilli() + 5_000
}

class BoundaryScheduler(
    private val context: Context,
    private val zone: ZoneId,
) {
    fun schedule(next: NextEntry?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, PrayerBoundaryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        if (next == null) return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            BoundaryTimes.triggerAtMillis(next, zone),
            pendingIntent,
        )
    }
}
```

`PrayerBoundaryReceiver.kt`:

```kotlin
package io.canavar.vakit.complication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.canavar.vakit.VakitApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrayerBoundaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as VakitApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.complicationRefresher.requestUpdate()
                app.container.scheduleNextBoundary()
            } catch (error: Exception) {
                // The next hourly worker retries the schedule.
            } finally {
                pending.finish()
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'io.canavar.vakit.complication.BoundarySchedulerTest'
```

Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 5: Wire the container and manifest**

Add to `AppContainer.kt`:

```kotlin
val boundaryScheduler = BoundaryScheduler(context, zone)
```

Replace the temporary no-op with:

```kotlin
suspend fun scheduleNextBoundary() {
    val districtId = settings.read().districtId ?: return
    boundaryScheduler.schedule(nextEntry(districtId))
}
```

Add to `start()` after `complicationRefresher.requestUpdate()`:

```kotlin
CoroutineScope(Dispatchers.IO).launch { scheduleNextBoundary() }
```

Add the import `kotlinx.coroutines.CoroutineScope` and `kotlinx.coroutines.launch`.

Add inside `<application>` in `AndroidManifest.xml`:

```xml
<receiver
    android:name=".complication.PrayerBoundaryReceiver"
    android:exported="false" />
```

- [ ] **Step 6: Build and run all tests**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/complication app/src/main/java/io/canavar/vakit/AppContainer.kt app/src/main/AndroidManifest.xml app/src/test/java/io/canavar/vakit/complication
git commit -m "feat: refresh the complication at each prayer boundary"
```

---

## Task 11: Status screen, district picker, and permissions

**Files:**
- Create: `app/src/main/java/io/canavar/vakit/ui/StatusUiState.kt`
- Create: `app/src/main/java/io/canavar/vakit/ui/StatusScreen.kt`
- Create: `app/src/main/java/io/canavar/vakit/ui/DistrictPickerActivity.kt`
- Modify: `app/src/main/java/io/canavar/vakit/MainActivity.kt`
- Modify: `app/src/main/java/io/canavar/vakit/AppContainer.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AppContainer`, `SettingsRepository`, `TimetableRepository`, `LocationScheduler`, `ComplicationRefresher`.
- Produces:
  - `data class StatusUiState(districtName: String?, province: String?, next: NextEntry?, today: List<Pair<Prayer, String>>, lastFixAt: Long?, locationBlocked: Boolean, autoLocation: Boolean, hasFinePermission: Boolean, hasBackgroundPermission: Boolean)`.
  - `suspend fun AppContainer.loadStatus(hasFine: Boolean, hasBackground: Boolean): StatusUiState`.
  - `class DistrictPickerActivity : ComponentActivity`.

- [ ] **Step 1: Write the status state and loader**

`StatusUiState.kt`:

```kotlin
package io.canavar.vakit.ui

import io.canavar.vakit.domain.NextEntry
import io.canavar.vakit.domain.Prayer

data class StatusUiState(
    val districtName: String?,
    val province: String?,
    val next: NextEntry?,
    val today: List<Pair<Prayer, String>>,
    val lastFixAt: Long?,
    val locationBlocked: Boolean,
    val autoLocation: Boolean,
    val hasFinePermission: Boolean,
    val hasBackgroundPermission: Boolean,
)
```

Add to `AppContainer.kt`:

```kotlin
suspend fun loadStatus(
    hasFinePermission: Boolean,
    hasBackgroundPermission: Boolean,
): StatusUiState = withContext(Dispatchers.IO) {
    val current = settings.read()
    val districtId = current.districtId
    val today = LocalDate.now(zone)
    val monthDay = canonicalMonthDay(today)
    val times = districtId?.let {
        timetable.times(it, monthDay.monthValue, monthDay.dayOfMonth)
    }
    StatusUiState(
        districtName = current.districtName,
        province = current.districtProvince,
        next = districtId?.let { nextEntry(it) },
        today = times?.let { value ->
            Prayer.entries.map { prayer ->
                prayer to TimeFormat.hhmm(value.minuteOf(prayer))
            }
        } ?: emptyList(),
        lastFixAt = current.lastFix?.atEpochMs,
        locationBlocked = current.locationBlocked,
        autoLocation = current.autoLocation,
        hasFinePermission = hasFinePermission,
        hasBackgroundPermission = hasBackgroundPermission,
    )
}
```

Imports: `io.canavar.vakit.domain.Prayer`, `io.canavar.vakit.domain.TimeFormat`, `io.canavar.vakit.ui.StatusUiState`, `java.time.LocalDate`.

- [ ] **Step 2: Write the screens**

`StatusScreen.kt`:

```kotlin
package io.canavar.vakit.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import io.canavar.vakit.VakitApp
import io.canavar.vakit.domain.TimeFormat
import io.canavar.vakit.location.LocationScheduler
import kotlinx.coroutines.launch

@Composable
fun StatusRoute() {
    val context = LocalContext.current
    val container = (context.applicationContext as VakitApp).container
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<StatusUiState?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val hasBackground = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(reload) {
        state = container.loadStatus(hasFine, hasBackground)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) LocationScheduler.runNow(context)
        reload++
    }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { reload++ }

    StatusScreen(
        state = state,
        onRefreshLocation = {
            LocationScheduler.runNow(context)
            container.complicationRefresher.requestUpdate()
            reload++
        },
        onPickDistrict = {
            pickLauncher.launch(Intent(context, DistrictPickerActivity::class.java))
        },
        onGrantLocation = {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onOpenSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onUseGps = {
            scope.launch {
                container.settings.setAutoLocation(true)
                LocationScheduler.runNow(context)
                reload++
            }
        },
    )
}

@Composable
fun StatusScreen(
    state: StatusUiState?,
    onRefreshLocation: () -> Unit,
    onPickDistrict: () -> Unit,
    onGrantLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onUseGps: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state == null) {
            Text("Yükleniyor…")
            return@Column
        }
        Text("Vakit", style = MaterialTheme.typography.title3)
        Text(state.districtName ?: "İlçe seçilmedi")
        state.next?.let {
            Text("Sonraki: ${it.prayer.displayName} ${TimeFormat.hhmm(it.at)}")
        }
        Spacer(Modifier.height(8.dp))
        state.today.forEach { (prayer, time) ->
            Text("${prayer.displayName}: $time")
        }
        if (state.locationBlocked) {
            Text("Konum alınamadı")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRefreshLocation) { Text("Konumu yenile") }
        Button(onClick = onPickDistrict) { Text("İlçe seç") }
        if (!state.hasFinePermission) {
            Button(onClick = onGrantLocation) { Text("İzin ver") }
        } else if (!state.hasBackgroundPermission) {
            Button(onClick = onOpenSettings) { Text("Arka plan izni") }
        }
        if (!state.autoLocation) {
            Button(onClick = onUseGps) { Text("GPS kullan") }
        }
    }
}
```

`DistrictPickerActivity.kt`:

```kotlin
package io.canavar.vakit.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import io.canavar.vakit.VakitApp
import io.canavar.vakit.domain.District
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DistrictPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DistrictPickerRoute()
            }
        }
    }
}

@Composable
fun DistrictPickerRoute() {
    val context = LocalContext.current
    val container = (context.applicationContext as VakitApp).container
    val scope = rememberCoroutineScope()
    var districts by remember { mutableStateOf<List<District>>(emptyList()) }
    var province by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        districts = withContext(Dispatchers.IO) { container.timetable.districts() }
    }

    if (province == null) {
        val provinces = districts.map { it.province }.distinct().sorted()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(provinces) { name ->
                Chip(
                    label = { Text(name) },
                    onClick = { province = name },
                )
            }
        }
    } else {
        val selected = province
        val towns = districts.filter { it.province == selected }.sortedBy { it.name }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(towns) { district ->
                Chip(
                    label = { Text(district.name) },
                    onClick = {
                        scope.launch {
                            container.settings.setDistrict(district, auto = false)
                            container.scheduleNextBoundary()
                            container.complicationRefresher.requestUpdate()
                            (context as? Activity)?.finish()
                        }
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 3: Replace the launcher activity and register the picker**

`MainActivity.kt`:

```kotlin
package io.canavar.vakit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material.MaterialTheme
import io.canavar.vakit.ui.StatusRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                StatusRoute()
            }
        }
    }
}
```

Add to `AndroidManifest.xml` inside `<application>`:

```xml
<activity
    android:name=".ui.DistrictPickerActivity"
    android:exported="false" />
```

- [ ] **Step 4: Build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/canavar/vakit/ui app/src/main/java/io/canavar/vakit/MainActivity.kt app/src/main/java/io/canavar/vakit/AppContainer.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add status screen and district picker"
```

---

## Task 12: Generate and ship the full timetable

**Files:**
- Modify: `app/src/main/assets/timetable.db`
- Modify: `app/src/main/assets/timetable.version`

**Interfaces:**
- Consumes: `scripts/build_timetable.py`.
- Produces: the real 2026 timetable asset for all districts.

- [ ] **Step 1: Confirm every district has coordinates**

Run:

```bash
python3 scripts/build_timetable.py --report-unmatched
```

Expected: `matched: 869 districts, 81 provinces` (or the live count). No `unmatched:` block. If an unmatched district appears, add an entry to `scripts/district-overrides.json` with its coordinates and rerun.

- [ ] **Step 2: Generate the full database**

Run:

```bash
time python3 scripts/build_timetable.py
```

Expected: about 20 minutes. Ends with `wrote app/src/main/assets/timetable.db: ... districts, ... rows`. The cache in `scripts/cache/` makes a rerun resumable.

- [ ] **Step 3: Verify the database**

Run:

```bash
python3 - <<'PY'
import sqlite3
con = sqlite3.connect("app/src/main/assets/timetable.db")
print("districts", con.execute("select count(*) from districts").fetchone()[0])
print("rows", con.execute("select count(*) from times").fetchone()[0])
print("min day", con.execute("select min(month*100+day) from times").fetchone()[0])
print("max day", con.execute("select max(month*100+day) from times").fetchone()[0])
print("distinct districts", con.execute("select count(distinct district_id) from times").fetchone()[0])
print("ankara june 15", con.execute("select imsak,gunes,ogle,ikindi,aksam,yatsi from times where district_id=9206 and month=6 and day=15").fetchone())
PY
```

Expected: `districts` is the live count (869 at the time of writing), `rows` equals `districts × 365`, `min day 101`, `max day 1231`, `distinct districts` equals `districts`, and Ankara's six times appear.

- [ ] **Step 4: Rebuild and reinstall**

Run:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`.

- [ ] **Step 5: Commit the database**

```bash
git add app/src/main/assets/timetable.db app/src/main/assets/timetable.version
git commit -m "data: bundle the full 2026 Diyanet timetable"
```

---

## Task 13: Device verification

**Files:**
- No code changes expected. Fix defects in the owning task's files and commit.

**Interfaces:**
- Consumes: the installed APK.
- Produces: a verified watch behavior.

- [ ] **Step 1: Pair the watch**

On the watch: Settings → About → Software versions → tap build number seven times. Then Developer options → Wireless debugging → Pair new device.

Run on the computer, substituting the watch's values:

```bash
adb pair <watch-ip>:<pair-port>
adb connect <watch-ip>:<debug-port>
adb devices -l
```

Expected: the watch appears as `device`.

- [ ] **Step 2: Install and grant permissions**

Run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant io.canavar.vakit android.permission.ACCESS_FINE_LOCATION
adb shell pm grant io.canavar.vakit android.permission.ACCESS_BACKGROUND_LOCATION
adb shell am start -n io.canavar.vakit/.MainActivity
```

Expected: the app opens. If `pm grant` fails for background location, open Settings → Apps → Vakit → Permissions → Location and choose "Allow all the time". Record whether Wear OS 6 offers that option.

- [ ] **Step 3: Verify first run**

On the watch:

1. Confirm the district appears.
2. Confirm the six times appear.
3. Confirm the next entry line is correct for the current time.

- [ ] **Step 4: Add the complication**

Long-press the watch face → Customize → choose a complication slot → Vakit.

Expected: the slot shows `İkindi` and `14:47`, or the current next entry.

- [ ] **Step 5: Verify reboot behavior**

Run:

```bash
adb reboot
```

Wait for the watch to boot, then look at the face before opening the app.

Expected: the face shows the correct next entry immediately. `adb logcat -s Vakit` may show the location worker start.

- [ ] **Step 6: Verify offline behavior**

Run:

```bash
adb shell settings put global airplane_mode_on 1
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
```

Expected: the complication still changes at the next boundary. The app makes no network calls.

Disable airplane mode afterwards:

```bash
adb shell settings put global airplane_mode_on 0
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false
```

- [ ] **Step 7: Verify manual override and GPS mode**

In the app:

1. Tap "İlçe seç", choose a different province and district.
2. Confirm the complication changes.
3. Confirm "GPS kullan" appears.
4. Tap "GPS kullan", then "Konumu yenile".
5. Confirm the district returns to the nearest one.

- [ ] **Step 8: Check battery**

After 24 hours, run:

```bash
adb shell dumpsys batterystats io.canavar.vakit | head -60
```

Expected: the location worker appears about 24 times, with no network wakeups.

- [ ] **Step 9: Commit any fixes**

```bash
git status
git add <changed files>
git commit -m "fix: <device finding>"
```

---

## Self-Review Notes

- The spec's acceptance criterion for a short-text complication is satisfied by the title/text split. `ShortTextComplicationData.MAX_TEXT_LENGTH = 7` is why the clock is the text and the name is the title.
- The spec's "no network" constraint is enforced by the absence of `INTERNET` in the manifest and by the generator being the only HTTP client.
- The spec's "immediate check on boot" is covered by `BootReceiver` enqueueing a one-time worker, and by the complication rendering from the cache before that worker finishes.
- The five coordinate overrides are exact values verified against the coordinate dataset.
