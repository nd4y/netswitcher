package icu.nd4y.netswitcher.action

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import icu.nd4y.netswitcher.NetSwitcherApp
import icu.nd4y.netswitcher.data.ActionResult
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.engine.SwitchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Single entry point every button surface funnels through. */
object ActionDispatcher {

    private val _lastResult = MutableStateFlow<ActionResult?>(null)
    val lastResult: StateFlow<ActionResult?> = _lastResult

    private val _running = MutableStateFlow<String?>(null)

    /** Id of the profile currently being applied, or null. */
    val running: StateFlow<String?> = _running

    /** Fire-and-forget: safe to call from a tile, a widget or a trampoline activity. */
    fun dispatch(context: Context, profileId: String) {
        val app = context.applicationContext
        NetSwitcherApp.appScope.launch { runNow(app, profileId) }
    }

    suspend fun runNow(context: Context, profileId: String): ActionResult {
        val app = context.applicationContext
        val config = ConfigRepository.get(app).current()
        val profile = config.profile(profileId)
            ?: return ActionResult(false, "Профиль не найден").also { publish(app, it) }
        return runNow(app, profile)
    }

    suspend fun runNow(context: Context, profile: Profile): ActionResult {
        val app = context.applicationContext
        val config = ConfigRepository.get(app).current()
        _running.value = profile.id
        val result = try {
            SwitchEngine(app).run(profile, config.backend)
        } catch (error: Throwable) {
            ActionResult(false, "Ошибка: ${error.message}", listOf(error.stackTraceToString()))
        } finally {
            _running.value = null
        }
        publish(app, result, config.showToasts)
        return result
    }

    private fun publish(context: Context, result: ActionResult, toast: Boolean = true) {
        _lastResult.value = result
        if (!toast) return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_SHORT).show()
        }
    }
}
