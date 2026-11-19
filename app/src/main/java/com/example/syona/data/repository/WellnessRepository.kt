package com.example.syona.data.repository

import android.util.Log
import com.example.syona.data.db.DailyGoalEntity
import com.example.syona.data.db.GoalDao
import com.example.syona.data.db.HabitCompletionEntity
import com.example.syona.data.db.HabitDao
import com.example.syona.data.db.HabitEntity
import com.example.syona.data.db.MoodDao
import com.example.syona.data.db.MoodEntryEntity
import com.example.syona.data.db.ProfileDao
import com.example.syona.data.db.ProfileEntity
import com.example.syona.data.db.StepDao
import com.example.syona.data.db.StepDayEntity
import com.example.syona.data.db.WaterDao
import com.example.syona.data.db.WaterLogEntity
import com.example.syona.data.model.Habit
import com.example.syona.data.model.HabitFrequency
import com.example.syona.data.model.HabitIcon
import com.example.syona.data.model.HydrationState
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodPreferences
import com.example.syona.data.model.MoodType
import com.example.syona.data.model.MoodViewMode
import com.example.syona.data.model.ProfileData
import com.example.syona.data.model.StepCounterPreferences
import com.example.syona.data.model.StepSensorType
import com.example.syona.data.model.WellnessStats
import com.example.syona.data.settings.AppSettings
import com.example.syona.data.settings.SettingsStore
import com.example.syona.di.ApplicationScope
import com.example.syona.domain.PerfectDays
import com.example.syona.domain.StreakResult
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Facade over Room + DataStore with the same public API the UI/services used before.
 *
 * After [awaitReady] has loaded everything once, the in-memory [State] is the source of truth:
 * every write updates it (and the public StateFlows) synchronously under [lock], then enqueues
 * the matching DB/DataStore write on a single ordered writer coroutine.
 */
