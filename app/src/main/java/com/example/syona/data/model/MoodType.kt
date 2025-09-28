package com.example.syona.data.model

enum class MoodType(val emoji: String, val label: String, val score: Int, val colorHex: String) {
    HAPPY("😊", "Happy", 5, "#FFD166"),
    CALM("😌", "Calm", 4, "#06D6A0"),
    NEUTRAL("😐", "Neutral", 3, "#118AB2"),
    SAD("😔", "Sad", 2, "#073B4C"),
    ANGRY("😡", "Angry", 1, "#EF476F");

    companion object {
        fun fromLabel(label: String?): MoodType = values().firstOrNull {
            it.label.equals(label, ignoreCase = true)
        } ?: NEUTRAL
    }
}
