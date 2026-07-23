package icu.nd4y.netswitcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamlConfigTest {

    @Test
    fun `default config survives a round trip`() {
        val original = Config.default()
        val decoded = YamlConfig.decode(YamlConfig.encode(original)).getOrThrow()

        assertEquals(original.profiles, decoded.profiles)
        assertEquals(original.homeIds, decoded.homeIds)
        assertEquals(original.shortcutIds, decoded.shortcutIds)
        assertEquals(original.widgetIds, decoded.widgetIds)
        assertEquals(original.tileBindings, decoded.tileBindings)
        assertEquals(original.backend, decoded.backend)
        assertEquals(original.widgetColumns, decoded.widgetColumns)
        assertEquals(original.showToasts, decoded.showToasts)
    }

    @Test
    fun `passwords with quotes and backslashes survive`() {
        val original = Config.default().let { config ->
            config.copy(
                profiles = config.profiles.map { profile ->
                    if (profile.kind == ProfileKind.WIFI) {
                        profile.copy(password = """he said "hi\there" # not a comment""")
                    } else {
                        profile
                    }
                }
            )
        }
        val decoded = YamlConfig.decode(YamlConfig.encode(original)).getOrThrow()
        assertEquals(original.profiles, decoded.profiles)
    }

    @Test
    fun `hand written minimal file parses`() {
        val yaml = """
            version: 1
            backend: SHIZUKU
            widgetColumns: 3
            profiles:
              - id: home
                name: Дом
                kind: WIFI
                ssid: My-Home
                security: wpa3
                password: secret
              - id: wifioff
                name: Wi-Fi off
                kind: WIFI_OFF
                mobileData: ENABLE
            shortcuts:
              - home
              - wifioff
              - ghost
            widget: []
            tiles:
              "1": home
              "2": nonexistent
        """.trimIndent()

        val config = YamlConfig.decode(yaml).getOrThrow()

        assertEquals(2, config.profiles.size)
        assertEquals(Backend.SHIZUKU, config.backend)
        assertEquals(3, config.widgetColumns)
        assertEquals(WifiSecurity.WPA3, config.profiles[0].security)
        assertEquals(MobileDataAction.ENABLE, config.profiles[1].mobileData)
        // Unknown ids are dropped rather than producing dead buttons.
        assertEquals(listOf("home", "wifioff"), config.shortcutIds)
        assertEquals(mapOf("1" to "home"), config.tileBindings)
    }

    /**
     * YAML 1.1 reads bare `off`/`on`/`yes`/`no` as booleans, so an id written without
     * quotes would silently become "false". The emitter always quotes ids; this pins
     * down that a hand-written file at least stays internally consistent.
     */
    @Test
    fun `bare yaml booleans as ids stay consistent`() {
        val yaml = """
            profiles:
              - id: off
                name: Wi-Fi off
                kind: WIFI_OFF
            shortcuts:
              - off
        """.trimIndent()

        val config = YamlConfig.decode(yaml).getOrThrow()
        assertEquals(config.profiles.single().id, config.shortcutIds.single())
    }

    @Test
    fun `toggles round trip and a missing home key means show everything`() {
        val yaml = """
            profiles:
              - id: "wifi_sw"
                name: "Wi-Fi"
                kind: WIFI_TOGGLE
              - id: "air_sw"
                name: "Авиарежим"
                kind: AIRPLANE_TOGGLE
        """.trimIndent()

        val config = YamlConfig.decode(yaml).getOrThrow()

        assertEquals(ProfileKind.WIFI_TOGGLE, config.profiles[0].kind)
        assertEquals(ProfileKind.AIRPLANE_TOGGLE, config.profiles[1].kind)
        assertTrue(config.profiles.all { it.kind.isToggle })
        assertEquals(listOf("wifi_sw", "air_sw"), config.homeIds)

        // An explicit empty list, on the other hand, really means an empty main screen.
        val emptied = YamlConfig.decode("$yaml\nhome: []").getOrThrow()
        assertTrue(emptied.homeIds.isEmpty())
    }

    @Test
    fun `garbage input fails instead of wiping the configuration`() {
        assertTrue(YamlConfig.decode("не yaml вовсе: [[[").isFailure)
        assertTrue(YamlConfig.decode("version: 1").isFailure)
    }
}
