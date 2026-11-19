# Syona — Technical Report

Date: 2026-11-19. Companion documents: `ANDROID_APP_AUDIT_AND_IMPROVEMENT_PLAN.md` (findings and status) and `ANDROID_APP_TEST_REPORT.md` (tests).

## 1. Architecture (unchanged)
Single-module Kotlin app using XML Views with ViewBinding, in an MVVM-lite pattern:

- **UI:** 3 activities (`OnboardingActivity` launcher → `LoginActivity` → `MainActivity`), 4 bottom-nav fragments (Habits, Mood, Hydration, Profile), plus bottom sheets and dialogs.
- **ViewModels** expose `StateFlow`s from `WellnessRepository` (manual factories, no DI framework).
- **`WellnessRepository`** is created once in `SyonaApp`. It owns `MutableStateFlow`s, the day-rollover resets (habits, hydration, steps), streaks and the aggregated stats.
- **`PreferencesManager`** keeps one immutable `PreferencesState` in memory under a lock. Every change re-serializes the whole state with Gson into a single SharedPreferences key.
- **Background:** `HydrationReminderWorker` (WorkManager periodic, at least 15 min), `StepCounterService` (foreground service of type `health`, step counter or step detector sensor), `BootReceiver` (restarts the step service).

There is no network, backend, or authentication. "Login" is a local profile set-up.

## 2. Technology stack
Kotlin 2.0.21 · AGP 8.13.0 · Gradle 8.13 · compile/target SDK 36 · min SDK 24 · Material 1.13 · Navigation 2.8.3 · Lifecycle 2.8.6 · WorkManager 2.9.1 · MPAndroidChart 3.1.0 · Gson 2.11.0 · **new:** desugar_jdk_libs 2.1.5 · **removed:** Coil (unused).

## 3. Features
Habit tracking (frequencies, icons, notes, daily reset, progress, streaks) · mood journal (list/calendar, insights, trend chart) · hydration (intake, goal, reminders) · profile (photo, stats, step counter, privacy/about, share, clear data) · onboarding.

## 4. Problems found and fixes

