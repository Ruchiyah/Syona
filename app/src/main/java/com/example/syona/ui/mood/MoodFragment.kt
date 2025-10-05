package com.example.syona.ui.mood

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.syona.R
import com.example.syona.SyonaApp
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodViewMode
import com.example.syona.databinding.FragmentMoodBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MoodFragment : Fragment() {

    private var _binding: FragmentMoodBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MoodViewModel by viewModels {
        MoodViewModelFactory((requireActivity().application as SyonaApp).repository)
    }

    private lateinit var moodAdapter: MoodAdapter
    private lateinit var calendarAdapter: MoodCalendarAdapter

    private var showingList = true
    private var insightsDismissed = false
    private val starAnimators = mutableListOf<ObjectAnimator>()

    private val shareFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy • hh:mm a")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupToggle()
        setupActions()
        observeMoods()
        observePreferences()
    startStarAnimation(binding.headerContainer.headerStarLeft, 0L)
    startStarAnimation(binding.headerContainer.headerStarRight, 300L)
    }

    private fun setupRecyclerViews() {
        moodAdapter = MoodAdapter(
            onDelete = { entry -> viewModel.deleteMood(entry) }
        )
        binding.moodRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = moodAdapter
            itemAnimator = null
        }
        calendarAdapter = MoodCalendarAdapter()
        binding.calendarRecycler.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = calendarAdapter
        }
    }

    private fun setupToggle() {
        // Initialize with List selected
        showingList = true
        updateToggleStyles(true)
        
        binding.listToggle.setOnClickListener {
            if (!showingList) {
                showingList = true
                updateToggleStyles(true)
                updateContentVisibility()
                viewModel.selectViewMode(MoodViewMode.LIST)
            }
        }
        binding.calendarToggle.setOnClickListener {
            if (showingList) {
                showingList = false
                updateToggleStyles(false)
                updateContentVisibility()
                viewModel.selectViewMode(MoodViewMode.CALENDAR)
                updateCalendar(viewModel.moodEntries.value)
            }
        }
    }

    private fun setupActions() {
        binding.addMoodButton.setOnClickListener { openMoodSheet() }
        binding.addFirstMoodButton.setOnClickListener { openMoodSheet() }
    }

    private fun observeMoods() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.moodEntries.collectLatest { entries ->
                moodAdapter.submitList(entries)
                updateEmptyState(entries)
                updateInsights(entries)
                if (!showingList) {
                    updateCalendar(entries)
                }
            }
        }
    }

    private fun observePreferences() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.moodPreferences.collectLatest { prefs ->
                insightsDismissed = prefs.insightsDismissed
                showingList = prefs.viewMode == MoodViewMode.LIST
                updateToggleStyles(showingList)
                updateContentVisibility()
                if (!showingList) {
                    updateCalendar(viewModel.moodEntries.value)
                }
            }
        }
    }

    private fun updateEmptyState(entries: List<MoodEntry>) {
        val hasEntries = entries.isNotEmpty()
        binding.emptyMood.isVisible = !hasEntries
        binding.toggleContainer.isVisible = true // Always show toggle for demo
        binding.moodInsightsCardContainer.isVisible = !insightsDismissed
        updateContentVisibility(hasEntries)
    }

    private fun updateContentVisibility(hasEntries: Boolean = true) {
        binding.moodRecycler.isVisible = hasEntries && showingList
        binding.calendarRecycler.isVisible = hasEntries && !showingList
    }

    private fun updateInsights(entries: List<MoodEntry>) {
        if (insightsDismissed) {
            binding.moodInsightsCardContainer.isVisible = false
            return
        }

        // Show card always, but with different content based on whether there are entries
        binding.moodInsightsCardContainer.isVisible = true

        if (entries.isEmpty()) {
            // Show N/A state for new users
            binding.moodInsightsSubtitle.text = "Start logging your moods to see insights here."
            binding.moodInsightsPercent.text = "N/A"
            binding.moodInsightsMoodLabel.text = "N/A"
            return
        }

        // Find the most frequent mood (dominant mood)
        val grouped = entries.groupBy { it.mood }
        val dominant = grouped.maxByOrNull { it.value.size }?.key ?: return
        val dominantCount = grouped[dominant]?.size ?: 0
        val total = entries.size
        
        val dominantPercent = ((dominantCount.toDouble() / total.toDouble()) * 100).toInt()

        binding.moodInsightsSubtitle.text = getString(
            R.string.mood_insights_description,
            dominant.label.lowercase(),
            total
        )
        val percentText = getString(R.string.percent_format, dominantPercent)
        binding.moodInsightsPercent.text = percentText
        binding.moodInsightsMoodLabel.text = dominant.label

        // Force view to refresh
        binding.moodInsightsPercent.invalidate()
        binding.moodInsightsPercent.requestLayout()
    }

    private fun updateCalendar(entries: List<MoodEntry>) {
        val today = LocalDate.now()
        val firstDayOfMonth = today.withDayOfMonth(1)
        val daysInMonth = today.lengthOfMonth()
        val startOffset = firstDayOfMonth.dayOfWeek.value % 7
        val items = mutableListOf<MoodCalendarItem>()
        repeat(startOffset) { items.add(MoodCalendarItem(null, null, null)) }
        val grouped = entries.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() }
        for (day in 1..daysInMonth) {
            val date = firstDayOfMonth.withDayOfMonth(day)
            val dailyEntries = grouped[date]
            val latest = dailyEntries?.maxByOrNull { it.timestamp }
            val color = latest?.let { moodEntry ->
                val base = android.graphics.Color.parseColor(moodEntry.mood.colorHex)
                androidx.core.graphics.ColorUtils.setAlphaComponent(base, 68)
            }
            items.add(
                MoodCalendarItem(
                    dayNumber = day,
                    emoji = latest?.mood?.emoji,
                    backgroundColor = color
                )
            )
        }
        calendarAdapter.submit(items)
    }

    private fun updateToggleStyles(isListSelected: Boolean) {
        val context = requireContext()
        styleToggleButton(binding.listToggle, isListSelected, context)
        styleToggleButton(binding.calendarToggle, !isListSelected, context)
    }

    private fun styleToggleButton(button: MaterialButton, isSelected: Boolean, context: android.content.Context) {
        val background = if (isSelected) R.drawable.bg_toggle_selected else R.drawable.bg_toggle_unselected
        val textColorRes = if (isSelected) android.R.color.white else R.color.color_text_secondary
        val iconTintRes = if (isSelected) android.R.color.white else R.color.color_text_secondary
        button.setBackgroundResource(background)
        button.setTextColor(ContextCompat.getColor(context, textColorRes))
        button.iconTint = ContextCompat.getColorStateList(context, iconTintRes)

        // Remove any underlines or text decorations
        button.paintFlags = button.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
        button.paintFlags = button.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
    }

    private fun openMoodSheet() {
        val sheet = MoodEntryBottomSheet()
        sheet.onSave = { moodType, note ->
            viewModel.addMood(moodType, note)
        }
        sheet.show(parentFragmentManager, "MoodEntry")
    }

    private fun shareMoodEntry(entry: MoodEntry) {
        val timestamp = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault())
        val note = entry.note?.takeIf { it.isNotBlank() }
        val message = buildString {
            append(getString(R.string.mood_share_entry_heading, entry.mood.label))
            append("\n")
            append(getString(R.string.mood_share_entry_time, shareFormatter.format(timestamp)))
            note?.let {
                append("\n")
                append(getString(R.string.mood_share_entry_note, it))
            }
            append("\n\n")
            append(getString(R.string.mood_share_entry_footer))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.label_share_mood_subject))
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.label_share)))
    }

    private fun startStarAnimation(view: View, delay: Long) {
        // Rotation animation
        val rotationAnimator = ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).apply {
            duration = 6000
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            startDelay = delay
        }
        
        // Scale animation (zoom in/out for sparkle effect)
        val scaleXAnimator = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.3f, 1f).apply {
            duration = 2000
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE
            startDelay = delay
        }
        
        val scaleYAnimator = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.3f, 1f).apply {
            duration = 2000
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE
            startDelay = delay
        }
        
        rotationAnimator.start()
        scaleXAnimator.start()
        scaleYAnimator.start()
        
        starAnimators.add(rotationAnimator)
        starAnimators.add(scaleXAnimator)
        starAnimators.add(scaleYAnimator)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        starAnimators.forEach { it.cancel() }
        starAnimators.clear()
        _binding = null
    }
}
