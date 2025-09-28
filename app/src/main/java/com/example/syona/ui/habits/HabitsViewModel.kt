package com.example.syona.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.syona.data.model.Habit
import com.example.syona.data.repository.WellnessRepository
import kotlinx.coroutines.flow.StateFlow

class HabitsViewModel(private val repository: WellnessRepository) : ViewModel() {

    val habits: StateFlow<List<Habit>> = repository.observeHabits()

    val stats = repository.observeStats()

    fun toggleCompletion(habit: Habit) {
        repository.toggleHabitCompletion(habit.id)
    }

    fun saveHabit(habit: Habit) {
        val existing = habits.value.firstOrNull { it.id == habit.id }
        if (existing == null) {
            repository.addHabit(habit.name, habit.icon, habit.frequency.displayName, habit.notes)
        } else {
            repository.updateHabit(habit)
        }
    }

    fun deleteHabit(habit: Habit) {
        repository.deleteHabit(habit.id)
    }

    fun reorder(from: Int, to: Int) {
        repository.reorderHabits(from, to)
    }
}

class HabitsViewModelFactory(private val repository: WellnessRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HabitsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
