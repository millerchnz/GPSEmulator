package com.millerchnz.gpsemulator.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formats {
    private val dateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val timeOnly: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

    fun dateTime(millis: Long): String =
        dateTime.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun timeOnly(millis: Long): String =
        timeOnly.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun iso(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    fun decimals(value: Double, places: Int): String =
        String.format(Locale.US, "%.${places}f", value)

    fun decimals(value: Float, places: Int): String =
        String.format(Locale.US, "%.${places}f", value)

    fun coordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

    fun distance(meters: Float): String = when {
        meters < 1_000f -> String.format(Locale.US, "%.0f m", meters)
        else -> String.format(Locale.US, "%.2f km", meters / 1_000f)
    }

    fun count(value: Int): String = String.format(Locale.US, "%,d", value)
}
