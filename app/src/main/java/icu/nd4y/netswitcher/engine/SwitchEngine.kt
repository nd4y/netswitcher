package icu.nd4y.netswitcher.engine

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.provider.Settings
import icu.nd4y.netswitcher.data.ActionResult
import icu.nd4y.netswitcher.data.Backend
import icu.nd4y.netswitcher.data.MobileDataAction
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.ProfileKind
import icu.nd4y.netswitcher.data.WifiSecurity
import kotlinx.coroutines.delay

/**
 * Turns a [Profile] into the sequence of privileged shell commands that actually
 * changes the device's connectivity.
 *
 * Since Android 10 a normal app cannot toggle the Wi-Fi radio or join a saved
 * network by itself, so everything here goes through a [PrivilegedShell]
 * (Shizuku or root). Without one we degrade to registering a network suggestion
 * and opening the system Internet panel.
 */
class SwitchEngine(context: Context) {

    private val appContext = context.applicationContext

    suspend fun run(profile: Profile, backend: Backend): ActionResult {
        val log = mutableListOf<String>()
        val privileges = PrivilegeManager.resolve(backend)
        log += "Бэкенд: ${privileges.description}"

        val shell = privileges.shell
            ?: return fallback(profile, privileges.description, log)

        return when (profile.kind) {
            ProfileKind.WIFI -> connectWifi(shell, profile, log)
            ProfileKind.CELLULAR -> switchToCellular(shell, profile, log)
            ProfileKind.ETHERNET -> switchToEthernet(shell, profile, log)
            ProfileKind.WIFI_OFF -> turnWifiOff(shell, profile, log)
            ProfileKind.WIFI_ON -> turnWifiOn(shell, profile, log)
            ProfileKind.WIFI_TOGGLE -> toggleWifi(shell, log)
            ProfileKind.CELLULAR_TOGGLE -> toggleMobileData(shell, profile, log)
            ProfileKind.ETHERNET_TOGGLE -> toggleEthernet(shell, profile, log)
            ProfileKind.AIRPLANE_TOGGLE -> toggleAirplane(shell, log)
        }
    }

    // --------------------------------------------------------------- Toggles

    private suspend fun toggleWifi(
        shell: PrivilegedShell,
        log: MutableList<String>,
    ): ActionResult {
        val wasOn = NetworkStatus.isWifiOn(appContext)
        val target = !wasOn
        exec(shell, "cmd wifi set-wifi-enabled ${if (target) "enabled" else "disabled"}", log)
        val settled = await(log) { NetworkStatus.isWifiOn(appContext) == target }
        return ActionResult(
            settled,
            when {
                !settled -> "Не удалось переключить Wi-Fi"
                target -> "Wi-Fi включён"
                else -> "Wi-Fi выключен"
            },
            log,
        )
    }

    private suspend fun toggleMobileData(
        shell: PrivilegedShell,
        profile: Profile,
        log: MutableList<String>,
    ): ActionResult {
        if (profile.subscriptionId >= 0) {
            TelephonyOps.setDefaultDataSub(shell, profile.subscriptionId, log)
        }
        val wasOn = NetworkStatus.isMobileDataOn(appContext)
        val target = !wasOn
        exec(shell, "svc data ${if (target) "enable" else "disable"}", log)
        val settled = await(log) { NetworkStatus.isMobileDataOn(appContext) == target }
        return ActionResult(
            settled,
            when {
                !settled -> "Не удалось переключить мобильные данные"
                target -> "Мобильные данные включены"
                else -> "Мобильные данные выключены"
            },
            log,
        )
    }

    /**
     * There is no public way to bring a wired interface up or down, so we try the
     * ethernet shell command first and fall back to `ip link` — which needs root.
     */
    private suspend fun toggleEthernet(
        shell: PrivilegedShell,
        profile: Profile,
        log: MutableList<String>,
    ): ActionResult {
        val iface = profile.ethernetInterface.ifBlank { "eth0" }
        val wasUp = NetworkStatus.isEthernetUp(appContext)
        val target = !wasUp
        val verb = if (target) "enable" else "disable"

        val viaCmd = exec(shell, "cmd ethernet $verb ${shQuote(iface)}", log)
        if (!viaCmd.ok || viaCmd.output.contains("Unknown command", ignoreCase = true)) {
            exec(shell, "ip link set ${shQuote(iface)} ${if (target) "up" else "down"}", log)
        }

        val settled = await(log) { NetworkStatus.isEthernetUp(appContext) == target }
        return if (settled) {
            ActionResult(true, if (target) "Ethernet включён" else "Ethernet выключен", log)
        } else {
            ActionResult(
                false,
                "Не удалось переключить Ethernet ($iface) — обычно требуются root-права",
                log,
            )
        }
    }

