# Syona — Test Report

Date: 2026-11-19 · Environment: macOS (arm64), JDK 21 (Temurin), Gradle 8.13, AGP 8.13.0, Android SDK 36

## 1. Strategy

| Layer | Approach | Executed? |
|---|---|---|
| Build verification | `assembleDebug`, `assembleRelease`, `lintDebug` | ✅ |
| Unit tests (JVM) | JUnit 4, pure Kotlin logic: frequency rules, persisted-state parsing, chart aggregation | ✅ |
| Artifact verification | `dexdump` of the release APK to confirm desugaring | ✅ |
| Instrumented / UI / device tests | Espresso / manual on emulator | ⛔ **Not executed** — see §5 |

Robolectric was deliberately not added: it downloads large Android runtime jars, and the machine had about 0.4–1.1 GB of free disk space.

## 2. Build verification results

| Check | Baseline (before changes) | Final |
|---|---|---|
| `assembleDebug` | ✅ Pass | ✅ Pass |
| `assembleRelease` | not run at baseline | ✅ Pass (unsigned APK, 6.9 MB, minify off) |
| `testDebugUnitTest` | ✅ 1/1 (template test only) | ✅ **21/21** |
| `lintDebug` | ❌ **116 errors**, 293 warnings | ✅ **0 errors**, 290 warnings |
| Lint `NewApi` | 67 errors | 0 |
| Lint `UseAppTint` | 49 errors | 0 |
| Desugared `java.time` in release dex | n/a | ✅ 8 × `j$.time.LocalDate.now`, 0 × `java.time.LocalDate.now` |

Note: my first baseline attempt failed only because I ran Gradle with `--offline` while some artifacts were not cached. That was not a project defect; the online run is the baseline above.

Remaining lint warnings (not errors): HardcodedText 96, UnusedResources 87, ContentDescription about 31, UseKtx 12, GradleDependency 11, SetTextI18n 11, and others.

## 3. Unit test cases (all executed, all passed)

### `HabitFrequencyTest` (7)
| Test | Expected | Result |
|---|---|---|
| Daily & weekly show every day | true for Sun…Sat | ✅ |
| Weekdays → Mon–Fri only | exact list | ✅ |
| Weekends → Sat, Sun only | exact set | ✅ |
| Custom → only selected days | matches selection | ✅ |
| Custom with null/empty days → never | false | ✅ |
| `fromDisplayName` case-insensitive, default DAILY | WEEKENDS / CUSTOM / DAILY / DAILY | ✅ |
| `MoodType.fromLabel` default NEUTRAL | HAPPY / NEUTRAL | ✅ |

### `PreferencesStateParsingTest` (6) — persisted data robustness
| Test | Expected | Result |
|---|---|---|
| Legacy habit without `frequency`/`icon` | DAILY / HEART, `shouldShowToday` does not throw | ✅ |
| Unknown enum names (`FORTNIGHTLY`, `ROCKET`, `EXCITED`, `GYRO`) | repaired to defaults; unknown mood entry dropped | ✅ |
| `null` list items / habit without name | dropped | ✅ |
| `{}` | all defaults (goal 2000 ml, 8000 steps) | ✅ |
| `"null"` JSON literal | default state, no crash | ✅ |
| Full round trip (Gson write → read) | identical state | ✅ |

### `MoodChartDataTest` (7) — chart aggregation
| Test | Expected | Result |
|---|---|---|
| Empty input, all ranges | no points | ✅ |
| Days: last 7 days, chronological | 2 points, oldest first, 8-day-old entry excluded | ✅ |
| Weeks: average within a week | (Mon+Tue) → 4.0 | ✅ |
| Week crossing New Year (ISO week 1 of 2026) | 30 Dec 2025 + 1 Jan 2026 in one bucket | ✅ |
| Months: Nov 2025 vs Nov 2026 not merged | 2 separate points (**regression test for the old bug**) | ✅ |
| Months: average within a month | 4.0 | ✅ |
| Unknown range | no points | ✅ |

