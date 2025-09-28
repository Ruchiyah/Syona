package com.example.syona.ui.hydration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.syona.data.model.HydrationState
import com.example.syona.data.repository.WellnessRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HydrationViewModel(private val repository: WellnessRepository) : ViewModel() {

    val hydrationState: StateFlow<HydrationState> = repository.hydrationState

    fun addWaterIntake(amount: Int) {
        viewModelScope.launch {
            repository.addWaterIntake(amount)
        }
    }

    fun updateReminderSettings(enabled: Boolean, intervalMinutes: Int) {
        viewModelScope.launch {
            repository.updateHydrationReminders(enabled, intervalMinutes)
        }
    }

    fun resetDailyIntake() {
        viewModelScope.launch {
            repository.resetDailyHydration()
        }
    }

    fun updateDailyGoal(goalMl: Int) {
        viewModelScope.launch {
            repository.updateHydrationGoal(goalMl)
        }
    }
}

class HydrationViewModelFactory(private val repository: WellnessRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HydrationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HydrationViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}