package icu.nd4y.netswitcher.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import icu.nd4y.netswitcher.R
import icu.nd4y.netswitcher.action.ActionDispatcher
import icu.nd4y.netswitcher.action.Feedback
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.engine.NetworkStatus
import icu.nd4y.netswitcher.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * One Quick Settings tile per slot. Slots are bound to profiles in the app, so the
 * user decides which network sits behind "NetSwitcher 1".
 */
abstract class BaseSwitchTile(private val slot: Int) : TileService() {

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
            scope.launch {
                val profile = boundProfile()
                if (profile == null) {
                    openApp()
                } else {
                    qsTile?.apply {
                        state = Tile.STATE_ACTIVE
                        subtitle = "Переключаю…"
                        updateTile()
                    }
                    Feedback.announceStart(
                        applicationContext,
                        profile.name,
                        ActionDispatcher.startNotification,
                    )
                    ActionDispatcher.runNow(applicationContext, profile, alreadyAnnounced = true)
                    refresh()
                }
            }
        }
        if (isSecure && isLocked) unlockAndRun(work) else work.run()
    }

    private suspend fun boundProfile(): Profile? {
        val config = ConfigRepository.get(applicationContext).current()
        return config.profile(config.tileBindings[slot.toString()])
    }

    private suspend fun refresh() {
        val tile = qsTile ?: return
        val profile = boundProfile()
        if (profile == null) {
            tile.label = getString(R.string.app_name)
            tile.subtitle = "Слот $slot не задан"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
            tile.state = Tile.STATE_INACTIVE
        } else {
            tile.label = profile.name
            tile.subtitle = profile.subtitle
            tile.icon = Icon.createWithResource(this, profile.iconRes)
            tile.state =
                if (NetworkStatus.quickActive(applicationContext, profile)) Tile.STATE_ACTIVE
                else Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, slot, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

class SwitchTile1 : BaseSwitchTile(1)
class SwitchTile2 : BaseSwitchTile(2)
class SwitchTile3 : BaseSwitchTile(3)
class SwitchTile4 : BaseSwitchTile(4)
class SwitchTile5 : BaseSwitchTile(5)
class SwitchTile6 : BaseSwitchTile(6)
class SwitchTile7 : BaseSwitchTile(7)
class SwitchTile8 : BaseSwitchTile(8)

/** Maps a 1-based slot number to the matching tile service component. */
fun tileComponent(context: Context, slot: Int): ComponentName? {
    if (slot !in 1..Config.TILE_COUNT) return null
    return ComponentName(context, "icu.nd4y.netswitcher.tile.SwitchTile$slot")
}
