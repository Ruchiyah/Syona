package com.example.syona

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.syona.databinding.ActivityMainBinding
import com.example.syona.ui.onboarding.OnboardingActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    private val repository by lazy { (application as SyonaApp).repository }

    // Permission launcher for requesting multiple permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions granted or denied - app continues normally
        // Individual features will check their specific permissions when needed
        permissions.entries.forEach { entry ->
            val permission = entry.key
            val isGranted = entry.value

            // If notification permission was granted, we might need to reschedule reminders
            if (permission == Manifest.permission.POST_NOTIFICATIONS && isGranted) {
                // Permission granted - any pending reminder setup will now work
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!repository.isOnboardingComplete()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_habits,
                R.id.navigation_mood,
                R.id.navigation_hydration,
                R.id.navigation_profile
            )
        )

        binding.bottomNavigationCard.bottomNavigation.setupWithNavController(navController)

        // Handle navigation from notifications
        handleNotificationNavigation()

        // Request permissions on first launch
        requestNecessaryPermissions()
    }

    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check and add POST_NOTIFICATIONS permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Check and add ACTIVITY_RECOGNITION permission for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        // Request permissions if any are needed
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun handleNotificationNavigation() {
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo == "hydration") {
            binding.bottomNavigationCard.bottomNavigation.selectedItemId = R.id.navigation_hydration
        }
    }
}