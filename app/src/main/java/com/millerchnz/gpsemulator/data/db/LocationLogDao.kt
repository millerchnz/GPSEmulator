package com.millerchnz.gpsemulator.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationLogDao {
    @Insert
    suspend fun insert(row: LocationLogEntity): Long

    @Query("SELECT COUNT(*) FROM location_logs")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM location_logs ORDER BY recordedAtMillis DESC LIMIT 1")
    suspend fun latest(): LocationLogEntity?

    @Query(
        """
        SELECT * FROM location_logs
        WHERE recordedAtMillis >= :fromMillis
          AND recordedAtMillis <= :toMillis
          AND (:provider IS NULL OR requestedProvider = :provider)
        ORDER BY recordedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun observeFiltered(
        fromMillis: Long,
        toMillis: Long,
        provider: String?,
        limit: Int,
    ): Flow<List<LocationLogEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM location_logs
        WHERE recordedAtMillis >= :fromMillis
          AND recordedAtMillis <= :toMillis
          AND (:provider IS NULL OR requestedProvider = :provider)
        """,
    )
    fun observeFilteredCount(
        fromMillis: Long,
        toMillis: Long,
        provider: String?,
    ): Flow<Int>

    /** Unbounded read used by CSV/JSON export so the file matches the active filter, not the visible page. */
    @Query(
        """
        SELECT * FROM location_logs
        WHERE recordedAtMillis >= :fromMillis
          AND recordedAtMillis <= :toMillis
          AND (:provider IS NULL OR requestedProvider = :provider)
        ORDER BY recordedAtMillis DESC
        """,
    )
    suspend fun forExport(
        fromMillis: Long,
        toMillis: Long,
        provider: String?,
    ): List<LocationLogEntity>

    @Query("DELETE FROM location_logs")
    suspend fun deleteAll()

    @Query(
        """
        DELETE FROM location_logs WHERE id NOT IN (
            SELECT id FROM (
                SELECT id FROM location_logs ORDER BY recordedAtMillis DESC, id DESC LIMIT :keepNewest
            )
        )
        """,
    )
    suspend fun trimTo(keepNewest: Int): Int
}
