package com.example.syona.fakes

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeHabitDao : HabitDao {
    val habits = MutableStateFlow<Map<Long, HabitEntity>>(emptyMap())
    val completions = MutableStateFlow<Set<HabitCompletionEntity>>(emptySet())

    private fun sorted(list: Collection<HabitEntity>) =
        list.sortedWith(compareBy<HabitEntity> { it.sortOrder }.thenBy { it.id })

    override fun observeActive(): Flow<List<HabitEntity>> =
        habits.map { m -> sorted(m.values.filterNot { it.archived }) }

    override suspend fun getAll(): List<HabitEntity> = sorted(habits.value.values)

    override suspend fun upsert(vararg h: HabitEntity) = upsertAll(h.toList())

    override suspend fun upsertAll(list: List<HabitEntity>) {
        habits.update { m -> m + list.associateBy { it.id } }
    }

    override suspend fun insertAllIfAbsent(list: List<HabitEntity>) {
        habits.update { m -> list.associateBy { it.id } + m }
    }

    // Mirrors Room's ON DELETE CASCADE on habit_completion.
    override suspend fun delete(id: Long) {
        habits.update { it - id }
        completions.update { s -> s.filterNot { it.habitId == id }.toSet() }
    }

    override fun observeCompletionsForDay(day: Long): Flow<List<Long>> =
        completions.map { s -> s.filter { it.day == day }.map { it.habitId } }

    override fun observeAllCompletions(): Flow<List<HabitCompletionEntity>> =
        completions.map { it.toList() }

    override suspend fun insertCompletion(c: HabitCompletionEntity) {
        completions.update { it + c }
    }

    override suspend fun deleteCompletion(habitId: Long, day: Long) {
        completions.update { it - HabitCompletionEntity(habitId, day) }
    }

    override suspend fun insertCompletions(list: List<HabitCompletionEntity>) {
        completions.update { it + list }
    }

    override suspend fun clear() {
        habits.value = emptyMap()
        completions.value = emptySet()
    }
}

class FakeMoodDao : MoodDao {
    val entries = MutableStateFlow<Map<Long, MoodEntryEntity>>(emptyMap())

    override fun observeAll(): Flow<List<MoodEntryEntity>> =
        entries.map { m -> m.values.sortedByDescending { it.timestamp } }

    override suspend fun upsertAll(list: List<MoodEntryEntity>) {
        entries.update { m -> m + list.associateBy { it.id } }
    }

    override suspend fun insert(entry: MoodEntryEntity) = upsertAll(listOf(entry))

    override suspend fun insertAllIfAbsent(list: List<MoodEntryEntity>) {
        entries.update { m -> list.associateBy { it.id } + m }
    }

    override suspend fun delete(id: Long) {
        entries.update { it - id }
    }

    override suspend fun clear() {
        entries.value = emptyMap()
    }
}

class FakeWaterDao : WaterDao {
    val logs = MutableStateFlow<List<WaterLogEntity>>(emptyList())
    private var nextId = 1L

    override fun observeTotalForDay(day: Long): Flow<Int> =
        logs.map { l -> l.filter { it.day == day }.sumOf { it.amountMl } }

    override suspend fun totalForDay(day: Long): Int =
        logs.value.filter { it.day == day }.sumOf { it.amountMl }

    override suspend fun insert(log: WaterLogEntity): Long {
        val id = if (log.id == 0L) nextId++ else log.id
        logs.update { it + log.copy(id = id) }
        return id
    }

    override suspend fun upsertAll(list: List<WaterLogEntity>) {
        val ids = list.map { it.id }.toSet()
        logs.update { l -> l.filterNot { it.id in ids } + list }
    }

    override suspend fun insertAllIfAbsent(list: List<WaterLogEntity>) {
        val ids = logs.value.map { it.id }.toSet()
        logs.update { l -> l + list.filterNot { it.id in ids } }
    }

    override suspend fun deleteDay(day: Long) {
        logs.update { l -> l.filterNot { it.day == day } }
    }

    // replaceDay uses the interface default (deleteDay + insert); no transaction needed in-memory.

    override suspend fun clear() {
        logs.value = emptyList()
    }
}

class FakeStepDao : StepDao {
    val days = MutableStateFlow<Map<Long, StepDayEntity>>(emptyMap())

    override fun observeDay(day: Long): Flow<StepDayEntity?> = days.map { it[day] }

    override suspend fun get(day: Long): StepDayEntity? = days.value[day]

    override suspend fun upsert(s: StepDayEntity) {
        days.update { it + (s.day to s) }
    }

    override suspend fun clear() {
        days.value = emptyMap()
    }
}

class FakeGoalDao : GoalDao {
    val goals = MutableStateFlow<Map<Long, DailyGoalEntity>>(emptyMap())

    override suspend fun upsert(g: DailyGoalEntity) {
        goals.update { it + (g.day to g) }
    }

    override suspend fun insertAllIfAbsent(list: List<DailyGoalEntity>) {
        goals.update { m -> list.associateBy { it.day } + m }
    }

    override suspend fun clear() {
        goals.value = emptyMap()
    }
}

class FakeProfileDao : ProfileDao {
    val profile = MutableStateFlow<ProfileEntity?>(null)

    override fun observe(): Flow<ProfileEntity?> = profile

    override suspend fun get(): ProfileEntity? = profile.value

    override suspend fun upsert(p: ProfileEntity) {
        profile.value = p
    }

    override suspend fun insertIfAbsent(p: ProfileEntity) {
        if (profile.value == null) profile.value = p
    }

    override suspend fun clear() {
        profile.value = null
    }
}
