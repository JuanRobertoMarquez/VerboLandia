package com.example.verbolandia.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.verbolandia.model.Verb
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "verbs.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_VERBS = "verbs"

        private const val COL_BASE = "base"
        private const val COL_THIRD_PERSON = "third_person"
        private const val COL_PAST = "past"
        private const val COL_PAST_PARTICIPLE = "past_participle"
        private const val COL_GERUND = "gerund"

        // Common Spanish meanings dictionary for popular verbs
        private val SPANISH_TRANSLATIONS = mapOf(
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
            "catch" to "Atrapar, atrapar",
            "change" to "Cambiar",
            "choose" to "Elegir, escoger",
            "clean" to "Limpiar",
            "close" to "Cerrar",
            "come" to "Venir",
            "cook" to "Cocinar",
            "cost" to "Costar",
            "create" to "Crear",
            "cut" to "Cortar",
            "dance" to "Bailar",
            "decide" to "Decidir",
            "do" to "Hacer",
            "draw" to "Dibujar",
            "drink" to "Beber, tomar",
            "drive" to "Conducir, manejar",
            "eat" to "Comer",
            "explain" to "Explicar",
            "fall" to "Caer",
            "feel" to "Sentir",
            "fight" to "Luchar, pelear",
            "find" to "Encontrar",
            "fly" to "Volar",
            "forget" to "Olvidar",
            "get" to "Obtener, conseguir",
            "give" to "Dar",
            "go" to "Ir",
            "grow" to "Crecer",
            "have" to "Tener, haber",
            "hear" to "Oír, escuchar",
            "help" to "Ayudar",
            "hide" to "Esconder, ocultar",
            "hit" to "Golpear",
            "hold" to "Sostener, mantener",
            "hope" to "Esperar (esperanza)",
            "hurt" to "Lastimar, doler",
            "keep" to "Mantener, guardar",
            "know" to "Saber, conocer",
            "learn" to "Aprender",
            "leave" to "Salir, dejar",
            "let" to "Permitir, dejar",
            "like" to "Gustar",
            "listen" to "Escuchar",
            "live" to "Vivir",
            "look" to "Mirar, buscar",
            "love" to "Amar",
            "make" to "Hacer, fabricar",
            "mean" to "Significar, querer decir",
            "meet" to "Conocer, reunirse",
            "need" to "Necesitar",
            "open" to "Abrir",
            "pay" to "Pagar",
            "play" to "Jugar, tocar (instrumento)",
            "put" to "Poner, colocar",
            "read" to "Leer",
            "remember" to "Recordar",
            "run" to "Correr",
            "say" to "Decir",
            "see" to "Ver",
            "sell" to "Vender",
            "send" to "Enviar",
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

    init {
        copyDatabaseIfNeeded()
    }

    private fun copyDatabaseIfNeeded() {
        val dbPath = context.getDatabasePath(DATABASE_NAME)
        if (!dbPath.exists()) {
            dbPath.parentFile?.mkdirs()
            try {
                val inputStream: InputStream = context.assets.open("verbs.db")
                val outputStream: OutputStream = FileOutputStream(dbPath)
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {}

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    fun getAllVerbs(limit: Int = 200): List<Verb> {
        val list = mutableListOf<Verb>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_VERBS WHERE base NOT GLOB '[0-9]*' ORDER BY base ASC LIMIT $limit",
            null
        )
        if (cursor.moveToFirst()) {
            do {
                list.add(cursorToVerb(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun searchVerbs(query: String): List<Verb> {
        val list = mutableListOf<Verb>()
        val db = readableDatabase
        val cleanQuery = query.trim().lowercase()
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_VERBS WHERE base LIKE ? OR past LIKE ? OR past_participle LIKE ? OR gerund LIKE ? LIMIT 100",
            arrayOf("%$cleanQuery%", "%$cleanQuery%", "%$cleanQuery%", "%$cleanQuery%")
        )
        if (cursor.moveToFirst()) {
            do {
                list.add(cursorToVerb(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getVerbByBase(base: String): Verb? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_VERBS WHERE base = ? LIMIT 1", arrayOf(base))
        var verb: Verb? = null
        if (cursor.moveToFirst()) {
            verb = cursorToVerb(cursor)
        }
        cursor.close()
        return verb
    }

    fun getRandomVerbs(count: Int = 10): List<Verb> {
        val list = mutableListOf<Verb>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_VERBS WHERE base NOT GLOB '[0-9]*' ORDER BY RANDOM() LIMIT $count",
            null
        )
        if (cursor.moveToFirst()) {
            do {
                list.add(cursorToVerb(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getVerbOfDay(): Verb {
        val popularVerbs = listOf("break", "go", "think", "make", "take", "write", "speak", "see", "bring", "find", "choose")
        val todayIndex = (System.currentTimeMillis() / (1000 * 60 * 60 * 24) % popularVerbs.size).toInt()
        val targetBase = popularVerbs[todayIndex]
        return getVerbByBase(targetBase) ?: Verb(
            base = "break",
            thirdPerson = "breaks",
            past = "broke",
            pastParticiple = "broken",
            gerund = "breaking",
            spanishMeaning = "Romper, quebrar",
            phonetic = "/breɪk/",
            isIrregular = true,
            level = "B1"
        )
    }

    private fun cursorToVerb(cursor: android.database.Cursor): Verb {
        val baseIndex = cursor.getColumnIndex(COL_BASE)
        val thirdPersonIndex = cursor.getColumnIndex(COL_THIRD_PERSON)
        val pastIndex = cursor.getColumnIndex(COL_PAST)
        val pastParticipleIndex = cursor.getColumnIndex(COL_PAST_PARTICIPLE)
        val gerundIndex = cursor.getColumnIndex(COL_GERUND)

        val base = if (baseIndex != -1) cursor.getString(baseIndex) else ""
        val thirdPerson = if (thirdPersonIndex != -1) cursor.getString(thirdPersonIndex) else ""
        val past = if (pastIndex != -1) cursor.getString(pastIndex) else ""
        val pastParticiple = if (pastParticipleIndex != -1) cursor.getString(pastParticipleIndex) else ""
        val gerund = if (gerundIndex != -1) cursor.getString(gerundIndex) else ""

        // Calculate if irregular (if past doesn't end with "ed" or "d")
        val isIrregular = !past.endsWith("ed") && !past.endsWith("d")

        val spanishMeaning = SPANISH_TRANSLATIONS[base.lowercase()] ?: "Traducir: $base"
        val phonetic = "/${base}/"
        val level = if (isIrregular) "B1" else "A1"

        return Verb(
            base = base,
            thirdPerson = thirdPerson,
            past = past,
            pastParticiple = pastParticiple,
            gerund = gerund,
            spanishMeaning = spanishMeaning,
            phonetic = phonetic,
            isIrregular = isIrregular,
            level = level,
            mastery = 0,
            isFavorite = false
        )
    }
}
