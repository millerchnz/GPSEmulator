package com.millerchnz.gpsemulator.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceDao {
    @Query("SELECT * FROM geofences ORDER BY createdAtMillis ASC")
    fun observeAll(): Flow<List<GeofenceEntity>>

    @Query("SELECT * FROM geofences ORDER BY createdAtMillis ASC")
    suspend fun all(): List<GeofenceEntity>

    @Query("SELECT * FROM geofences WHERE id = :id")
    suspend fun byId(id: String): GeofenceEntity?

    @Upsert
    suspend fun upsert(fence: GeofenceEntity)

    @Query("DELETE FROM geofences WHERE id = :id")
    suspend fun delete(id: String)
}
