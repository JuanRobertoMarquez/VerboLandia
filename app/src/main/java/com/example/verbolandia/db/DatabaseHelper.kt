package com.example.verbolandia.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.verbolandia.model.Verb
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class DatabaseHelper private constructor(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "verbs.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_VERBS = "verbs"

        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                val instance = DatabaseHelper(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        ensureDatabaseUpdated()
    }

    @Synchronized
    private fun ensureDatabaseUpdated() {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        val assetVersion = getAssetUserVersion()
        val currentVersion = getCurrentDbUserVersion(dbFile)

        if (!dbFile.exists() || currentVersion < assetVersion) {
            copyDatabaseFromAssetsAtomic(dbFile)
        }
    }

    private fun getAssetUserVersion(): Int {
        var version = 1
        try {
            val tmpAssetFile = File.createTempFile("asset_ver_check", ".db", context.cacheDir)
            context.assets.open(DATABASE_NAME).use { input ->
                FileOutputStream(tmpAssetFile).use { output ->
                    input.copyTo(output)
                }
            }
            SQLiteDatabase.openDatabase(tmpAssetFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                version = db.version
            }
            tmpAssetFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return version
    }

    private fun getCurrentDbUserVersion(dbFile: File): Int {
        if (!dbFile.exists()) return 0
        return try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.version
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun copyDatabaseFromAssetsAtomic(targetFile: File) {
        targetFile.parentFile?.mkdirs()
        val tmpFile = File(targetFile.parentFile, "$DATABASE_NAME.tmp")

        try {
            context.assets.open(DATABASE_NAME).use { input ->
                FileOutputStream(tmpFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            tmpFile.renameTo(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {}

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    fun getAllVerbs(limit: Int = 2000): List<Verb> {
        val list = mutableListOf<Verb>()
        readableDatabase.rawQuery(
            "SELECT id, base, third_person, past, past_participle, gerund, irregular, common FROM $TABLE_VERBS ORDER BY base ASC LIMIT $limit",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursorToVerb(cursor))
                } while (cursor.moveToNext())
            }
        }
        return list
    }

    fun getCommonVerbs(limit: Int = 500): List<Verb> {
        val list = mutableListOf<Verb>()
        readableDatabase.rawQuery(
            "SELECT id, base, third_person, past, past_participle, gerund, irregular, common FROM $TABLE_VERBS WHERE common = 1 ORDER BY base ASC LIMIT $limit",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursorToVerb(cursor))
                } while (cursor.moveToNext())
            }
        }
        return list
    }

    fun getTotalVerbsCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_VERBS", null).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }
        return 0
    }

    fun getCountsByCategory(): Triple<Int, Int, Int> {
        var total = 0
        var irregular = 0
        var regular = 0
        readableDatabase.rawQuery("SELECT COUNT(*), SUM(irregular) FROM $TABLE_VERBS", null).use { cursor ->
            if (cursor.moveToFirst()) {
                total = cursor.getInt(0)
                irregular = cursor.getInt(1)
                regular = total - irregular
            }
        }
        return Triple(total, irregular, regular)
    }

    fun searchVerbs(
        query: String,
        translationsMap: Map<String, String> = emptyMap(),
        limit: Int = 20
    ): List<Verb> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return emptyList()

        // Find bases whose translation contains spanish query
        val matchedBasesFromSpanish = translationsMap.entries
            .filter { it.value.lowercase().contains(cleanQuery) }
            .map { it.key }
            .take(20)

        val list = mutableListOf<Verb>()
        val sql = """
            SELECT id, base, third_person, past, past_participle, gerund, irregular, common 
            FROM $TABLE_VERBS 
            WHERE base LIKE ? OR past LIKE ? OR past_participle LIKE ? OR gerund LIKE ?
            ORDER BY 
              CASE 
                WHEN LOWER(base) = ? THEN 1 
                WHEN LOWER(base) LIKE ? THEN 2 
                WHEN LOWER(base) LIKE ? THEN 3 
                WHEN LOWER(past) = ? OR LOWER(past_participle) = ? THEN 4
                ELSE 5 
              END, base ASC 
            LIMIT ?
        """.trimIndent()

        val pattern = "%$cleanQuery%"
        val prefixPattern = "$cleanQuery%"

        readableDatabase.rawQuery(
            sql,
            arrayOf(
                pattern, pattern, pattern, pattern,
                cleanQuery, prefixPattern, pattern, cleanQuery, cleanQuery,
                limit.toString()
            )
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursorToVerb(cursor))
                } while (cursor.moveToNext())
            }
        }

        // If results < limit and we have spanish matches, append missing spanish matches
        if (list.size < limit && matchedBasesFromSpanish.isNotEmpty()) {
            val existingBases = list.map { it.base }.toSet()
            for (base in matchedBasesFromSpanish) {
                if (base !in existingBases) {
                    val verb = getVerbByBase(base)
                    if (verb != null) {
                        list.add(verb)
                        if (list.size >= limit) break
                    }
                }
            }
        }

        return list
    }

    fun getVerbByBase(base: String): Verb? {
        readableDatabase.rawQuery(
            "SELECT id, base, third_person, past, past_participle, gerund, irregular, common FROM $TABLE_VERBS WHERE LOWER(base) = ? LIMIT 1",
            arrayOf(base.trim().lowercase())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToVerb(cursor)
            }
        }
        return null
    }

    fun getVerbsByBases(bases: List<String>): List<Verb> {
        if (bases.isEmpty()) return emptyList()
        val list = mutableListOf<Verb>()
        val placeholders = bases.joinToString(",") { "?" }
        readableDatabase.rawQuery(
            "SELECT id, base, third_person, past, past_participle, gerund, irregular, common FROM $TABLE_VERBS WHERE base IN ($placeholders)",
            bases.toTypedArray()
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursorToVerb(cursor))
                } while (cursor.moveToNext())
            }
        }
        // Retain original order of bases
        val map = list.associateBy { it.base }
        return bases.mapNotNull { map[it] }
    }

    private fun cursorToVerb(cursor: android.database.Cursor): Verb {
        val id = cursor.getInt(0)
        val base = cursor.getString(1)
        val thirdPerson = cursor.getString(2)
        val past = cursor.getString(3)
        val pastParticiple = cursor.getString(4)
        val gerund = cursor.getString(5)
        val irregular = cursor.getInt(6) == 1
        val common = cursor.getInt(7) == 1

        val phonetic = "/${base.lowercase()}/"
        val level = if (irregular) "B1" else "A1"

        return Verb(
            id = id,
            base = base,
            thirdPerson = thirdPerson,
            past = past,
            pastParticiple = pastParticiple,
            gerund = gerund,
            isIrregular = irregular,
            isCommon = common,
            spanishMeaning = "", // populated by repository
            phonetic = phonetic,
            level = level
        )
    }
}
