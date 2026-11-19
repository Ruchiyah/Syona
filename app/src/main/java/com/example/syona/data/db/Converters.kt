package com.example.syona.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun intListToString(list: List<Int>): String = list.joinToString(",")

    @TypeConverter
    fun stringToIntList(value: String): List<Int> =
        if (value.isEmpty()) emptyList() else value.split(",").map { it.toInt() }

    @TypeConverter
    fun stringListToString(list: List<String>): String = list.joinToString(SEP)

    @TypeConverter
    fun stringToStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEP)

    private companion object {
        const val SEP = "\u001F"
    }
}
