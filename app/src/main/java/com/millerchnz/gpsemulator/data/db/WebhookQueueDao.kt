package com.millerchnz.gpsemulator.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WebhookQueueDao {
    @Insert
    suspend fun insert(row: WebhookQueueEntity): Long

    @Query("SELECT * FROM webhook_queue ORDER BY id ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<WebhookQueueEntity>

    @Query("SELECT COUNT(*) FROM webhook_queue WHERE kind = :kind")
    fun observeCount(kind: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM webhook_queue")
    suspend fun count(): Int

    @Query("UPDATE webhook_queue SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markFailure(id: Long, error: String?)

    @Query("DELETE FROM webhook_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM webhook_queue WHERE attempts >= :maxAttempts")
    suspend fun dropExhausted(maxAttempts: Int)

    @Query("DELETE FROM webhook_queue WHERE kind = :kind")
    suspend fun deleteKind(kind: String)

    @Query(
        """
        DELETE FROM webhook_queue WHERE id NOT IN (
            SELECT id FROM (
                SELECT id FROM webhook_queue ORDER BY id DESC LIMIT :keepNewest
            )
        )
        """,
    )
    suspend fun trimTo(keepNewest: Int)
}
