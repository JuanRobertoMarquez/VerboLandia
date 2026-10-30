package com.example.verbolandia.util

class HistoryPolicy(private val clock: Clock = DefaultClock()) {

    companion object {
        const val MAX_HISTORY_ITEMS = 8
        const val EXPIRATION_MS = 72L * 60 * 60 * 1000 // 72 hours in milliseconds
    }

    fun isExpired(searchedAt: Long, currentTimeMs: Long = clock.currentTimeMillis()): Boolean {
        return (currentTimeMs - searchedAt) > EXPIRATION_MS
    }

    fun getCutoffTimestamp(currentTimeMs: Long = clock.currentTimeMillis()): Long {
        return currentTimeMs - EXPIRATION_MS
    }
}
