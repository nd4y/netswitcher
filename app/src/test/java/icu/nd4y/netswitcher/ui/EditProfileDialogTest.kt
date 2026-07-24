package icu.nd4y.netswitcher.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.ProfileKind
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config as RoboConfig
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The dialog is tall; a roomy screen plus performScrollTo keeps the bottom
// buttons reachable for clicks.
@RoboConfig(sdk = [35], qualifiers = "w480dp-h1000dp")
class EditProfileDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val wifi = Profile(id = "w", name = "Home", kind = ProfileKind.WIFI, ssid = "X")

    @Test
    fun `wifi profile shows its fields`() {
        compose.setContent {
            NetSwitcherTheme {
                EditProfileDialog(profile = wifi, onDismiss = {}, onSave = {})
            }
        }
        compose.onNodeWithText("SSID").assertExists()
        compose.onNodeWithText("Пароль").assertExists()
        compose.onNodeWithText("Сохранить").assertExists()
    }

    @Test
    fun `delete is offered only for existing profiles and fires the callback`() {
        var deleted = false
        compose.setContent {
            NetSwitcherTheme {
                EditProfileDialog(
                    profile = wifi,
                    onDismiss = {},
                    onSave = {},
                    onDelete = { deleted = true },
                )
            }
        }
        compose.onNodeWithText("Удалить").performScrollTo().performClick()
        assertTrue(deleted)
    }

    @Test
    fun `no delete button without a callback`() {
        compose.setContent {
            NetSwitcherTheme {
                EditProfileDialog(profile = wifi, onDismiss = {}, onSave = {})
            }
        }
        compose.onNodeWithText("Удалить").assertDoesNotExist()
    }

    @Test
    fun `saving is blocked while the name is blank`() {
        compose.setContent {
            NetSwitcherTheme {
                EditProfileDialog(profile = wifi.copy(name = ""), onDismiss = {}, onSave = {})
            }
        }
        compose.onNodeWithText("Сохранить").assertIsNotEnabled()
    }
}
