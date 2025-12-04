package com.example.syona.ui.mood

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.syona.R
import com.example.syona.databinding.DialogMoodChartBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoodChartDialog : DialogFragment() {

    private var _binding: DialogMoodChartBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MoodViewModel by activityViewModels {
        MoodViewModelFactory((requireActivity().application as com.example.syona.SyonaApp).repository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMoodChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        setupListeners()
        observeData()
    }

    private fun setupChart() {
        val chart = binding.moodChart
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled = false
        
        // Axis Setup
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        xAxis.granularity = 1f

        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = ContextCompat.getColor(requireContext(), R.color.light_gray)
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 5f
        leftAxis.setLabelCount(6, true)
        leftAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        chart.axisRight.isEnabled = false

        // Initial Load (Days)
        updateChartData("Days")
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.moodChartTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.text?.toString()?.let { updateChartData(it) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.moodEntries.collectLatest {
                // Refresh current tab
                val currentTab = binding.moodChartTabs.getTabAt(binding.moodChartTabs.selectedTabPosition)?.text?.toString() ?: "Days"
                updateChartData(currentTab)
            }
        }
    }

    private fun updateChartData(range: String) {
        val dataPoints = viewModel.getMoodDataForRange(range)
        val chart = binding.moodChart

        if (dataPoints.isEmpty()) {
            chart.clear()
            chart.setNoDataText("No mood data for this period")
            return
        }

        val entries = dataPoints.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second, pair.first)
        }

        val dataSet = LineDataSet(entries, "Mood")
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.cubicIntensity = 0.2f
        dataSet.setDrawFilled(true)
        dataSet.setDrawCircles(true)
        dataSet.lineWidth = 3f
        dataSet.circleRadius = 5f
        dataSet.setCircleColor(ContextCompat.getColor(requireContext(), R.color.color_gradient_end))
        dataSet.highLightColor = ContextCompat.getColor(requireContext(), R.color.color_gradient_start)
        dataSet.color = ContextCompat.getColor(requireContext(), R.color.color_gradient_end)
        dataSet.fillColor = ContextCompat.getColor(requireContext(), R.color.color_gradient_start)
        dataSet.fillAlpha = 50
        dataSet.setDrawHorizontalHighlightIndicator(false)

        val lineData = LineData(dataSet)
        lineData.setDrawValues(false)
        chart.data = lineData

        // X-Axis Formatter
        val xAxis = chart.xAxis
        // Show all labels for Days, let chart decide for Weeks/Months but try to show up to 12
        val labelCount = if (range == "Days") dataPoints.size else Math.min(dataPoints.size, 12)
        xAxis.setLabelCount(labelCount, range == "Days") // force=true for Days
        
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                if (index >= 0 && index < dataPoints.size) {
                    val timestamp = dataPoints[index].first
                    val date = Date(timestamp)
                    val format = when (range) {
                        "Days" -> SimpleDateFormat("EEE", Locale.getDefault())
                        "Weeks" -> SimpleDateFormat("dd MMM", Locale.getDefault())
                        "Months" -> SimpleDateFormat("MMM", Locale.getDefault())
                        else -> SimpleDateFormat("dd/MM", Locale.getDefault())
                    }
                    return format.format(date)
                }
                return ""
            }
        }

        chart.animateXY(1000, 1000)
        chart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
