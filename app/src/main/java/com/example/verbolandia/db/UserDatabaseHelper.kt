package com.example.verbolandia.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.verbolandia.util.AttemptRecord
import com.example.verbolandia.util.VerbStatsRecord

class UserDatabaseHelper private constructor(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "user_data.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: UserDatabaseHelper? = null

        fun getInstance(context: Context): UserDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                val instance = UserDatabaseHelper(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                base TEXT PRIMARY KEY,
                searched_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_picks (
                day TEXT NOT NULL,
                position INTEGER NOT NULL,
                base TEXT NOT NULL,
                PRIMARY KEY(day, position)
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS verb_stats (
                base TEXT PRIMARY KEY,
                views INTEGER DEFAULT 0,
                attempts INTEGER DEFAULT 0,
                correct INTEGER DEFAULT 0,
                favorite INTEGER DEFAULT 0,
                mastery INTEGER DEFAULT 0,
                last_error_at INTEGER DEFAULT 0,
                last_correct_at INTEGER DEFAULT 0
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS practice_attempts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                base TEXT NOT NULL,
                correct INTEGER NOT NULL,
                mode TEXT NOT NULL,
                answered_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS translations (
                base TEXT PRIMARY KEY,
                spanish TEXT NOT NULL,
                source TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        migrateFavoritesFromSharedPreferences(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        migrateFavoritesFromSharedPreferences(db)
    }

    private fun migrateFavoritesFromSharedPreferences(db: SQLiteDatabase) {
        try {
            val prefs = context.getSharedPreferences("verbolandia_prefs", Context.MODE_PRIVATE)
            val favSet = prefs.getStringSet("favorites", null) ?: return
            if (favSet.isEmpty()) return

            db.beginTransaction()
            try {
                for (base in favSet) {
                    val values = ContentValues().apply {
                        put("base", base)
                        put("favorite", 1)
                    }
                    db.insertWithOnConflict("verb_stats", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                    db.execSQL("UPDATE verb_stats SET favorite = 1 WHERE base = ?", arrayOf(base))
                }
                db.setTransactionSuccessful()
                prefs.edit().remove("favorites").apply()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Search History ---
    fun addSearchHistory(base: String, searchedAt: Long, expirationCutoffMs: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Purge expired entries (> 72 hours)
            db.delete("search_history", "searched_at < ?", arrayOf(expirationCutoffMs.toString()))

            val values = ContentValues().apply {
                put("base", base)
                put("searched_at", searchedAt)
            }
            db.insertWithOnConflict("search_history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getSearchHistory(expirationCutoffMs: Long, limit: Int = 8): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase

        // Purge expired
        writableDatabase.delete("search_history", "searched_at < ?", arrayOf(expirationCutoffMs.toString()))

        db.rawQuery(
            "SELECT base FROM search_history WHERE searched_at >= ? ORDER BY searched_at DESC LIMIT ?",
            arrayOf(expirationCutoffMs.toString(), limit.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursor.getString(0))
                } while (cursor.moveToNext())
            }
        }
        return list
    }

    fun clearSearchHistory() {
        writableDatabase.delete("search_history", null, null)
    }

    // --- Daily Picks ---
    fun getDailyPicks(day: String): List<String> {
        val list = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT base FROM daily_picks WHERE day = ? ORDER BY position ASC",
            arrayOf(day)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursor.getString(0))
                } while (cursor.moveToNext())
            }
        }
        return list
    }

    fun saveDailyPicks(day: String, bases: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            bases.forEachIndexed { index, base ->
                val values = ContentValues().apply {
                    put("day", day)
                    put("position", index)
                    put("base", base)
                }
                db.insertWithOnConflict("daily_picks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getBasesPickedInDays(days: List<String>): Set<String> {
        val set = mutableSetOf<String>()
        if (days.isEmpty()) return set
        val placeholders = days.joinToString(",") { "?" }
        readableDatabase.rawQuery(
            "SELECT DISTINCT base FROM daily_picks WHERE day IN ($placeholders)",
            days.toTypedArray()
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    set.add(cursor.getString(0))
                } while (cursor.moveToNext())
            }
        }
        return set
    }

    // --- Verb Stats & Favorites ---
    fun getVerbStats(base: String): VerbStatsRecord {
        readableDatabase.rawQuery(
            "SELECT views, attempts, correct, favorite, mastery, last_error_at, last_correct_at FROM verb_stats WHERE base = ?",
            arrayOf(base)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return VerbStatsRecord(
                    base = base,
                    views = cursor.getInt(0),
                    attempts = cursor.getInt(1),
                    correctCount = cursor.getInt(2),
                    isFavorite = cursor.getInt(3) == 1,
                    mastery = cursor.getInt(4),
                    lastErrorAtMs = cursor.getLong(5),
                    lastCorrectAtMs = cursor.getLong(6)
                )
            }
        }
        return VerbStatsRecord(base = base)
    }

    fun getAllVerbStats(): List<VerbStatsRecord> {
        val list = mutableListOf<VerbStatsRecord>()
        readableDatabase.rawQuery(
            "SELECT base, views, attempts, correct, favorite, mastery, last_error_at, last_correct_at FROM verb_stats",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(
                        VerbStatsRecord(
                            base = cursor.getString(0),
                            views = cursor.getInt(1),
                            attempts = cursor.getInt(2),
                            correctCount = cursor.getInt(3),
                            isFavorite = cursor.getInt(4) == 1,
                            mastery = cursor.getInt(5),
                            lastErrorAtMs = cursor.getLong(6),
                            lastCorrectAtMs = cursor.getLong(7)
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        return list
    }

    fun incrementViews(base: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("base", base)
            put("views", 1)
        }
        db.insertWithOnConflict("verb_stats", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        db.execSQL("UPDATE verb_stats SET views = views + 1 WHERE base = ?", arrayOf(base))
    }

    fun toggleFavorite(base: String): Boolean {
        val db = writableDatabase
        val currentStats = getVerbStats(base)
        val newFav = if (currentStats.isFavorite) 0 else 1
        val values = ContentValues().apply {
            put("base", base)
            put("favorite", newFav)
        }
        db.insertWithOnConflict("verb_stats", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        db.execSQL("UPDATE verb_stats SET favorite = ? WHERE base = ?", arrayOf(newFav.toString(), base))
        return newFav == 1
    }

    fun recordPracticeAttempt(base: String, isCorrect: Boolean, mode: String, nowMs: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("base", base)
                put("correct", if (isCorrect) 1 else 0)
                put("mode", mode)
                put("answered_at", nowMs)
            }
            db.insert("practice_attempts", null, values)

            val last5Attempts = mutableListOf<Boolean>()
            db.rawQuery(
                "SELECT correct FROM practice_attempts WHERE base = ? ORDER BY id DESC LIMIT 5",
                arrayOf(base)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        last5Attempts.add(cursor.getInt(0) == 1)
                    } while (cursor.moveToNext())
                }
            }
            val newMastery = if (last5Attempts.isNotEmpty()) {
                ((last5Attempts.count { it }.toDouble() / last5Attempts.size) * 100).toInt()
            } else 0

            val currentStats = getVerbStats(base)
            val newAttempts = currentStats.attempts + 1
            val newCorrect = currentStats.correctCount + (if (isCorrect) 1 else 0)
            val lastErr = if (!isCorrect) nowMs else currentStats.lastErrorAtMs
            val lastCorr = if (isCorrect) nowMs else currentStats.lastCorrectAtMs

            val statsValues = ContentValues().apply {
                put("base", base)
                put("attempts", newAttempts)
                put("correct", newCorrect)
                put("mastery", newMastery)
                put("last_error_at", lastErr)
                put("last_correct_at", lastCorr)
            }
            db.insertWithOnConflict("verb_stats", null, statsValues, SQLiteDatabase.CONFLICT_IGNORE)
            db.execSQL(
                """
                UPDATE verb_stats 
                SET attempts = ?, correct = ?, mastery = ?, last_error_at = ?, last_correct_at = ? 
                WHERE base = ?
                """.trimIndent(),
                arrayOf(newAttempts.toString(), newCorrect.toString(), newMastery.toString(), lastErr.toString(), lastCorr.toString(), base)
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllPracticeAttempts(): List<AttemptRecord> {
        val list = mutableListOf<AttemptRecord>()
        readableDatabase.rawQuery(
            "SELECT base, correct, mode, answered_at FROM practice_attempts ORDER BY answered_at ASC",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(
                        AttemptRecord(
                            base = cursor.getString(0),
                            correct = cursor.getInt(1) == 1,
                            mode = cursor.getString(2),
                            answeredAtMs = cursor.getLong(3)
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        return list
    }

    fun resetProgress() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("practice_attempts", null, null)
            db.delete("verb_stats", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --- Translations ---
    fun getTranslation(base: String): String? {
        readableDatabase.rawQuery(
            "SELECT spanish FROM translations WHERE base = ? LIMIT 1",
            arrayOf(base)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    fun saveTranslation(base: String, spanish: String, source: String, nowMs: Long) {
        val values = ContentValues().apply {
            put("base", base)
            put("spanish", spanish)
            put("source", source)
            put("updated_at", nowMs)
        }
        writableDatabase.insertWithOnConflict("translations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllTranslations(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        readableDatabase.rawQuery("SELECT base, spanish FROM translations", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    map[cursor.getString(0)] = cursor.getString(1)
                } while (cursor.moveToNext())
            }
        }
        return map
    }
}
