package com.example.verbolandia.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.verbolandia.model.PhrasalVerb
import com.example.verbolandia.model.Verb
import com.example.verbolandia.model.VerbTense
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class DatabaseHelper private constructor(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "verbs.db"
        private const val DATABASE_VERSION = 3
        private const val TABLE_VERBS = "verbs"
        private const val TABLE_TENSES = "verb_tenses"
        private const val TABLE_PHRASAL = "phrasal_verbs"

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
        var version = 2
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
            "SELECT * FROM $TABLE_VERBS ORDER BY base ASC LIMIT $limit",
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
            "SELECT * FROM $TABLE_VERBS WHERE common = 1 ORDER BY base ASC LIMIT $limit",
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
        readableDatabase.rawQuery("SELECT COUNT(*), SUM(irregular) FROM $TABLE_VERBS WHERE common = 1", null).use { cursor ->
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
            SELECT * 
            FROM $TABLE_VERBS 
            WHERE base LIKE ? OR past LIKE ? OR past_participle LIKE ? OR gerund LIKE ? OR spanish_meaning LIKE ?
            ORDER BY 
              CASE 
                WHEN LOWER(base) = ? THEN 1 
                WHEN LOWER(base) LIKE ? THEN 2 
                WHEN LOWER(spanish_meaning) = ? THEN 3
                WHEN LOWER(spanish_meaning) LIKE ? THEN 4
                WHEN LOWER(base) LIKE ? THEN 5 
                WHEN LOWER(past) = ? OR LOWER(past_participle) = ? THEN 6
                ELSE 7 
              END, base ASC 
            LIMIT ?
        """.trimIndent()

        val pattern = "%$cleanQuery%"
        val prefixPattern = "$cleanQuery%"

        readableDatabase.rawQuery(
            sql,
            arrayOf(
                pattern, pattern, pattern, pattern, pattern,
                cleanQuery, prefixPattern, cleanQuery, prefixPattern, pattern, cleanQuery, cleanQuery,
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
            "SELECT * FROM $TABLE_VERBS WHERE LOWER(base) = ? LIMIT 1",
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
            "SELECT * FROM $TABLE_VERBS WHERE base IN ($placeholders)",
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

    fun getTensesForVerb(verbId: Int): List<VerbTense> {
        val list = mutableListOf<VerbTense>()
        readableDatabase.rawQuery(
            "SELECT id, verb_id, tense_code, tense_name_en, tense_name_es, affirmative_en, negative_en, interrogative_en, example_es FROM $TABLE_TENSES WHERE verb_id = ? ORDER BY id ASC",
            arrayOf(verbId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(
                        VerbTense(
                            id = cursor.getInt(0),
                            verbId = cursor.getInt(1),
                            tenseCode = cursor.getString(2),
                            tenseNameEn = cursor.getString(3),
                            tenseNameEs = cursor.getString(4),
                            affirmativeEn = cursor.getString(5),
                            negativeEn = cursor.getString(6),
                            interrogativeEn = cursor.getString(7),
                            exampleEs = cursor.getString(8)
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        return list
    }

    fun getPhrasalVerbs(verbId: Int): List<PhrasalVerb> {
        val list = mutableListOf<PhrasalVerb>()
        readableDatabase.rawQuery(
            "SELECT id, verb_id, phrasal_en, meaning_es, example_en, example_es FROM $TABLE_PHRASAL WHERE verb_id = ? ORDER BY id ASC",
            arrayOf(verbId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(
                        PhrasalVerb(
                            id = cursor.getInt(0),
                            verbId = cursor.getInt(1),
                            phrasalEn = cursor.getString(2),
                            meaningEs = cursor.getString(3),
                            exampleEn = cursor.getString(4),
                            exampleEs = cursor.getString(5)
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        return list
    }

    private fun cursorToVerb(cursor: android.database.Cursor): Verb {
        val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
        val base = cursor.getString(cursor.getColumnIndexOrThrow("base"))
        val thirdPerson = cursor.getString(cursor.getColumnIndexOrThrow("third_person"))
        val past = cursor.getString(cursor.getColumnIndexOrThrow("past"))
        val pastParticiple = cursor.getString(cursor.getColumnIndexOrThrow("past_participle"))
        val gerund = cursor.getString(cursor.getColumnIndexOrThrow("gerund"))
        val irregularIdx = cursor.getColumnIndex("irregular")
        val irregular = if (irregularIdx >= 0 && !cursor.isNull(irregularIdx)) cursor.getInt(irregularIdx) == 1 else false

        val commonIdx = cursor.getColumnIndex("common")
        val common = if (commonIdx >= 0 && !cursor.isNull(commonIdx)) cursor.getInt(commonIdx) == 1 else true

        val spanishIdx = cursor.getColumnIndex("spanish_meaning")
        val spanishMeaning = if (spanishIdx >= 0 && !cursor.isNull(spanishIdx)) cursor.getString(spanishIdx) else ""

        val ipaBaseIdx = cursor.getColumnIndex("ipa_base")
        val ipaBase = if (ipaBaseIdx >= 0 && !cursor.isNull(ipaBaseIdx)) cursor.getString(ipaBaseIdx) else ""

        val ipaPastIdx = cursor.getColumnIndex("ipa_past")
        val ipaPast = if (ipaPastIdx >= 0 && !cursor.isNull(ipaPastIdx)) cursor.getString(ipaPastIdx) else ""

        val ipaPpIdx = cursor.getColumnIndex("ipa_participle")
        val ipaPp = if (ipaPpIdx >= 0 && !cursor.isNull(ipaPpIdx)) cursor.getString(ipaPpIdx) else ""

        val levelIdx = cursor.getColumnIndex("cefr_level")
        val level = if (levelIdx >= 0 && !cursor.isNull(levelIdx)) cursor.getString(levelIdx)
            else com.example.verbolandia.util.VerbClassifier.getLevel(base, irregular)

        val morphIdx = cursor.getColumnIndex("morph_group")
        val morphGroup = if (morphIdx >= 0 && !cursor.isNull(morphIdx)) cursor.getString(morphIdx) else "regular_ed"

        val phonGroupIdx = cursor.getColumnIndex("phonetic_group_id")
        val phoneticGroupId = if (phonGroupIdx >= 0 && !cursor.isNull(phonGroupIdx)) cursor.getInt(phonGroupIdx) else 1

        val errorTipIdx = cursor.getColumnIndex("common_error_tip")
        val commonErrorTip = if (errorTipIdx >= 0 && !cursor.isNull(errorTipIdx)) cursor.getString(errorTipIdx) else ""

        val phonetic = if (ipaBase.isNotBlank()) ipaBase else "/${base.lowercase()}/"

        return Verb(
            id = id,
            base = base,
            thirdPerson = thirdPerson,
            past = past,
            pastParticiple = pastParticiple,
            gerund = gerund,
            isIrregular = irregular,
            isCommon = common,
            spanishMeaning = spanishMeaning,
            phonetic = phonetic,
            level = level,
            ipaBase = ipaBase,
            ipaPast = ipaPast,
            ipaParticiple = ipaPp,
            morphGroup = morphGroup,
            phoneticGroupId = phoneticGroupId,
            commonErrorTip = commonErrorTip
        )
    }
}