    private suspend fun toggleAirplane(
        shell: PrivilegedShell,
        log: MutableList<String>,
    ): ActionResult {
        val wasOn = NetworkStatus.isAirplaneOn(appContext)
        val target = !wasOn

        exec(shell, "cmd connectivity airplane-mode ${if (target) "enable" else "disable"}", log)
        var settled = await(log) { NetworkStatus.isAirplaneOn(appContext) == target }

        if (!settled) {
            // Older/vendor builds without the connectivity shell command: poke the
            // setting and announce it ourselves.
            exec(shell, "settings put global airplane_mode_on ${if (target) 1 else 0}", log)
            exec(
                shell,
                "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $target",
                log,
            )
            settled = await(log) { NetworkStatus.isAirplaneOn(appContext) == target }
        }

        return ActionResult(
            settled,
            when {
                !settled -> "Не удалось переключить авиарежим"
                target -> "Авиарежим включён"
                else -> "Авиарежим выключен"
            },
            log,
        )
    }

    /** Polls [condition] for a couple of seconds; the framework settles asynchronously. */
    private suspend fun await(
        log: MutableList<String>,
        attempts: Int = 8,
        condition: () -> Boolean,
    ): Boolean {
        repeat(attempts) {
            delay(300)
            if (condition()) return true
        }
        log += "Состояние не изменилось за ${attempts * 300} мс"
        return false
    }

    // ---------------------------------------------------------------- Wi-Fi

    private suspend fun connectWifi(
        shell: PrivilegedShell,
        profile: Profile,
        log: MutableList<String>,
    ): ActionResult {
        if (profile.ssid.isBlank()) {
            return ActionResult(false, "У профиля «${profile.name}» не задан SSID", log)
        }

        // Pressing the button for the network you are already on means "leave it".
        // There is no shell command to drop a single association, so the radio goes
        // off — which is also what makes the button behave like a toggle.
        if (profile.tapAgainDisconnects && NetworkStatus.isWifiOn(appContext)) {
            val current = NetworkStatus.currentSsid(appContext, shell)
            if (current == profile.ssid) {
                log += "Уже подключены к $current — отключаемся"
                exec(shell, "cmd wifi set-wifi-enabled disabled", log)
                val off = await(log) { !NetworkStatus.isWifiOn(appContext) }
                return ActionResult(
                    off,
                    if (off) "Отключено от ${profile.ssid} (Wi-Fi выключен)"
                    else "Не удалось отключиться от ${profile.ssid}",
                    log,
                )
            }
        }

        if (profile.security.needsPassword && profile.password.isBlank()) {
            return ActionResult(
                false,
                "Для «${profile.ssid}» нужен пароль — задайте его в профиле",
                log,
            )
        }

        exec(shell, "cmd wifi set-wifi-enabled enabled", log)
        delay(700)

        val command = buildString {
            append("cmd wifi connect-network ")
            append(shQuote(profile.ssid))
            append(' ')
            append(profile.security.token)
            if (profile.security.needsPassword) {
                append(' ')
                append(shQuote(profile.password))
            }
            if (profile.hiddenSsid) append(" -h")
            if (profile.bssid.isNotBlank()) append(" -b ${profile.bssid}")
        }
        val result = exec(shell, command, log)

        applyMobileData(shell, profile.mobileData, log)

        if (!result.ok || result.output.contains("Error", ignoreCase = true)) {
            return ActionResult(false, "Не удалось подключиться: ${result.output.take(180)}", log)
        }

        val ssid = awaitSsid(shell, profile.ssid, log)
        return if (ssid != null) {
            ActionResult(true, "Подключено: $ssid", log)
        } else {
            // The command was accepted; association may still be in flight.
            ActionResult(true, "Команда отправлена: ${profile.ssid}", log)
        }
    }

    private suspend fun awaitSsid(
        shell: PrivilegedShell,
        expected: String,
        log: MutableList<String>,
    ): String? {
        repeat(8) {
            delay(600)
            val ssid = NetworkStatus.currentSsid(appContext, shell)
            if (ssid == expected) {
                log += "Ассоциация подтверждена: $ssid"
                return ssid
            }
        }
        log += "SSID пока не подтверждён (подключение может ещё идти)"
        return null
    }

    // ------------------------------------------------------------- Cellular

    private suspend fun switchToCellular(
        shell: PrivilegedShell,
        profile: Profile,
        log: MutableList<String>,
    ): ActionResult {
        var simOk = true
        if (profile.subscriptionId >= 0) {
            simOk = TelephonyOps.setDefaultDataSub(shell, profile.subscriptionId, log)
        }

        // A cellular profile without an explicit choice still means "give me data".
        val dataAction =
            if (profile.mobileData == MobileDataAction.KEEP) MobileDataAction.ENABLE
            else profile.mobileData
        applyMobileData(shell, dataAction, log)

        if (profile.disableWifi) {
            exec(shell, "cmd wifi set-wifi-enabled disabled", log)
        }

        val simName = TelephonyOps.readSims(appContext)
            .firstOrNull { it.subscriptionId == TelephonyOps.currentDefaultDataSubId() }
            ?.label

        val message = when {
            !simOk -> "Wi-Fi выключен, но переключить SIM не удалось"
            simName != null -> "Мобильная сеть: $simName"
            else -> "Переключено на мобильную сеть"
        }
        return ActionResult(simOk, message, log)
    }

