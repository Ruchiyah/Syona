# Data Foundation (Phases 1–2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single Gson-in-SharedPreferences blob with Room + DataStore. Migrate existing users' data without loss, and keep per-day history. Keep every current screen working unchanged.

**Architecture:** New `data/db` (Room entities + DAOs), `data/settings` (DataStore-backed `SettingsStore`), `domain/` (pure-Kotlin schedule + perfect-day streak rules), `data/legacy` (pure mapper + migrator). `WellnessRepository` keeps its **current public API** (all methods and `StateFlow`s that fragments/services call today). Internally it becomes a facade over Room/DataStore, so no UI file changes in this plan. Hilt provides the graph. `SyonaApp.repository` stays the access point for existing callers.

**Tech Stack:** Kotlin 2.0.21, KSP `2.0.21-1.0.27`, Room `2.6.1` (+ room-ktx, room-compiler via KSP), Hilt `2.52` (KSP), DataStore Preferences `1.1.1`, kotlinx-coroutines-test (same version as the resolved `kotlinx-coroutines-core`), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-19-syona-portfolio-app-design.md` (§5 Constraints, §7 Architecture / Data model / Migration, §10 phases 1–2)

## Global Constraints
- minSdk 24, targetSdk/compileSdk 36. Core-library desugaring stays on. `java.time` is allowed.
- No `INTERNET` permission. No network libraries.
- Lint must report **0 errors**. `./gradlew testDebugUnitTest` must pass after every task.
- No Robolectric (disk is tight). JVM tests use fakes. Room DAO tests live in `androidTest` and run only when a device/emulator is available. Record "not run" honestly if none is available.
- Git: branch `feature/data-foundation`; one commit per task (owner approved commits). Never `git add -A` blindly; add the files listed.
- Dates: a "day" is `LocalDate.toEpochDay(): Long` in `ZoneId.systemDefault()` at the time of the event.
- Legacy store: SharedPreferences file `syona_prefs`, key `wellness_state`. **Never delete or modify it** in this plan (it is the fallback for one version).
- Mood score mapping: ANGRY=1, SAD=2, NEUTRAL=3, CALM=4, HAPPY=5 (current `MoodType.score`).
- Legacy `WEEKLY` habits become `TIMES_PER_WEEK` with `targetPerWeek = 1`.
- Perfect day = at least one *required* habit on that day, and all required habits completed. A habit is required on day `d` if it is not archived, `createdAtDay <= d`, it is not `TIMES_PER_WEEK`, and `HabitFrequency.shouldShowOnDay(dayOfWeek(d), customDays)`. (Builder call, recorded in spec §7: weekly-target habits don't block a perfect day.)

## Review Focus
1. **Migration killed between DB commit and setting the `migratedV2` flag** → rerun must not duplicate rows. Migrated rows use deterministic IDs + `REPLACE`. Test in Task 6.
2. **Midnight passes while the app is open** → `refreshAll()` (already called on `ACTION_TIME_TICK`/resume) moves "today". Today's habits show unchecked, water 0, and toggles write to the new day. Test in Task 7.
3. **Clear all data then restart** → must not re-import the legacy blob. `migratedV2` and `onboardingComplete` survive a clear. Test in Task 7.
4. **Step sensor resets after reboot** (`total < lastSensorTotal`) → delta 0, no negative steps. Test in Task 7.
5. **Deleting a habit** → its completions are removed (FK cascade) and perfect-day history is recomputed without it. Tests in Task 2 (instrumented) and Task 4.

---

### Task 0: Branch and baseline commit

**Files:** Modify: `.gitignore`

- [ ] **Step 1:** `git checkout -b feature/data-foundation`
- [ ] **Step 2:** Append `.kotlin/` and `.vscode/` to `.gitignore`.
- [ ] **Step 3:** Commit the current working tree as the baseline: `git add -A && git commit -m "chore: baseline of current working tree before data-foundation work"`. This is the one allowed `-A`: the owner asked for the tree as-is. Then confirm `git status` is clean apart from ignored files.

---

### Task 1: Build setup (KSP, Room, Hilt, DataStore) and Hilt application

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/java/com/example/syona/SyonaApp.kt`
- Create: `app/src/main/java/com/example/syona/di/AppModule.kt`

