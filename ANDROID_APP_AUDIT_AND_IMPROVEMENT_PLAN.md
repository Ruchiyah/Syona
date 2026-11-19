# Syona — Android App Audit & Improvement Plan

Audit date: 2026-11-19 (audit of the local working tree, not the single git commit)

> Status legend: ✅ done & verified · 🟡 done, needs device verification · ⛔ not done (reason given) · ➖ intentionally deferred

---

## 1. Project overview

**Syona** is an offline, single-user wellness tracker (SLIIT Y2S2 MAD lab project). It has four tabs:

| Tab | Purpose |
|---|---|
| Habits | Daily habit checklist with frequency (daily / weekdays / weekends / weekly / custom days), icons, notes, daily auto-reset, progress bar, streaks |
| Mood | Mood journal (5 moods + optional note), list view filtered by selected day, month calendar view, "insights" card, trend chart dialog (Days / Weeks / Months) |
| Hydration | Water intake logging (250/500/750/1000 ml), daily goal, periodic reminder notifications via WorkManager |
| Profile | Name/age/gender/goal, profile photo, stats summary, mood trend chart, background step counter (foreground service), privacy/about dialogs, share, "clear all data" |

First launch: Onboarding pager → Login (local profile set-up, no authentication) → Main.

There is **no backend, no network API, and no authentication**. All data is local.

## 2. Technology stack (as found)

| Item | Value |
|---|---|
| Language / UI | Kotlin 2.0.21, XML Views + ViewBinding, Material 3 (1.13.0) |
| Build | Gradle 8.13, AGP 8.13.0, version catalog, configuration cache on |
| SDK | compileSdk 36, targetSdk 36, **minSdk 24**, Java/JVM target 11 |
| Navigation | Jetpack Navigation 2.8.3 (fragment), BottomNavigationView |
| State | ViewModel + Kotlin `StateFlow` exposed from a repository |
| Persistence | SharedPreferences, single key holding one Gson-serialized `PreferencesState` |
| Background | WorkManager 2.9.1 (hydration reminders), foreground `Service` type `health` (step counter), `BOOT_COMPLETED` receiver |
| Libraries | MPAndroidChart v3.1.0 (JitPack), Gson 2.11.0, Coil 2.7.0 (declared, unused), Lottie (commented out) |
| Tests | Template `ExampleUnitTest` / `ExampleInstrumentedTest` only |

## 3. Architecture

```
Activities: OnboardingActivity (launcher) → LoginActivity → MainActivity (NavHost + bottom nav)
Fragments:  HabitsFragment, MoodFragment, HydrationFragment, ProfileFragment, (SettingsFragment – stub)
ViewModels: Habits/Mood/Hydration/ProfileViewModel  (manual factories)
            │
            ▼
WellnessRepository (singleton held by SyonaApp; MutableStateFlows; day-rollover logic; stats)
            │
            ▼
PreferencesManager (synchronized in-memory PreferencesState  ⇄  Gson JSON in SharedPreferences)

Background: StepCounterService ─► repository.applyStepCounterReading()
            HydrationReminderWorker ─► reads PreferencesManager directly, posts notification
            BootReceiver ─► restarts StepCounterService
```

Assessment: an MVVM-lite structure that suits an app this size. Separation is reasonable. Weak points are the whole-state rewrite on every change, UI fragments holding logic (ProfileFragment ≈ 1 000 lines), and duplicated chart code.

## 4. Feature status (baseline)

| Feature | Status at baseline |
|---|---|
| Onboarding / login / profile set-up | Works (code review) |
| Habits CRUD, frequency filter, daily reset | Works, but **wrong habit can be toggled** on recycled rows (H1); add/edit lost after rotation (H2) |
| Mood logging, list/calendar, insights | Works; entry lost after rotation (H2) |
| Mood trend chart | Works; month aggregation merges the same month across years (M4) |
| Hydration logging & goal | Works |
| Hydration reminders | Works if permission already granted; **not scheduled** if permission is granted after enabling (H4); reminder replaced by step notification (H3) |
| Step counter | Works when permission granted; **can crash** on Android 14+ if permission revoked (C2) |
| Profile photo | Crop path never runs (no FileProvider declared); large photos can **OOM-crash** (C3) |
| Settings screen | Placeholder (`TODO`), unreachable from UI |
| Whole app on Android 7.0/7.1 (API 24/25) | **Crashes on launch** (C1) |

