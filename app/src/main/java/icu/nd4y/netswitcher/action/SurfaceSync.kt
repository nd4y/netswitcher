package icu.nd4y.netswitcher.action

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.glance.appwidget.updateAll
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.tile.PanelTile
import icu.nd4y.netswitcher.tile.tileComponent
import icu.nd4y.netswitcher.widget.NetSwitcherWidget

/**
 * Keeps the three "outside the app" surfaces — launcher shortcuts, the home
 * screen widget and the Quick Settings tiles — in step with the configuration.
 */
object SurfaceSync {

    suspend fun syncAll(context: Context) {
        val config = ConfigRepository.get(context).current()
        syncShortcuts(context, config)
        syncTiles(context)
        runCatching { NetSwitcherWidget().updateAll(context.applicationContext) }
    }

    fun syncShortcuts(context: Context, config: Config) {
        val app = context.applicationContext
        val max = runCatching { ShortcutManagerCompat.getMaxShortcutCountPerActivity(app) }
            .getOrDefault(4)
            .coerceAtLeast(1)

        val shortcuts = config.resolve(config.shortcutIds).take(max).mapIndexed { index, profile ->
            ShortcutInfoCompat.Builder(app, "profile_${profile.id}")
                .setShortLabel(profile.name.take(10))
                .setLongLabel(profile.name)
                .setIcon(shortcutIcon(app, profile))
                .setRank(index)
                .setIntent(
                    Intent(app, ActionActivity::class.java)
                        .setAction(ActionActivity.ACTION_RUN)
                        .putExtra(ActionActivity.EXTRA_PROFILE_ID, profile.id)
                        .putExtra(ActionActivity.EXTRA_PROFILE_NAME, profile.name)
                )
                .build()
        }

        runCatching { ShortcutManagerCompat.setDynamicShortcuts(app, shortcuts) }
    }

    fun syncTiles(context: Context) {
        val app = context.applicationContext
        for (slot in 1..Config.TILE_COUNT) {
            val component: ComponentName = tileComponent(app, slot) ?: continue
            runCatching { TileService.requestListeningState(app, component) }
        }
        // The panel tile mirrors the current network in its subtitle — refresh it too.
        runCatching {
            TileService.requestListeningState(app, ComponentName(app, PanelTile::class.java))
        }
    }

    private fun shortcutIcon(context: Context, profile: Profile): IconCompat {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#0B3D5C"))

        val drawable = ContextCompat.getDrawable(context, profile.iconRes)
        if (drawable != null) {
            val inset = size / 4
            drawable.setBounds(inset, inset, size - inset, size - inset)
            drawable.setTint(Color.WHITE)
            drawable.draw(canvas)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(size / 2f, size / 2f, size / 5f, paint)
        }
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }
}