**Interfaces:**
- Produces: `@HiltAndroidApp class SyonaApp` with `@Inject lateinit var repository: WellnessRepository` (public getter unchanged for existing callers). `AppModule` (`@InstallIn(SingletonComponent::class)`) is the home for providers added in later tasks.

- [ ] **Step 1:** Add the catalog entries: versions `ksp`, `room`, `hilt`, `datastore`, `coroutines` (read the resolved core version with `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep kotlinx-coroutines-core`). Libraries: `room-runtime`, `room-ktx`, `room-compiler`, `hilt-android`, `hilt-compiler`, `datastore-preferences`, `kotlinx-coroutines-test`. Plugins: `com.google.devtools.ksp`, `com.google.dagger.hilt.android`.
- [ ] **Step 2:** Apply the plugins (root `apply false`, app applied). Add `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. Add `testImplementation(libs.kotlinx.coroutines.test)` and `androidTestImplementation(libs.room.testing)` (add `room-testing` to the catalog).
- [ ] **Step 3:** Annotate `SyonaApp` with `@HiltAndroidApp`. Leave the repository construction as it is for now (the `@Inject` switch happens in Task 8).
- [ ] **Step 4:** Run `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: BUILD SUCCESSFUL, 21 tests pass, 0 lint errors.
- [ ] **Step 5:** Commit: `build: add KSP, Room, Hilt, DataStore`

---

### Task 2: Room schema and DAOs

**Files:**
- Create in `app/src/main/java/com/example/syona/data/db/`: `Entities.kt`, `HabitDao.kt`, `MoodDao.kt`, `WaterDao.kt`, `StepDao.kt`, `GoalDao.kt`, `ProfileDao.kt`, `SyonaDatabase.kt`, `Converters.kt`
- Modify: `di/AppModule.kt` (provide DB + DAOs)
- Test: `app/src/androidTest/java/com/example/syona/data/db/DaoTest.kt`

**Interfaces (Produces):**
```kotlin
@Entity(tableName = "habit") data class HabitEntity(
  @PrimaryKey val id: Long, val name: String, val icon: String /*HabitIcon.name*/,
  val frequency: String /*DAILY|WEEKDAYS|WEEKENDS|CUSTOM|TIMES_PER_WEEK*/,
  val customDays: List<Int> /*Calendar.DAY_OF_WEEK values*/, val targetPerWeek: Int = 0,
  val reminderMinuteOfDay: Int? = null, val colorArgb: Int? = null, val notes: String? = null,
  val sortOrder: Int, val archived: Boolean = false, val createdAtDay: Long)
@Entity(tableName = "habit_completion", primaryKeys = ["habitId","day"],
  foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habitId"], onDelete = CASCADE)],
  indices = [Index("day")]) data class HabitCompletionEntity(val habitId: Long, val day: Long)
@Entity(tableName = "mood_entry") data class MoodEntryEntity(
  @PrimaryKey val id: Long, val score: Int, val note: String?, val timestamp: Long, val tags: List<String> = emptyList())
@Entity(tableName = "water_log", indices = [Index("day")]) data class WaterLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val amountMl: Int, val timestamp: Long, val day: Long)
@Entity(tableName = "step_day") data class StepDayEntity(@PrimaryKey val day: Long, val steps: Int)
@Entity(tableName = "daily_goal") data class DailyGoalEntity(@PrimaryKey val day: Long, val waterGoalMl: Int, val stepGoal: Int)
@Entity(tableName = "profile") data class ProfileEntity(@PrimaryKey val id: Int = 0,
  val name: String, val age: Int?, val gender: String, val wellnessGoal: String, val photoPath: String?)
```
`Converters`: `List<Int>` ↔ comma string, `List<String>` ↔ `\u001F`-separated string.

