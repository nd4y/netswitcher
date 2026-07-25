package icu.nd4y.netswitcher.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import icu.nd4y.netswitcher.NetSwitcherApp
import icu.nd4y.netswitcher.R
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.data.ProfileKind
import icu.nd4y.netswitcher.engine.NetworkStatus
import icu.nd4y.netswitcher.engine.PrivilegeManager
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
 * (see [PanelOverlayController]). A third-party overlay window sits *below* the
 * notification shade in z-order, so we also collapse the shade via the privileged shell
 * — the overlay then sits over the wallpaper/app, the closest a non-system app gets to
 * the system "Internet" panel. Without the overlay permission (or a shell to collapse
 * with) it falls back to launching [PanelActivity], which collapses the shade itself.
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
                showOverlayPanel()
            } else {
                openPanelActivity()
            }
        }
        if (isSecure && isLocked) unlockAndRun(work) else work.run()
    }

    private fun showOverlayPanel() {
        val app = applicationContext
        PanelOverlayController.show(app)
        // A third-party overlay renders below the shade, so it would be hidden behind it.
        // Collapse the shade through the privileged shell; the overlay then sits on top.
        NetSwitcherApp.appScope.launch {
            val backend = ConfigRepository.get(app).current().backend
            val shell = PrivilegeManager.resolve(backend).shell
            if (shell != null) {
                shell.exec("cmd statusbar collapse")
            } else {
                // No shell to collapse with — fall back to the activity path, which
                // collapses the shade itself. WindowManager + startActivityAndCollapse
                // must run on the main thread.
                Handler(Looper.getMainLooper()).post {
                    PanelOverlayController.dismiss()
                    openPanelActivity()
                }
            }
        }
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
            // Show something immediately; the SSID follows once the shell answers —
            // WifiInfo can't provide it here, see [NetworkStatus.currentSsidShared].
            tile.subtitle = "Wi-Fi вкл."
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()

            val ssid = NetworkStatus.currentSsidShared(applicationContext, config.backend)
            val current = qsTile ?: return
            if (ssid != null) {
                val profile = config.profiles.firstOrNull {
                    it.kind == ProfileKind.WIFI && it.ssid == ssid
                }
                current.subtitle = profile?.name ?: ssid
                current.state = Tile.STATE_ACTIVE
                current.updateTile()
            }
        } else {
            tile.subtitle = "Wi-Fi выключен"
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
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
