package com.example.verbolandia.data

import android.content.Context
import com.example.verbolandia.db.DatabaseHelper
import com.example.verbolandia.model.Verb

class VerbRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)
    private val prefs = context.getSharedPreferences("verbolandia_prefs", Context.MODE_PRIVATE)
    private val transPrefs = context.getSharedPreferences("verbolandia_translations", Context.MODE_PRIVATE)

    fun getVerbs(): List<Verb> {
        val verbs = dbHelper.getAllVerbs(500)
        return applySavedState(verbs)
    }

    fun getTotalCount(): Int {
        return dbHelper.getTotalVerbsCount()
    }

    fun searchVerbs(query: String): List<Verb> {
        val verbs = dbHelper.searchVerbs(query)
        return applySavedState(verbs)
    }

    fun getVerbByBase(base: String): Verb? {
        val verb = dbHelper.getVerbByBase(base) ?: return null
        val favorites = getFavoriteBases()
        verb.isFavorite = favorites.contains(verb.base)
        verb.mastery = prefs.getInt("mastery_${verb.base}", 0)
        return verb
    }

    fun saveTranslation(verbBase: String, translation: String) {
        transPrefs.edit().putString("trans_$verbBase", translation).apply()
    }

    fun getRandomVerbs(count: Int = 10): List<Verb> {
        return applySavedState(dbHelper.getRandomVerbs(count))
    }

    fun getVerbOfDay(): Verb {
        val verb = dbHelper.getVerbOfDay()
        val favorites = getFavoriteBases()
        verb.isFavorite = favorites.contains(verb.base)
        verb.mastery = prefs.getInt("mastery_${verb.base}", 0)
        return verb
    }

    fun toggleFavorite(verbBase: String): Boolean {
        val favorites = getFavoriteBases().toMutableSet()
        val isFav = if (favorites.contains(verbBase)) {
            favorites.remove(verbBase)
            false
        } else {
            favorites.add(verbBase)
            true
        }
        prefs.edit().putStringSet("favorites", favorites).apply()
        return isFav
    }

    fun getFavoriteBases(): Set<String> {
        return prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    }

    fun updateMastery(verbBase: String, newMastery: Int) {
        prefs.edit().putInt("mastery_$verbBase", newMastery).apply()
    }

    fun getPracticedCount(): Int {
        return prefs.getInt("total_practiced", 0)
    }

    fun incrementPracticedCount() {
        val current = getPracticedCount()
        prefs.edit().putInt("total_practiced", current + 1).apply()
    }

    fun resetProgress() {
        prefs.edit().clear().apply()
    }

    private fun applySavedState(verbs: List<Verb>): List<Verb> {
        val favorites = getFavoriteBases()
        return verbs.map { verb ->
            verb.isFavorite = favorites.contains(verb.base)
            verb.mastery = prefs.getInt("mastery_${verb.base}", 0)
            verb
        }
    }
}
