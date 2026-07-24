package icu.nd4y.netswitcher.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import icu.nd4y.netswitcher.R
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.data.ProfileKind
import icu.nd4y.netswitcher.engine.NetworkStatus
import icu.nd4y.netswitcher.ui.PanelActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A single fixed Quick Settings tile that opens the [PanelActivity] pop-up — the
 * NetSwitcher answer to the system "Internet" panel, but instant and showing exactly
 * the toggles and networks the user configured. Unlike [BaseSwitchTile] it is not bound
 * to one profile, but — like the system "Internet" tile — it mirrors whichever
 * configured Wi-Fi network is currently active in its label, and dims to
 * [Tile.STATE_INACTIVE] when Wi-Fi is off.
 */
class PanelTile : TileService() {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch { refresh() }
    }

    override fun onStopListening() {
        super.onStopListening()
        scope.cancel()
    }

    override fun onClick() {
        super.onClick()
        val work = Runnable { openPanel() }
        if (isSecure && isLocked) unlockAndRun(work) else work.run()
    }

    private suspend fun refresh() {
        val tile = qsTile ?: return
        val config = ConfigRepository.get(applicationContext).current()
        val active = config.profiles.firstOrNull {
            it.kind == ProfileKind.WIFI && NetworkStatus.quickActive(applicationContext, it)
        }

        when {
            active != null -> {
                tile.label = active.name
                tile.subtitle = active.ssid.takeIf { it != active.name } ?: "Подключено"
                tile.icon = Icon.createWithResource(this, active.iconRes)
                tile.state = Tile.STATE_ACTIVE
            }

            NetworkStatus.isWifiOn(applicationContext) -> {
                // Wi-Fi is on but not joined to any network we know about.
                tile.label = getString(R.string.tile_panel)
                tile.subtitle = "Сеть не распознана"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
                tile.state = Tile.STATE_ACTIVE
            }

            else -> {
                tile.label = getString(R.string.tile_panel)
                tile.subtitle = "Wi-Fi выключен"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
                tile.state = Tile.STATE_INACTIVE
            }
        }
        tile.updateTile()
    }

    private fun openPanel() {
        val intent = Intent(this, PanelActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
