package com.millerchnz.gpsemulator.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class WebhookResponse(
    val code: Int,
    val body: String?,
) {
    val isSuccess: Boolean get() = code in 200..299

    /**
     * A 408, 429 or 5xx is worth retrying later; any other rejection is a configuration
     * problem that will keep failing, so the queue drops it instead of looping forever.
     */
    val isRetryable: Boolean
        get() = code == 408 || code == 429 || code >= 500
}

class WebhookClient {
    suspend fun get(
        url: String,
        headerName: String?,
        headerValue: String?,
    ): Result<WebhookResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                if (!headerName.isNullOrBlank()) {
                    setRequestProperty(headerName.trim(), headerValue.orEmpty())
                }
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..399) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }?.take(MAX_BODY_CHARS)
                WebhookResponse(code, body)
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_BODY_CHARS = 500
    }
}
