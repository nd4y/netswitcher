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
    fun `deleting a profile offers an undo snackbar that counts down and dismisses`() {
        // Open the editor for the first network card, then delete it. The snackbar's
        // countdown runs on the virtual test clock, so stop auto-advance first —
        // otherwise waitForIdle fast-forwards through the 4s window and the snackbar
        // is gone before it can be observed.
        compose.onAllNodesWithContentDescription("Изменить").onFirst().performClick()
        // Scroll while the clock still auto-advances — performScrollTo animates and
        // would deadlock on a frozen frame clock. Freeze only for the click itself.
        compose.onNodeWithText("Удалить").performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Удалить").performClick()

        // The undo affordance appears and stays up mid-countdown.
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Отменить").assertExists()

        // Once the countdown elapses the snackbar dismisses itself.
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithText("Отменить").assertDoesNotExist()
    }
}
