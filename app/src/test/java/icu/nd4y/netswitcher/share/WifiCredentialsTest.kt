package icu.nd4y.netswitcher.share

import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.ProfileKind
import icu.nd4y.netswitcher.data.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiCredentialsTest {

    private fun wifi(
        ssid: String,
        password: String = "",
        security: WifiSecurity = WifiSecurity.WPA2,
        hidden: Boolean = false,
    ) = Profile(
        id = "x",
        name = "x",
        kind = ProfileKind.WIFI,
        ssid = ssid,
        password = password,
        security = security,
        hiddenSsid = hidden,
    )

    @Test
    fun `qr payload has the standard shape`() {
        val payload = WifiCredentials.qrPayload(wifi("Home", "secret"))
        assertEquals("WIFI:T:WPA;S:Home;P:secret;;", payload)
    }

    @Test
    fun `open network carries no password and nopass type`() {
        val payload = WifiCredentials.qrPayload(wifi("Cafe", security = WifiSecurity.OPEN))
        assertEquals("WIFI:T:nopass;S:Cafe;;", payload)
        assertFalse(payload.contains("P:"))
    }

    @Test
    fun `hidden network sets the H flag`() {
        val payload = WifiCredentials.qrPayload(wifi("Secret", "pw", hidden = true))
        assertTrue(payload.contains(";H:true;"))
    }

    @Test
    fun `special characters are backslash escaped`() {
        // SSID and password with every reserved character.
        val payload = WifiCredentials.qrPayload(wifi("a;b,c:d\"e\\f", "p;q"))
        assertTrue(payload.contains("""S:a\;b\,c\:d\"e\\f;"""))
        assertTrue(payload.contains("""P:p\;q;"""))
    }

    @Test
    fun `wsc payload is a credential tlv wrapping the network attributes`() {
        val bytes = WifiCredentials.wscPayload(wifi("Net", "12345678"))

        // Outer attribute: Credential (0x100E) with a length matching the remaining bytes.
        assertEquals(0x10, bytes[0].toInt() and 0xFF)
        assertEquals(0x0E, bytes[1].toInt() and 0xFF)
        val declaredLen = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        assertEquals(bytes.size - 4, declaredLen)

        // The SSID and network key bytes appear verbatim inside.
        assertTrue(indexOf(bytes, "Net".toByteArray()) >= 0)
        assertTrue(indexOf(bytes, "12345678".toByteArray()) >= 0)

        // SSID attribute id 0x1045 with the right length precedes the SSID.
        val ssidHeader = byteArrayOf(0x10, 0x45, 0x00, 0x03) + "Net".toByteArray()
        assertTrue(indexOf(bytes, ssidHeader) >= 0)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
