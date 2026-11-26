package com.example.verbolandia

import com.example.verbolandia.data.AuthValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun `isValidEmail accepts standard emails and rejects malformed ones`() {
        assertTrue(AuthValidator.isValidEmail("gael@escom.ipn.mx"))
        assertTrue(AuthValidator.isValidEmail("  user.name+tag@example.com  "))
        assertFalse(AuthValidator.isValidEmail(""))
        assertFalse(AuthValidator.isValidEmail("not-an-email"))
        assertFalse(AuthValidator.isValidEmail("user@domain"))
    }

    @Test
    fun `isValidPassword requires at least 6 characters`() {
        assertFalse(AuthValidator.isValidPassword(""))
        assertFalse(AuthValidator.isValidPassword("12345"))
        assertTrue(AuthValidator.isValidPassword("123456"))
    }

    @Test
    fun `isValidPhone requires at least 7 digits`() {
        assertFalse(AuthValidator.isValidPhone("123456"))
        assertTrue(AuthValidator.isValidPhone("55 1234-5678"))
    }

    @Test
    fun `validateLogin returns specific error or null when valid`() {
        assertEquals(
            AuthValidator.ErrorType.INVALID_EMAIL,
            AuthValidator.validateLogin("bad", "123456")
        )
        assertEquals(
            AuthValidator.ErrorType.SHORT_PASSWORD,
            AuthValidator.validateLogin("gael@ipn.mx", "123")
        )
        assertNull(AuthValidator.validateLogin("gael@ipn.mx", "123456"))
    }

    @Test
    fun `validateSignup validates all fields in order`() {
        assertEquals(
            AuthValidator.ErrorType.EMPTY_NAME,
            AuthValidator.validateSignup("", "CDMX", "Mexico", "5512345678", "gael@ipn.mx", "123456")
        )
        assertEquals(
            AuthValidator.ErrorType.EMPTY_CITY,
            AuthValidator.validateSignup("Gael", "", "Mexico", "5512345678", "gael@ipn.mx", "123456")
        )
        assertEquals(
            AuthValidator.ErrorType.EMPTY_COUNTRY,
            AuthValidator.validateSignup("Gael", "CDMX", "", "5512345678", "gael@ipn.mx", "123456")
        )
        assertEquals(
            AuthValidator.ErrorType.INVALID_PHONE,
            AuthValidator.validateSignup("Gael", "CDMX", "Mexico", "123", "gael@ipn.mx", "123456")
        )
        assertNull(
            AuthValidator.validateSignup("Gael", "CDMX", "Mexico", "5512345678", "gael@ipn.mx", "123456")
        )
    }
}
