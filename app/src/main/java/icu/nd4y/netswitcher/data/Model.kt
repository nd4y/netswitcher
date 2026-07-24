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

    /** Just turn the Wi-Fi radio on and let auto-join pick a network. */
    WIFI_ON,

    /** On/off switch for the Wi-Fi radio. */
    WIFI_TOGGLE,

    /** On/off switch for mobile data, optionally pinned to a SIM. */
    CELLULAR_TOGGLE,

    /** On/off switch for the wired interface. */
    ETHERNET_TOGGLE,

    /** On/off switch for airplane mode. */
    AIRPLANE_TOGGLE;

    /** Toggles report state and flip it, so they get their own compact row. */
    val isToggle: Boolean
        get() = this == WIFI_TOGGLE || this == CELLULAR_TOGGLE ||
            this == ETHERNET_TOGGLE || this == AIRPLANE_TOGGLE
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

/** How loudly the "switching…" notification announces itself. Never with sound. */
enum class StartNotification {
    /** Sits quietly in the shade (and in the status bar chip on Android 16+). */
    SHADE,

    /** Slides in as a heads-up banner over whatever is on screen. */
    HEADS_UP,
}

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
    /** WIFI: pressing the button while already on this network disconnects instead. */
    val tapAgainDisconnects: Boolean = true,
) {
    val iconRes: Int
        get() = when (kind) {
            ProfileKind.WIFI -> R.drawable.ic_wifi
            ProfileKind.CELLULAR -> R.drawable.ic_cellular
            ProfileKind.ETHERNET -> R.drawable.ic_ethernet
            ProfileKind.WIFI_OFF -> R.drawable.ic_wifi_off
            ProfileKind.WIFI_ON -> R.drawable.ic_wifi
            ProfileKind.WIFI_TOGGLE -> R.drawable.ic_wifi
            ProfileKind.CELLULAR_TOGGLE -> R.drawable.ic_cellular
            ProfileKind.ETHERNET_TOGGLE -> R.drawable.ic_ethernet
            ProfileKind.AIRPLANE_TOGGLE -> R.drawable.ic_airplane
        }

    val subtitle: String
        get() = when (kind) {
            ProfileKind.WIFI -> ssid.ifBlank { "SSID не задан" }
            ProfileKind.CELLULAR -> if (subscriptionId >= 0) "SIM #$subscriptionId" else "текущая SIM"
            ProfileKind.ETHERNET -> ethernetInterface
            ProfileKind.WIFI_OFF -> "Wi-Fi выкл."
            ProfileKind.WIFI_ON -> "Wi-Fi вкл."
            ProfileKind.WIFI_TOGGLE -> "переключатель"
            ProfileKind.CELLULAR_TOGGLE ->
                if (subscriptionId >= 0) "переключатель · SIM #$subscriptionId" else "переключатель"

            ProfileKind.ETHERNET_TOGGLE -> "переключатель · $ethernetInterface"
            ProfileKind.AIRPLANE_TOGGLE -> "переключатель"
        }
}

