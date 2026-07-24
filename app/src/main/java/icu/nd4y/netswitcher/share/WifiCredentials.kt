package icu.nd4y.netswitcher.share

import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.WifiSecurity
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Turns a Wi-Fi [Profile] into the two interchange formats everything else here builds on:
 *
 *  * the `WIFI:` URI a QR code carries — the de-facto standard the stock camera on both
 *    Android and iOS understands and offers to connect to;
 *  * the Wi-Fi Simple Configuration (WSC) credential an NFC tag carries under the
 *    `application/vnd.wfa.wsc` MIME type, which the system reads on tap.
 *
 * Both encode the same thing: SSID, authentication, and the pre-shared key.
 */
object WifiCredentials {

    const val NFC_MIME_TYPE = "application/vnd.wfa.wsc"

    /**
     * The `WIFI:T:...;S:...;P:...;H:...;;` string carried by a Wi-Fi QR code.
     *
     * Special characters (`\ ; , : "`) are backslash-escaped as the format requires,
     * so an SSID or password containing them still round-trips.
     */
    fun qrPayload(profile: Profile): String {
        val type = when (profile.security) {
            WifiSecurity.WPA2, WifiSecurity.WPA3 -> "WPA"
            WifiSecurity.OPEN, WifiSecurity.OWE -> "nopass"
        }
        val builder = StringBuilder("WIFI:T:").append(type)
            .append(";S:").append(escape(profile.ssid)).append(';')
        if (profile.security.needsPassword) {
            builder.append("P:").append(escape(profile.password)).append(';')
        }
        if (profile.hiddenSsid) builder.append("H:true;")
        return builder.append(';').toString()
    }

    /** Plain text for the share sheet: readable credentials plus the machine-readable URI. */
    fun shareText(profile: Profile): String {
        val lines = mutableListOf("Wi-Fi: ${profile.ssid}")
        if (profile.security.needsPassword) lines += "Пароль: ${profile.password}"
        if (profile.hiddenSsid) lines += "Скрытая сеть"
        lines += ""
        lines += qrPayload(profile)
        return lines.joinToString("\n")
    }

    private fun escape(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            if (ch == '\\' || ch == ';' || ch == ',' || ch == ':' || ch == '"') out.append('\\')
            out.append(ch)
        }
        return out.toString()
    }

    /**
     * The payload bytes for an `application/vnd.wfa.wsc` NDEF record: a single WSC
     * Credential attribute (0x100E) wrapping the network's sub-attributes. This is what
     * Android writes to a tag when you share Wi-Fi via NFC, and what it reads back on tap.
     */
    fun wscPayload(profile: Profile): ByteArray {
        val ssid = profile.ssid.toByteArray(StandardCharsets.UTF_8)
        val key =
            if (profile.security.needsPassword) profile.password.toByteArray(StandardCharsets.UTF_8)
            else ByteArray(0)

        // WSC authentication / encryption type words. WPA2-PSK + AES covers WPA2 and, in
        // transitional mode, WPA3 clients too — good enough for sharing a home PSK.
        val (authType, encType) = when (profile.security) {
            WifiSecurity.OPEN, WifiSecurity.OWE -> AUTH_OPEN to ENC_NONE
            WifiSecurity.WPA2, WifiSecurity.WPA3 -> AUTH_WPA2PSK to ENC_AES
        }

        val credential = ByteArrayOutputStream().apply {
            writeTlv(ID_NETWORK_INDEX, byteArrayOf(1))
            writeTlv(ID_SSID, ssid)
            writeTlv(ID_AUTH_TYPE, word(authType))
            writeTlv(ID_ENCRYPT_TYPE, word(encType))
            writeTlv(ID_NETWORK_KEY, key)
            writeTlv(ID_MAC_ADDRESS, ByteArray(6)) // 00:00:00:00:00:00 — unspecified
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            writeTlv(ID_CREDENTIAL, credential)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeTlv(id: Int, value: ByteArray) {
        write((id ushr 8) and 0xFF)
        write(id and 0xFF)
        write((value.size ushr 8) and 0xFF)
        write(value.size and 0xFF)
        write(value)
    }

    private fun word(value: Int): ByteArray =
        byteArrayOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte())

    // WSC attribute ids (Wi-Fi Simple Configuration Technical Specification).
    private const val ID_CREDENTIAL = 0x100E
    private const val ID_NETWORK_INDEX = 0x1026
    private const val ID_SSID = 0x1045
    private const val ID_AUTH_TYPE = 0x1003
    private const val ID_ENCRYPT_TYPE = 0x100F
    private const val ID_NETWORK_KEY = 0x1027
    private const val ID_MAC_ADDRESS = 0x1020

    private const val AUTH_OPEN = 0x0001
    private const val AUTH_WPA2PSK = 0x0020
    private const val ENC_NONE = 0x0001
    private const val ENC_AES = 0x0008
}
