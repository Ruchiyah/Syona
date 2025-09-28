package com.example.syona.data.model

data class MoodEntry(
    val id: Long = System.currentTimeMillis(),
    val mood: MoodType,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
