package com.example.syona.ui.habits

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.syona.R
import com.example.syona.SyonaApp
import com.example.syona.data.model.Habit
import com.example.syona.databinding.FragmentHabitsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class HabitsFragment : Fragment() {

    private var _binding: FragmentHabitsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HabitsViewModel by viewModels {
        HabitsViewModelFactory((requireActivity().application as SyonaApp).repository)
    }

    private lateinit var habitAdapter: HabitAdapter
    private var celebrationShownForDay = false
    private var progressAnimator: ValueAnimator? = null
    private val starAnimators = mutableListOf<ObjectAnimator>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHabitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupProgressBar()
        observeState()
        binding.addHabitButton.setOnClickListener { showHabitForm(null) }
        startStarAnimation(binding.headerContainer.headerStarLeft, 0L)
        startStarAnimation(binding.headerContainer.headerStarRight, 300L)
    }

    private fun setupProgressBar() {
        // Initialize progress bar
        binding.progressIndicator.max = 100
        binding.progressIndicator.progress = 0
        binding.progressIndicator.isIndeterminate = false
        android.util.Log.d("HabitsFragment", "ProgressBar initialized: max=100, progress=0")
    }

    private fun setupRecyclerView() {
        habitAdapter = HabitAdapter(
            onToggle = { viewModel.toggleCompletion(it) },
            onEdit = { showHabitForm(it) },
            onDelete = { viewModel.deleteHabit(it) }
        )
        binding.habitRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = habitAdapter
            itemAnimator?.changeDuration = 250
        }
        // Drag functionality removed for cleaner UI
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.habits.collectLatest { habits ->
                habitAdapter.submitList(habits)
                // Show enhanced empty state instead of simple text
                binding.emptyHabitsState.visibility = if (habits.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collectLatest { stats ->
                val total = stats.totalHabits
                val completed = stats.habitsCompletedToday
                val percent = if (total == 0) 0 else ((completed.toDouble() / total.toDouble()) * 100).roundToInt()

                android.util.Log.d("HabitsFragment", "Stats updated: $completed/$total ($percent%)")

                // Update text immediately
                binding.progressPercentLabel.text = getString(R.string.percent_format, percent)
                binding.progressSummary.text = getString(R.string.habit_progress_summary, completed, total)

                // Animate progress bar
                animateProgress(percent)

                // Show celebration when all habits completed
                val allCompleted = total > 0 && completed == total
                updateCelebration(allCompleted)
            }
        }
    }

    private fun animateProgress(target: Int) {
        val start = binding.progressIndicator.progress
        android.util.Log.d("HabitsFragment", "Animating progress: $start -> $target")
        progressAnimator?.cancel()

        if (start == target) {
            android.util.Log.d("HabitsFragment", "Progress already at target: $target")
            return
        }

        progressAnimator = ValueAnimator.ofInt(start, target).apply {
            duration = 800
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                binding.progressIndicator.progress = value
                binding.progressIndicator.invalidate()
                android.util.Log.d("HabitsFragment", "Progress updated to: $value")
            }
            start()
        }
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

    private fun showHabitForm(habit: Habit?) {
        val bottomSheet = HabitFormBottomSheet.newInstance(habit)
        bottomSheet.onSave = { updatedHabit ->
            viewModel.saveHabit(updatedHabit)
        }
        bottomSheet.show(parentFragmentManager, "HabitForm")
    }

    private fun updateCelebration(show: Boolean) {
        if (show && !celebrationShownForDay) {
            celebrationShownForDay = true
            // Confetti animation removed - library not available
            // Simple celebration could be added here with built-in animations if needed
        } else if (!show) {
            celebrationShownForDay = false
            // No confetti to dismiss
        }
    }

    private fun dismissCelebration() {
        // Confetti view removed - no action needed
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressAnimator?.cancel()
        starAnimators.forEach { it.cancel() }
        starAnimators.clear()
        _binding = null
    }
}
