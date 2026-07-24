package icu.nd4y.netswitcher.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import icu.nd4y.netswitcher.data.Config
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config as RoboConfig

/**
 * Composes the widget at explicit sizes and asserts which rows survive — the exact
 * regressions users hit: rows silently vanishing past the RemoteViews child budget,
 * and resizing not adapting the row count.
 */
@RunWith(AndroidJUnit4::class)
@RoboConfig(sdk = [35])
class WidgetLayoutTest {

    private val config = Config.default()

    // Default widget list: wifi_sw, lte_sw, eth_sw, air_sw, home, home5, guest, iot —
    // four rows of two at the default column count.
    private val buttons = config.resolve(config.widgetIds)

    @Test
    fun `tall widget renders every configured row`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(250.dp, 400.dp))
        provideComposable {
            GlanceTheme {
                WidgetBody(config, buttons, active = emptyMap(), busyId = null)
            }
        }
        buttons.forEach { profile ->
            onNode(hasTextEqualTo(profile.name)).assertExists()
        }
    }

    @Test
    fun `short widget drops the rows that no longer fit but keeps the first ones full`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            // ~130dp fits exactly one full-height row.
            setAppWidgetSize(DpSize(250.dp, 130.dp))
            provideComposable {
                GlanceTheme {
                    WidgetBody(config, buttons, active = emptyMap(), busyId = null)
                }
            }
            // Row 1 stays…
            onNode(hasTextEqualTo("Wi-Fi")).assertExists()
            onNode(hasTextEqualTo("LTE")).assertExists()
            // …later rows are hidden rather than squeezed.
            onNode(hasTextEqualTo("Ethernet")).assertDoesNotExist()
            onNode(hasTextEqualTo("Home")).assertDoesNotExist()
        }

    @Test
    fun `busy button reports its state`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(250.dp, 400.dp))
        provideComposable {
            GlanceTheme {
                WidgetBody(config, buttons, active = emptyMap(), busyId = "wifi_sw")
            }
        }
        onNode(hasTextEqualTo("переключаю…")).assertExists()
    }
}