DAOs (Flows for observation, `suspend` for writes):
- `HabitDao`: `observeActive(): Flow<List<HabitEntity>>` (archived = 0, ordered by `sortOrder`), `getAll(): List<HabitEntity>`, `upsert(vararg h)`, `upsertAll(list)`, `delete(id: Long)`, `observeCompletionsForDay(day: Long): Flow<List<Long>>` (habitIds), `observeAllCompletions(): Flow<List<HabitCompletionEntity>>`, `insertCompletion(c)` (IGNORE), `deleteCompletion(habitId, day)`, `insertCompletions(list)` (REPLACE), `clear()` (habits; completions cascade).
- `MoodDao`: `observeAll(): Flow<List<MoodEntryEntity>>` (timestamp DESC), `upsertAll`, `insert`, `delete(id)`, `clear()`.
- `WaterDao`: `observeTotalForDay(day): Flow<Int>` (`COALESCE(SUM(amountMl),0)`), `totalForDay(day): Int`, `insert(log): Long`, `upsertAll`, `deleteDay(day)`, `clear()`.
- `StepDao`: `observeDay(day): Flow<StepDayEntity?>`, `upsert(s)`, `clear()`.
- `GoalDao`: `upsert(g)`, `clear()`.
- `ProfileDao`: `observe(): Flow<ProfileEntity?>`, `get(): ProfileEntity?`, `upsert(p)`, `clear()`.
- `SyonaDatabase` version 1, `exportSchema = true`, name `syona.db`.

- [ ] **Step 1: Write instrumented tests** in `DaoTest` with `Room.inMemoryDatabaseBuilder`:
  - `deletingHabitCascadesCompletions`: insert habit 1 with completions on days 10, 11 → `delete(1)` → `observeAllCompletions().first()` is empty.
  - `waterTotalSumsOnlyThatDay`: logs 250 (day 5), 500 (day 5), 750 (day 6) → `totalForDay(5) == 750`, `totalForDay(7) == 0`.
  - `completionInsertIsIdempotent`: insert (1, 10) twice → one row.
  - `listConvertersRoundTrip`: habit with `customDays = [1,7]` and a mood with `tags = ["work","sleep, late"]` read back equal.
- [ ] **Step 2:** Implement the entities, converters, DAOs, database and the `AppModule` providers (`@Singleton` DB via `Room.databaseBuilder`, each DAO).
- [ ] **Step 3:** Run `./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`. Expected: success. The schema file `app/schemas/.../1.json` is generated.
- [ ] **Step 4:** If a device is connected (`adb devices` lists one), run `./gradlew connectedDebugAndroidTest`. Expected: 4 pass. Otherwise note "DaoTest not run: no device" in the commit body.
- [ ] **Step 5:** Commit `app/src/main/java/com/example/syona/data/db`, `di/AppModule.kt`, `app/schemas`, `DaoTest.kt`: `feat(data): Room schema and DAOs`

---

### Task 3: SettingsStore (DataStore)

**Files:**
- Create: `data/settings/AppSettings.kt`, `data/settings/SettingsStore.kt`, `data/settings/DataStoreSettingsStore.kt`
- Modify: `di/AppModule.kt`
- Test: `app/src/test/java/com/example/syona/data/settings/DataStoreSettingsStoreTest.kt`, `app/src/test/java/com/example/syona/fakes/InMemorySettingsStore.kt`

**Interfaces (Produces):**
```kotlin
data class AppSettings(
  val onboardingComplete: Boolean = false, val migratedV2: Boolean = false,
  val waterGoalMl: Int = 2000, val remindersEnabled: Boolean = false, val reminderIntervalMinutes: Int = 60,
  val stepEnabled: Boolean = false, val stepGoal: Int = 8000, val stepSensorType: StepSensorType = StepSensorType.AUTO,
  val lastSensorTotal: Float = 0f, val stepCounterDay: Long = 0L,
  val moodViewMode: MoodViewMode = MoodViewMode.LIST, val moodInsightsDismissed: Boolean = false,
  val legacyPerfectBest: Int = 0, val legacyPerfectCurrent: Int = 0, val legacyPerfectLastDay: Long = Long.MIN_VALUE)
interface SettingsStore {
  val settings: Flow<AppSettings>
  suspend fun current(): AppSettings
  suspend fun update(transform: (AppSettings) -> AppSettings)
}
```
`DataStoreSettingsStore(dataStore: DataStore<Preferences>)`: one key per field. Unknown enum names fall back to the defaults above. DataStore file name `settings`.

