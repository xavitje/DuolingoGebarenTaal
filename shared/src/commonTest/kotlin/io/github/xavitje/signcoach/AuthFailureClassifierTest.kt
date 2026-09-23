package io.github.xavitje.signcoach

import io.github.xavitje.signcoach.domain.AppError
import io.github.xavitje.signcoach.presentation.isDefinitiveAuthFailure
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Dekt de kernregel achter de "app moet ingelogd blijven zonder internet"-bugfix:
 * [isDefinitiveAuthFailure] moet een echte serverafwijzing (ongeldige/ingetrokken refresh token)
 * onderscheiden van een netwerk-/verbindingsfout. Alleen het eerste mag AuthViewModel.restoreSession()
 * laten uitloggen — bij het tweede moet de gebruiker offline ingelogd blijven met de lokaal
 * gecachete sessie (zie AuthViewModel.kt).
 */
class AuthFailureClassifierTest {

    @Test
    fun unauthorized_isDefinitive() {
        assertTrue(isDefinitiveAuthFailure(AppError.Unauthorized))
    }

    @Test
    fun serverError400_isDefinitive() {
        assertTrue(isDefinitiveAuthFailure(AppError.ServerError(400)))
    }

    @Test
    fun serverError422_isDefinitive() {
        assertTrue(isDefinitiveAuthFailure(AppError.ServerError(422)))
    }

    @Test
    fun serverError500_isNotDefinitive() {
        assertFalse(
            isDefinitiveAuthFailure(AppError.ServerError(500)),
            "een 5xx-serverfout betekent niet dat de refresh token ongeldig is — mag niet uitloggen",
        )
    }

    @Test
    fun networkUnavailable_isNotDefinitive() {
        assertFalse(
            isDefinitiveAuthFailure(AppError.NetworkUnavailable),
            "geen internet mag de gebruiker nooit uitloggen (kern van de offline-first bugfix)",
        )
    }

    @Test
    fun networkTimeout_isNotDefinitive() {
        assertFalse(isDefinitiveAuthFailure(AppError.NetworkTimeout))
    }

    @Test
    fun unknownWrappedException_isNotDefinitive() {
        assertFalse(isDefinitiveAuthFailure(AppError.Unknown(RuntimeException("boom"))))
    }

    @Test
    fun genericNonAppError_isNotDefinitive() {
        assertFalse(isDefinitiveAuthFailure(IllegalStateException("unexpected")))
    }
}
