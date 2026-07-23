package icu.nd4y.netswitcher.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.ProfileKind

data class NetworkSnapshot(
    val transport: String,
    val detail: String,
    val wifiEnabled: Boolean,
)

object NetworkStatus {

    /**
     * `cmd wifi status` is preferred when privileged: unlike WifiInfo it reports the
     * SSID without needing location permission or location services turned on.
     */
    suspend fun read(context: Context, shell: PrivilegedShell?): NetworkSnapshot {
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
        val wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)

        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching {
            connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        }.getOrNull()

        val transport = when {
            caps == null -> "Нет сети"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Мобильная сеть"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Другое"
        }

        val detail = when (transport) {
            "Wi-Fi" -> currentSsid(context, shell) ?: "SSID недоступен"
            "Мобильная сеть" -> {
                val sub = TelephonyOps.currentDefaultDataSubId()
                val sims = TelephonyOps.readSims(context)
                sims.firstOrNull { it.subscriptionId == sub }?.label ?: "SIM #$sub"
            }
            else -> if (wifiEnabled) "Wi-Fi включён" else "Wi-Fi выключен"
        }

        return NetworkSnapshot(transport, detail, wifiEnabled)
    }

    /**
     * Cheap, synchronous "is this profile the current state?" check for tiles and
     * widget buttons — no privileged shell, no blocking calls.
     */
    fun quickActive(context: Context, profile: Profile): Boolean = runCatching {
        val wifiManager = context.getSystemService(WifiManager::class.java)
        val wifiOn = wifiManager?.isWifiEnabled == true
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)

        when (profile.kind) {
            ProfileKind.WIFI -> {
                @Suppress("DEPRECATION")
                val ssid = wifiManager?.connectionInfo?.ssid?.trim('"')
                wifiOn && ssid != null && ssid == profile.ssid
            }

            ProfileKind.WIFI_OFF -> !wifiOn

            ProfileKind.CELLULAR ->
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
                    (profile.subscriptionId < 0 ||
                        TelephonyOps.currentDefaultDataSubId() == profile.subscriptionId)

            ProfileKind.ETHERNET ->
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }
    }.getOrDefault(false)

    suspend fun currentSsid(context: Context, shell: PrivilegedShell?): String? {
        if (shell != null) {
            val status = shell.exec("cmd wifi status")
            val line = status.output.lineSequence().firstOrNull { it.contains("SSID:") }
            val parsed = line?.substringAfter("SSID:")?.trim()?.substringBefore(",")?.trim('"')
            if (!parsed.isNullOrBlank() && parsed != "<unknown ssid>") return parsed
            if (status.output.contains("Wifi is disabled", ignoreCase = true)) return null
        }
        return runCatching {
            @Suppress("DEPRECATION")
            val info = context.getSystemService(WifiManager::class.java)?.connectionInfo
            info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        }.getOrNull()
    }
}
