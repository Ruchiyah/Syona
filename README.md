# Syona — Wellness Companion (Android)

Syona is an offline Android app for tracking daily habits, mood, hydration and steps. All data stays on the device. There is no account, server or network access.

## Features
- **Habits:** create habits with an icon, notes and a frequency (daily, weekdays, weekends, weekly, or custom days). Tick them off each day; they reset at midnight. Includes a progress bar and longest-streak tracking.
- **Mood journal:** log one of five moods with an optional note. Browse by day in a list or a month calendar, see your dominant mood, and view a trend chart (days / weeks / months).
- **Hydration:** log water in 250–1000 ml steps against a daily goal (500–10 000 ml). Optional reminder notifications every 30 min – 8 h (minimum 15 min, an Android limit). Reminders stop once the goal is reached.
- **Profile:** name, age, gender, wellness goal and photo; summary stats; mood trend chart.
- **Step counter:** optional background step counting using the phone's step sensor (foreground service with an ongoing notification). It restarts after reboot if it was enabled.
- **Clear all data** from the Profile screen.

## Requirements
- Android Studio (recent; the project uses AGP 8.13 / Gradle 8.13), JDK 17+ (built with JDK 21)
- Android SDK Platform 36
- Device or emulator running Android 7.0 (API 24) or newer
- Internet access at **build time** only (Maven Central, Google, JitPack for MPAndroidChart)

## Build & run
```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # install on a connected device/emulator
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lintDebug              # static analysis (must report 0 errors)
./gradlew assembleRelease        # unsigned release APK (no signing config in repo)
```
Or open the folder in Android Studio and run the `app` configuration.

## Configuration
- `local.properties` (not committed) holds `sdk.dir`; Android Studio creates it automatically.
- There are no API keys or secrets.
- To publish, you would need to add your own signing config, change `applicationId` away from `com.example.*`, and bump `versionCode`.

## Permissions
| Permission | Used for |
|---|---|
| `POST_NOTIFICATIONS` (13+) | Hydration reminders, step-counter notification |
| `ACTIVITY_RECOGNITION` (10+) | Step counting |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH` | Background step counter |
| `RECEIVE_BOOT_COMPLETED` | Restart the step counter after reboot |
| `VIBRATE`, `WAKE_LOCK` | Reminder vibration; WorkManager |

## Project structure
```
app/src/main/java/com/example/syona/
  SyonaApp.kt, MainActivity.kt
  data/model/          Habit, MoodEntry, HydrationState, ProfileData, StepCounterPreferences, …
  data/preferences/    PreferencesManager (Gson JSON in SharedPreferences), PreferencesState
  data/repository/     WellnessRepository (single source of truth, StateFlows, daily resets)
  ui/onboarding, ui/login, ui/habits, ui/mood, ui/hydration, ui/profile, ui/components
  service/             Hydration reminders (WorkManager)
  services/            StepCounterService (foreground service)
  receivers/           BootReceiver
```

## Data & privacy
Data is stored unencrypted in the app's private storage. Because `android:allowBackup="true"`, it can be included in your Android device backup. *Clear all data* in Profile removes everything except the onboarding flag.

## Further documentation
- `ANDROID_APP_AUDIT_AND_IMPROVEMENT_PLAN.md`: audit findings and status
- `ANDROID_APP_TECHNICAL_REPORT.md`: architecture, fixes, security notes
- `ANDROID_APP_TEST_REPORT.md`: test results and manual test checklist
