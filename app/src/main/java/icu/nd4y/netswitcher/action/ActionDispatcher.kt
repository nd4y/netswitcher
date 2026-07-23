package icu.nd4y.netswitcher.action

import android.content.Context
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

    /**
     * Mirrors the persisted "show toasts" setting so a press arriving on a cold
     * process can be acknowledged without first awaiting a DataStore read.
     */
    @Volatile
    var toastsEnabled: Boolean = false
        private set

    fun rememberToastPreference(enabled: Boolean) {
        toastsEnabled = enabled
    }

    /**
     * Fire-and-forget: safe to call from a tile, a widget or a trampoline activity.
     * Pass [label] when the caller already knows the profile name — the press is then
     * acknowledged before any suspending work happens.
     */
    fun dispatch(context: Context, profileId: String, label: String? = null) {
        val app = context.applicationContext
        val announced = label != null
        if (label != null) Feedback.announceStart(app, label, toastsEnabled)
        NetSwitcherApp.appScope.launch { runNow(app, profileId, announced) }
    }

    suspend fun runNow(
        context: Context,
        profileId: String,
        alreadyAnnounced: Boolean = false,
    ): ActionResult {
        val app = context.applicationContext
        val config = ConfigRepository.get(app).current()
        val profile = config.profile(profileId)
            ?: return ActionResult(false, "Профиль не найден").also {
                _lastResult.value = it
                Feedback.announceResult(app, it, toastsEnabled)
            }
        return runNow(app, profile, alreadyAnnounced)
    }

    suspend fun runNow(
        context: Context,
        profile: Profile,
        alreadyAnnounced: Boolean = false,
    ): ActionResult {
        val app = context.applicationContext
        val config = ConfigRepository.get(app).current()
        rememberToastPreference(config.showToasts)

        if (!alreadyAnnounced) Feedback.announceStart(app, profile.name, config.showToasts)

        _running.value = profile.id
        val result = try {
            SwitchEngine(app).run(profile, config.backend)
        } catch (error: Throwable) {
            ActionResult(false, "Ошибка: ${error.message}", listOf(error.stackTraceToString()))
        } finally {
            _running.value = null
        }

        _lastResult.value = result
        Feedback.announceResult(app, result, config.showToasts)
        return result
    }
}
