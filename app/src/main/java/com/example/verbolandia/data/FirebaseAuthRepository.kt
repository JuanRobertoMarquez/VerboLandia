package com.example.verbolandia.data

import android.content.Context
import android.content.SharedPreferences
import com.example.verbolandia.model.UserProfile
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Autenticación con Firebase Authentication (correo y contraseña) y perfil/progreso
 * guardados en Cloud Firestore, en la colección "users" (un documento por cada uid).
 *
 * El perfil se guarda también en una copia local (SharedPreferences, sin contraseña)
 * para que [currentUser] pueda responder al instante, incluso sin internet.
 */
class FirebaseAuthRepository(context: Context) : AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val cache: SharedPreferences =
        context.applicationContext.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

    override fun currentUser(): UserProfile? {
        val firebaseUser = auth.currentUser ?: return null
        return readCache(firebaseUser.uid) ?: minimalProfile(firebaseUser)
    }

    override suspend fun login(email: String, password: String): Result<UserProfile> = guard {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Firebase no devolvió usuario")
        // Si Firestore falla justo al entrar, se permite el acceso con los datos locales
        runCatching { fetchProfile(user) }
            .getOrElse { readCache(user.uid) ?: minimalProfile(user) }
    }

    override suspend fun signup(
        name: String,
        city: String,
        country: String,
        phone: String,
        email: String,
        password: String
    ): Result<UserProfile> = guard {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Firebase no devolvió usuario")

        val profile = UserProfile(
            uid = user.uid,
            name = name.trim(),
            city = city.trim(),
            country = country.trim(),
            phone = phone.trim(),
            email = user.email ?: email.trim().lowercase(),
            masteredCount = 0,
            totalPracticed = 0,
            lastSyncedAt = System.currentTimeMillis()
        )

        try {
            userDocument(user.uid).set(profile.toMap()).awaitOrTimeout()
        } catch (e: Exception) {
            // Sin perfil en la nube la cuenta quedaría a medias: se revierte el registro
            runCatching { user.delete().await() }
            throw e
        }

        runCatching {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(profile.name).build()).await()
        }
        writeCache(profile)
        profile
    }

    override suspend fun syncProgress(masteredCount: Int, totalPracticed: Int): Result<UserProfile> = guard {
        val user = auth.currentUser ?: throw AuthException(AuthException.ErrorCode.NOT_AUTHENTICATED, "No active session")
        val now = System.currentTimeMillis()

        userDocument(user.uid).set(
            mapOf(
                FIELD_MASTERED to masteredCount,
                FIELD_PRACTICED to totalPracticed,
                FIELD_SYNCED_AT to now
            ),
            SetOptions.merge()
        ).awaitOrTimeout()

        val updated = (readCache(user.uid) ?: minimalProfile(user)).copy(
            masteredCount = masteredCount,
            totalPracticed = totalPracticed,
            lastSyncedAt = now
        )
        writeCache(updated)
        updated
    }

    override suspend fun refreshProfile(): Result<UserProfile> = guard {
        val user = auth.currentUser ?: throw AuthException(AuthException.ErrorCode.NOT_AUTHENTICATED, "No active session")
        fetchProfile(user)
    }

    override fun logout() {
        auth.signOut()
        cache.edit().clear().apply()
    }

    // ---------- Firestore ----------

    private fun userDocument(uid: String) = firestore.collection(COLLECTION_USERS).document(uid)

    /** Lee el documento users/{uid}; si no existe, usa los datos básicos de la cuenta. */
    private suspend fun fetchProfile(user: FirebaseUser): UserProfile {
        val snapshot = userDocument(user.uid).get().awaitOrTimeout()
        val profile = if (snapshot.exists()) snapshot.toProfile(user) else minimalProfile(user)
        writeCache(profile)
        return profile
    }

    private fun DocumentSnapshot.toProfile(user: FirebaseUser) = UserProfile(
        uid = user.uid,
        name = getString(FIELD_NAME).orEmpty(),
        city = getString(FIELD_CITY).orEmpty(),
        country = getString(FIELD_COUNTRY).orEmpty(),
        phone = getString(FIELD_PHONE).orEmpty(),
        email = getString(FIELD_EMAIL) ?: user.email.orEmpty(),
        masteredCount = getLong(FIELD_MASTERED)?.toInt() ?: 0,
        totalPracticed = getLong(FIELD_PRACTICED)?.toInt() ?: 0,
        lastSyncedAt = getLong(FIELD_SYNCED_AT) ?: 0L
    )

    private fun UserProfile.toMap(): Map<String, Any> = mapOf(
        FIELD_UID to uid,
        FIELD_NAME to name,
        FIELD_CITY to city,
        FIELD_COUNTRY to country,
        FIELD_PHONE to phone,
        FIELD_EMAIL to email,
        FIELD_MASTERED to masteredCount,
        FIELD_PRACTICED to totalPracticed,
        FIELD_SYNCED_AT to lastSyncedAt
    )

    private fun minimalProfile(user: FirebaseUser) = UserProfile(
        uid = user.uid,
        name = user.displayName.orEmpty(),
        city = "",
        country = "",
        phone = "",
        email = user.email.orEmpty()
    )

    // ---------- Copia local del perfil (sin contraseña) ----------

    private fun writeCache(profile: UserProfile) {
        val json = JSONObject(profile.toMap())
        cache.edit().putString(cacheKey(profile.uid), json.toString()).apply()
    }

    private fun readCache(uid: String): UserProfile? {
        val raw = cache.getString(cacheKey(uid), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            UserProfile(
                uid = uid,
                name = json.optString(FIELD_NAME),
                city = json.optString(FIELD_CITY),
                country = json.optString(FIELD_COUNTRY),
                phone = json.optString(FIELD_PHONE),
                email = json.optString(FIELD_EMAIL),
                masteredCount = json.optInt(FIELD_MASTERED, 0),
                totalPracticed = json.optInt(FIELD_PRACTICED, 0),
                lastSyncedAt = json.optLong(FIELD_SYNCED_AT, 0L)
            )
        }.getOrNull()
    }

    private fun cacheKey(uid: String) = "$CACHE_PROFILE_PREFIX$uid"

    // ---------- Errores y tiempos de espera ----------

    private suspend fun <T> guard(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toAuthException())
    }

    private fun Throwable.toAuthException(): Throwable = when (this) {
        is AuthException -> this
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException ->
            AuthException(AuthException.ErrorCode.INVALID_CREDENTIALS, message.orEmpty())
        is FirebaseAuthUserCollisionException ->
            AuthException(AuthException.ErrorCode.EMAIL_ALREADY_EXISTS, message.orEmpty())
        is FirebaseNetworkException ->
            AuthException(AuthException.ErrorCode.NETWORK_ERROR, message.orEmpty())
        is FirebaseFirestoreException ->
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                AuthException(AuthException.ErrorCode.NETWORK_ERROR, message.orEmpty())
            } else {
                this
            }
        else -> this
    }

    /**
     * Las escrituras de Firestore sin internet se quedan "pendientes" y el Task nunca termina;
     * con este límite la pantalla recibe un error de red en lugar de quedarse cargando.
     */
    private suspend fun <T> Task<T>.awaitOrTimeout(): T {
        var completed = false
        val value = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            await().also { completed = true }
        }
        if (!completed) {
            throw AuthException(AuthException.ErrorCode.NETWORK_ERROR, "Firestore timeout")
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val NETWORK_TIMEOUT_MS = 10_000L

        const val CACHE_PREFS = "verbolandia_profile_cache"
        const val CACHE_PROFILE_PREFIX = "profile_"

        const val FIELD_UID = "uid"
        const val FIELD_NAME = "name"
        const val FIELD_CITY = "city"
        const val FIELD_COUNTRY = "country"
        const val FIELD_PHONE = "phone"
        const val FIELD_EMAIL = "email"
        const val FIELD_MASTERED = "masteredCount"
        const val FIELD_PRACTICED = "totalPracticed"
        const val FIELD_SYNCED_AT = "lastSyncedAt"
    }
}
