package com.example.verbolandia.util

interface Clock {
    fun currentTimeMillis(): Long
}

class DefaultClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
