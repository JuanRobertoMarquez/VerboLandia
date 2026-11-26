package com.example.verbolandia.util

import androidx.annotation.StringRes
import com.example.verbolandia.R
import com.example.verbolandia.data.AuthException

/** Traduce los errores del repositorio de autenticación al mensaje que ve el usuario. */
object AuthErrorMapper {

    @StringRes
    fun messageFor(error: Throwable): Int = when ((error as? AuthException)?.code) {
        AuthException.ErrorCode.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
        AuthException.ErrorCode.EMAIL_ALREADY_EXISTS -> R.string.auth_error_email_exists
        AuthException.ErrorCode.NETWORK_ERROR -> R.string.auth_error_network
        AuthException.ErrorCode.NOT_AUTHENTICATED, null -> R.string.auth_error_generic
    }
}