| ID | Problem | Fix | Files |
|---|---|---|---|
| C1 | `java.time` with minSdk 24 and no desugaring → launch crash on Android 7.x | `isCoreLibraryDesugaringEnabled` + `desugar_jdk_libs` | `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| C2 | `health` FGS started without `ACTIVITY_RECOGNITION` → `SecurityException` on 14+ | Permission check in `StepCounterService.start()` and `onStartCommand`. API-34 typed `startForeground` in try/catch → `stopSelf`. Start wrapped for `ForegroundServiceStartNotAllowedException` | `services/StepCounterService.kt`, `receivers/BootReceiver.kt` |
| C3 | Full-resolution photo decode → `OutOfMemoryError` (not caught) | Two-pass sampled decode (about 800 px), catch `OutOfMemoryError`. Dead crop path removed (it never ran: no FileProvider) | `ui/profile/ProfileFragment.kt`, removed `res/xml/file_paths.xml` |
| H1 | Recycled habit row toggled the previous habit | Detach listener → set state → attach a state-aware listener | `ui/habits/HabitAdapter.kt` |
| H2 | Habit/mood sheet result lost on recreation | Fragment Result API | `HabitFormBottomSheet.kt`, `HabitsFragment.kt`, `MoodEntryBottomSheet.kt`, `MoodFragment.kt` |
| H3 | Step and hydration notifications shared ID 1001 | Step notification → 2001 | `StepCounterService.kt` |
| H4 | Reminders not scheduled after a late permission grant; prompt on every tab visit | Schedule on grant; removed the per-visit prompt | `ui/hydration/HydrationFragment.kt` |
| M1 | Infinite `ObjectAnimator`s never cancelled (leak + CPU) | Track and cancel | `HydrationFragment.kt`, `ProfileFragment.kt`, `LoginActivity.kt` |
| M2 | Every step event rewrites the full JSON state | 10 s sensor batching (`maxReportLatencyUs`) | `StepCounterService.kt` |
| M3 | Progress ring hard-coded to 300 px | `resolveSize` + proportional stroke/text | `ui/components/CircularProgressView.kt` |
| M4 | "Months" chart merged the same month from different years; logic duplicated twice | `MoodChartData.aggregate()` with year-aware keys | new `ui/mood/MoodChartData.kt`, `MoodViewModel.kt`, `ProfileViewModel.kt` |
| M5 | Gson can inject `null` into non-null Kotlin fields | `PreferencesState.sanitized()` applied on load | `data/preferences/PreferencesState.kt`, `PreferencesManager.kt` |
| M6 | 49 lint errors `android:tint` in layouts | `app:tint` | `res/layout/*.xml` |
| M7 | Exported `MainActivity`, unused `INTERNET`, receiver without flag, privacy text claimed encryption | `exported=false`, permission removed, `RECEIVER_NOT_EXPORTED`, truthful text | `AndroidManifest.xml`, `MainActivity.kt`, `ProfileFragment.kt` |
| M8 | Clear-data left reminder work and step service running | Cancel and stop | `ProfileFragment.kt` |
| — | `windowLightNavigationBar` (API 27) lint error | `tools:targetApi="27"` | `res/values/themes.xml` |
| — | Chart close button 24 dp, unlabeled | 48 dp + content description | `dialog_mood_chart.xml`, `strings.xml` |
| — | Gson needs keep rules if R8 is enabled | Added | `proguard-rules.pro` |

## 5. Performance notes (evidence-based only)
- **Step counter I/O:** previously one full-state JSON serialization and SharedPreferences write per sensor event, and the state includes the Base64 profile photo (tens of KB). Batching lets sensor hubs that support it deliver at most one batch per 10 s. **Not measured** (no device). The benefit depends on hardware FIFO support.
- **Animators:** each visit to Hydration/Profile previously left 6 infinite animators running against detached views. They are now cancelled with the view.
- **APK:** release, unsigned, minify off: 6.9 MB. Coil was removed (it was unused, so its classes were dead weight). R8 was not enabled. Enabling it would shrink the APK further but needs a device regression pass.
- Not changed (no measurement to justify it): `ACTION_TIME_TICK` → `refreshAll()` once a minute while MainActivity is alive. This is cheap in-memory work, and it writes only on day rollover.

## 6. Security and privacy findings
- All data is in app-private SharedPreferences, **unencrypted**. `allowBackup="true"` with empty rules means mood notes, profile and photo are included in Android cloud backup and device transfer. **Left unchanged:** whether a user's journal should be backed up is a product decision. The in-app privacy text now states this accurately.
- No secrets, API keys, tokens or hard-coded URLs found. `local.properties` only holds `sdk.dir` and is git-ignored.
- Components: only the launcher activity is exported. The service and receivers are not exported. The `BOOT_COMPLETED` receiver is non-exported and receives the protected system broadcast.
- Logging: `WellnessRepository` logs hydration amounts at debug level, and `HabitsFragment` logs progress per animation frame. These are not sensitive but noisy. Left as is (low).
- Permissions after the change: POST_NOTIFICATIONS, VIBRATE, ACTIVITY_RECOGNITION, FOREGROUND_SERVICE(+_HEALTH), WAKE_LOCK, RECEIVE_BOOT_COMPLETED. `WAKE_LOCK` appears to be used only by WorkManager internally.

## 7. Release configuration review
- `versionCode 1` / `versionName "1.0"`. Increment before any store upload.
- No `signingConfig`: the release APK is unsigned. Signing keys were not created, touched or requested.
- `isMinifyEnabled = false`. If enabled, the new Gson keep rules apply. A full device regression pass is required first.
- `applicationId = "com.example.syona"`. Google Play rejects the `com.example` namespace, so it must change before publishing (product decision; not changed).
- An AAB can be produced with `./gradlew bundleRelease` (not run, to save disk).

## 8. Known remaining issues (not fixed)
- Dark mode is likely to have contrast problems (unverified).
- 96 hard-coded strings (no localization), 87 unused resources.
- `SettingsFragment` is a TODO stub, `ChartUtils` and `MoodFragment.shareMoodEntry` are unused, and ProfileFragment is large (about 1 000 lines).
- The step-counter design writes the entire app state per update. The structural fix is to store steps and the photo separately (needs a data migration).
- Several libraries have newer versions (lint `GradleDependency`). Not upgraded, because no defect requires it.