### `ExampleUnitTest` (1) — template, passes.

## 4. Failed tests
None among those executed.

## 5. Tests NOT executed and why

**Blocker:** all three local AVDs (`Medium_Phone_API_36.0`, `Pixel_7`, `Pixel_Tablet`) reference system images whose `system.img`/`vendor.img` are missing, so the emulator reports "No initial system image for this configuration!". Re-downloading an image needs about 1.5–2 GB, and the disk had about 0.4–1.1 GB free. No physical device was connected (`adb devices` was empty). I did not delete any of your files to make room.

Therefore **none** of the following were executed. Run them manually once a device or emulator is available:

| # | Area | Steps | Expected |
|---|---|---|---|
| D1 | API 24/25 launch (C1) | Install debug APK on an Android 7.0/7.1 emulator. Launch. | Onboarding appears; no `NoClassDefFoundError` |
| D2 | Onboarding → login → main | Go through the pager, enter a name, tap *Get Started*. Relaunch the app. | Lands on Habits; onboarding not shown again |
| D3 | Habit checkbox (H1) | Add about 12 habits. Check #1–#3. Scroll to the bottom and back. Check #11. | Only the tapped habit toggles; #1–#3 stay checked |
| D4 | Add/edit habit after rotation (H2) | Open *Add habit*, type a name, rotate, tap *Add*. | Habit saved |
| D5 | Add mood after rotation (H2) | Open mood sheet, pick a mood, rotate, *Save*. | Entry appears |
| D6 | Habit frequency | Create a "Weekends" habit on a weekday. | Hidden today; still listed in storage (appears on Sat) |
| D7 | Day rollover | Complete habits and log water. Set device date +1 day. Reopen. | Habits unchecked, water 0 ml |
| D8 | Hydration reminder after late grant (H4) | Fresh install, deny notifications at first launch. Hydration → enable reminders → grant. | Immediate reminder posted; periodic work enqueued (`adb shell dumpsys jobscheduler`) |
| D9 | No repeated prompt (H4) | Deny notifications, switch tabs to Hydration repeatedly. | No permission dialog/toast on each visit |
| D10 | Notification coexistence (H3) | Enable step counter + reminders, walk or simulate steps. | Both notifications visible simultaneously |
| D11 | Step FGS without permission (C2) | Android 14+: enable step counter, revoke *Physical activity* in Settings, reboot. | No crash after boot; service not running |
| D12 | Step counter with permission | Enable in Profile → grant. Walk (or `adb emu sensor set` on emulator). | Count increases (may lag ≤10 s due to batching) |
| D13 | Large profile photo (C3) | Pick a ≥ 24 MP photo. | Photo shown, no crash |
| D14 | Hydration ring size (M3) | Open Hydration on a 420+ dpi phone. | Ring fills about 260 dp; text proportional |
| D15 | Clear all data (M8) | Profile → Clear All. | Data gone, step notification gone, reminder work cancelled |
| D16 | Tint change (M6) | Visually compare all screens with the previous build. | Icons keep their colours |
| D17 | Dark mode | Enable system dark theme, visit every screen. | Readable text, no white-on-white (**unverified risk**) |
| D18 | Tablet / landscape | `Pixel_Tablet` and landscape on phone. | Layout uses sw600dp/land dimensions, no clipping |
| D19 | Text scaling 200% / TalkBack | Accessibility settings. | Labels readable; icon buttons announced |

## 6. Remaining risks
- Every UI-level fix (H1, H2, H4, C2, C3, M3, M6) is verified only by code review and compilation, not at runtime.
- Dark mode is likely to have contrast problems (night theme forces white text over light cards). Not verified.
- Integration of the repository with `SharedPreferences` (`WellnessRepository`) has no automated test, because it needs an Android `Context` (Robolectric or instrumented).
- Release signing and R8 minification are not configured or exercised.
