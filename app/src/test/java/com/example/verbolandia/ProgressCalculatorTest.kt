package com.example.verbolandia

import com.example.verbolandia.util.AttemptRecord
import com.example.verbolandia.util.ProgressCalculator
import com.example.verbolandia.util.VerbStatsRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressCalculatorTest {

    @Test
    fun testEmptyProgressReturnsZeros() {
        val clock = TestClock(1700000000000L)
        val calculator = ProgressCalculator(clock)

        val summary = calculator.calculate(emptyList(), emptyList(), emptyMap())

        assertEquals(0, summary.globalRetention30DaysPct)
        assertEquals(0, summary.masteredVerbsCount)
        assertEquals(0, summary.criticalZoneCount)
        assertEquals(0, summary.todayCompletedCount)
    }

    @Test
    fun testRetentionAndCriticalZoneCalculation() {
        val nowMs = 1700000000000L
        val clock = TestClock(nowMs)
        val calculator = ProgressCalculator(clock)

        val attempts = listOf(
            AttemptRecord("break", true, "quiz", nowMs - 1000),
            AttemptRecord("break", true, "quiz", nowMs - 2000),
            AttemptRecord("go", false, "quiz", nowMs - 3000),
            AttemptRecord("go", false, "quiz", nowMs - 4000),
            AttemptRecord("go", false, "quiz", nowMs - 5000)
        )

        val stats = listOf(
            VerbStatsRecord("break", views = 5, attempts = 5, correctCount = 5, mastery = 100),
            VerbStatsRecord("go", views = 5, attempts = 3, correctCount = 0, mastery = 0)
        )

        val summary = calculator.calculate(attempts, stats, emptyMap())

        assertEquals(1, summary.masteredVerbsCount)
        assertEquals(1, summary.criticalZoneCount)
        assertEquals(5, summary.todayCompletedCount)
    }
}