    // ------------------------------------------------------------- Ethernet

    private suspend fun switchToEthernet(
        shell: PrivilegedShell,
        profile: Profile,
        log: MutableList<String>,
    ): ActionResult {
        if (profile.disableWifi) {
            exec(shell, "cmd wifi set-wifi-enabled disabled", log)
        }
        applyMobileData(shell, profile.mobileData, log)

        if (profile.ethernetInterface.isNotBlank()) {
            // Best effort: only works when the shell runs as root.
            exec(shell, "ip link set ${shQuote(profile.ethernetInterface)} up", log)
        }

        delay(800)
        val snapshot = NetworkStatus.read(appContext, shell)
        return if (snapshot.transport == "Ethernet") {
            ActionResult(true, "Активен Ethernet", log)
        } else {
            ActionResult(
                true,
                "Wi-Fi отключён; активная сеть: ${snapshot.transport}",
                log,
            )
        }
    }

    // ------------------------------------------------------------ Wi-Fi off

    private suspend fun turnWifiOff(
        shell: PrivilegedShell,
        profile: Profile,
        log: MutableList<String>,
    ): ActionResult {
        val result = exec(shell, "cmd wifi set-wifi-enabled disabled", log)
        applyMobileData(shell, profile.mobileData, log)
        return if (result.ok) {
            ActionResult(true, "Wi-Fi выключен", log)
        } else {
            ActionResult(false, "Не удалось выключить Wi-Fi: ${result.output.take(180)}", log)
        }
    }

    // ------------------------------------------------------------- Wi-Fi on

    private suspend fun turnWifiOn(
        shell: PrivilegedShell,
        profile: Profile,
        log: MutableList<String>,
    ): ActionResult {
        val result = exec(shell, "cmd wifi set-wifi-enabled enabled", log)
        applyMobileData(shell, profile.mobileData, log)
        if (!result.ok) {
            return ActionResult(false, "Не удалось включить Wi-Fi: ${result.output.take(180)}", log)
        }
        // Auto-join picks the network, so just report what it landed on if it is quick.
        repeat(6) {
            delay(700)
            val ssid = NetworkStatus.currentSsid(appContext, shell)
            if (ssid != null) return ActionResult(true, "Wi-Fi включён: $ssid", log)
        }
        return ActionResult(true, "Wi-Fi включён", log)
    }

    // ---------------------------------------------------------------- Utils

    private suspend fun applyMobileData(
        shell: PrivilegedShell,
        action: MobileDataAction,
        log: MutableList<String>,
    ) {
        when (action) {
            MobileDataAction.KEEP -> Unit
            MobileDataAction.ENABLE -> exec(shell, "svc data enable", log)
            MobileDataAction.DISABLE -> exec(shell, "svc data disable", log)
        }
    }

    private suspend fun exec(
        shell: PrivilegedShell,
        command: String,
        log: MutableList<String>,
    ): ShellResult {
        val result = shell.exec(command)
        log += "$ $command -> ${result.exitCode}" +
            if (result.output.isBlank()) "" else " | ${result.output.take(200)}"
        return result
    }

    // ------------------------------------------------------------- Fallback

    /**
     * No privileged shell available. The best a plain app can do is register the
     * network as a suggestion and hand the user off to the system panel.
     */
    private fun fallback(
        profile: Profile,
        reason: String,
        log: MutableList<String>,
    ): ActionResult {
        if (profile.kind == ProfileKind.WIFI && profile.ssid.isNotBlank()) {
            runCatching {
                val builder = WifiNetworkSuggestion.Builder().setSsid(profile.ssid)
                if (profile.password.isNotBlank()) {
                    when (profile.security) {
                        WifiSecurity.WPA2 -> builder.setWpa2Passphrase(profile.password)
                        WifiSecurity.WPA3 -> builder.setWpa3Passphrase(profile.password)
                        WifiSecurity.OPEN, WifiSecurity.OWE -> Unit
                    }
                }
                if (profile.hiddenSsid) builder.setIsHiddenSsid(true)
                builder.setIsAppInteractionRequired(false)
                val manager = appContext.getSystemService(WifiManager::class.java)
                manager?.removeNetworkSuggestions(emptyList())
                val status = manager?.addNetworkSuggestions(listOf(builder.build()))
                log += "addNetworkSuggestions -> $status"
            }.onFailure { log += "Не удалось добавить suggestion: ${it.message}" }
        }

        val opened = runCatching {
            appContext.startActivity(
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
        log += "Открыта системная панель сети: $opened"

        return ActionResult(
            false,
            "$reason. Открыта системная панель — переключите вручную.",
            log,
        )
    }
}
