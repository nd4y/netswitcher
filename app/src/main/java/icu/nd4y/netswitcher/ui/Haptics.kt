package icu.nd4y.netswitcher.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * A light tick for in-app controls. Actions that reach [icu.nd4y.netswitcher.action.Feedback]
 * already vibrate on their own, so this is for everything else — switches, chips,
 * checkboxes, tabs.
 */
@Composable
fun rememberClickHaptics(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.tick(HapticFeedbackConstants.CONTEXT_CLICK) } }
}

/** The heavier bump that tells the user a drag has taken hold. */
@Composable
fun rememberDragHaptics(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.tick(HapticFeedbackConstants.LONG_PRESS) } }
}

private fun View.tick(constant: Int) {
    runCatching {
        performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }
}
