package icu.nd4y.netswitcher.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.ProfileKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config as RoboConfig
import org.robolectric.annotation.GraphicsMode

/** The merged main tab: dashboard buttons plus profile management in one place. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RoboConfig(sdk = [35])
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun controller() = ConfigController(
        ApplicationProvider.getApplicationContext(),
        CoroutineScope(SupervisorJob() + Dispatchers.Main),
    )

    private fun show(config: Config = Config.default(), onEdit: (Profile) -> Unit = {}) {
        compose.setContent {
            NetSwitcherTheme {
                HomeScreen(config = config, controller = controller(), onEdit = onEdit)
            }
        }
    }

    @Test
    fun `renders toggles networks and the other-profiles section`() {
        show()

        // The four default toggles…
        compose.onNodeWithText("Wi-Fi").assertExists()
        compose.onNodeWithText("LTE").assertExists()
        compose.onNodeWithText("Авиарежим").assertExists()
        // …the network cards…
        compose.onNodeWithText("Home 5G").assertExists()
        compose.onNodeWithText("Guest").assertExists()
        // …and the management section for what the main screen doesn't draw.
        compose.onNodeWithText("Остальные профили").assertExists()
        compose.onNodeWithText("LTE only").assertExists()
        compose.onNodeWithText("Wi-Fi off").assertExists()
        compose.onNodeWithText("Ethernet only").assertExists()
    }

    @Test
    fun `edit icon hands the tapped profile to the editor`() {
        var edited: Profile? = null
        show(onEdit = { edited = it })

        compose.onAllNodesWithContentDescription("Изменить").onFirst().performClick()

        assertNotNull(edited)
        assertEquals(ProfileKind.WIFI, edited?.kind)
    }

    @Test
    fun `add button opens the editor with a fresh network draft`() {
        var edited: Profile? = null
        show(onEdit = { edited = it })

        compose.onNodeWithContentDescription("Добавить профиль").performClick()

        assertEquals("Новая сеть", edited?.name)
        assertEquals(ProfileKind.WIFI, edited?.kind)
    }

    @Test
    fun `share icon appears only for networks with an ssid`() {
        val config = Config.default().let { base ->
            base.copy(
                profiles = base.profiles.map {
                    if (it.id == "home") it.copy(ssid = "") else it
                },
            )
        }
        show(config)

        // Default config has 5 networks with an SSID; "home" just lost its own —
        // its card must not offer sharing, leaving 4 share buttons.
        val shareButtons = compose.onAllNodesWithContentDescription("Поделиться")
        shareButtons.onFirst().assertExists()
        assertEquals(
            4,
            shareButtons.fetchSemanticsNodes().size,
        )
    }
}
