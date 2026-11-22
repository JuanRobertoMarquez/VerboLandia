package com.example.verbolandia

import com.example.verbolandia.model.TriadVerbResult
import com.example.verbolandia.model.Verb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriadModelTest {

    @Test
    fun testTriadResultSuccessCreation() {
        val verb = Verb(base = "freeze", thirdPerson = "freezes", past = "froze", pastParticiple = "frozen", gerund = "freezing")
        val result = TriadVerbResult(
            verb = verb,
            isCorrect = true,
            wrongSelection = "",
            correctExpected = ""
        )

        assertTrue(result.isCorrect)
        assertEquals("freeze", result.verb.base)
        assertEquals("", result.wrongSelection)
    }

    @Test
    fun testTriadResultFailureRecordsErrorDetails() {
        val verb = Verb(base = "choose", thirdPerson = "chooses", past = "chose", pastParticiple = "chosen", gerund = "choosing")
        val result = TriadVerbResult(
            verb = verb,
            isCorrect = false,
            wrongSelection = "choosed",
            correctExpected = "chose"
        )

        assertFalse(result.isCorrect)
        assertEquals("choosed", result.wrongSelection)
        assertEquals("chose", result.correctExpected)
    }

    @Test
    fun testStreakComboExpFormula() {
        // Base XP is 10, bonus is streak * 3
        fun calculateExp(streak: Int): Int = 10 + (streak * 3)

        assertEquals(13, calculateExp(1))
        assertEquals(16, calculateExp(2))
        assertEquals(19, calculateExp(3))
        assertEquals(25, calculateExp(5))
    }
}
