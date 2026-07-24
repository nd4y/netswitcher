package icu.nd4y.netswitcher.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.ProfileKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config as RoboConfig
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RoboConfig(sdk = [35])
class ShareNetworkDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val profile = Profile(
        id = "w",
        name = "Home",
        kind = ProfileKind.WIFI,
        ssid = "ND4Y-Home",
        password = "secret",
    )

    @Test
    fun `dialog renders the qr and the share actions`() {
        compose.setContent {
            NetSwitcherTheme {
                ShareNetworkDialog(profile = profile, onDismiss = {})
            }
        }

        compose.onNodeWithText("Поделиться сетью").assertExists()
        compose.onNodeWithText("ND4Y-Home").assertExists()
        // The QR bitmap actually rendered (ZXing + Bitmap under Robolectric).
        compose.onNodeWithContentDescription("QR-код сети ND4Y-Home").assertExists()
        compose.onNodeWithText("Отправить текст").assertExists()
        compose.onNodeWithText("Отправить QR").assertExists()
        compose.onNodeWithText("Закрыть").assertExists()
    }
}
