package io.github.xavitje.signcoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * SignCoach Redesign-thema: koraal als merkkleur, schemer-plum voor diepte,
 * klaver/zon als semantische kleuren voor gelukt/reeks. Vervangt het oude
 * indigo/amber-thema — dezelfde Material3-componenten werken ongewijzigd,
 * ze pakken nu automatisch de nieuwe kleuren, vormen en typografie op.
 */
@Composable
fun SignCoachTheme(
    // SignCoach Redesign kiest bewust één donkere identiteit voor de hele app
    // (niet gekoppeld aan het systeemthema van het toestel).
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = signCoachTypography(),
        shapes = SignCoachShapes,
        content = content,
    )
}
