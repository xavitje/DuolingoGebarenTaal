package com.example.signcoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Eigen SignCoach-thema: diep petrolblauw/indigo als primaire kleur, warm amber/koraal als
 * accent. Vervangt de kale MaterialTheme {} die er nu staat — dezelfde Material3-componenten
 * werken verder ongewijzigd, ze pakken nu automatisch de eigen kleuren en typografie op.
 */
@Composable
fun SignCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SignCoachTypography,
        content = content,
    )
}
