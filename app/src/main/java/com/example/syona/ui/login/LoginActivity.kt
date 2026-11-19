package com.example.syona.ui.login

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.syona.MainActivity
import com.example.syona.R
import com.example.syona.SyonaApp
import com.example.syona.data.model.ProfileData
import com.example.syona.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val repository by lazy { (application as SyonaApp).repository }
    private val starAnimators = mutableListOf<ObjectAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if user has already completed onboarding and profile setup
        if (repository.isOnboardingComplete()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStarAnimations()
        setupWellnessGoalSpinner()
        setupClickListeners()
    }

    private fun setupStarAnimations() {
        startStarAnimation(binding.starTopLeft, 3000L)
        startStarAnimation(binding.starTopRight, 4000L)
        startStarAnimation(binding.starBottomLeft, 3500L)
        startStarAnimation(binding.starBottomRight, 4500L)
    }

    private fun startStarAnimation(star: ImageView, duration: Long) {
        val rotation = ObjectAnimator.ofFloat(star, "rotation", 0f, 360f).apply {
            this.duration = duration
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
        }
        rotation.start()
        starAnimators += rotation
    }

    private fun setupWellnessGoalSpinner() {
        val goals = arrayOf(
            "Select your goal",
            "Better Sleep",
            "Reduce Stress",
            "Increase Energy",
            "Improve Mood",
            "Build Healthy Habits",
            "Stay Hydrated",
            "General Wellness"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, goals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.goalSpinner.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.getStartedButton.setOnClickListener {
            if (validateAndSaveProfile()) {
                // Mark onboarding as complete and navigate to MainActivity
                repository.setOnboardingComplete(true)
                repository.refreshAll()
                
                startActivity(Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                finish()
            }
        }

        binding.skipButton.setOnClickListener {
            // Skip profile setup, but still mark onboarding as complete
            Toast.makeText(this, "You can complete your profile later in settings", Toast.LENGTH_LONG).show()
            
            repository.setOnboardingComplete(true)
            repository.refreshAll()
            
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
    }

    private fun validateAndSaveProfile(): Boolean {
        val name = binding.nameInput.text.toString().trim()
        
        if (name.isEmpty()) {
            binding.nameInputLayout.error = "Please enter your name"
            binding.nameInput.requestFocus()
            return false
        }

        binding.nameInputLayout.error = null

        val age = binding.ageInput.text.toString().toIntOrNull()
        
        val gender = when (binding.genderRadioGroup.checkedRadioButtonId) {
            R.id.maleRadio -> "Male"
            R.id.femaleRadio -> "Female"
            R.id.otherRadio -> "Other"
            else -> ""
        }

        val goalPosition = binding.goalSpinner.selectedItemPosition
        val goal = if (goalPosition > 0) {
            binding.goalSpinner.selectedItem.toString()
        } else {
            ""
        }

        // Save profile data
        val profileData = ProfileData(
            name = name,
            age = age,
            gender = gender,
            wellnessGoal = goal,
            photoUri = null
        )
        repository.updateProfile(profileData)

        Toast.makeText(this, "Welcome, $name! 👋", Toast.LENGTH_SHORT).show()
        return true
    }

    override fun onDestroy() {
        // Infinite animators would otherwise outlive (and leak) this activity
        starAnimators.forEach { it.cancel() }
        starAnimators.clear()
        super.onDestroy()
    }
}
