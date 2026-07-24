package icu.nd4y.netswitcher.data

import org.yaml.snakeyaml.Yaml

/**
 * Human-editable YAML representation of [Config].
 *
 * Emitting is hand-rolled so the file stays readable and comment-friendly; parsing
 * goes through SnakeYAML because the file is meant to be edited by hand and needs to
 * survive whatever formatting the user throws at it.
 */
object YamlConfig {

    const val FORMAT_VERSION = 1

    fun encode(config: Config): String = buildString {
        appendLine("# NetSwitcher configuration")
        appendLine("# Импорт: Настройки → Импорт конфигурации (YAML)")
        appendLine("version: $FORMAT_VERSION")
        appendLine("backend: ${config.backend.name}          # AUTO | SHIZUKU | ROOT | NONE")
        appendLine("startNotification: ${config.startNotification.name}   # SHADE | HEADS_UP")
        appendLine("verboseLog: ${config.verboseLog}")
        appendLine("widgetColumns: ${config.widgetColumns}")
        appendLine()
        appendLine("profiles:")
        config.profiles.forEach { profile ->
            appendLine("  - id: ${quote(profile.id)}")
            appendLine("    name: ${quote(profile.name)}")
            appendLine("    kind: ${profile.kind.name}")
            when (profile.kind) {
                ProfileKind.WIFI -> {
                    appendLine("    ssid: ${quote(profile.ssid)}")
                    appendLine("    security: ${profile.security.name}")
                    appendLine("    password: ${quote(profile.password)}")
                    if (profile.hiddenSsid) appendLine("    hidden: true")
                    if (profile.bssid.isNotBlank()) {
                        appendLine("    bssid: ${quote(profile.bssid)}")
                    }
                    appendLine("    tapAgainDisconnects: ${profile.tapAgainDisconnects}")
                }

                ProfileKind.CELLULAR, ProfileKind.CELLULAR_TOGGLE -> {
                    appendLine("    subscriptionId: ${profile.subscriptionId}   # -1 = не менять SIM")
                }

                ProfileKind.ETHERNET, ProfileKind.ETHERNET_TOGGLE -> {
                    appendLine("    ethernetInterface: ${quote(profile.ethernetInterface)}")
                }

                ProfileKind.WIFI_OFF, ProfileKind.WIFI_ON,
                ProfileKind.WIFI_TOGGLE, ProfileKind.AIRPLANE_TOGGLE,
                -> Unit
            }
            if (!profile.kind.isToggle) {
                appendLine("    mobileData: ${profile.mobileData.name}   # KEEP | ENABLE | DISABLE")
                if (profile.kind != ProfileKind.WIFI && profile.kind != ProfileKind.WIFI_ON) {
                    appendLine("    disableWifi: ${profile.disableWifi}")
                }
            }
        }
        appendLine()
        appendLine("# Кнопки на главном экране приложения (порядок важен).")
        appendLine("# Профили-переключатели рисуются компактным рядом сверху.")
        appendLine("home:${list(config.homeIds)}")
        appendLine("# Ярлыки на удержании иконки приложения (порядок важен)")
        appendLine("shortcuts:${list(config.shortcutIds)}")
        appendLine("# Кнопки на виджете (порядок важен)")
        appendLine("widget:${list(config.widgetIds)}")
        appendLine("# Плитки быстрых настроек: номер плитки -> id профиля")
        appendLine("tiles:")
        if (config.tileBindings.isEmpty()) {
            appendLine("  {}")
        } else {
            config.tileBindings.entries
                .sortedBy { it.key.toIntOrNull() ?: 0 }
                .forEach { (slot, id) -> appendLine("  \"$slot\": ${quote(id)}") }
        }
    }

    fun decode(text: String): Result<Config> = runCatching {
        val root = Yaml().load<Any?>(text) as? Map<*, *>
            ?: error("Ожидался YAML-объект верхнего уровня")

        val profiles = (root["profiles"] as? List<*>).orEmpty().mapIndexedNotNull { index, raw ->
            val map = raw as? Map<*, *> ?: return@mapIndexedNotNull null
            val id = map.string("id") ?: "p$index"
            Profile(
                id = id,
                name = map.string("name") ?: id,
                kind = map.enum("kind", ProfileKind.entries, ProfileKind.WIFI),
                ssid = map.string("ssid").orEmpty(),
                security = map.enum("security", WifiSecurity.entries, WifiSecurity.WPA2),
                password = map.string("password").orEmpty(),
                hiddenSsid = map.bool("hidden") ?: false,
                bssid = map.string("bssid").orEmpty(),
                subscriptionId = map.int("subscriptionId") ?: -1,
                ethernetInterface = map.string("ethernetInterface") ?: "eth0",
                mobileData = map.enum("mobileData", MobileDataAction.entries, MobileDataAction.KEEP),
                disableWifi = map.bool("disableWifi") ?: true,
                tapAgainDisconnects = map.bool("tapAgainDisconnects") ?: true,
            )
        }
        if (profiles.isEmpty()) error("В файле нет ни одного профиля")

        val known = profiles.map { it.id }.toSet()
        val tiles = (root["tiles"] as? Map<*, *>).orEmpty()
            .mapNotNull { (slot, id) ->
                val key = slot?.toString() ?: return@mapNotNull null
                val value = id?.toString() ?: return@mapNotNull null
                if (value in known) key to value else null
            }
            .toMap()

        val byId = profiles.associateBy { it.id }

        Config(
            profiles = profiles,
            // A file without a `home:` key means "show everything", not "show nothing".
            // Either way the list is trimmed to what the main screen actually renders
            // (toggles and Wi-Fi networks) — a one-shot action in `home` would be a
            // dead entry that never draws but clutters the buttons picker.
            homeIds = root.ids("home", known)
                .ifEmpty {
                    if (root.containsKey("home")) emptyList() else profiles.map { it.id }
                }
                .filter { byId[it]?.kind?.rendersOnHomeScreen == true },
            shortcutIds = root.ids("shortcuts", known),
            widgetIds = root.ids("widget", known),
            tileBindings = tiles,
            backend = root.enum("backend", Backend.entries, Backend.AUTO),
            startNotification = root.enum(
                "startNotification",
                StartNotification.entries,
                StartNotification.SHADE,
            ),
            verboseLog = root.bool("verboseLog") ?: false,
            widgetColumns = (root.int("widgetColumns") ?: 2).coerceIn(1, 4),
        )
    }

    // ------------------------------------------------------------- Emitting

    private fun list(values: List<String>): String =
        if (values.isEmpty()) " []" else "\n" + values.joinToString("\n") { "  - ${quote(it)}" }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    // ------------------------------------------------------------- Parsing

    private fun Map<*, *>.string(key: String): String? =
        this[key]?.toString()?.takeIf { it.isNotBlank() }

    private fun Map<*, *>.int(key: String): Int? = when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }

    private fun Map<*, *>.bool(key: String): Boolean? = when (val value = this[key]) {
        is Boolean -> value
        is String -> value.trim().lowercase() in setOf("true", "yes", "on", "1")
        else -> null
    }

    private fun <T : Enum<T>> Map<*, *>.enum(key: String, values: List<T>, fallback: T): T {
        val raw = string(key) ?: return fallback
        return values.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: fallback
    }

    private fun Map<*, *>.ids(key: String, known: Set<String>): List<String> =
        (this[key] as? List<*>).orEmpty()
            .mapNotNull { it?.toString() }
            .filter { it in known }
}