@Serializable
data class Config(
    val profiles: List<Profile> = emptyList(),
    /** Ordered profile ids shown on the main screen; empty means "all of them". */
    val homeIds: List<String> = emptyList(),
    /** Ordered profile ids exposed as launcher long-press shortcuts. */
    val shortcutIds: List<String> = emptyList(),
    /** Ordered profile ids drawn on the home screen widget. */
    val widgetIds: List<String> = emptyList(),
    /** Quick Settings tile slot (1..8, as string) -> profile id. */
    val tileBindings: Map<String, String> = emptyMap(),
    val backend: Backend = Backend.AUTO,
    val startNotification: StartNotification = StartNotification.SHADE,
    val widgetColumns: Int = 2,
    val verboseLog: Boolean = false,
) {
    fun profile(id: String?): Profile? = profiles.firstOrNull { it.id == id }

    fun resolve(ids: List<String>): List<Profile> = ids.mapNotNull { id -> profile(id) }

    fun homeProfiles(): List<Profile> = resolve(homeIds)

    /**
     * Applies a new order to [orderedIds], keeping every other profile where it is.
     * The profile list is the canonical order, and the main screen mirrors it — so a
     * drag in either place moves the card in both.
     */
    fun reordered(orderedIds: List<String>): Config {
        val ids = profiles.map { it.id }.reorderSubset(orderedIds)
        val byId = profiles.associateBy { it.id }
        return copy(
            profiles = ids.mapNotNull { byId[it] },
            homeIds = homeIds.reorderSubset(orderedIds.filter { it in homeIds }),
        )
    }

    /** Restores where buttons live without touching the profiles themselves. */
    fun withDefaultLayout(): Config {
        val toggles = profiles.filter { it.kind.isToggle }.map { it.id }
        val networks = profiles.filter { it.kind == ProfileKind.WIFI }.map { it.id }
        return copy(
            homeIds = profiles.map { it.id },
            shortcutIds = (toggles + networks).take(4),
            widgetIds = (toggles + networks).take(8),
            tileBindings = (toggles + networks + profiles.map { it.id })
                .distinct()
                .take(TILE_COUNT)
                .mapIndexed { index, id -> (index + 1).toString() to id }
                .toMap(),
            widgetColumns = 2,
        )
    }

    companion object {
        const val TILE_COUNT = 8

        fun default(): Config {
            val wifi = { id: String, name: String, ssid: String ->
                Profile(id = id, name = name, kind = ProfileKind.WIFI, ssid = ssid)
            }
            // Placeholder SSIDs — the point is to show the shape of a profile, the
            // user replaces them (or imports a YAML config) with their own networks.
            val profiles = listOf(
                Profile(id = "wifi_sw", name = "Wi-Fi", kind = ProfileKind.WIFI_TOGGLE),
                Profile(id = "lte_sw", name = "LTE", kind = ProfileKind.CELLULAR_TOGGLE),
                Profile(id = "eth_sw", name = "Ethernet", kind = ProfileKind.ETHERNET_TOGGLE),
                Profile(id = "air_sw", name = "Авиарежим", kind = ProfileKind.AIRPLANE_TOGGLE),
                wifi("home", "Home", "Home"),
                wifi("home5", "Home 5G", "Home-5G"),
                wifi("guest", "Guest", "Guest"),
                wifi("guest5", "Guest 5G", "Guest-5G"),
                wifi("iot", "IoT", "IoT"),
                Profile(
                    id = "lte",
                    name = "LTE",
                    kind = ProfileKind.CELLULAR,
                    mobileData = MobileDataAction.ENABLE,
                    disableWifi = true,
                ),
                Profile(
                    id = "wifion",
                    name = "Wi-Fi on",
                    kind = ProfileKind.WIFI_ON,
                    mobileData = MobileDataAction.KEEP,
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
                // Toggles first: they render as the compact top row of the main screen.
                homeIds = profiles.map { it.id },
                shortcutIds = listOf("wifi_sw", "lte_sw", "home", "air_sw"),
                widgetIds = listOf(
                    "wifi_sw", "lte_sw", "eth_sw", "air_sw",
                    "home", "home5", "guest", "iot",
                ),
                tileBindings = mapOf(
                    "1" to "wifi_sw",
                    "2" to "lte_sw",
                    "3" to "eth_sw",
                    "4" to "air_sw",
                    "5" to "home",
                    "6" to "home5",
                ),
            )
        }
    }
}

/**
 * Rewrites the positions occupied by [orderedSubset]'s members with that new order,
 * leaving everything else untouched. A no-op if the subset is not fully contained.
 */
fun List<String>.reorderSubset(orderedSubset: List<String>): List<String> {
    if (orderedSubset.isEmpty()) return this
    val members = orderedSubset.toSet()
    val slots = indices.filter { this[it] in members }
    if (slots.size != orderedSubset.size) return this
    val result = toMutableList()
    slots.forEachIndexed { position, slot -> result[slot] = orderedSubset[position] }
    return result
}

/** Outcome of pressing a button, shown as a toast and in the in-app log. */
data class ActionResult(
    val success: Boolean,
    val message: String,
    val log: List<String> = emptyList(),
)
