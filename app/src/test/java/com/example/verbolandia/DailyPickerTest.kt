package com.example.verbolandia

import com.example.verbolandia.model.Verb
import com.example.verbolandia.util.Clock
import com.example.verbolandia.util.DailyPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestClock(var timeMs: Long) : Clock {
    override fun currentTimeMillis(): Long = timeMs
}

class DailyPickerTest {

    @Test
    fun testSameDayProducesSameDeterministicSelection() {
        val testClock = TestClock(1700000000000L)
        val picker = DailyPicker(testClock)

        val candidates = (1..20).map { i ->
            Verb(id = i, base = "verb$i", thirdPerson = "verb${i}s", past = "verb${i}ed", pastParticiple = "verb${i}ed", gerund = "verb${i}ing", isCommon = true)
        }

        val dayStr = picker.getTodayDayString()
        val pick1 = picker.selectDailyVerbs(candidates, emptySet(), dayStr, 5)
        val pick2 = picker.selectDailyVerbs(candidates, emptySet(), dayStr, 5)

        assertEquals(5, pick1.size)
        assertEquals(pick1.map { it.base }, pick2.map { it.base })
    }

    @Test
    fun testExcludesRecentlyUsedBases() {
        val testClock = TestClock(1700000000000L)
        val picker = DailyPicker(testClock)

        val candidates = (1..20).map { i ->
            Verb(id = i, base = "verb$i", thirdPerson = "verb${i}s", past = "verb${i}ed", pastParticiple = "verb${i}ed", gerund = "verb${i}ing", isCommon = true)
        }

        val recentlyUsed = setOf("verb1", "verb2", "verb3")
        val pick = picker.selectDailyVerbs(candidates, recentlyUsed, "20261029", 5)

        for (verb in pick) {
            assertTrue(verb.base !in recentlyUsed)
        }
    }
}