## 5. Findings by severity

### CRITICAL

**C1 — App crashes at launch on API 24–25 (`java.time` without desugaring)** — ✅
- Files: `WellnessRepository.kt`, `MoodViewModel.kt`, `MoodFragment.kt`, `MoodAdapter.kt`, `ChartUtils.kt`, … (67 lint `NewApi` errors)
- Evidence: `./gradlew lintDebug` → `Error: Call requires API level 26, or core library desugaring (current min is 24): java.time…`. `WellnessRepository.init` → `refreshAll()` → `LocalDate.now()` runs inside `SyonaApp.onCreate`.
- Root cause: `minSdk = 24` but `java.time` exists only from API 26, and desugaring is not enabled.
- Fix: enable `isCoreLibraryDesugaringEnabled` + `desugar_jdk_libs`. No code changes, and minSdk support is kept.
- Risk: low (standard AGP feature; slight APK size increase).

**C2 — Step counter foreground service can crash (Android 14+)** — 🟡
- Files: `services/StepCounterService.kt`, `receivers/BootReceiver.kt`
- Evidence: `onCreate()` calls `startForeground()` for a `health`-type FGS with no check that `ACTIVITY_RECOGNITION` is granted. Android 14+ throws `SecurityException` in that case. The paths that reach it without a check are `BootReceiver` (after reboot, with permission revoked in Settings) and `START_STICKY` restarts.
- Fix: check the permission before `startForegroundService` (in `StepCounterService.start`). Inside the service, call `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_HEALTH)` in a try/catch and `stopSelf()` on failure.
- Risk: low.

**C3 — Profile photo selection can crash with `OutOfMemoryError`** — ✅ (logic) / 🟡 (device)
- File: `ui/profile/ProfileFragment.kt` `handleImageSelection`
- Evidence: `BitmapFactory.decodeStream()` decodes the full-resolution image on the main thread. A 48 MP photo is about 192 MB as ARGB_8888. `OutOfMemoryError` is an `Error`, so the surrounding `catch (e: Exception)` does not catch it.
- Fix: two-pass decode with `inSampleSize` targeting about 800 px, plus a catch for `OutOfMemoryError`.
- Risk: low.

### HIGH

**H1 — Checking a habit can toggle a different habit** — ✅
- File: `ui/habits/HabitAdapter.kt` `bind()`
- Root cause: `habitCheckBox.isChecked = …` runs **before** the previous listener is removed. When a recycled row is rebound to a habit with a different completion state, the old listener fires `onToggle(previousHabit)`. The listener also ignores the actual checked state and always toggles.
- Fix: clear the listener, set the state, then attach a listener that toggles only when the new state differs from the model.

**H2 — Add-habit / add-mood sheets silently drop input after rotation or process recreation** — ✅
- Files: `HabitFormBottomSheet.kt`, `MoodEntryBottomSheet.kt`, `HabitsFragment.kt`, `MoodFragment.kt`
- Root cause: the result is delivered through a `var onSave` lambda set by the caller. After recreation the framework rebuilds the sheet and `onSave` is `null`, so Save does nothing.
- Fix: use the Fragment Result API (`setFragmentResult` / `setFragmentResultListener`), which survives recreation.

**H3 — Notification ID collision (1001) between step-counter FGS and hydration reminder** — ✅
- Files: `StepCounterService.kt`, `HydrationReminderWorker.kt`
- Effect: with both active, each step update replaces the hydration reminder (and the reminder replaces the ongoing step notification).
- Fix: give the step counter its own ID (2001).