@Singleton
class WellnessRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val moodDao: MoodDao,
    private val waterDao: WaterDao,
    private val stepDao: StepDao,
    private val goalDao: GoalDao,
    private val profileDao: ProfileDao,
    private val settings: SettingsStore,
    @ApplicationScope private val scope: CoroutineScope,
    private val clock: Clock
) {
    private data class State(
        val settings: AppSettings = AppSettings(),
        val habits: List<HabitEntity> = emptyList(), // active, ordered by sortOrder, id
        val completions: Set<HabitCompletionEntity> = emptySet(), // all days
        val moods: List<MoodEntryEntity> = emptyList(), // newest first
        val waterToday: Int = 0,
        val stepsToday: Int = 0, // steps of settings.stepCounterDay
        val profile: ProfileEntity? = null
    )

    private data class StreakKey(
        val habits: List<HabitEntity>,
        val completions: Set<HabitCompletionEntity>,
        val today: Long,
        val settings: AppSettings
    )

    private val lock = Any()

    @Volatile
    private var state = State()
    private var waterGeneration = 0L
    private var streakKey: StreakKey? = null
    private var streakCache = StreakResult(0, 0)

    private val today = MutableStateFlow(currentDay())

    // Single writer: writes run one at a time, in exactly the order they were enqueued.
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val ready = scope.async(start = CoroutineStart.LAZY) { load() }

    private val habitsFlow = MutableStateFlow<List<Habit>>(emptyList())
    private val moodFlow = MutableStateFlow<List<MoodEntry>>(emptyList())
    private val moodPreferencesFlow = MutableStateFlow(MoodPreferences())
    private val hydrationFlow = MutableStateFlow(HydrationState())
    private val profileFlow = MutableStateFlow(ProfileData())
    private val statsFlow = MutableStateFlow(WellnessStats())
    private val stepCounterFlow = MutableStateFlow(StepCounterPreferences())

    val hydrationState: StateFlow<HydrationState> = hydrationFlow.asStateFlow()

    init {
        scope.launch {
            for (write in writes) {
                try {
                    write()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Persisting change failed", e)
                }
            }
        }
        publish()
    }

    /** Completes once settings and the initial DB contents have been loaded into memory. */
    suspend fun awaitReady() = ready.await()

    /** Current hydration state, for callers outside the UI (e.g. the reminder worker). */
    suspend fun hydrationSnapshot(): HydrationState {
        awaitReady()
        refreshAll() // the process may have outlived midnight; no-op on the same day
        return hydrationFlow.value
    }

    private suspend fun load() {
        val day = currentDay()
        var loaded = settings.current()
        val rollSteps = loaded.stepCounterDay < day
        if (rollSteps) loaded = loaded.copy(lastSensorTotal = 0f, stepCounterDay = day)
        val newState = State(
            settings = loaded,
            habits = habitDao.observeActive().first(),
            completions = habitDao.observeAllCompletions().first().toSet(),
            moods = moodDao.observeAll().first(),
            waterToday = waterDao.totalForDay(day),
            stepsToday = stepDao.observeDay(loaded.stepCounterDay).first()?.steps ?: 0,
            profile = profileDao.get()
        )
        synchronized(lock) {
            today.value = day
            set(newState)
            if (rollSteps) enqueue { settings.update { it.copy(lastSensorTotal = 0f, stepCounterDay = day) } }
        }
    }

    /** Rolls the in-memory "today" over to the clock's current day (midnight, app resume). */
    fun refreshAll() {
        val day = currentDay()
        synchronized(lock) {
            if (day == today.value) return
            today.value = day
            val generation = ++waterGeneration
            var next = state.copy(waterToday = 0)
            if (next.settings.stepCounterDay < day) {
                next = next.copy(
                    stepsToday = 0,
                    settings = next.settings.copy(lastSensorTotal = 0f, stepCounterDay = day)
                )
                enqueue { settings.update { it.copy(lastSensorTotal = 0f, stepCounterDay = day) } }
            }
            set(next)
            enqueue {
                val total = waterDao.totalForDay(day)
                synchronized(lock) {
                    if (today.value == day && waterGeneration == generation && total != state.waterToday) {
                        set(state.copy(waterToday = total))
                    }
                }
            }
        }
    }

    fun observeHabits(): StateFlow<List<Habit>> = habitsFlow.asStateFlow()

    fun observeMoodEntries(): StateFlow<List<MoodEntry>> = moodFlow.asStateFlow()

    fun observeMoodPreferences(): StateFlow<MoodPreferences> = moodPreferencesFlow.asStateFlow()

    fun observeHydration(): StateFlow<HydrationState> = hydrationFlow.asStateFlow()

    fun observeProfile(): StateFlow<ProfileData> = profileFlow.asStateFlow()

    fun observeStats(): StateFlow<WellnessStats> = statsFlow.asStateFlow()

    fun observeStepCounter(): StateFlow<StepCounterPreferences> = stepCounterFlow.asStateFlow()

    // ---- Habits ----

    fun addHabit(name: String, icon: HabitIcon, frequency: String, customDays: List<Int>?, notes: String?) {
        synchronized(lock) {
            val s = state
            var id = clock.millis()
            while (s.habits.any { it.id == id }) id++
            val habit = HabitEntity(
                id = id,
                name = name,
                icon = icon.name,
                frequency = HabitFrequency.DAILY.name,
                customDays = customDays.orEmpty(),
                notes = notes,
                sortOrder = (s.habits.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                createdAtDay = today.value
            ).withFrequency(HabitFrequency.fromDisplayName(frequency))
            set(s.copy(habits = s.habits + habit))
            enqueue { habitDao.upsert(habit) }
        }
    }

    /** True if any stored active habit has [id], whether or not it is scheduled today. */
    fun hasHabit(id: Long): Boolean = state.habits.any { it.id == id }

    fun updateHabit(updated: Habit) {
        synchronized(lock) {
            val s = state
            val existing = s.habits.firstOrNull { it.id == updated.id } ?: return
            val habit = existing.copy(
                name = updated.name,
                icon = updated.icon.name,
                customDays = updated.customDays.orEmpty(),
                notes = updated.notes
            ).withFrequency(updated.frequency)
            set(s.copy(habits = s.habits.map { if (it.id == habit.id) habit else it }))
            enqueue { habitDao.upsert(habit) }
        }
    }

    fun toggleHabitCompletion(habitId: Long) {
        synchronized(lock) {
            val s = state
            if (s.habits.none { it.id == habitId }) return
            val completion = HabitCompletionEntity(habitId, today.value)
            if (completion in s.completions) {
                set(s.copy(completions = s.completions - completion))
                enqueue { habitDao.deleteCompletion(completion.habitId, completion.day) }
            } else {
                set(s.copy(completions = s.completions + completion))
                enqueue { habitDao.insertCompletion(completion) }
            }
        }
    }

    /** Positions refer to today's visible list; hidden habits keep their relative slots. */
    fun reorderHabits(fromPosition: Int, toPosition: Int) {
        synchronized(lock) {
            val visible = habitsFlow.value.map { it.id }.toMutableList()
            if (fromPosition !in visible.indices || toPosition !in visible.indices) return
            visible.add(toPosition, visible.removeAt(fromPosition))
            val s = state
            val byId = s.habits.associateBy { it.id }
            val visibleIds = visible.toSet()
            val nextVisible = visible.iterator()
            val reordered = s.habits
                .map { if (it.id in visibleIds) byId.getValue(nextVisible.next()) else it }
                .mapIndexed { index, habit -> habit.copy(sortOrder = index) }
            val changed = reordered.filter { it != byId[it.id] }
            if (changed.isEmpty()) return
            set(s.copy(habits = reordered))
            enqueue { habitDao.upsertAll(changed) }
        }
    }

    fun deleteHabit(habitId: Long) {
        synchronized(lock) {
            val s = state
            set(
                s.copy(
                    habits = s.habits.filterNot { it.id == habitId },
                    completions = s.completions.filterNot { it.habitId == habitId }.toSet()
                )
            )
            enqueue { habitDao.delete(habitId) } // completions go via ON DELETE CASCADE
        }
    }

    // ---- Mood ----

    fun addMoodEntry(moodType: MoodType, note: String?) {
        val now = clock.millis()
        synchronized(lock) {
            val s = state
            var id = now
            while (s.moods.any { it.id == id }) id++
            val entry = MoodEntryEntity(id = id, score = moodType.score, note = note, timestamp = now)
            set(s.copy(moods = (s.moods + entry).sortedByDescending { it.timestamp }))
            enqueue { moodDao.insert(entry) }
            if (s.settings.moodInsightsDismissed) updateSettings { it.copy(moodInsightsDismissed = false) }
        }
    }

    fun deleteMoodEntry(entryId: Long) {
        synchronized(lock) {
            val s = state
            val remaining = s.moods.filterNot { it.id == entryId }
            set(s.copy(moods = remaining))
            enqueue { moodDao.delete(entryId) }
            if (remaining.isEmpty() && s.settings.moodInsightsDismissed) {
                updateSettings { it.copy(moodInsightsDismissed = false) }
            }
        }
    }

    fun setMoodViewMode(mode: MoodViewMode) = updateSettings { it.copy(moodViewMode = mode) }

    fun setMoodInsightsDismissed(dismissed: Boolean) =
        updateSettings { it.copy(moodInsightsDismissed = dismissed) }

    // ---- Hydration ----

    fun updateHydrationState(update: (HydrationState) -> HydrationState) {
        synchronized(lock) {
            val old = hydrationFlow.value
            val new = update(old)
            setWaterIntake(new.intake)
            updateSettings(writeGoalRow = new.dailyGoal != old.dailyGoal) {
                it.copy(
                    waterGoalMl = new.dailyGoal,
                    remindersEnabled = new.remindersEnabled,
                    reminderIntervalMinutes = new.reminderIntervalMinutes
                )
            }
        }
    }

    fun addWaterIntake(amount: Int) = updateHydrationState { it.copy(intake = it.intake + amount) }

    fun updateHydrationGoal(goalMl: Int) = updateHydrationState { it.copy(dailyGoal = goalMl) }

    fun updateHydrationReminders(enabled: Boolean, intervalMinutes: Int) = updateHydrationState {
        it.copy(remindersEnabled = enabled, reminderIntervalMinutes = intervalMinutes)
    }

    fun resetDailyHydration() = updateHydrationState { it.copy(intake = 0) }

    /** Must hold [lock]. Increases are logged as a new entry; decreases rewrite today's logs. */
    private fun setWaterIntake(intake: Int) {
        val old = state.waterToday
        if (intake == old) return
        val day = today.value
        val now = clock.millis()
        waterGeneration++
        set(state.copy(waterToday = intake))
        if (intake > old) {
            enqueue { waterDao.insert(WaterLogEntity(amountMl = intake - old, timestamp = now, day = day)) }
        } else {
            val log = if (intake > 0) WaterLogEntity(amountMl = intake, timestamp = now, day = day) else null
            enqueue { waterDao.replaceDay(day, log) }
        }
    }

    // ---- Profile / app ----

    fun updateProfile(profileData: ProfileData) {
        synchronized(lock) {
            val entity = profileData.toEntity()
            set(state.copy(profile = entity))
            enqueue { profileDao.upsert(entity) }
        }
    }

    fun clearAllData() {
        synchronized(lock) {
            val fresh = AppSettings(onboardingComplete = state.settings.onboardingComplete, migratedV2 = true)
            waterGeneration++
            set(State(settings = fresh))
            enqueue {
                habitDao.clear() // completions go via ON DELETE CASCADE
                moodDao.clear()
                waterDao.clear()
                stepDao.clear()
                goalDao.clear()
                profileDao.clear()
                settings.update { fresh }
            }
        }
    }

    fun isOnboardingComplete(): Boolean = state.settings.onboardingComplete

    fun setOnboardingComplete(value: Boolean) = updateSettings { it.copy(onboardingComplete = value) }

    /** No-op: habit completions are stored per day, so there is nothing to reset. Kept for callers. */
    fun updateHabitsResetDate() = Unit

    // ---- Steps ----

    fun setStepCounterEnabled(enabled: Boolean) = updateSettings { it.copy(stepEnabled = enabled) }

    fun setStepCounterGoal(goal: Int) {
        val safeGoal = goal.coerceAtLeast(1000)
        updateSettings(writeGoalRow = true) { it.copy(stepGoal = safeGoal) }
    }

    fun setStepSensorType(type: StepSensorType) = updateSettings { it.copy(stepSensorType = type) }

    fun resetTodaySteps() = updateSteps { day -> StepState(steps = 0, lastSensorTotal = 0f, day = day) }

    fun applyStepCounterReading(totalSteps: Float) = updateSteps { day ->
        val start = if (this.day < day) StepState(0, totalSteps, day) else this
        val previousTotal = start.lastSensorTotal
        val delta = when {
            totalSteps < previousTotal -> 0
            previousTotal == 0f -> 0
            else -> (totalSteps - previousTotal).toInt()
        }
        start.copy(steps = (start.steps + delta).coerceAtLeast(0), lastSensorTotal = totalSteps)
    }

    fun incrementStepDetector(count: Int) {
        if (count <= 0) return
        updateSteps { day ->
            val start = if (this.day < day) copy(steps = 0, day = day) else this
            start.copy(steps = (start.steps + count).coerceAtLeast(0))
        }
    }

    private data class StepState(val steps: Int, val lastSensorTotal: Float, val day: Long)

    private fun updateSteps(transform: StepState.(today: Long) -> StepState) {
        synchronized(lock) {
            val s = state
            val current = StepState(s.stepsToday, s.settings.lastSensorTotal, s.settings.stepCounterDay)
            val next = current.transform(currentDay())
            if (next == current) return
            val sensor: (AppSettings) -> AppSettings =
                { it.copy(lastSensorTotal = next.lastSensorTotal, stepCounterDay = next.day) }
            set(s.copy(stepsToday = next.steps, settings = sensor(s.settings)))
            enqueue {
                stepDao.upsert(StepDayEntity(next.day, next.steps))
                settings.update(sensor)
            }
        }
    }

    // ---- Internals ----

    /** [transform] must be pure: it is applied to memory now and to the store later. */
    private fun updateSettings(writeGoalRow: Boolean = false, transform: (AppSettings) -> AppSettings) {
        synchronized(lock) {
            val next = transform(state.settings)
            if (next == state.settings && !writeGoalRow) return
            set(state.copy(settings = next))
            val day = today.value
            enqueue {
                settings.update(transform)
                if (writeGoalRow) goalDao.upsert(DailyGoalEntity(day, next.waterGoalMl, next.stepGoal))
            }
        }
    }

    private fun enqueue(write: suspend () -> Unit) {
        writes.trySend(write)
    }

    /** Must hold [lock]. Replaces the state and republishes every public flow. */
    private fun set(next: State) {
        state = next
        publish()
    }

    private fun publish() {
        val s = state
        val day = today.value
        val doneToday = s.completions.filter { it.day == day }.map { it.habitId }.toSet()
        val habits = s.habits.filter { it.isShownOn(day) }.map { it.toHabit(it.id in doneToday) }
        val moods = s.moods.map { it.toMoodEntry() }
        val hydration = HydrationState(
            dailyGoal = s.settings.waterGoalMl,
            intake = s.waterToday,
            remindersEnabled = s.settings.remindersEnabled,
            reminderIntervalMinutes = s.settings.reminderIntervalMinutes,
            lastResetDate = startOfDayMillis(day)
        )
        val steps = StepCounterPreferences(
            enabled = s.settings.stepEnabled,
            dailyGoal = s.settings.stepGoal,
            sensorType = s.settings.stepSensorType,
            stepsToday = s.stepsToday,
            lastSensorTotal = s.settings.lastSensorTotal,
            lastResetDate = startOfDayMillis(s.settings.stepCounterDay)
        )
        val streak = streak(s, day)
        val recentMoods = moods.take(30)
        habitsFlow.value = habits
        moodFlow.value = moods
        moodPreferencesFlow.value = MoodPreferences(s.settings.moodViewMode, s.settings.moodInsightsDismissed)
        hydrationFlow.value = hydration
        profileFlow.value = s.profile.toProfileData()
        stepCounterFlow.value = steps
        statsFlow.value = WellnessStats(
            habitsCompletedToday = habits.count { it.completed },
            totalHabits = habits.size,
            currentStreak = streak.current,
            longestStreak = streak.best,
            averageMoodScore = if (recentMoods.isEmpty()) 0.0 else recentMoods.sumOf { it.mood.score }.toDouble() / recentMoods.size,
            hydrationPercentage = if (hydration.dailyGoal == 0) 0
            else (hydration.intake * 100 / hydration.dailyGoal).coerceIn(0, 100),
            stepsToday = steps.stepsToday,
            stepsGoal = steps.dailyGoal
        )
    }

    /** Perfect-day streak; cached because publish() runs on every step-sensor event. */
    private fun streak(s: State, day: Long): StreakResult {
        val legacy = AppSettings(
            legacyPerfectBest = s.settings.legacyPerfectBest,
            legacyPerfectCurrent = s.settings.legacyPerfectCurrent,
            legacyPerfectLastDay = s.settings.legacyPerfectLastDay
        )
        val key = StreakKey(s.habits, s.completions, day, legacy)
        if (key == streakKey) return streakCache
        val from = s.completions.minOfOrNull { it.day }?.coerceAtMost(day) ?: day
        val perfect = PerfectDays.perfectDays(s.habits, s.completions.toList(), from, day)
        streakCache = PerfectDays.streak(
            perfect, day, legacy.legacyPerfectCurrent, legacy.legacyPerfectLastDay, legacy.legacyPerfectBest
        )
        streakKey = key
        return streakCache
    }

    private fun currentDay(): Long = LocalDate.now(clock).toEpochDay()

    private fun startOfDayMillis(day: Long): Long =
        LocalDate.ofEpochDay(day).atStartOfDay(clock.zone).toInstant().toEpochMilli()

    private companion object {
        const val TAG = "WellnessRepository"
    }
}
