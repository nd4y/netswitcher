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
        }
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
