package com.millerchnz.gpsemulator.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceEventDao {
    @Insert
    suspend fun insert(row: GeofenceEventEntity): Long

    @Query("SELECT COUNT(*) FROM geofence_events")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM geofence_events
        WHERE recordedAtMillis >= :fromMillis
          AND recordedAtMillis <= :toMillis
          AND (:transition IS NULL OR transition = :transition)
          AND (:fenceId IS NULL OR fenceId = :fenceId)
        ORDER BY recordedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun observeFiltered(
        fromMillis: Long,
        toMillis: Long,
        transition: String?,
        fenceId: String?,
        limit: Int,
    ): Flow<List<GeofenceEventEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM geofence_events
        WHERE recordedAtMillis >= :fromMillis
          AND recordedAtMillis <= :toMillis
          AND (:transition IS NULL OR transition = :transition)
          AND (:fenceId IS NULL OR fenceId = :fenceId)
        """,
    )
    fun observeFilteredCount(
        fromMillis: Long,
        toMillis: Long,
        transition: String?,
        fenceId: String?,
    ): Flow<Int>

    @Query(
        """
        SELECT * FROM geofence_events
        WHERE recordedAtMillis >= :fromMillis
          AND recordedAtMillis <= :toMillis
          AND (:transition IS NULL OR transition = :transition)
          AND (:fenceId IS NULL OR fenceId = :fenceId)
        ORDER BY recordedAtMillis DESC
        """,
    )
    suspend fun forExport(
        fromMillis: Long,
        toMillis: Long,
        transition: String?,
        fenceId: String?,
    ): List<GeofenceEventEntity>

    @Query("DELETE FROM geofence_events")
    suspend fun deleteAll()

    @Query(
        """
        DELETE FROM geofence_events WHERE id NOT IN (
            SELECT id FROM (
                SELECT id FROM geofence_events ORDER BY recordedAtMillis DESC, id DESC LIMIT :keepNewest
            )
        )
        """,
    )
    suspend fun trimTo(keepNewest: Int): Int
}