**H4 — Hydration reminders not scheduled after permission granted; permission requested on every tab visit** — ✅
- File: `HydrationFragment.kt`
- Root cause: the permission callback only handles denial. `requestNotificationPermission()` runs in every `onViewCreated`.
- Fix: on grant, re-run `updateReminderSettings()`. Request the permission only when the user enables reminders (MainActivity already asks once at first launch).

### MEDIUM

**M1 — Infinite animators leak destroyed views** (Hydration, Profile, Login) — ✅. They are never cancelled, so they keep running and keep old view hierarchies alive across tab switches. Fix: track the animators and cancel them in `onDestroyView` / `onDestroy`.

**M2 — Each step event rewrites the whole JSON state (including the Base64 photo) to disk** — ✅ (mitigated). Fix: request hardware batching (`maxReportLatencyUs` = 10 s) when registering step sensors. This needs no storage schema change. A full fix (separate storage for the photo / step state) is deferred ➖.

**M3 — `CircularProgressView` ignores its layout size** — ✅. `onMeasure` hard-codes 300 px, so the 260 dp hydration ring renders at about 114 dp on a 420 dpi phone, with px-based stroke and text. Fix: honour `resolveSize`, and scale stroke and text with the radius.

**M4 — Mood chart "Months" merges the same month from different years; logic duplicated** — ✅. Fix: extract `MoodChartData.aggregate()` (pure Kotlin, unit-tested) and group by year+month / week-based-year+week.

**M5 — Stored data with missing fields or unknown enum values can crash the app (Gson bypasses Kotlin null-safety)** — ✅. Structural risk, confirmed by unit test: `Habit` and `MoodEntry` have no no-arg constructor, so Gson allocates them without running the constructor. A field missing from stored JSON (e.g. a habit saved by an older build without `frequency`), or an enum name that no longer exists, arrives as `null` in a non-null property, causing an NPE in `shouldShowToday`. No actual user data with this shape was available to inspect. Fix: `PreferencesState.sanitized()` repairs or drops such values after loading.

**M6 — Lint `UseAppTint` errors (49)** — ✅. `android:tint` on ImageViews is changed to `app:tint` (AppCompat-consistent tinting).

**M7 — Security/privacy hygiene** — ✅
- `MainActivity` is `exported="true"` with no need. Changed to `false`.
- `INTERNET` permission is unused (no network code; Coil is unused). Removed, together with the unused Coil dependency.
- The privacy policy claims data "is encrypted". It is plain SharedPreferences with `allowBackup=true`. The text is corrected to describe the actual behaviour. Backup behaviour itself is unchanged (product decision).
- `registerReceiver` is now called via `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`.

**M8 — "Clear all data" leaves the reminder work scheduled and the step service running** — ✅. Both are now cancelled or stopped.

### LOW (documented, mostly deferred ➖)
- 96 hard-coded UI strings, 11 `SetTextI18n` (i18n), 87 unused resources.
- `SettingsFragment` is a TODO stub reachable only via an unused nav action. `ChartUtils` and `MoodFragment.shareMoodEntry` are unused. Kept: deleting them is not required for function.
- Dead image-crop code path (no FileProvider). Removed together with `xml/file_paths.xml`. Behaviour is unchanged (the crop never ran).
- Odd manifest override disabling WorkManager's `BatteryNotLowProxy`. Harmless, left as is.
- Mood chart close button: 24 dp touch target, no content description. Fixed to 48 dp + description ✅.
- Dark mode: the night theme sets white global text over light cards. Possible contrast problems, **not verified** (no emulator).
- Release: `isMinifyEnabled=false`, no signing config, `versionCode 1`. If R8 is enabled, Gson models need keep rules (rules added in advance) ✅.

## 6. Root-cause summary of confirmed errors
| ID | Root cause |
|---|---|
| C1 | Platform API above minSdk with no desugaring; lint never run |
| C2 | Android 14 FGS-type permission rules not handled on non-UI start paths |
| C3 | Unbounded bitmap decode; `Error` not caught by `catch (Exception)` |
| H1 | Listener ordering on recycled ViewHolder |
| H2 | Non-persistent callback across fragment recreation |
| H3 | Two components picked the same constant |
| H4 | Permission result ignored on success |

