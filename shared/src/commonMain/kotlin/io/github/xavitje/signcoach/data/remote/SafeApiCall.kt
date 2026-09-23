package io.github.xavitje.signcoach.data.remote

import io.github.xavitje.signcoach.domain.AppError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.SerializationException

/**
 * Helpt bij het afvangen van alle mogelijke netwerkfouten (Zero Trust).
 * Voert het [block] uit en mapt Exceptions naar een sterke [AppError].
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        val appError = when (e) {
            is AppError -> e
            is HttpRequestTimeoutException,
            is ConnectTimeoutException,
            is SocketTimeoutException -> AppError.NetworkTimeout

            is IOException -> AppError.NetworkUnavailable

            is ResponseException -> {
                when (e.response.status.value) {
                    401, 403 -> AppError.Unauthorized
                    404 -> AppError.NotFound
                    else -> AppError.ServerError(e.response.status.value, e.message)
                }
            }

            is SerializationException -> AppError.MalformedData

            else -> AppError.Unknown(e)
        }
        Result.failure(appError)
    }
}