- [ ] **Step 1: Failing JVM tests** using `PreferenceDataStoreFactory.create(scope = TestScope(...)) { tmpFolder.newFile("t.preferences_pb") }`:
  - `defaultsWhenEmpty`: `current() == AppSettings()`.
  - `updatePersistsAllFields`: update with every field non-default → a new store over the same file returns an equal `AppSettings`.
  - `badEnumFallsBack`: write the raw key `step_sensor_type = "GYRO"` → `current().stepSensorType == AUTO`.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*DataStoreSettingsStoreTest'`. Expected: FAIL (class missing).
- [ ] **Step 3:** Implement the store, `InMemorySettingsStore` (a `MutableStateFlow`-backed fake for later tests) and the DI provider (`PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }`, `@Singleton`).
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(data): DataStore-backed SettingsStore`

---

### Task 4: Domain rules: habit schedule and perfect-day streaks

**Files:**
- Create: `app/src/main/java/com/example/syona/domain/HabitSchedule.kt`, `domain/PerfectDays.kt`
- Modify: `data/model/HabitFrequency.kt` (add `TIMES_PER_WEEK("Times per week")`. Keep `WEEKLY` only so legacy JSON still parses. `shouldShowOnDay(TIMES_PER_WEEK) = true`.)
- Test: `app/src/test/java/com/example/syona/domain/PerfectDaysTest.kt`

**Interfaces (Produces):**
```kotlin
// domain/HabitSchedule.kt
fun HabitEntity.isRequiredOn(day: Long): Boolean
// domain/PerfectDays.kt
data class StreakResult(val current: Int, val best: Int)
object PerfectDays {
  fun perfectDays(habits: List<HabitEntity>, completions: List<HabitCompletionEntity>, from: Long, to: Long): Set<Long>
  fun streak(perfect: Set<Long>, today: Long, legacyCurrent: Int, legacyLastDay: Long, legacyBest: Int): StreakResult
}
```
`streak` rules: the current run ends at `today`, or at `today-1` if today is not yet perfect. Count consecutive perfect days backwards. If the run's first day minus 1 equals `legacyLastDay`, or there is no run and `legacyLastDay` is `today-1` or `today`, add `legacyCurrent`. `best = max(legacyBest, longest run in perfect (with the legacy join applied to the run it joins), current)`.

- [ ] **Step 1: Failing tests** (days as plain Longs; 2026-11-16 = Monday, epochDay `20408`; compute it with `LocalDate.of(2026,11,16).toEpochDay()` in the test):
  - `weekdaysHabitNotRequiredOnSaturday`, `habitNotRequiredBeforeCreated`, `timesPerWeekNeverRequired`, `archivedNeverRequired`.
  - `dayWithNoRequiredHabitsIsNotPerfect`.
  - `perfectRequiresAllRequiredHabits`: habits A, B daily. Day 1 both done, day 2 only A → `perfectDays == {day1}`.
  - `currentStreakAllowsTodayIncomplete`: perfect = {t-3, t-2, t-1} → current 3.
  - `currentStreakBrokenByGap`: perfect = {t-4, t-2, t-1} → current 2, best 2.
  - `legacyStreakJoinsHistory`: legacyCurrent 12, legacyLastDay t-3, perfect {t-2, t-1} → current 14, best 14.
  - `legacyStreakAloneStillCurrentYesterday`: legacyCurrent 5, legacyLastDay t-1, perfect {} → current 5.
  - `legacyBestIsFloor`: legacyBest 30, perfect {t} → best 30, current 1.
  - `deletedHabitRemovedFromHistory`: recompute without habit B → day 2 becomes perfect.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS (plus the existing `HabitFrequencyTest`).
- [ ] **Step 5:** Commit: `feat(domain): habit schedule and perfect-day streak rules`

---

### Task 5: Legacy mapper (pure)

**Files:**
- Create: `app/src/main/java/com/example/syona/data/legacy/LegacyMapper.kt`
- Test: `app/src/test/java/com/example/syona/data/legacy/LegacyMapperTest.kt`

**Interfaces:**
- Consumes: `PreferencesState` + `PreferencesManager.parseState(gson, json)` (existing), the entities (Task 2), `AppSettings` (Task 3).
- Produces:
```kotlin
data class MigrationBundle(
  val habits: List<HabitEntity>, val completions: List<HabitCompletionEntity>, val moods: List<MoodEntryEntity>,
  val waterLogs: List<WaterLogEntity>, val stepDays: List<StepDayEntity>, val goals: List<DailyGoalEntity>,
  val profile: ProfileEntity?, val photoBase64: String?, val settings: (AppSettings) -> AppSettings)
