package com.example.verbolandia.data

import android.content.Context
import com.example.verbolandia.db.DatabaseHelper
import com.example.verbolandia.db.UserDatabaseHelper
import com.example.verbolandia.model.Verb
import com.example.verbolandia.util.Clock
import com.example.verbolandia.util.DailyPicker
import com.example.verbolandia.util.DefaultClock
import com.example.verbolandia.util.HistoryPolicy
import com.example.verbolandia.util.ProgressCalculator
import com.example.verbolandia.util.ProgressSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VerbRepository(
    context: Context,
    private val clock: Clock = DefaultClock()
) {

    companion object {
        @Volatile
        private var INSTANCE: VerbRepository? = null

        fun getInstance(context: Context): VerbRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = VerbRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        // Expanded Spanish translations map for built-in local dictionary
        private val LOCAL_SPANISH_DICTIONARY = mapOf(
            "accept" to "Aceptar",
            "add" to "Añadir, agregar",
            "admit" to "Admitir",
            "agree" to "Estar de acuerdo",
            "allow" to "Permitir",
            "answer" to "Responder",
            "appear" to "Aparecer",
            "apply" to "Aplicar",
            "argue" to "Discutir",
            "arrive" to "Llegar",
            "ask" to "Preguntar, pedir",
            "be" to "Ser, estar",
            "become" to "Convertirse, llegar a ser",
            "begin" to "Empezar, comenzar",
            "believe" to "Creer",
            "bite" to "Morder",
            "blow" to "Soplar",
            "break" to "Romper, quebrar",
            "bring" to "Traer",
            "build" to "Construir",
            "buy" to "Comprar",
            "call" to "Llamar",
            "can" to "Poder",
            "carry" to "Llevar, cargar",
            "catch" to "Atrapar, coger",
            "change" to "Cambiar",
            "choose" to "Elegir, escoger",
            "clean" to "Limpiar",
            "close" to "Cerrar",
            "come" to "Venir",
            "consider" to "Considerar",
            "continue" to "Continuar",
            "cook" to "Cocinar",
            "cost" to "Costar",
            "create" to "Crear",
            "cut" to "Cortar",
            "dance" to "Bailar",
            "decide" to "Decidir",
            "die" to "Morir",
            "do" to "Hacer",
            "draw" to "Dibujar",
            "drink" to "Beber, tomar",
            "drive" to "Conducir, manejar",
            "eat" to "Comer",
            "expect" to "Esperar (expectativa)",
            "explain" to "Explicar",
            "fall" to "Caer",
            "feel" to "Sentir",
            "fight" to "Luchar, pelear",
            "find" to "Encontrar",
            "fly" to "Volar",
            "follow" to "Seguir",
            "forget" to "Olvidar",
            "get" to "Obtener, conseguir",
            "give" to "Dar",
            "go" to "Ir",
            "grow" to "Crecer",
            "happen" to "Suceder, pasar",
            "have" to "Tener, haber",
            "hear" to "Oír, escuchar",
            "help" to "Ayudar",
            "hide" to "Esconder, ocultar",
            "hit" to "Golpear",
            "hold" to "Sostener, mantener",
            "hope" to "Esperar (esperanza)",
            "hurt" to "Lastimar, doler",
            "include" to "Incluir",
            "keep" to "Mantener, guardar",
            "kill" to "Matar",
            "know" to "Saber, conocer",
            "lead" to "Guiar, liderar",
            "learn" to "Aprender",
            "leave" to "Salir, dejar",
            "let" to "Permitir, dejar",
            "like" to "Gustar",
            "listen" to "Escuchar",
            "live" to "Vivir",
            "look" to "Mirar, buscar",
            "lose" to "Perder",
            "love" to "Amar",
            "make" to "Hacer, fabricar",
            "mean" to "Significar, querer decir",
            "meet" to "Conocer, reunirse",
            "move" to "Mover, mudarse",
            "must" to "Deber",
            "need" to "Necesitar",
            "offer" to "Ofrecer",
            "open" to "Abrir",
            "pay" to "Pagar",
            "play" to "Jugar, tocar (instrumento)",
            "provide" to "Proporcionar",
            "put" to "Poner, colocar",
            "reach" to "Alcanzar",
            "read" to "Leer",
            "remain" to "Permanecer",
            "remember" to "Recordar",
            "run" to "Correr",
            "say" to "Decir",
            "see" to "Ver",
            "sell" to "Vender",
            "send" to "Enviar",
            "serve" to "Servir",
            "set" to "Establecer, colocar",
            "show" to "Mostrar",
            "sing" to "Cantar",
            "sit" to "Sentarse",
            "sleep" to "Dormir",
            "speak" to "Hablar",
            "spend" to "Gastar, pasar tiempo",
            "stand" to "Estar de pie",
            "start" to "Empezar, iniciar",
            "stay" to "Permanecer, quedarse",
            "stop" to "Detener, parar",
            "swim" to "Nadar",
            "take" to "Tomar, llevar",
            "talk" to "Hablar, conversar",
            "teach" to "Enseñar",
            "tell" to "Decir, contar",
            "think" to "Pensar",
            "try" to "Intentar, probar",
            "understand" to "Entender, comprender",
            "use" to "Usar, utilizar",
            "visit" to "Visitar",
            "wait" to "Esperar (tiempo)",
            "walk" to "Caminar",
            "want" to "Querer",
            "watch" to "Ver, observar",
            "win" to "Ganar",
            "work" to "Trabajar",
            "write" to "Escribir"
        )
    }

    private val dbHelper = DatabaseHelper.getInstance(context)
    private val userDbHelper = UserDatabaseHelper.getInstance(context)
    private val dailyPicker = DailyPicker(clock)
    private val historyPolicy = HistoryPolicy(clock)
    private val progressCalculator = ProgressCalculator(clock)

    suspend fun getDailyVerbs(category: String = "ALL"): List<Verb> = withContext(Dispatchers.IO) {
        val now = clock.currentTimeMillis()
        val todayStr = dailyPicker.getTodayDayString(now)

        // Check stored daily picks in user_data.db
        var storedBases = userDbHelper.getDailyPicks(todayStr)
        if (storedBases.isEmpty()) {
            val commonCandidates = dbHelper.getCommonVerbs(500)
            val recentDays = dailyPicker.getRecentDayStrings(7, now)
            val recentlyUsedBases = userDbHelper.getBasesPickedInDays(recentDays)

            val selectedVerbs = dailyPicker.selectDailyVerbs(commonCandidates, recentlyUsedBases, todayStr, 10)
            storedBases = selectedVerbs.map { it.base }
            userDbHelper.saveDailyPicks(todayStr, storedBases)
        }

        var verbs = dbHelper.getVerbsByBases(storedBases)
        if (verbs.isEmpty()) {
            verbs = dbHelper.getAllVerbs(10)
        }

        val enriched = enrichVerbs(verbs)
        filterByCategory(enriched, category)
    }

    suspend fun searchVerbs(query: String, category: String = "ALL"): List<Verb> = withContext(Dispatchers.IO) {
        val translationsMap = userDbHelper.getAllTranslations() + LOCAL_SPANISH_DICTIONARY
        val results = dbHelper.searchVerbs(query, translationsMap, limit = 20)
        val enriched = enrichVerbs(results)
        filterByCategory(enriched, category)
    }

    suspend fun getSearchHistory(): List<Verb> = withContext(Dispatchers.IO) {
        val now = clock.currentTimeMillis()
        val cutoff = historyPolicy.getCutoffTimestamp(now)
        val bases = userDbHelper.getSearchHistory(cutoff, limit = 8)
        val verbs = dbHelper.getVerbsByBases(bases)
        enrichVerbs(verbs)
    }

    suspend fun addSearchHistory(base: String) = withContext(Dispatchers.IO) {
        val now = clock.currentTimeMillis()
        val cutoff = historyPolicy.getCutoffTimestamp(now)
        userDbHelper.addSearchHistory(base, now, cutoff)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        userDbHelper.clearSearchHistory()
    }

    suspend fun getVerbByBase(base: String): Verb? = withContext(Dispatchers.IO) {
        val verb = dbHelper.getVerbByBase(base) ?: return@withContext null
        enrichVerb(verb)
    }

    suspend fun getLastOpenedOrFirstDailyVerb(): Verb? = withContext(Dispatchers.IO) {
        val now = clock.currentTimeMillis()
        val cutoff = historyPolicy.getCutoffTimestamp(now)
        val historyBases = userDbHelper.getSearchHistory(cutoff, limit = 1)
        if (historyBases.isNotEmpty()) {
            val v = dbHelper.getVerbByBase(historyBases.first())
            if (v != null) return@withContext enrichVerb(v)
        }
        val daily = getDailyVerbs("ALL")
        daily.firstOrNull()
    }

    suspend fun recordView(base: String) = withContext(Dispatchers.IO) {
        userDbHelper.incrementViews(base)
    }

    suspend fun toggleFavorite(base: String): Boolean = withContext(Dispatchers.IO) {
        userDbHelper.toggleFavorite(base)
    }

    suspend fun recordPracticeAttempt(base: String, isCorrect: Boolean, mode: String) = withContext(Dispatchers.IO) {
        val now = clock.currentTimeMillis()
        userDbHelper.recordPracticeAttempt(base, isCorrect, mode, now)
    }

    suspend fun getVerbStatsRecord(base: String) = withContext(Dispatchers.IO) {
        userDbHelper.getVerbStats(base)
    }

    suspend fun getProgressSummary(): ProgressSummary = withContext(Dispatchers.IO) {
        val attempts = userDbHelper.getAllPracticeAttempts()
        val stats = userDbHelper.getAllVerbStats()
        val allVerbs = dbHelper.getAllVerbs(25000)
        val verbsMap = allVerbs.associateBy { it.base }
        progressCalculator.calculate(attempts, stats, verbsMap)
    }

    suspend fun getCategoryCounts(): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        dbHelper.getCountsByCategory()
    }

    suspend fun saveTranslation(base: String, spanish: String, source: String = "local") = withContext(Dispatchers.IO) {
        val now = clock.currentTimeMillis()
        userDbHelper.saveTranslation(base, spanish, source, now)
    }

    suspend fun resetProgress() = withContext(Dispatchers.IO) {
        userDbHelper.resetProgress()
    }

    suspend fun getRandomVerbs(count: Int = 10): List<Verb> = withContext(Dispatchers.IO) {
        val verbs = dbHelper.getAllVerbs(500).shuffled().take(count)
        enrichVerbs(verbs)
    }

    private fun enrichVerbs(verbs: List<Verb>): List<Verb> {
        return verbs.map { enrichVerb(it) }
    }

    private fun enrichVerb(verb: Verb): Verb {
        val stats = userDbHelper.getVerbStats(verb.base)
        val customTrans = userDbHelper.getTranslation(verb.base)
            ?: LOCAL_SPANISH_DICTIONARY[verb.base.lowercase()]

        return verb.copy(
            spanishMeaning = customTrans ?: "Toca para traducir",
            isFavorite = stats.isFavorite,
            mastery = stats.mastery
        )
    }

    private fun filterByCategory(verbs: List<Verb>, category: String): List<Verb> {
        return when (category.uppercase()) {
            "IRREGULAR" -> verbs.filter { it.isIrregular }
            "REGULAR" -> verbs.filter { !it.isIrregular }
            else -> verbs
        }
    }
}
