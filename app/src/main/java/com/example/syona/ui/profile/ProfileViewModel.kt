package com.example.syona.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.syona.data.model.ProfileData
import com.example.syona.data.model.StepSensorType
import com.example.syona.data.repository.WellnessRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val repository: WellnessRepository) : ViewModel() {

    val profileData: StateFlow<ProfileData> = repository.observeProfile()
    
    val stats = repository.observeStats()

    val stepCounter = repository.observeStepCounter()

    fun updateProfile(
        name: String,
        age: Int?,
        gender: String,
        wellnessGoal: String,
        photoUri: String?
    ) {
        viewModelScope.launch {
            val updatedProfile = ProfileData(
                name = name,
                age = age,
                gender = gender,
                wellnessGoal = wellnessGoal,
                photoUri = photoUri
            )
            repository.updateProfile(updatedProfile)
        }
    }

    fun clearProfile() {
        viewModelScope.launch {
            repository.updateProfile(ProfileData())
        }
    }

    fun setStepCounterEnabled(enabled: Boolean) {
        repository.setStepCounterEnabled(enabled)
    }

    fun setStepCounterGoal(goal: Int) {
        repository.setStepCounterGoal(goal)
    }

    fun setStepSensorType(type: StepSensorType) {
        repository.setStepSensorType(type)
    }

    fun resetTodaySteps() {
        repository.resetTodaySteps()
    }

    fun recordStepCounter(totalSteps: Float) {
        repository.applyStepCounterReading(totalSteps)
    }

    val moodEntries = repository.observeMoodEntries()

    fun getMoodDataForRange(range: String): List<Pair<Long, Float>> =
        com.example.syona.ui.mood.MoodChartData.aggregate(moodEntries.value, range)

    fun recordStepDetector(count: Int) {
        repository.incrementStepDetector(count)
    }
}

class ProfileViewModelFactory(private val repository: WellnessRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
