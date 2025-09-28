package com.example.syona.data.model

data class MoodPreferences(
    val viewMode: MoodViewMode = MoodViewMode.LIST,
    val insightsDismissed: Boolean = false
)
