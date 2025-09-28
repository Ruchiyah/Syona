package com.example.syona.data.model

import androidx.annotation.DrawableRes
import com.example.syona.R

enum class HabitIcon(@DrawableRes val drawableRes: Int, val displayName: String) {
    HEART(R.drawable.ic_habit_heart, "Heart"),
    BOOK(R.drawable.ic_habit_book, "Read"),
    DUMBBELL(R.drawable.ic_habit_dumbbell, "Workout"),
    SUNRISE(R.drawable.ic_habit_sunrise, "Sunrise"),
    BED(R.drawable.ic_habit_bed, "Sleep"),
    READ(R.drawable.ic_habit_read, "Study"),
    WRITE(R.drawable.ic_habit_write, "Journal"),
    MUSIC(R.drawable.ic_habit_music, "Music"),
    SMILE(R.drawable.ic_habit_smile, "Smile");

    companion object {
        fun fromName(name: String?): HabitIcon = values().firstOrNull { it.name == name }
            ?: HEART
    }
}
