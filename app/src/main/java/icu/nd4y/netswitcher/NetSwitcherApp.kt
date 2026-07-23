package icu.nd4y.netswitcher

import android.app.Application
import icu.nd4y.netswitcher.action.SurfaceSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NetSwitcherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Shortcuts and tiles live outside the UI process lifecycle; make sure they
        // reflect whatever configuration was persisted last time.
        appScope.launch { SurfaceSync.syncAll(this@NetSwitcherApp) }
    }

    companion object {
        lateinit var instance: NetSwitcherApp
            private set

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
