# Syona — Portfolio-Grade Wellness App: Design

Date: 2026-11-19 · Status: draft for approval (owner answers from builder check included)

> Phases 1–2 status: a failed migration logs the error, starts with an empty store and retries next launch (legacy blob untouched; retries are add-only, see R8). The 'Export raw data' error screen ships in phase 3.

## 1. Intent and why
Turn Syona into a **portfolio piece**: a private, offline wellness tracker built and engineered to the standard of the best apps in its category (Loop Habit Tracker, Daylio, Waterllama), so that both a user and a reviewing engineer would rate it highly. It will **not** be published to the Play Store (owner's decision), but it must be release-quality: no crashes, no data loss, polished UI, tested.

**Positioning:** *"Everything about your day in one private app: habits, mood, water and steps. No account, no ads, no internet. Your data never leaves your phone unless you export it."*

## 2. Goals
1. Data is safe: a real database, automatic migration of existing users' data, backup/restore, export, undo.
2. Feature depth that matches the category leaders in the four pillars (habits, mood, hydration, steps), plus cross-pillar insights none of them offer offline.
3. A modern, consistent UI that keeps Syona's identity (teal→blue gradients `#2AB6A3 → #4D9BE6`, star accents, rounded white cards). Working dark mode, accessibility, translatable text.
4. Engineering that reads well in a portfolio: layered architecture, DI, tests, CI, clear README with screenshots.

## 3. Non-goals
Accounts, cloud sync, ads, analytics/telemetry, social/sharing feeds, AI or anything needing network, Play Store publishing tasks (signing, store listing, privacy URL), iOS.

## 4. Anti-goals (failure even if it "works")
- Any loss or corruption of a user's existing habits, moods, water or step history during migration.
- The app asks for internet permission or sends data anywhere.
- A rewrite that loses Syona's visual identity and looks like a generic template.
- Features that add clutter: every screen must still be usable one-handed in a few seconds.

## 5. Constraints
- Existing app: Kotlin 2.0.21, AGP 8.13, minSdk 24, targetSdk 36, XML Views, Gson-in-SharedPreferences state (`PreferencesState`), core-library desugaring on.
- Disk on the dev machine is tight (about 3 GB free). Keep dependencies lean and avoid Robolectric. UI verification on an emulator needs a system image (about 1.5 GB). Treat device verification as best-effort and clearly report what was not verified.
- Must keep working on API 24+.
- The lab coursework is already marked: no assignment-brief constraints remain (owner).
- Git: work on a branch. First commit = current working tree as-is (baseline), then one commit per phase (owner approved).
- Device testing: owner approved downloading an emulator image, but it needs about 8–10 GB free (image + emulator data + new library caches) and only about 3 GB is free. Until the owner frees space or connects a phone, phases proceed with JVM tests. Instrumented/UI tests and README screenshots are run as soon as a device is available, before the final report.

## 6. Approaches considered
| Approach | Trade-off | Decision |
|---|---|---|
| A. Keep XML screens, add features | Least work. Dated tech for a 2026 portfolio; widgets/dark mode harder | Rejected |
| B. Full Compose rewrite in one go | Strongest signal. ~4k lines of UI rewritten blind (screens can't be seen until the emulator works); high risk | Rejected |
| **C. Incremental: data layer first, new features in Compose, existing screens migrated one at a time** | Same end state as B. App works after every step. Riskiest part (data) done first, where it is fully unit-testable | **Chosen by owner** |

## 7. Architecture (end state)
Single `:app` module (my call: multi-module adds build cost and disk use for little gain at this size; packages enforce layering instead).

```
ui/        Compose screens + ViewModels (StateFlow<UiState>), navigation-compose, theme
domain/    pure-Kotlin use cases & calculators (streaks, insights, schedules) – no Android imports
data/      Room DB (entities, DAOs), repositories, DataStore (settings), backup/export
platform/  reminders (AlarmManager/WorkManager), step service, widgets (Glance), biometric lock
```

- **DI:** Hilt (KSP). My call: it's what Android teams expect to see in a portfolio.
- **Persistence:** Room for all user records. Preferences DataStore for settings (theme, lock enabled, units, reminder defaults).
- **Serialization:** kotlinx.serialization for backup files. Gson stays only to read the legacy blob during migration, then is removed.
- **Compose ↔ XML coexistence:** `MainActivity` hosts navigation. While migrating, unconverted screens remain Fragments; when the last one converts, the app becomes single-activity Compose with navigation-compose.

### Data model (Room)
| Table | Key fields |
|---|---|
| `habit` | id, name, icon, colour, frequency (DAILY/WEEKDAYS/WEEKENDS/CUSTOM/TIMES_PER_WEEK), customDays, targetPerWeek, reminderTime?, notes, sortOrder, archived, createdAt |
| `habit_completion` | habitId, date (LocalDate epoch day) — PK (habitId, date) |
| `mood_entry` | id, mood (1–5), note, timestamp, tags |
| `water_log` | id, amountMl, timestamp |
| `step_day` | date PK, steps |
| `daily_goal` | date PK, waterGoalMl, stepGoal (so past days keep the goal that applied then) |
| `profile` | singleton: name, age, gender, goal, photo **file path** (photo moves out of the DB into app-private storage) |

Key change from today: **history is stored per day**. Today's app only keeps "completed today" flags and today's water total, so history is lost at midnight. From the migration date on, every day is kept.

The current "Weekly" frequency (shown every day, no rule) becomes **TIMES_PER_WEEK** with a target (e.g. 3×/week), as Loop does. Migrated "Weekly" habits get target 1.

### Migration
On first launch of the new version: read the legacy JSON with the existing `PreferencesManager.parseState()` (already sanitizes bad data). Write everything into Room in one transaction:
- Habits, with today's completion if it was ticked.
- All mood entries.
- Today's water intake as a single log.
- Today's steps.
- Profile, with the Base64 photo decoded to a file.
- Settings to DataStore.

Only after the transaction commits, set a `migrated_v2` flag. Keep the old blob untouched for one version as a fallback. If migration throws, the app shows an error screen offering "Export raw data" rather than starting empty. *(Phases 1–2 status: a failed migration logs the error, starts with an empty store and retries next launch (legacy blob untouched; retries are add-only, see R8). The 'Export raw data' error screen ships in phase 3.)* The old app's single streak ("days on which every habit was done") is kept as a **Perfect days** streak shown on the Me tab. Its stored best value (`longestStreak`) is carried into DataStore as `legacyPerfectBest` and used as a floor (builder call). Per-habit streaks start from the migrated history. Legacy water and step values are written to the day they belong to (`lastResetDate`), not blindly to today. The five moods map to scores 1–5 as now (builder call).

## 8. Features (end state)

### Habits
- Daily checklist (today), tick with haptic + animated check; partial progress ring in header (existing).
- Frequencies as above. A **per-habit reminder time** (notification with "Done" action that ticks without opening the app).
- **Habit detail screen:** month calendar heatmap, current/best streak, completion % (30 days / all time). Edit, archive, delete with **undo snackbar**.
- Reorder by drag; archive instead of delete by default.

### Mood
- Log in two taps; optional note and **tags** (e.g. work, sleep, exercise; user-editable list).
- List + calendar views (existing), and Days/Weeks/Months trend chart (existing logic in `MoodChartData`).
- Edit past entries; log for a past date.

### Hydration
- Quick-add buttons (existing amounts, user can customise the four); undo last add.
- Logs stored individually, so a day shows its entries and the history chart shows 7/30-day bars against goal.
- Reminders (existing, WorkManager), plus **quiet hours** (no reminders between e.g. 22:00–07:00).

### Steps
- Existing foreground-service counter, now writing per-day rows. 7/30-day bar chart against goal.

### Insights (cross-pillar, the differentiator)
Computed on device in `domain/` (pure Kotlin, unit-tested):
- Weekly summary card: habits %, average mood, water days on goal, average steps.
- Correlations with plain-language output: "Your mood averages 4.2 on days you hit your water goal vs 3.1 otherwise". Shown only with at least 14 days of data and a minimum difference threshold, to avoid noise.
- Best/worst weekday per habit.

### Navigation (builder call)
Bottom tabs: **Today** (habit checklist + water/steps summary header) · **Mood** · **Water** · **Steps** (new tab) · **Me** (profile, Perfect-days streak, Insights). Settings is opened from a gear icon in the header. Pillars switched off in onboarding/settings remove their tab, their widget, their Today-header summary and the insights that use them.

### Data ownership
- **Backup** to a single `.json` file via the system file picker (Storage Access Framework, no storage permission). **Restore** with preview ("142 moods, 9 habits…") and replace confirmation.
- **CSV export** per pillar.
- **Clear all data** (existing), behind a two-step confirmation dialog.
- **Android auto-backup (owner: builder's pick):** cloud backup is excluded, device-to-device transfer is kept (`data_extraction_rules.xml` / `backup_rules.xml`). This makes "your data never leaves your phone unless you export it" true. A test asserts no `INTERNET` permission and cloud backup excluded.

### Privacy lock
When the lock is on, widgets and notifications show no habit names or amounts ("Open Syona to view") and their actions require unlocking (builder call).

Optional app lock (androidx.biometric: fingerprint/face, device-PIN fallback) on open and after 1 min in background. Off by default.

### Home-screen widgets (Glance)
1. Today's habits: tick from the widget.
2. Water: +250 ml button and progress.

### Onboarding & settings
- Replace the fake "Login" with a 3-step first-run: welcome → name (optional) → pick the pillars you want shown (unwanted tabs hidden).
- Real **Settings** screen (replaces the stub): theme (system/light/dark), units (ml/oz), first day of week, reminders, lock, backup/restore/export, about, licences.

### UI / UX standards
- Material 3 Compose theme built from Syona colours. Dynamic colour **off** to keep identity. Full light and dark palettes.
- 48 dp touch targets, content descriptions, TalkBack order checked, works at 200% font scale. All strings in `strings.xml`.
- Empty states for every list. No toasts for success (snackbars instead).
- Edge-to-edge, predictive back.
- Text sketch of the new Today/Habits screen:
```
┌──────────────────────────────┐
│ ✦ Good morning, Ruchi     ⚙  │  gradient header
│ 3 of 5 habits · 1.2/2.0 L    │
│ ◔ 60%                         │
├──────────────────────────────┤
│ [✓] 🏃 Morning run   🔥12    │  card, tap = detail
│ [ ] 📖 Read 20 min   🔥 4    │
│ [ ] 🧘 Meditate      3/5 wk  │
├──────────────────────────────┤
│ Habits  Mood  Water  Steps  Me│  bottom nav
└──────────────────────────────┘
```

## 9. Quality and testing
- **Unit (JVM):** domain calculators (streaks per frequency, insights, schedules), migration mapping, backup round-trip, ViewModels with fake repositories. Target: every domain rule covered.
- **Room DAO + migration tests:** instrumented (needs emulator). If no emulator, DAO logic is exercised via in-memory fakes and the gap is reported.
- **Compose UI tests:** key flows (add habit, tick, log mood, add water, backup/restore). Instrumented, best-effort.
- **CI:** GitHub Actions workflow running build, unit tests and lint on push (the file is written; whether it runs depends on the owner pushing).
- `lint` must stay at 0 errors. No `INTERNET` permission (checked by a test that parses the merged manifest).

## 10. Delivery order
Each phase leaves a working app:
1. Foundation: Hilt, Room schema, repositories, legacy migration, tests.
2. Existing screens rewired to Room (behaviour unchanged) + per-day history.
3. Compose theme + Settings screen + backup/restore/export.
4. Habit detail, reminders, undo, frequencies.
5. Mood tags/edit, hydration logs/quiet hours, step history.
6. Insights.
7. Convert Habits, Mood, Hydration, Profile screens to Compose; remove Fragments/XML.
8. Widgets, privacy lock, onboarding redo.
9. Accessibility/dark-mode pass, CI, README with screenshots, final test reports.

## 11. Left to the builder
Library versions (compatible with Kotlin 2.0.21 or upgrade Kotlin if needed), exact package layout, chart library for Compose (keep MPAndroidChart via `AndroidView` or draw with Canvas), animation details, icon set extensions, exact insight thresholds within the stated rules, CSV column layout.
