package icu.nd4y.netswitcher.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The config persisted in DataStore is JSON produced by [Profile]'s serializer, and a
 * failed decode silently resets it to defaults — so every schema change must keep the
 * previous release's output readable. Same Json settings as ConfigRepository.
 */
class ProfileJsonTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `autoJoin booleans persisted by 1_19 decode as ON and OFF`() {
        val off = """{"id":"g","name":"Guest","kind":"WIFI","ssid":"Guest","autoJoin":false}"""
        assertEquals(AutoJoin.OFF, json.decodeFromString(Profile.serializer(), off).autoJoin)

        val on = off.replace("false", "true")
        assertEquals(AutoJoin.ON, json.decodeFromString(Profile.serializer(), on).autoJoin)
    }

    @Test
    fun `autoJoin is stored by name, round trips and defaults to SYSTEM`() {
        val profile = Profile(
            id = "h", name = "Home", kind = ProfileKind.WIFI, ssid = "Home",
            autoJoin = AutoJoin.OFF, overwriteSaved = true,
        )
        val text = json.encodeToString(Profile.serializer(), profile)
        assertTrue(text, text.contains("\"autoJoin\":\"OFF\""))
        assertEquals(profile, json.decodeFromString(Profile.serializer(), text))

        val bare = """{"id":"h","name":"Home","kind":"WIFI"}"""
        val decoded = json.decodeFromString(Profile.serializer(), bare)
        assertEquals(AutoJoin.SYSTEM, decoded.autoJoin)
        assertEquals(false, decoded.overwriteSaved)
    }
}
