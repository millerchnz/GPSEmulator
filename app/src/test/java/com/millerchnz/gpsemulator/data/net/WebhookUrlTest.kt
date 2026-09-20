package com.millerchnz.gpsemulator.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebhookUrlTest {
    @Test
    fun appendsParamsToBareUrl() {
        val url = WebhookUrl.compose(
            baseUrl = "https://example.com/gpslog",
            params = listOf("lat" to "-36.8485", "lon" to "174.7633"),
        )
        assertEquals("https://example.com/gpslog?lat=-36.8485&lon=174.7633", url)
    }

    @Test
    fun keepsQueryAlreadyOnBaseUrl() {
        val url = WebhookUrl.compose(
            baseUrl = "https://example.com/gpslog?source=phone",
            params = listOf("lat" to "1.5"),
        )
        assertEquals("https://example.com/gpslog?source=phone&lat=1.5", url)
    }

    @Test
    fun toleratesTrailingSeparators() {
        val url = WebhookUrl.compose(
            baseUrl = "https://example.com/gpslog?",
            params = listOf("lat" to "1.5"),
        )
        assertEquals("https://example.com/gpslog?lat=1.5", url)
    }

    @Test
    fun encodesValues() {
        val url = WebhookUrl.compose(
            baseUrl = "https://example.com/gpslog",
            params = listOf("extras" to "satellites=7, key=a&b"),
        )
        assertEquals("https://example.com/gpslog?extras=satellites%3D7%2C+key%3Da%26b", url)
    }

    @Test
    fun appendsExtraParams() {
        val url = WebhookUrl.compose(
            baseUrl = "https://example.com/gpslog",
            params = listOf("lat" to "1"),
            extraParams = "token=abc123",
        )
        assertEquals("https://example.com/gpslog?lat=1&token=abc123", url)
    }

    @Test
    fun prependsDeviceIdAndDropsConflictingDeviceParams() {
        val url = WebhookUrl.compose(
            baseUrl = "https://example.com/gpslog",
            params = listOf("device" to "from-fields", "lat" to "1"),
            extraParams = "device=pixel8\ntoken=abc123",
            deviceId = "uuid-1",
        )
        assertEquals("https://example.com/gpslog?device=uuid-1&lat=1&token=abc123", url)
    }

    @Test
    fun parsesExtraParamsFromMixedSeparators() {
        val parsed = WebhookUrl.parseExtraParams("a=1&b=2\n c = 3 \n\n&=ignored&d=")
        assertEquals(listOf("a" to "1", "b" to "2", "c" to "3", "d" to ""), parsed)
    }

    @Test
    fun rejectsUnusableBaseUrls() {
        assertNotNull(WebhookUrl.validationError(""))
        assertNotNull(WebhookUrl.validationError("example.com/gpslog"))
        assertNotNull(WebhookUrl.validationError("https://"))
        assertNotNull(WebhookUrl.validationError("https://example.com/gps log"))
        assertNull(WebhookUrl.validationError("http://192.168.1.10:8080/log"))
        assertNull(WebhookUrl.validationError("https://example.com/gpslog"))
    }

    @Test
    fun composeReturnsNullForUnusableBaseUrl() {
        assertNull(WebhookUrl.compose("not a url", listOf("lat" to "1")))
    }
}
