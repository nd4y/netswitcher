package icu.nd4y.netswitcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamlConfigTest {

    @Test
    fun `default home screen excludes one-shot actions HomeScreen never renders`() {
        val config = Config.default()

        val oneShotIds = config.profiles
            .filter { it.kind in listOf(
                ProfileKind.CELLULAR, ProfileKind.ETHERNET,
                ProfileKind.WIFI_ON, ProfileKind.WIFI_OFF,
            ) }
            .map { it.id }
        assertTrue(oneShotIds.isNotEmpty()) // sanity: the default config still has them

        // They may live on the widget/shortcuts/tiles, just never on the main screen —
        // HomeScreen only draws toggles and Wi-Fi networks.
        assertTrue(oneShotIds.none { it in config.homeIds })
        assertEquals(
            config.profiles.filter { it.kind.rendersOnHomeScreen }.map { it.id },
            config.homeIds,
        )
    }

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
        assertEquals(original.startNotification, decoded.startNotification)
    }

    @Test
    fun `reordering the main screen reorders the profile list too`() {
        val config = Config.default()
        val networks = config.profiles.filter { it.kind == ProfileKind.WIFI }.map { it.id }
        val swapped = listOf(networks[1], networks[0]) + networks.drop(2)

        val moved = config.reordered(swapped)

        // Both surfaces agree, and non-network profiles have not budged.
        assertEquals(swapped, moved.profiles.filter { it.kind == ProfileKind.WIFI }.map { it.id })
        assertEquals(swapped, moved.homeIds.filter { it in networks })
        assertEquals(
            config.profiles.filterNot { it.kind == ProfileKind.WIFI }.map { it.id },
            moved.profiles.filterNot { it.kind == ProfileKind.WIFI }.map { it.id },
        )
        assertEquals(config.profiles.size, moved.profiles.size)
    }

    @Test
    fun `layout reset leaves profiles alone`() {
        val config = Config.default().let {
            it.copy(homeIds = emptyList(), widgetIds = emptyList(), tileBindings = emptyMap())
        }
        val restored = config.withDefaultLayout()

        assertEquals(config.profiles, restored.profiles)
        // Only what HomeScreen actually renders — toggles and Wi-Fi networks. One-shot
        // actions (LTE only, Wi-Fi on/off, Ethernet only) never appear there, so they
        // must not come back as dead entries in homeIds after a reset.
        assertEquals(
            config.profiles.filter { it.kind.rendersOnHomeScreen }.map { it.id },
            restored.homeIds,
        )
        assertTrue(restored.homeIds.none { id ->
            config.profile(id)?.kind in listOf(
                ProfileKind.CELLULAR, ProfileKind.ETHERNET,
                ProfileKind.WIFI_ON, ProfileKind.WIFI_OFF,
            )
        })
        assertTrue(restored.widgetIds.isNotEmpty())
        assertTrue(restored.tileBindings.isNotEmpty())
        assertTrue(restored.tileBindings.values.all { id -> config.profiles.any { it.id == id } })
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
