package icu.nd4y.netswitcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.netswitcher.action.SurfaceSync
import icu.nd4y.netswitcher.data.Backend
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.engine.ShizukuShell
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    config: Config,
    controller: ConfigController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by ShizukuEvents.ticks.collectAsStateWithLifecycle()

    // Keyed on `tick` so the status re-reads whenever Shizuku's binder or grant changes.
    val shizukuState = remember(tick) {
        ShizukuShell.binderAlive() to ShizukuShell.hasPermission()
    }
    val binderAlive = shizukuState.first
    val granted = shizukuState.second

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        Text("Источник привилегий", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Backend.entries.forEach { backend ->
                        val label = when (backend) {
                            Backend.AUTO -> "Авто"
                            Backend.SHIZUKU -> "Shizuku"
                            Backend.ROOT -> "Root"
                            Backend.NONE -> "Нет"
                        }
                        if (backend == config.backend) {
                            Button(
                                onClick = { controller.edit { it.copy(backend = backend) } },
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = { controller.edit { it.copy(backend = backend) } },
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1) }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = when {
                        granted -> "Shizuku подключён (${ShizukuShell.uidLabel()})"
                        binderAlive -> "Shizuku запущен, но разрешение не выдано"
                        else -> "Shizuku не запущен"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(
                        onClick = {
                            ShizukuShell.requestPermission()
                            ShizukuEvents.bump()
                        },
                        enabled = binderAlive && !granted,
                    ) { Text("Выдать разрешение") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { openShizuku(context) }) { Text("Открыть Shizuku") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Без Shizuku или root Android не даёт приложению переключать " +
                        "Wi-Fi-сети — останется только открытие системной панели.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Поведение", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                SettingToggle(
                    label = "Всплывающие уведомления о результате",
                    checked = config.showToasts,
                    onChange = { value -> controller.edit { it.copy(showToasts = value) } },
                )
                SettingToggle(
                    label = "Показывать журнал команд",
                    checked = config.verboseLog,
                    onChange = { value -> controller.edit { it.copy(verboseLog = value) } },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Обслуживание", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { SurfaceSync.syncAll(context) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Обновить ярлыки, виджет и плитки") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openAppSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Разрешения приложения") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { controller.edit { Config.default() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сбросить конфигурацию") }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun openShizuku(context: android.content.Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    if (launch != null) {
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openAppSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
