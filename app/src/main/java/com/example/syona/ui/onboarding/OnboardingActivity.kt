package com.example.syona.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.viewpager2.widget.ViewPager2
import com.example.syona.MainActivity
import com.example.syona.R
import com.example.syona.SyonaApp
import com.example.syona.databinding.ActivityOnboardingBinding
import com.example.syona.ui.login.LoginActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val repository by lazy { (application as SyonaApp).repository }

    private lateinit var pages: List<OnboardingPage>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (repository.isOnboardingComplete()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pages = listOf(
            OnboardingPage(
                title = getString(R.string.onboarding_title_welcome),
                description = getString(R.string.onboarding_desc_welcome),
                imageResId = R.drawable.onboarding_welcome,
                iconResId = R.drawable.ic_notifications
            ),
            OnboardingPage(
                title = getString(R.string.onboarding_title_habits),
                description = getString(R.string.onboarding_desc_habits),
                imageResId = R.drawable.onboarding_habits,
                iconResId = R.drawable.ic_check_circle
            ),
            OnboardingPage(
                title = getString(R.string.onboarding_title_mood),
                description = getString(R.string.onboarding_desc_mood),
                imageResId = R.drawable.onboarding_mood,
                iconResId = R.drawable.ic_mood
            ),
            OnboardingPage(
                title = getString(R.string.onboarding_title_hydration),
                description = getString(R.string.onboarding_desc_hydration),
                imageResId = R.drawable.onboarding_hydration,
                iconResId = R.drawable.ic_water_drop
            )
        )

        val adapter = OnboardingAdapter(pages)
        binding.onboardingPager.adapter = adapter
        
        // Set offscreen page limit to prevent loading issues
        binding.onboardingPager.offscreenPageLimit = 1
        
        binding.onboardingPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
                binding.nextButton.text = if (position == pages.lastIndex) {
                    getString(R.string.action_get_started)
                } else {
                    getString(R.string.action_next)
                }
            }
        })

        setupIndicators()
        updateIndicators(0)

        binding.skipButton.setOnClickListener { finishOnboarding() }
        binding.nextButton.setOnClickListener {
            val nextIndex = binding.onboardingPager.currentItem + 1
            if (nextIndex < pages.size) {
                binding.onboardingPager.setCurrentItem(nextIndex, true)
            } else {
                finishOnboarding()
            }
        }
    }

    private fun setupIndicators() {
        val size = resources.getDimensionPixelSize(R.dimen.indicator_size)
        val margin = resources.getDimensionPixelSize(R.dimen.indicator_margin)
        repeat(pages.size) {
            val indicator = ImageView(this).apply {
                setImageResource(R.drawable.indicator_inactive)
                layoutParams = android.view.ViewGroup.MarginLayoutParams(size, size).apply {
                    marginStart = margin
                    marginEnd = margin
                }
            }
            binding.pagerIndicators.addView(indicator)
        }
    }

    private fun updateIndicators(position: Int) {
        binding.pagerIndicators.children.forEachIndexed { index, view ->
            val drawable = if (index == position) R.drawable.indicator_active else R.drawable.indicator_inactive
            (view as? ImageView)?.setImageDrawable(ContextCompat.getDrawable(this, drawable))
        }
    }

    private fun finishOnboarding() {
        // Navigate to login/profile setup instead of marking complete
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
