package icu.nd4y.netswitcher.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config as RoboConfig
import org.robolectric.annotation.GraphicsMode

/** Boots the real activity: three tabs after the Профили/Сети merge, all navigable. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RoboConfig(sdk = [35])
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** The tab is the clickable node with that label — screen headers are not. */
    private fun tab(label: String) = compose.onNode(hasText(label) and hasClickAction())

    @Test
    fun `navigation has exactly the three merged tabs`() {
        tab("Сети").assertExists()
        tab("Кнопки").assertExists()
        tab("Настройки").assertExists()
        compose.onNodeWithText("Профили").assertDoesNotExist()
    }

    @Test
    fun `tabs actually switch their screens`() {
        tab("Кнопки").performClick()
        compose.onNodeWithText("Главный экран приложения").assertExists()

        tab("Настройки").performClick()
        compose.onNodeWithText("Источник привилегий").assertExists()

        tab("Сети").performClick()
        compose.onNodeWithText("Остальные профили").assertExists()
    }

    @Test
    fun `deleting a profile offers an undo snackbar`() {
        // Open the editor for the first network card, then delete it.
        compose.onAllNodesWithContentDescription("Изменить").onFirst().performClick()
        compose.onNodeWithText("Удалить").performScrollTo().performClick()

        // The undo affordance appears.
        compose.onNodeWithText("Отменить").assertExists()
    }
}
