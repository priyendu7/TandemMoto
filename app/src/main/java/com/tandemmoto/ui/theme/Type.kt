package com.tandemmoto.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val Default = Typography()

private fun TextStyle.larger(size: Int, lineHeight: Int) = copy(
    fontSize = size.sp,
    lineHeight = lineHeight.sp
)

// One step larger than the Material defaults: the app is read at a glance, often mid-ride.
internal val AppTypography = Typography(
    headlineMedium = Default.headlineMedium.larger(32, 40),
    titleLarge = Default.titleLarge.larger(24, 32),
    titleMedium = Default.titleMedium.larger(18, 26),
    bodyLarge = Default.bodyLarge.larger(18, 26),
    bodyMedium = Default.bodyMedium.larger(16, 24),
    labelLarge = Default.labelLarge.larger(16, 22)
)