## 7–9. Improvements (technical, UI/UX, security, performance)
Covered in §5. Principles followed: no redesign, no new runtime dependencies except `desugar_jdk_libs` (build-time), and no storage schema change.

## 10. Dependency / SDK updates
- **Added:** `com.android.tools:desugar_jdk_libs:2.1.5` (coreLibraryDesugaring).
- **Removed:** `io.coil-kt:coil` (unused).
- **Not upgraded:** lint reports newer versions (Navigation, Lifecycle, WorkManager, Kotlin). These are working, compatible versions. Upgrading would add risk with no fix attached, so it is deferred ➖.

## 11. Testing strategy
- Pure-JVM unit tests (JUnit 4) for business logic: habit frequency rules, Gson state sanitising, mood chart aggregation. Robolectric is avoided because the disk has only about 1 GB free.
- Build gates: `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `lintDebug` (no errors).
- Device/UI tests: **blocked**. All local AVD system images are missing `system.img`, and there is not enough disk to download one. Manual test steps are in `ANDROID_APP_TEST_REPORT.md`.

## 12. Implementation sequence
1. C1 desugaring → rebuild + lint
2. H1, H2, H3, H4, C2, C3
3. M1–M8
4. Unit tests, full build + lint + release build
5. Reports, README

## 13. Expected results
- No lint errors. App runs on API 24–36.
- Crash paths C1–C3 removed. Habit/mood input is reliable across rotation. Reminders work after a late permission grant.
- Lower background I/O from the step counter.

---

## Task completion status (final)

| ID | Item | Status | Verification |
|---|---|---|---|
| C1 | Core library desugaring for `java.time` | ✅ | Lint `NewApi` 67 → 0. `dexdump` of release APK: 8 calls to `j$.time.LocalDate.now`, 0 to `java.time` |
| C2 | Step FGS permission guard / API-34 FGS type / start guarded | 🟡 | Compiles, lint clean. Runtime check on Android 14+ needs a device |
| C3 | Down-sampled photo decode + `OutOfMemoryError` catch | 🟡 | Compiles. Needs a device with a large photo |
| H1 | Habit checkbox listener ordering | 🟡 | Code fix. Needs a device with enough habits to scroll |
| H2 | Fragment Result API for habit/mood sheets | 🟡 | Compiles. Needs rotate-and-save on a device |
| H3 | Step notification ID 1001 → 2001 | ✅ | Constants are now distinct (code) |
| H4 | Reminders scheduled after permission grant; no per-visit prompt | 🟡 | Needs a device |
| M1 | Cancel infinite animators (Hydration, Profile, Login) | ✅ | Code |
| M2 | Step-sensor batching (10 s max report latency) | 🟡 | Effect depends on device sensor FIFO |
| M3 | `CircularProgressView` honours layout size | 🟡 | Needs a visual check |
| M4 | Shared `MoodChartData` + year-aware grouping | ✅ | 7 unit tests |
| M5 | Gson state sanitising | ✅ | 6 unit tests |
| M6 | `android:tint` → `app:tint` (49) | ✅ | Lint `UseAppTint` 49 → 0. Visual check pending |
| M7 | MainActivity not exported, INTERNET + Coil removed, receiver flag, honest privacy text | ✅ | Build + lint |
| M8 | Clear-data cancels reminders, stops step service | ✅ | Code |
| — | `windowLightNavigationBar` lint error (`tools:targetApi`) | ✅ | Lint |
| — | Chart close button 48 dp + content description | ✅ | Lint |
| — | R8 keep rules for Gson models (minify still off) | ✅ | Rules present. Minified build not exercised |
| — | Removed dead crop code + `xml/file_paths.xml` | ✅ | Build |
| — | Dark mode, i18n, unused resources, SettingsFragment stub, dependency upgrades | ➖ | Deferred (see §5 LOW) |
| — | Emulator / instrumented / UI tests | ⛔ | Blocked: AVD images lack `system.img`; disk has <0.5 GB free |
