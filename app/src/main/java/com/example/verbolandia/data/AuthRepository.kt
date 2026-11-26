package com.example.verbolandia.data

import android.content.Context
import android.content.SharedPreferences
import com.example.verbolandia.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Contrato de autenticación y perfil de usuario.
 * Permite intercambiar entre almacenamiento local y Firebase Auth + Cloud Firestore.
 */
interface AuthRepository {
    fun currentUser(): UserProfile?
    fun isLoggedIn(): Boolean = currentUser() != null
    suspend fun login(email: String, password: String): Result<UserProfile>
    suspend fun signup(
        name: String,
        city: String,
        country: String,
        phone: String,
        email: String,
        password: String
    ): Result<UserProfile>

    suspend fun syncProgress(masteredCount: Int, totalPracticed: Int): Result<UserProfile>
    fun logout()
}

class AuthException(val code: ErrorCode, message: String) : Exception(message) {
    enum class ErrorCode {
        INVALID_CREDENTIALS,
        EMAIL_ALREADY_EXISTS,
        NOT_AUTHENTICATED
    }
}

/**
 * Implementación local respaldada por SharedPreferences para pruebas de flujo y UI
 * antes de conectar el SDK de Firebase.
 */
class LocalAuthRepository(context: Context) : AuthRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun currentUser(): UserProfile? {
        val activeEmail = prefs.getString(KEY_ACTIVE_EMAIL, null) ?: return null
        val jsonStr = prefs.getString(accountKey(activeEmail), null) ?: return null
        return parseProfile(jsonStr)
    }

    override suspend fun login(email: String, password: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            val normalizedEmail = email.trim().lowercase()
            val jsonStr = prefs.getString(accountKey(normalizedEmail), null)
                ?: return@withContext Result.failure(
                    AuthException(AuthException.ErrorCode.INVALID_CREDENTIALS, "Account not found")
                )

            val json = JSONObject(jsonStr)
            if (json.optString(FIELD_PASSWORD) != password) {
                return@withContext Result.failure(
                    AuthException(AuthException.ErrorCode.INVALID_CREDENTIALS, "Invalid password")
                )
            }

            prefs.edit().putString(KEY_ACTIVE_EMAIL, normalizedEmail).apply()
            Result.success(parseProfile(jsonStr))
        }

    override suspend fun signup(
        name: String,
        city: String,
        country: String,
        phone: String,
        email: String,
        password: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase()
        if (prefs.contains(accountKey(normalizedEmail))) {
            return@withContext Result.failure(
                AuthException(AuthException.ErrorCode.EMAIL_ALREADY_EXISTS, "Email already registered")
            )
        }

        val profile = UserProfile(
            uid = UUID.randomUUID().toString(),
            name = name.trim(),
            city = city.trim(),
            country = country.trim(),
            phone = phone.trim(),
            email = normalizedEmail,
            masteredCount = 0,
            totalPracticed = 0,
            lastSyncedAt = System.currentTimeMillis()
        )

        saveProfile(profile, password)
        prefs.edit().putString(KEY_ACTIVE_EMAIL, normalizedEmail).apply()
        Result.success(profile)
    }

    override suspend fun syncProgress(masteredCount: Int, totalPracticed: Int): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            val activeEmail = prefs.getString(KEY_ACTIVE_EMAIL, null)
                ?: return@withContext Result.failure(
                    AuthException(AuthException.ErrorCode.NOT_AUTHENTICATED, "No active session")
                )
            val jsonStr = prefs.getString(accountKey(activeEmail), null)
                ?: return@withContext Result.failure(
                    AuthException(AuthException.ErrorCode.NOT_AUTHENTICATED, "Account missing")
                )

            val existing = JSONObject(jsonStr)
            val updated = parseProfile(jsonStr).copy(
                masteredCount = masteredCount,
                totalPracticed = totalPracticed,
                lastSyncedAt = System.currentTimeMillis()
            )
            saveProfile(updated, existing.optString(FIELD_PASSWORD))
            Result.success(updated)
        }

    override fun logout() {
        prefs.edit().remove(KEY_ACTIVE_EMAIL).apply()
    }

    private fun saveProfile(profile: UserProfile, password: String) {
        val json = JSONObject().apply {
            put(FIELD_UID, profile.uid)
            put(FIELD_NAME, profile.name)
            put(FIELD_CITY, profile.city)
            put(FIELD_COUNTRY, profile.country)
            put(FIELD_PHONE, profile.phone)
            put(FIELD_EMAIL, profile.email)
            put(FIELD_PASSWORD, password)
            put(FIELD_MASTERED, profile.masteredCount)
            put(FIELD_PRACTICED, profile.totalPracticed)
            put(FIELD_SYNCED_AT, profile.lastSyncedAt)
        }
        prefs.edit().putString(accountKey(profile.email), json.toString()).apply()
    }

    private fun parseProfile(jsonStr: String): UserProfile {
        val json = JSONObject(jsonStr)
        return UserProfile(
            uid = json.optString(FIELD_UID),
            name = json.optString(FIELD_NAME),
            city = json.optString(FIELD_CITY),
            country = json.optString(FIELD_COUNTRY),
            phone = json.optString(FIELD_PHONE),
            email = json.optString(FIELD_EMAIL),
            masteredCount = json.optInt(FIELD_MASTERED, 0),
            totalPracticed = json.optInt(FIELD_PRACTICED, 0),
            lastSyncedAt = json.optLong(FIELD_SYNCED_AT, 0L)
        )
    }

    private fun accountKey(email: String) = "${KEY_ACCOUNT_PREFIX}${email.trim().lowercase()}"

    companion object {
        private const val PREFS_NAME = "verbolandia_auth_prefs"
        private const val KEY_ACTIVE_EMAIL = "active_email"
        private const val KEY_ACCOUNT_PREFIX = "account_"

        private const val FIELD_UID = "uid"
        private const val FIELD_NAME = "name"
        private const val FIELD_CITY = "city"
        private const val FIELD_COUNTRY = "country"
        private const val FIELD_PHONE = "phone"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_PASSWORD = "password"
        private const val FIELD_MASTERED = "mastered_count"
        private const val FIELD_PRACTICED = "total_practiced"
        private const val FIELD_SYNCED_AT = "last_synced_at"
    }
}

object AuthProvider {
    @Volatile
    private var instance: AuthRepository? = null

    fun getRepository(context: Context): AuthRepository {
        return instance ?: synchronized(this) {
            instance ?: LocalAuthRepository(context).also { instance = it }
        }
    }
}
