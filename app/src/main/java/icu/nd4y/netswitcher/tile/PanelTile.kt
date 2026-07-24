package icu.nd4y.netswitcher.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import icu.nd4y.netswitcher.R
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.data.ProfileKind
import icu.nd4y.netswitcher.engine.NetworkStatus
import icu.nd4y.netswitcher.ui.PanelActivity
import icu.nd4y.netswitcher.ui.PanelOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A single fixed Quick Settings tile that opens the NetSwitcher panel — the answer to
 * the system "Internet" panel, but instant and showing exactly the toggles and networks
 * the user configured. Unlike [BaseSwitchTile] it is not bound to one profile, but —
 * like the system "Internet" tile — it mirrors whichever configured Wi-Fi network is
 * currently active in its label, and dims to [Tile.STATE_INACTIVE] when Wi-Fi is off.
 *
 * With the "draw over other apps" permission granted, the panel shows as an overlay
 * above the still-open shade, exactly like the system tile — see [PanelOverlayController].
 * Without it, Android gives third-party tiles no way to show UI without first collapsing
 * the shade, so this falls back to launching [PanelActivity].
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
        val work = Runnable {
            if (Settings.canDrawOverlays(this)) {
                PanelOverlayController.show(applicationContext)
            } else {
                openPanelActivity()
            }
        }
        if (isSecure && isLocked) unlockAndRun(work) else work.run()
    }

    private suspend fun refresh() {
        val tile = qsTile ?: return
        val config = ConfigRepository.get(applicationContext).current()

        // Mirror the system "Internet" tile's shape: the title stays constant and the
        // current network lives in the subtitle — the title flipping to a profile name
        // read as a different tile every time the shade opened.
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)

        if (NetworkStatus.isWifiOn(applicationContext)) {
            val ssid = NetworkStatus.currentSsidSync(applicationContext)
            val profile = config.profiles.firstOrNull {
                it.kind == ProfileKind.WIFI && it.ssid == ssid
            }
            tile.subtitle = profile?.name ?: ssid ?: "Wi-Fi вкл."
            tile.state = Tile.STATE_ACTIVE
        } else {
            tile.subtitle = "Wi-Fi выключен"
            tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private fun openPanelActivity() {
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
