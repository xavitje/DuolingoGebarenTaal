package io.github.xavitje.signcoach.ui.common

import androidx.compose.runtime.Composable
import io.github.xavitje.signcoach.domain.AppError
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Vertaalt een [AppError] naar een gelokaliseerde string via Compose Resources.
 */
@Composable
fun AppError.toLocalizedString(): String {
    return when (this) {
        is AppError.NetworkUnavailable -> stringResource(Res.string.error_network_unavailable)
        is AppError.NetworkTimeout -> stringResource(Res.string.error_network_timeout)
        is AppError.Unauthorized -> stringResource(Res.string.error_unauthorized)
        is AppError.NotFound -> stringResource(Res.string.error_not_found)
        is AppError.MalformedData -> stringResource(Res.string.error_malformed_data)
        is AppError.ServerError -> stringResource(Res.string.error_server, statusCode.toString(), details ?: "")
        
        is AppError.FieldEmpty -> stringResource(Res.string.error_field_empty)
        is AppError.InvalidCredentials -> stringResource(Res.string.error_invalid_credentials)
        is AppError.PasswordsDoNotMatch -> stringResource(Res.string.error_passwords_do_not_match)
        is AppError.PasswordTooShort -> stringResource(Res.string.error_password_too_short)
        
        is AppError.CameraPermissionDenied -> stringResource(Res.string.error_camera_permission_denied)
        is AppError.NoHandDetected -> stringResource(Res.string.error_no_hand_detected)
        is AppError.TemplateLoadFailed -> stringResource(Res.string.error_template_load_failed)
        is AppError.TrackerError -> stringResource(Res.string.error_tracker, this.detail ?: "Unknown")
        is AppError.NoGestureSelected -> "Kies eerst een gebaar." // TODO: add to strings.xml later if time permits
        is AppError.GestureNotAvailable -> "Dit gebaar is niet beschikbaar." // TODO: add to strings.xml later if time permits

        is AppError.Unknown -> this.cause.message ?: stringResource(Res.string.error_unknown)
    }
}
