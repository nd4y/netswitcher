package icu.nd4y.netswitcher.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import icu.nd4y.netswitcher.data.Backend
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.ProfileKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    fun isWifiOn(context: Context): Boolean = runCatching {
        context.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
    }.getOrDefault(false)

    fun isAirplaneOn(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    }.getOrDefault(false)

    /**
     * `TelephonyManager.isDataEnabled` needs READ_PHONE_STATE; the global setting is
     * the fallback for when the user declined it.
     */
    fun isMobileDataOn(context: Context): Boolean {
        val fromTelephony = runCatching {
            val manager = context.getSystemService(TelephonyManager::class.java)
            val sub = TelephonyOps.currentDefaultDataSubId()
            val scoped =
                if (sub >= 0) manager?.createForSubscriptionId(sub) else manager
            @Suppress("MissingPermission")
            scoped?.isDataEnabled
        }.getOrNull()
        if (fromTelephony != null) return fromTelephony

        return runCatching {
            Settings.Global.getInt(context.contentResolver, "mobile_data", 0) == 1
        }.getOrDefault(false)
    }

    /** True when a wired link is actually present, not merely configured. */
    fun isEthernetUp(context: Context): Boolean = runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return false
        @Suppress("DEPRECATION")
        connectivity.allNetworks.any { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }
    }.getOrDefault(false)

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

            ProfileKind.WIFI_ON -> wifiOn

            ProfileKind.CELLULAR ->
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
                    (profile.subscriptionId < 0 ||
                        TelephonyOps.currentDefaultDataSubId() == profile.subscriptionId)

            ProfileKind.ETHERNET ->
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

            ProfileKind.WIFI_TOGGLE -> wifiOn
            ProfileKind.CELLULAR_TOGGLE -> isMobileDataOn(context)
            ProfileKind.ETHERNET_TOGGLE -> isEthernetUp(context)
            ProfileKind.AIRPLANE_TOGGLE -> isAirplaneOn(context)
        }
    }.getOrDefault(false)

    /**
     * Synchronous SSID via WifiInfo for surfaces that can't suspend (tiles). Needs the
     * fine-location permission; null when it is missing or nothing is connected.
     */
    fun currentSsidSync(context: Context): String? = runCatching {
        @Suppress("DEPRECATION")
        context.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
            ?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }.getOrNull()

    private val ssidMutex = Mutex()
    private var ssidCache: Pair<Long, String?>? = null

    /**
     * SSID for surfaces where WifiInfo is useless — TileServices are bound by SystemUI
     * and never count as a foreground app, so the location-gated WifiInfo path always
     * reads "<unknown ssid>" there. Goes through the privileged shell instead, with a
     * short-lived cache shared across callers: when the shade opens, all nine tiles
     * refresh at once, and only the first pays for the `cmd wifi status` exec.
     */
    suspend fun currentSsidShared(context: Context, backend: Backend): String? =
        ssidMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            ssidCache?.let { (at, ssid) -> if (now - at < 2_000) return ssid }
            val shell = runCatching { PrivilegeManager.resolve(backend).shell }.getOrNull()
            val ssid = currentSsid(context, shell)
            ssidCache = now to ssid
            ssid
        }

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
