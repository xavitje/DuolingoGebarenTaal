package io.github.xavitje.signcoach.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Simpele event-bus voor OAuth deep-link callbacks. Het platform-specifieke deel (MainActivity op
 * Android, straks een vergelijkbare hook op iOS) ontvangt de inkomende deep-link
 * (`signcoach://auth-callback?code=...`) en zet de `code` hier neer; [AppNavigation]/[AuthViewModel]
 * observeren dit en wisselen de code in voor een sessie via
 * [io.github.xavitje.signcoach.presentation.AuthViewModel.handleOAuthCallback].
 */
object OAuthCallbackBus {
    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = _pendingCode

    fun emit(code: String) {
        _pendingCode.value = code
    }

    fun consume() {
        _pendingCode.value = null
    }
}
