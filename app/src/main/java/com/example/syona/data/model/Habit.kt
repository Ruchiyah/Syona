package com.example.syona.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Habit(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val icon: HabitIcon = HabitIcon.HEART,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val customDays: List<Int>? = null,
    val completed: Boolean = false,
    val order: Int = 0,
    val notes: String? = null
) : Parcelable
