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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NetSwitcherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Feedback.ensureChannels(this)

        // Shortcuts and tiles live outside the UI process lifecycle; make sure they
        // reflect whatever configuration was persisted last time.
        appScope.launch {
            ActionDispatcher.rememberNotificationPreference(
                ConfigRepository.get(this@NetSwitcherApp).current().startNotification
            )
            SurfaceSync.syncAll(this@NetSwitcherApp)
        }

        // Repaint the widget as soon as an action starts, so the pressed button shows
        // it is working even while the privileged commands are still running — and
        // again once it finishes, so "переключаю…" doesn't linger.
        appScope.launch {
            var wasRunning = false
            ActionDispatcher.running.collect { runningId ->
                runCatching { NetSwitcherWidget().updateAll(this@NetSwitcherApp) }
                if (wasRunning && runningId == null) {
                    // Belt-and-suspenders: Glance widget updates go through their own
                    // async session, and a burst of updateAll() calls has been observed
                    // to apply out of order, leaving a button stuck showing "переключаю…"
                    // after the switch already finished. A short delayed follow-up
                    // self-corrects that without needing another press to notice.
                    delay(1200)
                    runCatching { NetSwitcherWidget().updateAll(this@NetSwitcherApp) }
                }
                wasRunning = runningId != null
            }
        }
    }

    companion object {
        lateinit var instance: NetSwitcherApp
            private set

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
