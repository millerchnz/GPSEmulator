package com.millerchnz.gpsemulator.data.net

import java.net.URLEncoder

/** Builds the GET that carries a fix or a geofence event to the configured base URL. */
object WebhookUrl {
    private const val PARAM_DEVICE = "device"

    /** Human readable reason the base URL is unusable, or null when it is fine. */
    fun validationError(baseUrl: String): String? {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) return "Enter a base URL"
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "URL must start with http:// or https://"
        }
        val withoutScheme = trimmed.substringAfter("://")
        if (withoutScheme.isBlank() || withoutScheme.startsWith("/")) return "URL is missing a host"
        if (trimmed.any { it.isWhitespace() }) return "URL cannot contain spaces"
        return null
    }

    /** Accepts `a=1&b=2`, newline separated pairs, or a mix of both. */
    fun parseExtraParams(raw: String): List<Pair<String, String>> {
        return raw.split('&', '\n')
            .mapNotNull { chunk ->
                val trimmed = chunk.trim().removePrefix("?")
                if (trimmed.isEmpty()) return@mapNotNull null
                val key = trimmed.substringBefore('=').trim()
                if (key.isEmpty()) return@mapNotNull null
                key to trimmed.substringAfter('=', "").trim()
            }
    }

    /**
     * Appends [params] plus any static pairs in [extraParams] to [baseUrl], preserving
     * a query string the base URL already carries. Returns null if the base URL is unusable.
     *
     * [deviceId] is always the first query parameter (`device=`). A `device` key in
     * [params] or [extraParams] is dropped so the install id cannot be overwritten.
     */
    fun compose(
        baseUrl: String,
        params: List<Pair<String, String>>,
        extraParams: String = "",
        deviceId: String? = null,
    ): String? {
        if (validationError(baseUrl) != null) return null
        val base = baseUrl.trim().trimEnd('?', '&')
        val id = deviceId?.trim().orEmpty()
        val extras = parseExtraParams(extraParams)
        val all = buildList {
            if (id.isNotEmpty()) add(PARAM_DEVICE to id)
            addAll(params.filterNot { id.isNotEmpty() && it.first.equals(PARAM_DEVICE, ignoreCase = true) })
            addAll(extras.filterNot { id.isNotEmpty() && it.first.equals(PARAM_DEVICE, ignoreCase = true) })
        }
        if (all.isEmpty()) return base
        val query = all.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val separator = if (base.contains('?')) "&" else "?"
        return base + separator + query
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
