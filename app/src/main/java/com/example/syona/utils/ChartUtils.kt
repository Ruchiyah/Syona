package com.example.syona.utils

import android.graphics.Color
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodType
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

object ChartUtils {

    private val dayFormatter = DateTimeFormatter.ofPattern("EEE")

    fun styleLineChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.setTouchEnabled(false)
        chart.legend.isEnabled = false
        chart.setNoDataText("Log your mood to see insights")
        chart.axisLeft.apply {
            setDrawGridLines(false)
            axisMinimum = 0f
            axisMaximum = 5.5f
            granularity = 1f
            textColor = Color.parseColor("#607D8B")
        }
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            textColor = Color.parseColor("#607D8B")
        }
    }

    fun prepareMoodTrend(entries: List<MoodEntry>, days: Int): List<Pair<LocalDate, Double>> {
        if (entries.isEmpty()) return emptyList()
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays((days - 1).toLong())
        val grouped = entries.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        val result = mutableListOf<Pair<LocalDate, Double>>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            val dayEntries = grouped[current].orEmpty()
            val average = if (dayEntries.isEmpty()) 0.0 else dayEntries.map { it.mood.score }.average()
            result.add(current to average)
            current = current.plusDays(1)
        }
        return result
    }

    fun populateMoodTrend(chart: LineChart, trend: List<Pair<LocalDate, Double>>) {
        if (trend.isEmpty()) {
            chart.data = null
            return
        }
        val entries = trend.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second.toFloat())
        }
        val dataSet = LineDataSet(entries, "Mood").apply {
            color = Color.parseColor("#56C596")
            lineWidth = 2.5f
            setDrawCircles(true)
            circleRadius = 4f
            setCircleColor(Color.parseColor("#56C596"))
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#56C596")
            fillAlpha = 60
        }
        chart.xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
            trend.map { dayFormatter.format(it.first) }
        )
        chart.data = LineData(dataSet)
    }

    fun buildShareableSummary(entries: List<MoodEntry>): String {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
        val recent = entries.filter { it.timestamp >= sevenDaysAgo }
        if (recent.isEmpty()) {
            return "I started journaling my moods with Syona!"
        }
        val counts = MoodType.values().associateWith { mood ->
            recent.count { it.mood == mood }
        }
        val favoriteMood = counts.maxByOrNull { it.value }
        val average = recent.map { it.mood.score }.average()
        val summaryLines = mutableListOf<String>()
        summaryLines += "My last 7 days in Syona"
        summaryLines += "Average mood score: ${String.format("%.1f", average)}"
        favoriteMood?.let {
            summaryLines += "Most frequent mood: ${it.key.emoji} ${it.key.label}"
        }
        summaryLines += "Entries logged: ${recent.size}"
        return summaryLines.joinToString(separator = "\n")
    }
}
