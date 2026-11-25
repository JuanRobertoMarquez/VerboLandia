package com.example.verbolandia.model

import java.io.Serializable

/**
 * Representa el perfil del estudiante en Verbolandia, compatible con Firebase Auth y Cloud Firestore.
 */
data class UserProfile(
    val uid: String,
    val name: String,
    val city: String,
    val country: String,
    val phone: String,
    val email: String,
    val masteredCount: Int = 0,
    val totalPracticed: Int = 0,
    val lastSyncedAt: Long = 0L
) : Serializable
