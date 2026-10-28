package com.androidatc.verbolandia

import androidx.room.Dao
import androidx.room.Query

@Dao
interface VerbDao {
    // Esta función obtiene toda la lista de la tabla
    @Query("SELECT * FROM verbs")
    suspend fun getAllVerbs(): List<VerbEntity>
}
