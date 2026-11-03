package com.example.verbolandia.util

import com.example.verbolandia.model.Verb
import java.util.Calendar

data class AttemptRecord(
    val base: String,
    val correct: Boolean,
    val mode: String,
    val answeredAtMs: Long
)

data class VerbStatsRecord(
    val base: String,
    val views: Int = 0,
    val attempts: Int = 0,
    val correctCount: Int = 0,
    val isFavorite: Boolean = false,
    val mastery: Int = 0,
    val lastErrorAtMs: Long = 0,
    val lastCorrectAtMs: Long = 0
) {
    val errorRatePercentage: Int
        get() = if (attempts > 0) (((attempts - correctCount).toDouble() / attempts) * 100).toInt() else 0
}

data class ProgressSummary(
    val globalRetention30DaysPct: Int = 0,
    val globalRetentionDiffPct: Int = 0, // difference vs previous 30 days
    val masteredVerbsCount: Int = 0,
    val masteredVerbsPct: Int = 0,
    val criticalZoneCount: Int = 0,
    val todayCompletedCount: Int = 0,
    val weeklyAccuracyPctList: List<Int> = List(7) { 0 }, // 7 days (last 7 days or Mon-Sun)
    val weeklyMaxPct: Int = 0,
    val topWeakVerbs: List<VerbStatsRecord> = emptyList(),
    val regularAccuracyPct: Int = 0,
    val invariantAccuracyPct: Int = 0,
    val vowelChangeAccuracyPct: Int = 0
)

class ProgressCalculator(private val clock: Clock = DefaultClock()) {

    fun calculate(
        attempts: List<AttemptRecord>,
        stats: List<VerbStatsRecord>,
        verbsMap: Map<String, Verb> = emptyMap()
    ): ProgressSummary {
        val now = clock.currentTimeMillis()
        val msInDay = 24L * 60 * 60 * 1000

        // 30 Days Retention
        val last30DaysStart = now - 30 * msInDay
        val prev30DaysStart = now - 60 * msInDay

        val attemptsLast30 = attempts.filter { it.answeredAtMs in last30DaysStart..now }
        val attemptsPrev30 = attempts.filter { it.answeredAtMs in prev30DaysStart until last30DaysStart }

        val ret30Pct = if (attemptsLast30.isNotEmpty()) {
            ((attemptsLast30.count { it.correct }.toDouble() / attemptsLast30.size) * 100).toInt()
        } else 0

        val retPrev30Pct = if (attemptsPrev30.isNotEmpty()) {
            ((attemptsPrev30.count { it.correct }.toDouble() / attemptsPrev30.size) * 100).toInt()
        } else 0

        val retDiff = ret30Pct - retPrev30Pct

        // Mastered & Critical Zone
        val practicedStats = stats.filter { it.attempts > 0 }
        val mastered = practicedStats.filter { it.mastery >= 80 && it.attempts >= 3 }
        val masteredPct = if (practicedStats.isNotEmpty()) {
            ((mastered.size.toDouble() / practicedStats.size) * 100).toInt()
        } else 0

        val criticalZone = practicedStats.filter { it.attempts >= 3 && it.errorRatePercentage >= 30 }

        // Today practice
        val startOfTodayMs = getStartOfDayMs(now)
        val todayAttempts = attempts.filter { it.answeredAtMs >= startOfTodayMs }

        // Weekly 7 Days
        val weeklyPctList = mutableListOf<Int>()
        var maxWeekly = 0
        for (i in 6 downTo 0) {
            val dayStart = startOfTodayMs - i * msInDay
            val dayEnd = dayStart + msInDay
            val dayAttempts = attempts.filter { it.answeredAtMs in dayStart until dayEnd }
            val dayPct = if (dayAttempts.isNotEmpty()) {
                ((dayAttempts.count { it.correct }.toDouble() / dayAttempts.size) * 100).toInt()
            } else 0
            weeklyPctList.add(dayPct)
            if (dayPct > maxWeekly) maxWeekly = dayPct
        }

        // Top Weak Verbs (top 4 with highest error rate, min 3 attempts)
        val weakList = practicedStats
            .filter { it.attempts >= 3 && it.errorRatePercentage >= 20 }
            .sortedByDescending { it.errorRatePercentage }
            .take(4)

        // Typology Accuracy
        val regularAttempts = attempts.filter { att ->
            val v = verbsMap[att.base]
            v != null && VerbClassifier.classify(v) == VerbTypology.REGULAR_ED
        }
        val invariantAttempts = attempts.filter { att ->
            val v = verbsMap[att.base]
            v != null && VerbClassifier.classify(v) == VerbTypology.IRREGULAR_INVARIABLE
        }
        val vowelChangeAttempts = attempts.filter { att ->
            val v = verbsMap[att.base]
            v != null && VerbClassifier.classify(v) == VerbTypology.IRREGULAR_VOWEL_CHANGE
        }

        val regPct = if (regularAttempts.isNotEmpty()) ((regularAttempts.count { it.correct }.toDouble() / regularAttempts.size) * 100).toInt() else 0
        val invPct = if (invariantAttempts.isNotEmpty()) ((invariantAttempts.count { it.correct }.toDouble() / invariantAttempts.size) * 100).toInt() else 0
        val vowPct = if (vowelChangeAttempts.isNotEmpty()) ((vowelChangeAttempts.count { it.correct }.toDouble() / vowelChangeAttempts.size) * 100).toInt() else 0

        return ProgressSummary(
            globalRetention30DaysPct = ret30Pct,
            globalRetentionDiffPct = retDiff,
            masteredVerbsCount = mastered.size,
            masteredVerbsPct = masteredPct,
            criticalZoneCount = criticalZone.size,
            todayCompletedCount = todayAttempts.size,
            weeklyAccuracyPctList = weeklyPctList,
            weeklyMaxPct = maxWeekly,
            topWeakVerbs = weakList,
            regularAccuracyPct = regPct,
            invariantAccuracyPct = invPct,
            vowelChangeAccuracyPct = vowPct
        )
    }

    private fun getStartOfDayMs(timeMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
