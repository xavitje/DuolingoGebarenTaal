package io.github.xavitje.signcoach.ui.common

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS heeft geen systeem-terugknop; terug gaat via de knoppen in de app zelf.
}
