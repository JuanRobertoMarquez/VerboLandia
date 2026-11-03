package com.example.verbolandia

import com.example.verbolandia.util.HistoryPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPolicyTest {

    @Test
    fun testHistoryExpirationAt72Hours() {
        val initialTimeMs = 1700000000000L
        val clock = TestClock(initialTimeMs)
        val policy = HistoryPolicy(clock)

        val searchedAt = initialTimeMs

        // At 70 hours: not expired
        val at70Hours = initialTimeMs + (70L * 3600 * 1000)
        assertFalse(policy.isExpired(searchedAt, at70Hours))

        // At 73 hours: expired!
        val at73Hours = initialTimeMs + (73L * 3600 * 1000)
        assertTrue(policy.isExpired(searchedAt, at73Hours))
    }
}