object LegacyMapper { fun map(state: PreferencesState, zone: ZoneId, today: LocalDate): MigrationBundle }
```
Mapping rules:
- `habit.id` is kept. `createdAtDay = min(today, dayOf(habit.id))` (legacy ids are creation millis; use `today` if `id < 1e12`). The frequency rule is in Global Constraints.
- Completed habits get a completion on `dayOf(habitsResetDate)`, or `today` if that is 0.
- Water: one log if `intake > 0`, with `id = -1` (deterministic), `day = dayOf(hydration.lastResetDate)`.
- Steps: one `StepDayEntity` if `stepsToday > 0 && stepCounter.lastResetDate > 0`, on `dayOf(lastResetDate)`.
- Goals: one `DailyGoalEntity` for `today`.
- Profile: `null` if every field is empty. `photoPath` is set later by the migrator.
- Settings transform copies goals, reminders, step prefs (`stepCounterDay = dayOf(lastResetDate)`), mood view prefs and `onboardingComplete`, plus `legacyPerfectBest = longestStreak`, `legacyPerfectCurrent = currentStreak`, `legacyPerfectLastDay = dayOf(lastCompletionDate)` (or `Long.MIN_VALUE` if 0).

- [ ] **Step 1: Failing tests:**
  - `weeklyBecomesTimesPerWeekTarget1`
  - `completedHabitGetsCompletionOnResetDay`: `habitsResetDate` = start of 2026-11-17 → completion day = 2026-11-17 epochDay, not today (11-19).
  - `waterLandsOnItsOwnDayWithFixedId`
  - `zeroIntakeAndZeroStepsProduceNoRows`
  - `moodScoresMapped`: HAPPY→5, ANGRY→1
  - `settingsCarryLegacyStreak`: longest 9, current 4, lastCompletion 11-18 → transform(AppSettings()) has 9 / 4 / epochDay(11-18) and `onboardingComplete` copied
  - `emptyStateMapsToEmptyBundle`
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit: `feat(data): pure legacy → Room mapper`

---

### Task 6: Legacy migrator

**Files:**
- Create: `data/legacy/LegacyMigrator.kt`, `data/legacy/MigrationSink.kt`, `data/legacy/RoomMigrationSink.kt`
- Modify: `di/AppModule.kt`
- Test: `app/src/test/java/com/example/syona/data/legacy/LegacyMigratorTest.kt`

**Interfaces (Produces):**
```kotlin
interface MigrationSink { suspend fun writeAll(bundle: MigrationBundle, photoPath: String?) } // one DB transaction, REPLACE semantics
class LegacyMigrator(
  private val readLegacyJson: () -> String?, private val sink: MigrationSink,
  private val settings: SettingsStore, private val savePhoto: (ByteArray) -> String /*returns path*/,
  private val decodeBase64: (String) -> ByteArray,
  private val zone: () -> ZoneId = { ZoneId.systemDefault() }, private val today: () -> LocalDate = { LocalDate.now() }) {
  sealed interface Result { data object AlreadyDone : Result; data object NothingToMigrate : Result; data object Migrated : Result; data class Failed(val error: Throwable) : Result }
  suspend fun migrateIfNeeded(): Result
}
```
Order: if `migratedV2`, return `AlreadyDone`. If the JSON is null, set `migratedV2 = true` and return `NothingToMigrate`. Otherwise parse → map → save the decoded photo (on decode failure use a null photo, don't fail) → `sink.writeAll` → `settings.update { bundle.settings(it).copy(migratedV2 = true) }`. Any exception returns `Failed` and leaves `migratedV2 = false`. `RoomMigrationSink` uses `db.withTransaction` with REPLACE upserts. The production `readLegacyJson` reads `syona_prefs`/`wellness_state`. `savePhoto` writes `filesDir/profile.jpg`. `decodeBase64` is `android.util.Base64.decode(s, Base64.DEFAULT)` in production and `java.util.Base64.getMimeDecoder()::decode` in JVM tests (`java.util.Base64` is not covered by desugaring, so it must not be used in app code).

- [ ] **Step 1: Failing tests** with a recording fake sink and `InMemorySettingsStore`:
  - `alreadyMigratedDoesNothing`
  - `noLegacyDataMarksDone`
  - `migratesAndSetsFlagAfterWrite`: the sink saw the bundle; the flag is true afterwards; the legacy streak was copied.
  - `sinkFailureLeavesFlagUnset`: sink throws → `Failed`, `migratedV2 == false`.
  - `rerunAfterCrashIsIdempotent`: the first run's sink writes and then the flag update is skipped (simulate by resetting the flag) → rerun → the fake sink (keyed by entity ids, REPLACE) holds exactly 1 water log and N habits, no duplicates.
  - `corruptPhotoDoesNotBlockMigration`
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS.
- [ ] **Step 5:** Commit: `feat(data): one-shot legacy migration`

---

### Task 7: WellnessRepository as a facade over Room/DataStore

**Files:**
- Modify: `app/src/main/java/com/example/syona/data/repository/WellnessRepository.kt` (rewrite internals, **same public API**)
- Create: `app/src/test/java/com/example/syona/fakes/FakeDaos.kt` (in-memory implementations of all six DAO interfaces; `MutableStateFlow`-backed)
- Test: `app/src/test/java/com/example/syona/data/repository/WellnessRepositoryTest.kt`

**Interfaces:**
- Consumes: the DAOs (Task 2), `SettingsStore` (Task 3), `PerfectDays` (Task 4).
- Produces: `class WellnessRepository @Inject constructor(habitDao, moodDao, waterDao, stepDao, goalDao, profileDao, settings: SettingsStore, @ApplicationScope scope: CoroutineScope, clock: Clock)`.
  - **Every public member of today's class is kept with the same signature** (`observe*()`, `hydrationState`, `refreshAll()`, `addHabit`, `updateHabit`, `toggleHabitCompletion`, `reorderHabits`, `deleteHabit`, `addMoodEntry`, `deleteMoodEntry`, `setMoodViewMode`, `setMoodInsightsDismissed`, `updateHydrationState`, `addWaterIntake`, `updateHydrationGoal`, `updateHydrationReminders`, `resetDailyHydration`, `updateProfile`, `clearAllData`, `setStepCounterEnabled`, `setStepCounterGoal`, `setStepSensorType`, `resetTodaySteps`, `applyStepCounterReading`, `incrementStepDetector`, `isOnboardingComplete`, `setOnboardingComplete`, `updateHabitsResetDate` (now a no-op, kept for callers)).
  - New: `suspend fun awaitReady()` (completes once settings are loaded and the first DB emissions have arrived), `suspend fun hydrationSnapshot(): HydrationState` (for the worker).
  - Add `@ApplicationScope` qualifier + provider (`SupervisorJob() + Dispatchers.Default`) and `Clock` (`java.time.Clock.systemDefaultZone()`) in `AppModule`.
- Behaviour:
  - `today: MutableStateFlow<Long>`, updated by `refreshAll()`.
  - `observeHabits()` = active habits ⨝ today's completions → legacy `Habit` model (`completed` flag, `frequency` via `HabitFrequency.valueOf`) filtered by `shouldShowToday`.
  - Hydration = settings + `observeTotalForDay(today)` (`lastResetDate` = start of today).
  - Stats: `longestStreak`/`currentStreak` from `PerfectDays` over all completions + legacy fields.
  - Writes run in `scope` but **update the in-memory StateFlow synchronously first** (sync callers like the step service and the UI see the change immediately), then persist through a single-writer `Mutex`.
  - `isOnboardingComplete()` reads the in-memory settings snapshot (loaded in `awaitReady`).
  - `clearAllData()` clears every table and resets settings to `AppSettings(onboardingComplete = <kept>, migratedV2 = true)`.
  - The step logic (reset per day, delta rules) is identical to today's, but steps persist to `StepDayEntity(today)` and the sensor fields to settings.
  - Writing the hydration goal or step goal also upserts `DailyGoalEntity(today)`.

- [ ] **Step 1: Failing tests** (`runTest` with `StandardTestDispatcher`; scope = `backgroundScope`; fixed `Clock` you can advance):
  - `addHabitAppearsAndToggleWritesTodayCompletion`
  - `toggleTwiceRemovesCompletion`
  - `midnightRolloverShowsHabitsUnchecked`: tick a habit, advance the clock 1 day, `refreshAll()` → `completed == false`; the old day's completion still exists in the fake DAO.
  - `waterAddsAndResetsPerDay`: add 250+500 → intake 750; next day → 0; `resetDailyHydration()` deletes only today's logs.
  - `goalChangeWritesDailyGoalRow`
  - `stepReadingDeltaAndRebootReset`: readings 1000 (baseline, delta 0), 1100 → 100 steps; 50 (reboot) → still 100; 80 → 130.
  - `clearAllDataKeepsOnboardingAndMigrationFlag`
  - `perfectDayStreakInStats`: one daily habit completed on t-1 and t → `stats.currentStreak == 2`, `longestStreak >= 2`.
  - `deleteHabitRemovesFromTodayAndStats`
  - `moodAddDeleteNewestFirst`
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS, plus all earlier tests.
- [ ] **Step 5:** Commit: `refactor(data): WellnessRepository backed by Room + DataStore (API unchanged)`

---

### Task 8: Wire it into the app and remove the legacy runtime path

**Files:**
- Modify: `SyonaApp.kt`, `service/HydrationReminderWorker.kt`, `receivers/BootReceiver.kt`, `MainActivity.kt` (only if needed for `awaitReady`), `ui/profile/ProfileFragment.kt` (photo now loads from `ProfileData.photoUri` = file path; keep the Base64 fallback for safety), `data/preferences/PreferencesManager.kt` (keep only `parseState` + reading the raw JSON; delete write methods)
- Test: `app/src/test/java/com/example/syona/ManifestPolicyTest.kt`

**Interfaces:**
- Consumes: `LegacyMigrator.migrateIfNeeded()`, `WellnessRepository.awaitReady()`, `hydrationSnapshot()`.

Startup: in `SyonaApp.onCreate`, after Hilt injection, call `runBlocking { migrator.migrateIfNeeded(); repository.awaitReady() }`. This is one-time and small, and is needed because Onboarding/Main read `isOnboardingComplete()` synchronously. Comment that phase 8 replaces it with a SplashScreen keep-on-screen condition. On `Result.Failed`, log it and continue with the legacy blob untouched. The flag stays false, so the next launch retries. (The spec's error screen with "Export raw data" is delivered in phase 3 alongside export. Note it in the spec status.)

The profile photo: when saving a new photo, `ProfileFragment` writes the JPEG to `filesDir/profile.jpg` and stores the path in `ProfileData.photoUri`. Loading tries `BitmapFactory.decodeFile(path)`, then the Base64 fallback.

- [ ] **Step 1: Failing test** `ManifestPolicyTest`: parse `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml` (locate it via a glob under `app/build/intermediates/merged_manifest*`) and assert that no `uses-permission` is `android.permission.INTERNET`. Make `testDebugUnitTest` depend on `processDebugMainManifest` in Gradle. (Already passes. It guards the "no network" promise.)
- [ ] **Step 2:** Implement the wiring. The worker uses `(applicationContext as SyonaApp).repository.hydrationSnapshot()`.
- [ ] **Step 3:** Run `./gradlew clean assembleDebug assembleRelease testDebugUnitTest lintDebug`. Expected: all green, 0 lint errors.
- [ ] **Step 4: Device smoke (if a device is available; otherwise list it as not run):** install the **old** APK (build it from the baseline commit) → create 2 habits (tick 1), 2 moods, 250 ml water, a profile with a photo → install the new APK over it → every value is present, the photo shows, the ticked habit is still ticked today, and a relaunch doesn't duplicate anything.
- [ ] **Step 5:** Commit: `feat: run migration at startup; app now on Room/DataStore`

---

## After this plan
Update `ANDROID_APP_AUDIT_AND_IMPROVEMENT_PLAN.md` / the spec status with what shipped, then plan Phase 3 (Compose theme, Settings, backup/restore/export, migration-failure screen, backup rules).
