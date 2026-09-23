package io.github.xavitje.signcoach.domain

/**
 * Centraal foutmodel ("Zero Trust").
 * Alle exceptions van Ktor, JSON parsing, etc. worden gemapt naar dit domeinmodel
 * zodat de ViewModel strict-getypeerde fouten kent en de UI deze kan vertalen (i18n).
 */
sealed class AppError : Exception() {
    /** Geen internet, of verbinding mislukt (bijv. DNS lookup fail). */
    object NetworkUnavailable : AppError()

    /** Timeout tijdens de request (server antwoordt niet snel genoeg). */
    object NetworkTimeout : AppError()

    /** 401 Unauthorized of 403 Forbidden. */
    object Unauthorized : AppError()

    /** 404 Not Found. */
    object NotFound : AppError()

    /** Server gaf een fout terug (bijv. 500) of 400 Bad Request met specifieke status. */
    data class ServerError(val statusCode: Int, val details: String? = null) : AppError()

    /** JSON parsingsfout of onverwacht antwoordformaat. */
    object MalformedData : AppError()

    // --- Client-side Validation Errors ---
    object FieldEmpty : AppError()
    object InvalidCredentials : AppError()
    object PasswordsDoNotMatch : AppError()
    object PasswordTooShort : AppError()

    // --- Contribution & Coach Errors ---
    object CameraPermissionDenied : AppError()
    object NoHandDetected : AppError()
    object TemplateLoadFailed : AppError()
    data class TrackerError(val detail: String?) : AppError()
    object NoGestureSelected : AppError()
    object GestureNotAvailable : AppError()

    /** Alle andere onverwachte uitzonderingen (bijv. coroutine cancellations, filesystem errors). */
    data class Unknown(override val cause: Throwable) : AppError()
}
