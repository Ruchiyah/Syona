package com.example.syona.ui.onboarding

import androidx.annotation.DrawableRes

data class OnboardingPage(
    val title: String,
    val description: String,
    @DrawableRes val imageResId: Int,
    @DrawableRes val iconResId: Int
)
