package com.example.syona.ui.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodPreferences
import com.example.syona.data.model.MoodType
import com.example.syona.data.model.MoodViewMode
import com.example.syona.data.repository.WellnessRepository
import kotlinx.coroutines.flow.StateFlow

class MoodViewModel(private val repository: WellnessRepository) : ViewModel() {

    val moodEntries: StateFlow<List<MoodEntry>> = repository.observeMoodEntries()
    val moodPreferences: StateFlow<MoodPreferences> = repository.observeMoodPreferences()

    fun addMood(moodType: MoodType, note: String?) {
        repository.addMoodEntry(moodType, note)
    }

    fun deleteMood(entry: MoodEntry) {
        repository.deleteMoodEntry(entry.id)
    }

    fun selectViewMode(mode: MoodViewMode) {
        repository.setMoodViewMode(mode)
    }

    fun dismissInsights() {
        repository.setMoodInsightsDismissed(true)
    }

    fun getMoodScoreAverage(days: Int = 7): Double {
        val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        val relevant = moodEntries.value.filter { it.timestamp >= cutoff }
        return if (relevant.isEmpty()) 0.0 else relevant.map { it.mood.score }.average()
    }

    fun isEmpty(): Boolean = moodEntries.value.isEmpty()
}

class MoodViewModelFactory(private val repository: WellnessRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MoodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MoodViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
