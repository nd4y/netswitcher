package icu.nd4y.netswitcher.action

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import icu.nd4y.netswitcher.NetSwitcherApp
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.data.YamlConfig
import kotlinx.coroutines.launch

/**
 * Headless YAML import for automated deployments:
 *
 * ```
 * adb shell am broadcast -n icu.nd4y.netswitcher/.action.ConfigImportReceiver \
 *     -a icu.nd4y.netswitcher.action.IMPORT_CONFIG --es yaml_base64 <base64>
 * ```
 *
 * The receiver is exported but guarded by `android.permission.DUMP` — a development
 * permission the adb shell holds and a regular third-party app cannot obtain, so only
 * someone who already has adb access to the device can push a configuration.
 *
 * The YAML travels base64-encoded to survive shell quoting (Cyrillic, quotes, #).
 * Import semantics match the in-app one: full replace, and a broken file is rejected
 * whole instead of half-applying. The outcome is reported back to `am broadcast`
 * (result code 0 = success) and shown as a toast on the device.
 */
class ConfigImportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_IMPORT) return
        val app = context.applicationContext

        val encoded = intent.getStringExtra(EXTRA_YAML_BASE64)
        if (encoded.isNullOrBlank()) {
            resultCode = 1
            resultData = "missing extra $EXTRA_YAML_BASE64"
            return
        }

        val pending = goAsync()
        NetSwitcherApp.appScope.launch {
            val outcome = runCatching {
                val text = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                val parsed = YamlConfig.decode(text).getOrThrow()
                ConfigRepository.get(app).update { parsed }
                SurfaceSync.syncAll(app)
                parsed.profiles.size
            }
            outcome.fold(
                onSuccess = { count ->
                    pending.setResultCode(0)
                    pending.setResultData("imported $count profiles")
                    toast(app, "Конфигурация импортирована: профилей — $count")
                },
                onFailure = { error ->
                    pending.setResultCode(1)
                    pending.setResultData("import failed: ${error.message}")
                    toast(app, "Ошибка импорта конфигурации: ${error.message}")
                },
            )
            pending.finish()
        }
    }

    private fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_IMPORT = "icu.nd4y.netswitcher.action.IMPORT_CONFIG"
        const val EXTRA_YAML_BASE64 = "yaml_base64"
    }
}
