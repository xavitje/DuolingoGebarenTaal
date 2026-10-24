package io.github.xavitje.signcoach.ui.common

import androidx.compose.runtime.Composable

/**
 * Vangt de systeem-terugknop (Android) af zolang [enabled] waar is. Zonder dit sloot de
 * terugknop in een les of op een ander tabblad meteen de hele app, omdat het leerpad de
 * onderste bestemming van de navigatie is. Op iOS is er geen systeem-terugknop: no-op.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
