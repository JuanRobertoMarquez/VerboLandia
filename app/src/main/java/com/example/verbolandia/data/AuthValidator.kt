package com.example.verbolandia.data

/**
 * Validaciones puras para formularios de inicio de sesión y registro.
 * No depende de APIs de Android para facilitar pruebas unitarias en JVM.
 */
object AuthValidator {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    const val MIN_PASSWORD_LENGTH = 6

    enum class ErrorType {
        EMPTY_NAME,
        EMPTY_CITY,
        EMPTY_COUNTRY,
        INVALID_PHONE,
        INVALID_EMAIL,
        SHORT_PASSWORD
    }

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    fun isValidPhone(phone: String): Boolean {
        val digits = phone.trim().filter { it.isDigit() }
        return digits.length >= 7
    }

    fun validateLogin(email: String, password: String): ErrorType? {
        if (!isValidEmail(email)) return ErrorType.INVALID_EMAIL
        if (!isValidPassword(password)) return ErrorType.SHORT_PASSWORD
        return null
    }

    fun validateSignup(
        name: String,
        city: String,
        country: String,
        phone: String,
        email: String,
        password: String
    ): ErrorType? {
        if (name.trim().isEmpty()) return ErrorType.EMPTY_NAME
        if (city.trim().isEmpty()) return ErrorType.EMPTY_CITY
        if (country.trim().isEmpty()) return ErrorType.EMPTY_COUNTRY
        if (!isValidPhone(phone)) return ErrorType.INVALID_PHONE
        if (!isValidEmail(email)) return ErrorType.INVALID_EMAIL
        if (!isValidPassword(password)) return ErrorType.SHORT_PASSWORD
        return null
    }
}
