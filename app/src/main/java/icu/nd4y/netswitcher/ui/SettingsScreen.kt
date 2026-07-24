package icu.nd4y.netswitcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import icu.nd4y.netswitcher.action.Feedback
import icu.nd4y.netswitcher.action.SurfaceSync
import icu.nd4y.netswitcher.data.Backend
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.data.StartNotification
import icu.nd4y.netswitcher.data.YamlConfig
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
    val haptics = rememberClickHaptics()
    val tick by ShizukuEvents.ticks.collectAsStateWithLifecycle()

    // Keyed on `tick` so the status re-reads whenever Shizuku's binder or grant changes.
    val shizukuState = remember(tick) {
        ShizukuShell.binderAlive() to ShizukuShell.hasPermission()
    }
    val binderAlive = shizukuState.first
    val granted = shizukuState.second

    // Re-checked on every recomposition triggered by tick, which also fires on onResume —
    // so coming back from the system permission screen refreshes this without extra wiring.
    val overlayGranted = remember(tick) { Settings.canDrawOverlays(context) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/yaml")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = runCatching {
                val text = YamlConfig.encode(ConfigRepository.get(context).current())
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(text.toByteArray())
                } ?: error("не удалось открыть файл на запись")
            }
            toast(
                context,
                outcome.fold(
                    onSuccess = { "Конфигурация сохранена" },
                    onFailure = { "Ошибка экспорта: ${it.message}" },
                )
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().decodeToString()
                } ?: error("не удалось прочитать файл")
                val parsed = YamlConfig.decode(text).getOrThrow()
                ConfigRepository.get(context).update { parsed }
                SurfaceSync.syncAll(context)
                parsed.profiles.size
            }
            toast(
                context,
                outcome.fold(
                    onSuccess = { "Импортировано профилей: $it" },
                    onFailure = { "Ошибка импорта: ${it.message}" },
                )
            )
        }
    }

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
        Text("Панель без сворачивания шторки", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = if (overlayGranted) {
                        "Разрешение выдано — плитка-панель открывается поверх шторки, " +
                            "как системная «Интернет», шторка не сворачивается."
                    } else {
                        "По умолчанию плитка-панель сворачивает шторку перед показом — " +
                            "так работает любая сторонняя плитка. Чтобы панель, как у " +
                            "системной «Интернет», появлялась прямо поверх открытой " +
                            "шторки, выдайте разрешение «Отображение поверх других окон»."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overlayGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openOverlaySettings(context) },
                    enabled = !overlayGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (overlayGranted) "Разрешение выдано" else "Выдать разрешение") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Уведомление о старте", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    StartNotification.entries.forEach { style ->
                        val label = when (style) {
                            StartNotification.SHADE -> "В шторке"
                            StartNotification.HEADS_UP -> "Всплывающее"
                        }
                        val onPick = {
                            haptics()
                            controller.edit { it.copy(startNotification = style) }
                        }
                        if (style == config.startNotification) {
                            Button(onClick = onPick, modifier = Modifier.weight(1f)) {
                                Text(label, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                                Text(label, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append("Уведомление появляется в начале операции и всегда без ")
                        append("звука и вибрации. По завершении оно закрывается само, ")
                        append("а результат приходит всплывающим сообщением.")
                        if (Feedback.liveUpdatesSupported) {
                            append(" Дополнительно показывается чип в статус-баре ")
                            append("(Live Update, Android 16+).")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                SettingToggle(
                    label = "Показывать журнал команд",
                    checked = config.verboseLog,
                    onChange = { value ->
                        haptics()
                        controller.edit { it.copy(verboseLog = value) }
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Конфигурация (YAML)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("netswitcher.yaml") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Экспорт в файл") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Импорт из файла") }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Импорт полностью заменяет профили и назначения кнопок. " +
                        "Файл можно править руками — формат описан комментариями внутри.",
                    style = MaterialTheme.typography.bodySmall,
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
                    onClick = {
                        haptics()
                        controller.edit { it.withDefaultLayout() }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сбросить расположение кнопок") }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Возвращает кнопки на главном экране, в ярлыках, виджете и " +
                        "плитках к расположению по умолчанию. Сами профили и пароли " +
                        "не трогает — полный сброс делается очисткой данных в " +
                        "настройках Android.",
                    style = MaterialTheme.typography.bodySmall,
                )
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

private fun toast(context: android.content.Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
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

private fun openOverlaySettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
