package icu.nd4y.netswitcher.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import icu.nd4y.netswitcher.ui.PanelActivity

/**
 * A single fixed Quick Settings tile that opens the [PanelActivity] pop-up — the
 * NetSwitcher answer to the system "Internet" panel, but instant and showing exactly
 * the toggles and networks the user configured. Unlike [BaseSwitchTile] it is not bound
 * to a profile and performs no action itself; it just brings up the panel.
 */
class PanelTile : TileService() {

    override fun onClick() {
        super.onClick()
        val work = Runnable { openPanel() }
        if (isSecure && isLocked) unlockAndRun(work) else work.run()
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
