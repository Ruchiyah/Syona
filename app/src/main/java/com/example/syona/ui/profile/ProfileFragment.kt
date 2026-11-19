package com.example.syona.ui.profile

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.syona.R
import com.example.syona.SyonaApp
import com.example.syona.data.model.StepCounterPreferences
import com.example.syona.data.model.StepSensorType
import com.example.syona.databinding.FragmentProfileBinding
import com.example.syona.services.StepCounterService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ProfileViewModel by viewModels {
        ProfileViewModelFactory((requireActivity().application as SyonaApp).repository)
    }
    
    private var placeholderPadding: Int = 0
    private var sensorManager: SensorManager? = null
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var latestStepPrefs: StepCounterPreferences? = null
    private var pendingEnableAfterPermission = false
    private val starAnimators = mutableListOf<ObjectAnimator>()
    
    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        // Note: the previous "com.android.camera.action.CROP" path never ran (no FileProvider was
        // declared, so it always fell back here). The image is centre-cropped to a square below.
        uri?.let { handleImageSelection(it) }
    }

    private val activityRecognitionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingEnableAfterPermission = false
            startStepListeningIfNeeded()
        } else {
            if (latestStepPrefs?.enabled == true) {
                // Permission denied but feature was enabled.
                // Show explanation and guide to settings if needed
                showPermissionDeniedDialog()
                viewModel.setStepCounterEnabled(false)
            }
            pendingEnableAfterPermission = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupStarAnimations()
        setupObservers()
        setupClickListeners()

        placeholderPadding = binding.profilePicture.paddingStart

        // Initialize or re-initialize sensor manager and sensors
        if (sensorManager == null) {
            sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }
        if (stepCounterSensor == null) {
            stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        }
        if (stepDetectorSensor == null) {
            stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        }
        
        setupMoodChart()
    }

    private fun setupMoodChart() {
        val chart = binding.moodChart
        val tabs = binding.moodChartTabs

        // Basic Chart Setup
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled = false
        
        // Axis Setup
        val xAxis = chart.xAxis
        xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
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

        // Tab Listener
        tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.text?.toString()?.let { updateChartData(it) }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
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
            com.github.mikephil.charting.data.Entry(index.toFloat(), pair.second, pair.first)
        }

        val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, "Mood")
        dataSet.mode = com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER
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
        
        // Custom MarkerView could be added here for better touch feedback

        val lineData = com.github.mikephil.charting.data.LineData(dataSet)
        lineData.setDrawValues(false)
        chart.data = lineData

        // X-Axis Formatter
        val xAxis = chart.xAxis
        // Show all labels for Days, let chart decide for Weeks/Months but try to show up to 12
        val labelCount = if (range == "Days") dataPoints.size else Math.min(dataPoints.size, 12)
        xAxis.setLabelCount(labelCount, range == "Days") // force=true for Days
        
        xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                if (index >= 0 && index < dataPoints.size) {
                    val timestamp = dataPoints[index].first
                    val date = java.util.Date(timestamp)
                    val format = when (range) {
                        "Days" -> java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
                        "Weeks" -> java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
                        "Months" -> java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
                        else -> java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
                    }
                    return format.format(date)
                }
                return ""
            }
        }

        chart.animateXY(1000, 1000)
        chart.invalidate()
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
        starAnimators += listOf(rotationAnimator, scaleXAnimator, scaleYAnimator)
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.profileData.collect { profile ->
                updateUI(profile)
            }
        }
        
        // Observe stats for Your Stats section
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collect { stats ->
                updateStatsUI(stats)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stepCounter.collect { prefs ->
                latestStepPrefs = prefs
                updateStepCard(prefs)
                if (prefs.enabled) {
                    startStepListeningIfNeeded()
                } else {
                    stopStepListening()
                }
            }
        }

        // Observe mood entries for chart updates
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.moodEntries.collect {
                val currentTab = binding.moodChartTabs.getTabAt(binding.moodChartTabs.selectedTabPosition)?.text?.toString() ?: "Days"
                updateChartData(currentTab)
            }
        }
    }

    private fun updateUI(profile: com.example.syona.data.model.ProfileData) {
        // Update profile photo
        if (!profile.photoUri.isNullOrEmpty()) {
            loadImageFromBase64(profile.photoUri)
        } else {
            showProfilePlaceholder()
        }
        
        // Update name
        binding.profileName.text = profile.name.ifEmpty { "Guest User" }

        val notSetText = getString(R.string.label_not_set)

        // Update personal information card
        binding.ageText.text = profile.age?.let { getString(R.string.profile_age_value, it) } ?: notSetText
        binding.genderText.text = profile.gender.ifEmpty { notSetText }
        binding.goalText.text = profile.wellnessGoal.ifEmpty { notSetText }
    }
    
    private fun updateStatsUI(stats: com.example.syona.data.model.WellnessStats) {
        // Update Habits Completed
        binding.statsHabitsCompleted.text = stats.habitsCompletedToday.toString()
        
        // Update Longest Streak
        val streakText = when {
            stats.longestStreak == 0 -> "0 days"
            stats.longestStreak == 1 -> "1 day"
            else -> "${stats.longestStreak} days"
        }
        binding.statsLongestStreak.text = streakText
        
        // Update Average Mood
        val moodText = when {
            stats.averageMoodScore == 0.0 -> "N/A"
            stats.averageMoodScore >= 4.5 -> "😊 Great"
            stats.averageMoodScore >= 3.5 -> "😌 Good"
            stats.averageMoodScore >= 2.5 -> "😐 Okay"
            stats.averageMoodScore >= 1.5 -> "😔 Low"
            else -> "😢 Poor"
        }
        binding.statsAverageMood.text = moodText

        // Update Hydration
        binding.statsHydration.text = "${stats.hydrationPercentage}%"
    }

    private fun updateStepCard(prefs: StepCounterPreferences) {
        binding.stepCountText.text = prefs.stepsToday.toString()
        val goal = prefs.dailyGoal.coerceAtLeast(1000)
        binding.stepGoalText.text = getString(R.string.step_goal_format, goal)
        binding.stepProgressSummary.text = getString(R.string.step_progress_format, prefs.stepsToday, goal)

        binding.stepProgressIndicator.max = goal
        val progress = prefs.stepsToday.coerceAtMost(goal)
        binding.stepProgressIndicator.setProgressCompat(progress, true)

        val hasPermission = !requiresActivityRecognitionPermission() || hasActivityRecognitionPermission()
        val sensorAvailable = determineSensorForPrefs(prefs) != null
        val statusRes = when {
            !prefs.enabled -> R.string.step_status_disabled
            !hasPermission -> R.string.step_status_permission
            !sensorAvailable -> R.string.step_status_sensor_missing
            else -> R.string.step_status_tracking
        }

        val statusText = getString(statusRes)
        binding.stepStatusText.text = statusText
        binding.stepSettingsStatus.text = statusText
    }

    private fun determineSensorForPrefs(prefs: StepCounterPreferences): Sensor? {
        return when (prefs.sensorType) {
            StepSensorType.AUTO -> stepCounterSensor ?: stepDetectorSensor
            StepSensorType.COUNTER -> stepCounterSensor ?: if (prefs.enabled) stepDetectorSensor else null
            StepSensorType.DETECTOR -> stepDetectorSensor ?: if (prefs.enabled) stepCounterSensor else null
        }
    }

    private fun startStepListeningIfNeeded() {
        val prefs = latestStepPrefs ?: return
        if (!prefs.enabled) return
        if (requiresActivityRecognitionPermission() && !hasActivityRecognitionPermission()) {
            if (!pendingEnableAfterPermission) {
                pendingEnableAfterPermission = true
                requestActivityRecognitionPermission()
            }
            return
        }
        if (!isAdded || !isResumed) {
            return
        }
        startStepListening(prefs)
    }

    private fun startStepListening(prefs: StepCounterPreferences) {
        val sensor = determineSensorForPrefs(prefs)
        if (sensor == null) return
        StepCounterService.start(requireContext())
    }

    private fun stopStepListening() {
        StepCounterService.stop(requireContext())
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requiresActivityRecognitionPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    private fun requestActivityRecognitionPermission() {
        if (!requiresActivityRecognitionPermission()) return
        if (hasActivityRecognitionPermission()) return
        activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    private fun isSensorAvailableFor(type: StepSensorType): Boolean {
        return when (type) {
            StepSensorType.AUTO -> stepCounterSensor != null || stepDetectorSensor != null
            StepSensorType.COUNTER -> stepCounterSensor != null
            StepSensorType.DETECTOR -> stepDetectorSensor != null
        }
    }

    private fun showStepCounterSettings() {
        val prefs = latestStepPrefs ?: return
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_step_counter_settings, null)

        val enableSwitch = view.findViewById<MaterialSwitch>(R.id.stepCounterSwitch)
        val permissionInfo = view.findViewById<TextView>(R.id.stepPermissionInfo)
    val sensorChipGroup = view.findViewById<ChipGroup>(R.id.sensorChipGroup)
    val autoChip = view.findViewById<Chip>(R.id.sensorChipAuto)
    val counterChip = view.findViewById<Chip>(R.id.sensorChipCounter)
    val detectorChip = view.findViewById<Chip>(R.id.sensorChipDetector)
        val sensorInfoText = view.findViewById<TextView>(R.id.sensorInfoText)
        val goalInputLayout = view.findViewById<TextInputLayout>(R.id.goalInputLayout)
        val goalInput = view.findViewById<TextInputEditText>(R.id.goalInput)
        val resetButton = view.findViewById<MaterialButton>(R.id.resetStepsButton)
        val cancelButton = view.findViewById<MaterialButton>(R.id.cancelButton)
        val saveButton = view.findViewById<MaterialButton>(R.id.saveButton)

        enableSwitch.isChecked = prefs.enabled
        goalInput.setText(prefs.dailyGoal.toString())

        val hasCounterSensor = stepCounterSensor != null
        val hasDetectorSensor = stepDetectorSensor != null

        counterChip.isEnabled = hasCounterSensor
        detectorChip.isEnabled = hasDetectorSensor
        counterChip.alpha = if (hasCounterSensor) 1f else 0.5f
        detectorChip.alpha = if (hasDetectorSensor) 1f else 0.5f

        val initialSensorChipId = when (prefs.sensorType) {
            StepSensorType.AUTO -> R.id.sensorChipAuto
            StepSensorType.COUNTER -> if (hasCounterSensor) R.id.sensorChipCounter else R.id.sensorChipAuto
            StepSensorType.DETECTOR -> if (hasDetectorSensor) R.id.sensorChipDetector else R.id.sensorChipAuto
        }
        sensorChipGroup.check(initialSensorChipId)

        fun updateSensorInfo(checkedId: Int) {
            val messageRes = when (checkedId) {
                R.id.sensorChipCounter -> R.string.step_settings_sensor_counter_helper
                R.id.sensorChipDetector -> R.string.step_settings_sensor_detector_helper
                else -> R.string.step_settings_sensor_auto_helper
            }
            sensorInfoText.setText(messageRes)
        }

        updateSensorInfo(sensorChipGroup.checkedChipId)

        sensorChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: View.NO_ID
            if (checkedId == View.NO_ID) return@setOnCheckedStateChangeListener

            when {
                checkedId == R.id.sensorChipCounter && !hasCounterSensor -> {
                    Toast.makeText(requireContext(), R.string.step_settings_sensor_unavailable, Toast.LENGTH_SHORT).show()
                    group.check(R.id.sensorChipAuto)
                }
                checkedId == R.id.sensorChipDetector && !hasDetectorSensor -> {
                    Toast.makeText(requireContext(), R.string.step_settings_sensor_unavailable, Toast.LENGTH_SHORT).show()
                    group.check(R.id.sensorChipAuto)
                }
                else -> updateSensorInfo(checkedId)
            }
        }

        fun refreshPermissionInfo() {
            permissionInfo.visibility = if (enableSwitch.isChecked && requiresActivityRecognitionPermission() && !hasActivityRecognitionPermission()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        enableSwitch.setOnCheckedChangeListener { _, _ ->
            refreshPermissionInfo()
        }

        refreshPermissionInfo()

        resetButton.setOnClickListener {
            viewModel.resetTodaySteps()
            Toast.makeText(requireContext(), R.string.step_settings_reset_confirmation, Toast.LENGTH_SHORT).show()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        saveButton.setOnClickListener {
            val enabled = enableSwitch.isChecked
            val selectedType = when (sensorChipGroup.checkedChipId) {
                R.id.sensorChipCounter -> StepSensorType.COUNTER
                R.id.sensorChipDetector -> StepSensorType.DETECTOR
                else -> StepSensorType.AUTO
            }

            if (enabled && !isSensorAvailableFor(selectedType)) {
                Toast.makeText(requireContext(), R.string.step_settings_sensor_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val goalText = goalInput.text?.toString()?.trim().orEmpty()
            val goalValue = goalText.toIntOrNull()
            if (goalValue == null || goalValue < 1000) {
                goalInputLayout.error = getString(R.string.step_settings_goal_error)
                return@setOnClickListener
            } else {
                goalInputLayout.error = null
            }

            viewModel.setStepSensorType(selectedType)
            viewModel.setStepCounterGoal(goalValue)
            viewModel.setStepCounterEnabled(enabled)

            if (enabled && requiresActivityRecognitionPermission() && !hasActivityRecognitionPermission()) {
                pendingEnableAfterPermission = true
                requestActivityRecognitionPermission()
            }

            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun setupClickListeners() {
        binding.editProfileButton.setOnClickListener {
            showEditProfileDialog()
        }

        val stepSettingsClickListener = View.OnClickListener { showStepCounterSettings() }
        binding.stepSettingsButton.setOnClickListener(stepSettingsClickListener)
        binding.stepSettingsShortcut.setOnClickListener(stepSettingsClickListener)
        binding.stepCardContainer.setOnClickListener(stepSettingsClickListener)

        binding.privacyButton.setOnClickListener {
            showPrivacyPolicyDialog()
        }

        binding.aboutButton.setOnClickListener {
            showAboutDialog()
        }

        binding.shareButton.setOnClickListener {
            shareApp()
        }

        binding.logoutButton.setOnClickListener {
            showClearDataConfirmation()
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.nameInput)
        val ageInput = dialogView.findViewById<TextInputEditText>(R.id.ageInput)
        val maleRadio = dialogView.findViewById<RadioButton>(R.id.maleRadio)
        val femaleRadio = dialogView.findViewById<RadioButton>(R.id.femaleRadio)
        val otherRadio = dialogView.findViewById<RadioButton>(R.id.otherRadio)
        val goalSpinner = dialogView.findViewById<Spinner>(R.id.goalSpinner)
    val photoButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.changePhotoButton)

        // Setup wellness goal spinner
        val goals = arrayOf(
            "Better Sleep",
            "Reduce Stress",
            "Increase Energy",
            "Improve Mood",
            "Build Healthy Habits",
            "Stay Hydrated",
            "General Wellness"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, goals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        goalSpinner.adapter = adapter

        // Pre-fill with current data
        val currentProfile = viewModel.profileData.value
        nameInput.setText(currentProfile.name)
        ageInput.setText(currentProfile.age?.toString() ?: "")
        
        when (currentProfile.gender) {
            "Male" -> maleRadio.isChecked = true
            "Female" -> femaleRadio.isChecked = true
            "Other" -> otherRadio.isChecked = true
        }

        val goalIndex = goals.indexOf(currentProfile.wellnessGoal)
        if (goalIndex >= 0) {
            goalSpinner.setSelection(goalIndex)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        photoButton.setOnClickListener {
            // Save current state before changing photo
            val name = nameInput.text.toString().trim()
            val age = ageInput.text.toString().toIntOrNull()
            val gender = when {
                maleRadio.isChecked -> "Male"
                femaleRadio.isChecked -> "Female"
                otherRadio.isChecked -> "Other"
                else -> ""
            }
            val goal = goalSpinner.selectedItem.toString()

            viewModel.updateProfile(
                name = name,
                age = age,
                gender = gender,
                wellnessGoal = goal,
                photoUri = currentProfile.photoUri
            )

            dialog.dismiss()
            showImagePickerDialog()
        }

        dialogView.findViewById<View>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.saveButton).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val age = ageInput.text.toString().toIntOrNull()
            val gender = when {
                maleRadio.isChecked -> "Male"
                femaleRadio.isChecked -> "Female"
                otherRadio.isChecked -> "Other"
                else -> ""
            }
            val goal = goalSpinner.selectedItem.toString()

            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.updateProfile(
                name = name,
                age = age,
                gender = gender,
                wellnessGoal = goal,
                photoUri = currentProfile.photoUri
            )
            Toast.makeText(requireContext(), "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showPrivacyPolicyDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🔒 Privacy Policy")
            .setMessage("""
                Syona Wellness App Privacy Policy
                
                Your privacy is important to us. This app stores your data locally on your device, in the app's private storage.
                
                Data Collection:
                • Personal information (name, age, gender)
                • Wellness goals and preferences
                • Habit tracking data
                • Mood journal entries
                • Hydration tracking data
                
                Data Usage:
                • All data is stored locally on your device
                • No data is transmitted to external servers
                • Data is used solely for app functionality
                
                Data Security:
                • Data is kept in app-private storage that other apps cannot read
                • Data may be included in your device's Android backup if backup is enabled
                • You can clear all data at any time
                
                For questions, please contact: support@syona.app
            """.trimIndent())
            .setPositiveButton("Got it") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("✨ About Syona")
            .setMessage("""
                Syona - Your Wellness Companion
                Version 1.0
                
                Syona is a comprehensive wellness app designed to help you track and improve various aspects of your daily life.
                
                Features:
                • 📋 Habit Tracking - Build and maintain healthy habits
                • 😊 Mood Journal - Track your emotional wellbeing
                • 💧 Hydration Tracker - Stay hydrated with reminders
                • 👤 Personal Profile - Customize your experience
                
                Mission:
                We believe that small, consistent actions lead to lasting change. Syona helps you stay accountable and motivated on your wellness journey.
                
                Developed with ❤️ for your wellbeing
                
                © 2025 Syona Wellness
            """.trimIndent())
            .setPositiveButton("Awesome!") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Check out Syona Wellness App!")
            putExtra(Intent.EXTRA_TEXT, """
                🌟 Syona - Your Wellness Companion 🌟
                
                I've been using Syona to track my habits, mood, and hydration. It's helping me build a healthier lifestyle!
                
                Features:
                ✅ Habit Tracking
                😊 Mood Journal
                💧 Hydration Reminders
                📊 Progress Insights
                
                Download it and start your wellness journey today!
            """.trimIndent())
        }
        startActivity(Intent.createChooser(shareIntent, "Share Syona via"))
    }

    private fun showClearDataConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚠️ Clear All Data")
            .setMessage("Are you sure you want to clear all your data? This action cannot be undone.\n\nThis will delete:\n• Your profile information\n• All habit data\n• Mood journal entries\n• Hydration tracking data")
            .setPositiveButton("Clear All") { dialog, _ ->
                val repository = (requireActivity().application as SyonaApp).repository
                repository.clearAllData()
                // Clearing data disables these features; also stop the work they had scheduled
                com.example.syona.service.HydrationReminderManager(requireContext()).cancelReminders()
                StepCounterService.stop(requireContext())
                Toast.makeText(requireContext(), "All data cleared successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission Required")
            .setMessage("Activity Recognition permission is required to count your steps. Please enable it in Settings.")
            .setPositiveButton("Settings") { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Could not open settings", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showImagePickerDialog() {
        val options = arrayOf("Choose from Gallery", "Remove Photo")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> imagePickerLauncher.launch("image/*")
                    1 -> removeProfilePhoto()
                }
            }
            .show()
    }
    
    private fun handleImageSelection(uri: Uri) {
        try {
            val bitmap = decodeSampledBitmap(uri, MAX_DECODE_SIZE_PX)

            if (bitmap == null) {
                showProfilePlaceholder()
                return
            }
            
            // Create a square bitmap if not already
            val squareBitmap = createSquareBitmap(bitmap)

            // Resize to reduce size while maintaining quality
            val resizedBitmap = resizeBitmap(squareBitmap, 400, 400)

            // Convert to Base64
            val base64String = bitmapToBase64(resizedBitmap)

            // Clean up
            if (squareBitmap != bitmap) squareBitmap.recycle()
            resizedBitmap.recycle()
            bitmap.recycle()
            
            // Update profile with photo
            val currentProfile = viewModel.profileData.value
            viewModel.updateProfile(
                name = currentProfile.name,
                age = currentProfile.age,
                gender = currentProfile.gender,
                wellnessGoal = currentProfile.wellnessGoal,
                photoUri = base64String
            )
            
            Toast.makeText(requireContext(), "Photo updated successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showProfilePlaceholder()
            Toast.makeText(requireContext(), "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: OutOfMemoryError) {
            showProfilePlaceholder()
            Toast.makeText(requireContext(), "Image is too large to load", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Decodes [uri] down-sampled so neither side greatly exceeds [maxSize] px. Decoding a modern
     * camera photo at full resolution (e.g. 48 MP ≈ 190 MB ARGB) would throw OutOfMemoryError.
     */
    private fun decodeSampledBitmap(uri: Uri, maxSize: Int): Bitmap? {
        val resolver = requireContext().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxSize && bounds.outHeight / (sampleSize * 2) >= maxSize) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
    
    private fun removeProfilePhoto() {
        val currentProfile = viewModel.profileData.value
        viewModel.updateProfile(
            name = currentProfile.name,
            age = currentProfile.age,
            gender = currentProfile.gender,
            wellnessGoal = currentProfile.wellnessGoal,
            photoUri = null
        )
        Toast.makeText(requireContext(), "Photo removed", Toast.LENGTH_SHORT).show()
    }
    
    private fun createSquareBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width == height) return bitmap
        
        val size = minOf(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2
        
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }
    
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()
        
        var finalWidth = maxWidth
        var finalHeight = maxHeight
        
        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
    
    private fun loadImageFromBase64(base64String: String) {
        try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            if (bitmap != null) {
                showProfileBitmap(bitmap)
            } else {
                showProfilePlaceholder()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showProfilePlaceholder()
        }
    }

    private fun showProfileBitmap(bitmap: Bitmap) {
        binding.profilePicture.apply {
            background = null
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            imageTintList = null
            clearColorFilter()
            setPadding(0, 0, 0, 0)
        }
    }

    private fun showProfilePlaceholder() {
        val padding = if (placeholderPadding != 0) placeholderPadding else binding.profilePicture.paddingStart
        binding.profilePicture.apply {
            if (background == null) {
                background = ContextCompat.getDrawable(requireContext(), R.drawable.blue_green_gradient)
            }
            setImageResource(R.drawable.ic_profile_placeholder)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.white))
            setPadding(padding, padding, padding, padding)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-initialize sensors if needed
        if (sensorManager == null) {
            sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }
        if (stepCounterSensor == null) {
            stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        }
        if (stepDetectorSensor == null) {
            stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        }
        startStepListeningIfNeeded()
    }

    override fun onPause() {
        // Don't stop the service - let it run in background for continuous tracking
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Infinite animators would otherwise keep running and hold the destroyed views
        starAnimators.forEach { it.cancel() }
        starAnimators.clear()
        _binding = null
        // Don't stop the service here - it should continue running in background
    }

    companion object {
        private const val MAX_DECODE_SIZE_PX = 800
    }
}
