package com.example.verbolandia.util

import com.example.verbolandia.model.Verb
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random

class DailyPicker(private val clock: Clock = DefaultClock()) {

    fun getTodayDayString(nowMs: Long = clock.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return sdf.format(Date(nowMs))
    }

    fun getRecentDayStrings(daysBack: Int = 7, nowMs: Long = clock.currentTimeMillis()): List<String> {
        val list = mutableListOf<String>()
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        for (i in 0 until daysBack) {
            list.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return list
    }

    fun selectDailyVerbs(
        candidates: List<Verb>,
        recentlyUsedBases: Set<String>,
        dayString: String = getTodayDayString(),
        count: Int = 10
    ): List<Verb> {
        // Filter candidates: prefer common=1, exclude recently used in last 7 days
        val pool = candidates.filter { it.base !in recentlyUsedBases }
        val finalPool = if (pool.size >= count) pool else candidates

        if (finalPool.isEmpty()) return emptyList()

        // Deterministic shuffling with seed generated from dayString (yyyyMMdd)
        val seed = dayString.toLongOrNull() ?: dayString.hashCode().toLong()
        val random = Random(seed)

        val shuffled = finalPool.shuffled(random)
        return shuffled.take(count)
    }
}
