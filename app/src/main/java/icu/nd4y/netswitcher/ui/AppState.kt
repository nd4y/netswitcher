package icu.nd4y.netswitcher.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import icu.nd4y.netswitcher.action.SurfaceSync
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Thin wrapper around [ConfigRepository] that also refreshes shortcuts, tiles and
 * the widget whenever the configuration changes — those surfaces would otherwise
 * keep showing stale labels.
 */
class ConfigController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    fun edit(block: (Config) -> Config) {
        scope.launch {
            ConfigRepository.get(context).update(block)
            SurfaceSync.syncAll(context)
        }
    }
}

@Composable
fun rememberConfigController(scope: CoroutineScope): ConfigController {
    val context = LocalContext.current.applicationContext
    return remember(scope) { ConfigController(context, scope) }
}
