package icu.nd4y.netswitcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The shipped example must stay importable as the schema evolves. */
class ExampleConfigTest {

    @Test
    fun `example config parses`() {
        val file = File("../examples/example-config.yaml")
        assertTrue("не найден ${file.absolutePath}", file.exists())

        val config = YamlConfig.decode(file.readText()).getOrThrow()

        assertEquals(12, config.profiles.size)
        assertTrue(config.profiles.any { it.kind == ProfileKind.WIFI_ON })
        assertTrue(config.profiles.any { it.kind == ProfileKind.CELLULAR })
        assertTrue(config.profiles.any { it.kind == ProfileKind.ETHERNET })
        // All four switches must be present and lead the main screen.
        assertEquals(
            listOf(
                ProfileKind.WIFI_TOGGLE,
                ProfileKind.CELLULAR_TOGGLE,
                ProfileKind.ETHERNET_TOGGLE,
                ProfileKind.AIRPLANE_TOGGLE,
            ),
            config.homeProfiles().take(4).map { it.kind },
        )
        // Every referenced id must resolve, otherwise buttons would silently do nothing.
        val ids = config.profiles.map { it.id }.toSet()
        assertTrue(config.homeIds.all { it in ids })
        assertTrue(config.shortcutIds.all { it in ids })
        assertTrue(config.widgetIds.all { it in ids })
        assertTrue(config.tileBindings.values.all { it in ids })
        assertEquals(6, config.tileBindings.size)
    }
}
