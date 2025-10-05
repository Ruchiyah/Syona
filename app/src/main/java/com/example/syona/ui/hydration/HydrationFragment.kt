package com.example.syona.ui.hydration

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.syona.R
import com.example.syona.data.preferences.PreferencesManager
import com.example.syona.data.repository.WellnessRepository
import com.example.syona.databinding.FragmentHydrationBinding
import com.example.syona.service.HydrationReminderManager
import com.example.syona.SyonaApp
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class HydrationFragment : Fragment() {

    private var _binding: FragmentHydrationBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var reminderManager: HydrationReminderManager
    private var customIntervalMinutes: Int = 90 // Default custom interval
    private var isInitialSetup: Boolean = true // Flag to prevent initial toast
    private var isSettingSpinner: Boolean = false // Flag to prevent spinner listener triggering during setup
    private var previousSpinnerPosition: Int = 1 // Track previous position to detect user changes
    private var skipNextCustomDialog: Boolean = false // Prevent dialog when restoring state
    private var spinnerTouched: Boolean = false // Track user interaction with spinner
    private val viewModel: HydrationViewModel by viewModels {
        HydrationViewModelFactory((requireActivity().application as SyonaApp).repository)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(requireContext(), "Notification permission is required for reminders", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHydrationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize reminder manager
        reminderManager = HydrationReminderManager(requireContext())
        
        setupUI()
        setupObservers()
        setupClickListeners()
        setupStarAnimations()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        // Prevent custom dialog/toast when returning to this tab if custom already selected
        if (binding.intervalSpinner.selectedItemPosition == 5) {
            skipNextCustomDialog = true
        }
        spinnerTouched = false
    }

    private fun setupUI() {
        // Setup reminder interval spinner
        val intervals = arrayOf("30 minutes", "1 hour", "2 hours", "3 hours", "4 hours", "Custom")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.intervalSpinner.adapter = adapter
        binding.intervalSpinner.setSelection(1) // Default to 1 hour
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hydrationState.collect { state ->
                updateUI(state.intake, state.dailyGoal)
                binding.goalEditText.setText(state.dailyGoal.toString())
                
                // Set reminder switch without triggering listener
                binding.reminderSwitch.setOnCheckedChangeListener(null)
                binding.reminderSwitch.isChecked = state.remindersEnabled
                binding.reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
                    binding.intervalLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
                    binding.intervalContent.visibility = if (isChecked) View.VISIBLE else View.GONE
                    // Only show toast after initial setup
                    if (!isInitialSetup) {
                        updateReminderSettings(showToast = true)
                    } else {
                        updateReminderSettings(showToast = false)
                    }
                }
                
                // Show/hide interval settings based on reminder state
                binding.intervalLayout.visibility = if (state.remindersEnabled) View.VISIBLE else View.GONE
                binding.intervalContent.visibility = if (state.remindersEnabled) View.VISIBLE else View.GONE
                
                // Set spinner selection based on reminder interval
                isSettingSpinner = true // Prevent spinner listener from triggering
                val intervalIndex = when (state.reminderIntervalMinutes) {
                    30 -> 0
                    60 -> 1
                    120 -> 2
                    180 -> 3
                    240 -> 4
                    else -> {
                        // It's a custom interval
                        customIntervalMinutes = state.reminderIntervalMinutes
                        5 // Custom position
                    }
                }
                binding.intervalSpinner.setSelection(intervalIndex)
                previousSpinnerPosition = intervalIndex // Update tracked position
                if (intervalIndex == 5) {
                    skipNextCustomDialog = true
                }
                isSettingSpinner = false
                
                // Mark initial setup as complete after first state collection
                isInitialSetup = false
            }
        }
    }

    private fun setupClickListeners() {
        // Water intake card buttons
        binding.card250.setOnClickListener { addWater(250) }
        binding.card500.setOnClickListener { addWater(500) }
        binding.card750.setOnClickListener { addWater(750) }
        binding.card1000.setOnClickListener { addWater(1000) }

        // Settings - removed listener setup here as it's now in setupObservers
        
        binding.intervalSpinner.setOnTouchListener { _, _ ->
            spinnerTouched = true
            false
        }

        // Interval spinner listener
        binding.intervalSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                // Prevent triggering during programmatic changes
                if (isSettingSpinner) {
                    previousSpinnerPosition = position
                    spinnerTouched = false
                    return
                }
                
                if (position == 5) {
                    val shouldSkip = (skipNextCustomDialog && !spinnerTouched) || isInitialSetup
                    skipNextCustomDialog = false
                    if (shouldSkip || !spinnerTouched) {
                        previousSpinnerPosition = position
                        spinnerTouched = false
                        return
                    }
                    Toast.makeText(requireContext(), "Set a custom reminder interval", Toast.LENGTH_SHORT).show()
                    showCustomIntervalDialog()
                    previousSpinnerPosition = position
                    spinnerTouched = false
                    return
                }

                if (!spinnerTouched || position == previousSpinnerPosition) {
                    previousSpinnerPosition = position
                    spinnerTouched = false
                    return
                }

                previousSpinnerPosition = position
                spinnerTouched = false

                // Only update if reminders are enabled
                if (binding.reminderSwitch.isChecked) {
                    updateReminderSettings(showToast = true)
                }
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                // Do nothing
            }
        }

        // Goal update button
        binding.updateGoalButton.setOnClickListener {
            updateGoal()
        }
        
        // Also update on Enter key
        binding.goalEditText.setOnEditorActionListener { _, _, _ ->
            updateGoal()
            binding.goalEditText.clearFocus()
            true
        }

        // Reset button
        binding.resetButton.setOnClickListener {
            viewModel.resetDailyIntake()
            Toast.makeText(requireContext(), "Daily progress reset!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addWater(amount: Int) {
        viewModel.addWaterIntake(amount)
        
        // Show encouraging message
        val messages = listOf(
            "Great job! Keep hydrating! 💧",
            "You're doing amazing! 🌟",
            "Stay hydrated, stay healthy! 💪",
            "Excellent progress! 🎉"
        )
        Toast.makeText(requireContext(), messages.random(), Toast.LENGTH_SHORT).show()
    }

    private fun updateGoal() {
        val goalText = binding.goalEditText.text.toString()
        val goal = goalText.toIntOrNull()
        
        if (goal == null) {
            binding.goalEditText.setText("2000")
            Toast.makeText(requireContext(), "Please enter a valid number", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (goal < 500) {
            binding.goalEditText.setText("2000")
            Toast.makeText(requireContext(), "Goal must be at least 500ml", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (goal > 10000) {
            binding.goalEditText.setText("10000")
            Toast.makeText(requireContext(), "Goal cannot exceed 10,000ml", Toast.LENGTH_SHORT).show()
            viewModel.updateDailyGoal(10000)
            return
        }
        
        viewModel.updateDailyGoal(goal)
        Toast.makeText(requireContext(), "Daily goal updated to ${goal}ml! 🎯", Toast.LENGTH_SHORT).show()
        
        // Hide keyboard
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.goalEditText.windowToken, 0)
    }

    private fun updateReminderSettings(showToast: Boolean = false) {
        val enabled = binding.reminderSwitch.isChecked
        val intervalMinutes = when (binding.intervalSpinner.selectedItemPosition) {
            0 -> 30
            1 -> 60
            2 -> 120
            3 -> 180
            4 -> 240
            5 -> customIntervalMinutes // Custom interval
            else -> 60
        }
        
        // Save settings to repository first
        viewModel.updateReminderSettings(enabled, intervalMinutes)
        
        if (enabled) {
            // Check if we have notification permission before scheduling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED) {
                    // Request permission first
                    Toast.makeText(
                        requireContext(),
                        "Please grant notification permission for reminders",
                        Toast.LENGTH_LONG
                    ).show()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }

            // Schedule reminders
            reminderManager.scheduleReminders(intervalMinutes)
            
            // Only show toast if explicitly requested (user action)
            if (showToast) {
                // Show friendly interval message
                val intervalText = when (binding.intervalSpinner.selectedItemPosition) {
                    0 -> "30 minutes"
                    1 -> "1 hour"
                    2 -> "2 hours"
                    3 -> "3 hours"
                    4 -> "4 hours"
                    5 -> formatCustomInterval(customIntervalMinutes)
                    else -> "$intervalMinutes minutes"
                }
                Toast.makeText(requireContext(), "Reminders enabled every $intervalText ⏰", Toast.LENGTH_SHORT).show()
            }
        } else {
            reminderManager.cancelReminders()
            if (showToast) {
                Toast.makeText(requireContext(), "Reminders disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(intake: Int, goal: Int) {
        binding.circularProgress.setProgress(intake, goal)
        
        // Update motivational text based on progress percentage
        val percentage = if (goal > 0) (intake.toFloat() / goal.toFloat() * 100).toInt() else 0
        binding.motivationalText.text = when {
            percentage >= 100 -> "🎉 Goal achieved! Amazing!"
            percentage >= 75 -> "Almost there! Keep it up! 💪"
            percentage >= 50 -> "Great progress! You're halfway! 🌟"
            percentage >= 25 -> "Good start! Keep going! 💧"
            else -> "Let's stay hydrated today! 💙"
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(
                        requireContext(),
                        "Notification permission is needed for hydration reminders",
                        Toast.LENGTH_LONG
                    ).show()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    
    private fun showCustomIntervalDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            android.R.layout.simple_list_item_1, null
        )

        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Minutes"
            setText(customIntervalMinutes.toString())
            setTextColor(resources.getColor(android.R.color.black, null))
            setPadding(50, 40, 50, 40)
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("⏰ Custom Reminder Interval")
            .setMessage("Enter the reminder interval in minutes (15-480)\n\nNote: Android requires a minimum of 15 minutes for scheduled reminders.")
            .setView(input)
            .setPositiveButton("Set") { dialog, _ ->
                val minutes = input.text.toString().toIntOrNull()
                
                if (minutes == null) {
                    Toast.makeText(requireContext(), "Please enter a valid number", Toast.LENGTH_SHORT).show()
                    binding.intervalSpinner.setSelection(1) // Reset to 1 hour
                    return@setPositiveButton
                }
                
                if (minutes < 15) {
                    Toast.makeText(requireContext(), "Minimum interval is 15 minutes (Android limitation)", Toast.LENGTH_LONG).show()
                    binding.intervalSpinner.setSelection(1)
                    return@setPositiveButton
                }
                
                if (minutes > 480) {
                    Toast.makeText(requireContext(), "Maximum interval is 8 hours (480 minutes)", Toast.LENGTH_SHORT).show()
                    binding.intervalSpinner.setSelection(1)
                    return@setPositiveButton
                }
                
                customIntervalMinutes = minutes
                
                // Update reminders if enabled
                if (binding.reminderSwitch.isChecked) {
                    updateReminderSettings(showToast = true)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Custom interval set to ${formatCustomInterval(minutes)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                // Revert to previous selection
                isSettingSpinner = true
                val previousIndex = when (customIntervalMinutes) {
                    30 -> 0
                    60 -> 1
                    120 -> 2
                    180 -> 3
                    240 -> 4
                    else -> 5 // Stay on Custom
                }
                binding.intervalSpinner.setSelection(previousIndex)
                previousSpinnerPosition = previousIndex // Update tracked position
                isSettingSpinner = false
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun formatCustomInterval(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes minutes"
            minutes == 60 -> "1 hour"
            minutes % 60 == 0 -> "${minutes / 60} hours"
            else -> {
                val hours = minutes / 60
                val mins = minutes % 60
                "${hours}h ${mins}m"
            }
        }
    }

    private fun setupStarAnimations() {
    startStarAnimation(binding.headerContainer.headerStarLeft, 0L)
    startStarAnimation(binding.headerContainer.headerStarRight, 300L)
    }

    private fun startStarAnimation(star: ImageView, delay: Long) {
        // Rotation animation
        val rotationAnimator = ObjectAnimator.ofFloat(star, "rotation", 0f, 360f).apply {
            this.duration = 6000
            this.startDelay = delay
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
        }
        
        // Scale animation (zoom in/out for sparkle effect)
        val scaleXAnimator = ObjectAnimator.ofFloat(star, "scaleX", 1f, 1.3f, 1f).apply {
            this.duration = 2000
            this.startDelay = delay
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        
        val scaleYAnimator = ObjectAnimator.ofFloat(star, "scaleY", 1f, 1.3f, 1f).apply {
            this.duration = 2000
            this.startDelay = delay
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        
        rotationAnimator.start()
        scaleXAnimator.start()
        scaleYAnimator.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}