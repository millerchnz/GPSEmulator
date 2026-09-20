package com.millerchnz.gpsemulator.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.millerchnz.gpsemulator.data.db.WebhookQueueDao
import com.millerchnz.gpsemulator.data.db.WebhookQueueEntity
import com.millerchnz.gpsemulator.data.settings.WebhookSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.math.min
import kotlin.math.pow

/**
 * Sends a webhook immediately, and parks it in [WebhookQueueDao] if that fails so it can
 * be replayed — on connectivity, after a backoff, or on the next process start.
 */
class WebhookDispatcher(
    private val context: Context,
    private val queue: WebhookQueueDao,
    private val client: WebhookClient,
    private val scope: CoroutineScope,
) {
    private val drainLock = Mutex()
    private val scheduleLock = Any()
    private var retryJob: Job? = null

    fun observePending(kind: String): Flow<Int> = queue.observeCount(kind)

    suspend fun send(kind: String, url: String, webhook: WebhookSettings) {
        val response = client.get(url, webhook.headerName, webhook.headerValue)
        val failure = response.exceptionOrNull()
        when {
            failure != null -> {
                enqueue(kind, url, webhook, failure.messageOrType())
                scheduleRetry(DEFAULT_RETRY_MS)
            }
            response.getOrNull()?.isSuccess == true -> Unit
            response.getOrNull()?.isRetryable == true -> {
                enqueue(kind, url, webhook, "HTTP ${response.getOrNull()?.code}")
                scheduleRetry(DEFAULT_RETRY_MS)
            }
            else -> Unit
        }
    }

    /** Replays queued requests oldest first, stopping at the first one that still fails. */
    suspend fun drain() {
        if (!drainLock.tryLock()) return
        try {
            queue.dropExhausted(WebhookQueueEntity.MAX_ATTEMPTS)
            while (true) {
                val batch = queue.oldest(DRAIN_BATCH)
                if (batch.isEmpty()) return
                for (row in batch) {
                    val response = client.get(row.url, row.headerName, row.headerValue)
                    val failure = response.exceptionOrNull()
                    val body = response.getOrNull()
                    when {
                        failure != null -> {
                            queue.markFailure(row.id, failure.messageOrType())
                            scheduleRetry(backoffMs(row.attempts + 1))
                            return
                        }
                        body != null && body.isSuccess -> queue.delete(row.id)
                        body != null && body.isRetryable -> {
                            queue.markFailure(row.id, "HTTP ${body.code}")
                            scheduleRetry(backoffMs(row.attempts + 1))
                            return
                        }
                        else -> queue.delete(row.id)
                    }
                }
            }
        } finally {
            drainLock.unlock()
        }
    }

    /** Drains the queue whenever a network becomes available. */
    fun watchConnectivity() {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        scope.launch { drain() }
                    }
                },
            )
        }
    }

    private fun scheduleRetry(delayMs: Long) {
        synchronized(scheduleLock) {
            if (retryJob?.isActive == true) return
            retryJob = scope.launch {
                delay(delayMs)
                drain()
            }
        }
    }

    private suspend fun enqueue(kind: String, url: String, webhook: WebhookSettings, error: String?) {
        queue.insert(
            WebhookQueueEntity(
                kind = kind,
                url = url,
                headerName = webhook.headerName.takeIf { it.isNotBlank() },
                headerValue = webhook.headerValue.takeIf { webhook.headerName.isNotBlank() },
                createdAtMillis = System.currentTimeMillis(),
                lastError = error,
            ),
        )
        queue.trimTo(WebhookQueueEntity.MAX_QUEUE_SIZE)
    }

    private fun Throwable.messageOrType(): String = message ?: this::class.java.simpleName

    private companion object {
        const val DRAIN_BATCH = 50
        const val DEFAULT_RETRY_MS = 15_000L
        const val MAX_RETRY_MS = 5 * 60_000L

        fun backoffMs(attempts: Int): Long {
            val factor = 2.0.pow(attempts.coerceAtLeast(1).coerceAtMost(8).toDouble())
            return min(MAX_RETRY_MS, (DEFAULT_RETRY_MS / 2 * factor).toLong())
        }
    }
}
