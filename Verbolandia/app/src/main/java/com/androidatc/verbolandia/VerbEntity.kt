package com.androidatc.verbolandia

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "verbs")
data class VerbEntity(
    @PrimaryKey val palabra: String,
    val frecuencia: Int
)