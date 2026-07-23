package icu.nd4y.netswitcher.data

import icu.nd4y.netswitcher.R
import kotlinx.serialization.Serializable

/** What a button actually does when pressed. */
enum class ProfileKind {
    /** Turn Wi-Fi on and join a specific SSID. */
    WIFI,

    /** Drop Wi-Fi and ride mobile data, optionally on a specific SIM. */
    CELLULAR,

    /** Drop Wi-Fi (and optionally mobile data) so a wired link becomes default. */
    ETHERNET,

    /** Just turn Wi-Fi off. */
    WIFI_OFF,
}

/** Security token accepted by `cmd wifi connect-network`. */
enum class WifiSecurity(val token: String, val needsPassword: Boolean) {
    WPA2("wpa2", true),
    WPA3("wpa3", true),
    OPEN("open", false),
    OWE("owe", false),
}

enum class MobileDataAction { KEEP, ENABLE, DISABLE }

/** Which privileged backend to use for the shell commands. */
enum class Backend { AUTO, SHIZUKU, ROOT, NONE }

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val kind: ProfileKind,
    // --- Wi-Fi ---
    val ssid: String = "",
    val security: WifiSecurity = WifiSecurity.WPA2,
    val password: String = "",
    val hiddenSsid: Boolean = false,
    val bssid: String = "",
    // --- Cellular ---
    /** Subscription id of the SIM to make the default data SIM; -1 = leave as is. */
    val subscriptionId: Int = -1,
    // --- Ethernet ---
    val ethernetInterface: String = "eth0",
    // --- Common ---
    val mobileData: MobileDataAction = MobileDataAction.KEEP,
    /** For CELLULAR / ETHERNET / WIFI_OFF: turn the Wi-Fi radio off. */
    val disableWifi: Boolean = true,
) {
    val iconRes: Int
        get() = when (kind) {
            ProfileKind.WIFI -> R.drawable.ic_wifi
            ProfileKind.CELLULAR -> R.drawable.ic_cellular
            ProfileKind.ETHERNET -> R.drawable.ic_ethernet
            ProfileKind.WIFI_OFF -> R.drawable.ic_wifi_off
        }

    val subtitle: String
        get() = when (kind) {
            ProfileKind.WIFI -> ssid.ifBlank { "SSID не задан" }
            ProfileKind.CELLULAR -> if (subscriptionId >= 0) "SIM #$subscriptionId" else "текущая SIM"
            ProfileKind.ETHERNET -> ethernetInterface
            ProfileKind.WIFI_OFF -> "Wi-Fi выкл."
        }
}

@Serializable
data class Config(
    val profiles: List<Profile> = emptyList(),
    /** Ordered profile ids exposed as launcher long-press shortcuts. */
    val shortcutIds: List<String> = emptyList(),
    /** Ordered profile ids drawn on the home screen widget. */
    val widgetIds: List<String> = emptyList(),
    /** Quick Settings tile slot (1..8, as string) -> profile id. */
    val tileBindings: Map<String, String> = emptyMap(),
    val backend: Backend = Backend.AUTO,
    val showToasts: Boolean = true,
    val widgetColumns: Int = 2,
    val verboseLog: Boolean = false,
) {
    fun profile(id: String?): Profile? = profiles.firstOrNull { it.id == id }

    fun resolve(ids: List<String>): List<Profile> = ids.mapNotNull { id -> profile(id) }

    companion object {
        const val TILE_COUNT = 8

        fun default(): Config {
            val wifi = { id: String, name: String, ssid: String ->
                Profile(id = id, name = name, kind = ProfileKind.WIFI, ssid = ssid)
            }
            val profiles = listOf(
                wifi("home", "Home", "ND4Y-Home"),
                wifi("home5", "Home 5G", "ND4Y-Home-5G"),
                wifi("guest", "Guest", "ND4Y-Guest"),
                wifi("guest5", "Guest 5G", "ND4Y-Guest-5G"),
                wifi("iot", "Home IoT", "ND4Y-Home-IoT"),
                Profile(
                    id = "lte",
                    name = "LTE",
                    kind = ProfileKind.CELLULAR,
                    mobileData = MobileDataAction.ENABLE,
                    disableWifi = true,
                ),
                Profile(
                    id = "wifioff",
                    name = "Wi-Fi off",
                    kind = ProfileKind.WIFI_OFF,
                    mobileData = MobileDataAction.KEEP,
                    disableWifi = true,
                ),
                Profile(
                    id = "eth",
                    name = "Ethernet",
                    kind = ProfileKind.ETHERNET,
                    mobileData = MobileDataAction.DISABLE,
                    disableWifi = true,
                ),
            )
            return Config(
                profiles = profiles,
                shortcutIds = listOf("home", "home5", "lte", "wifioff"),
                widgetIds = listOf("home", "home5", "guest", "iot", "lte", "wifioff"),
                tileBindings = mapOf("1" to "home", "2" to "home5", "3" to "lte", "4" to "wifioff"),
            )
        }
    }
}

/** Outcome of pressing a button, shown as a toast and in the in-app log. */
data class ActionResult(
    val success: Boolean,
    val message: String,
    val log: List<String> = emptyList(),
)
