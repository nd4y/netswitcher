package icu.nd4y.netswitcher

import android.app.Application
import androidx.glance.appwidget.updateAll
import icu.nd4y.netswitcher.action.ActionDispatcher
import icu.nd4y.netswitcher.action.Feedback
import icu.nd4y.netswitcher.action.SurfaceSync
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.widget.NetSwitcherWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NetSwitcherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Feedback.ensureChannel(this)

        // Shortcuts and tiles live outside the UI process lifecycle; make sure they
        // reflect whatever configuration was persisted last time.
        appScope.launch {
            ActionDispatcher.rememberToastPreference(
                ConfigRepository.get(this@NetSwitcherApp).current().showToasts
            )
            SurfaceSync.syncAll(this@NetSwitcherApp)
        }

        // Repaint the widget as soon as an action starts, so the pressed button shows
        // it is working even while the privileged commands are still running.
        appScope.launch {
            ActionDispatcher.running.collect {
                runCatching { NetSwitcherWidget().updateAll(this@NetSwitcherApp) }
            }
        }
    }

    companion object {
        lateinit var instance: NetSwitcherApp
            private set

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
