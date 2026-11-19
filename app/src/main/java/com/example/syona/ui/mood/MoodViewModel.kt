package com.example.syona.ui.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodPreferences
import com.example.syona.data.model.MoodType
import com.example.syona.data.model.MoodViewMode
import com.example.syona.data.repository.WellnessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MoodViewModel(private val repository: WellnessRepository) : ViewModel() {

    val moodEntries: StateFlow<List<MoodEntry>> = repository.observeMoodEntries()
    val moodPreferences: StateFlow<MoodPreferences> = repository.observeMoodPreferences()

    private val _selectedDate = MutableStateFlow(java.time.LocalDate.now())
    val selectedDate: StateFlow<java.time.LocalDate> = _selectedDate.asStateFlow()

    val filteredMoodEntries: StateFlow<List<MoodEntry>> = combine(
        moodEntries,
        _selectedDate
    ) { entries, date ->
        entries.filter { entry ->
            val entryDate = java.time.Instant.ofEpochMilli(entry.timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            entryDate == date
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectDate(date: java.time.LocalDate) {
        _selectedDate.value = date
    }

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

    fun getMoodDataForRange(range: String): List<Pair<Long, Float>> =
        MoodChartData.aggregate(moodEntries.value, range)
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
