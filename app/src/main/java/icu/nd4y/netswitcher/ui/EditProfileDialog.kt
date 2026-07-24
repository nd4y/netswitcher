package icu.nd4y.netswitcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import icu.nd4y.netswitcher.data.MobileDataAction
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.ProfileKind
import icu.nd4y.netswitcher.data.WifiSecurity
import icu.nd4y.netswitcher.engine.SimSlot
import icu.nd4y.netswitcher.engine.TelephonyOps

@Composable
fun EditProfileDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
) {
    val context = LocalContext.current
    val sims = remember { TelephonyOps.readSims(context) }
    var draft by remember(profile.id) { mutableStateOf(profile) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Профиль", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Название кнопки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text("Тип действия", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SelectorRow(
                    options = ProfileKind.entries.map { it to kindLabel(it) },
                    selected = draft.kind,
                    onSelect = { kind -> draft = applyKindDefaults(draft, kind) },
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                when (draft.kind) {
                    ProfileKind.WIFI -> WifiFields(draft) { draft = it }
                    ProfileKind.CELLULAR -> CellularFields(draft, sims) { draft = it }
                    ProfileKind.ETHERNET -> EthernetFields(draft) { draft = it }
                    ProfileKind.WIFI_OFF -> Text(
                        "Просто выключает Wi-Fi.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    ProfileKind.WIFI_ON -> Text(
                        "Включает радио Wi-Fi и отдаёт выбор сети автоподключению системы.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    ProfileKind.WIFI_TOGGLE -> Text(
                        "Одна кнопка: включает Wi-Fi, если он выключен, и наоборот.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    ProfileKind.CELLULAR_TOGGLE -> Column {
                        Text(
                            "Одна кнопка: включает и выключает мобильные данные.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        CellularFields(draft, sims) { draft = it }
                    }

                    ProfileKind.ETHERNET_TOGGLE -> Column {
                        Text(
                            "Одна кнопка: поднимает и опускает проводной интерфейс. " +
                                "Как правило требует root.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = draft.ethernetInterface,
                            onValueChange = { draft = draft.copy(ethernetInterface = it) },
                            label = { Text("Интерфейс (например eth0)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    ProfileKind.AIRPLANE_TOGGLE -> Text(
                        "Одна кнопка: включает и выключает режим полёта.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (!draft.kind.isToggle) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Text("Мобильные данные", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    SelectorRow(
                        options = MobileDataAction.entries.map { it to mobileDataLabel(it) },
                        selected = draft.mobileData,
                        onSelect = { draft = draft.copy(mobileData = it) },
                    )

                    if (draft.kind == ProfileKind.WIFI) {
                        Spacer(Modifier.height(8.dp))
                        ToggleRow(
                            label = "Повторное нажатие отключает от сети",
                            checked = draft.tapAgainDisconnects,
                            onChange = { draft = draft.copy(tapAgainDisconnects = it) },
                        )
                    } else if (draft.kind != ProfileKind.WIFI_ON) {
                        Spacer(Modifier.height(8.dp))
                        ToggleRow(
                            label = "Выключать Wi-Fi",
                            checked = draft.disableWifi,
                            onChange = { draft = draft.copy(disableWifi = it) },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(draft) },
                        enabled = draft.name.isNotBlank(),
                    ) { Text("Сохранить") }
                }
            }
        }
    }
}

@Composable
private fun WifiFields(draft: Profile, onChange: (Profile) -> Unit) {
    Column {
        OutlinedTextField(
            value = draft.ssid,
            onValueChange = { onChange(draft.copy(ssid = it)) },
            label = { Text("SSID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text("Тип защиты", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        SelectorRow(
            options = WifiSecurity.entries.map { it to it.name },
            selected = draft.security,
            onSelect = { onChange(draft.copy(security = it)) },
        )
        if (draft.security.needsPassword) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = draft.password,
                onValueChange = { onChange(draft.copy(password = it)) },
                label = { Text("Пароль") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Пароль нужен, потому что подключение выполняется командой " +
                    "cmd wifi connect-network — она не умеет выбирать уже сохранённую сеть.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = draft.bssid,
            onValueChange = { onChange(draft.copy(bssid = it)) },
            label = { Text("BSSID (необязательно)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        ToggleRow(
            label = "Скрытая сеть",
            checked = draft.hiddenSsid,
            onChange = { onChange(draft.copy(hiddenSsid = it)) },
        )
    }
}

@Composable
private fun CellularFields(
    draft: Profile,
    sims: List<SimSlot>,
    onChange: (Profile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = sims.firstOrNull { it.subscriptionId == draft.subscriptionId }

    Column {
        Text("SIM-карта для передачи данных", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { expanded = true }) {
            Text(current?.label ?: "Не менять текущую")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Не менять текущую") },
                onClick = {
                    onChange(draft.copy(subscriptionId = -1))
                    expanded = false
                },
            )
            sims.forEach { sim ->
                DropdownMenuItem(
                    text = { Text("${sim.label} (subId ${sim.subscriptionId})") },
                    onClick = {
                        onChange(draft.copy(subscriptionId = sim.subscriptionId))
                        expanded = false
                    },
                )
            }
        }
        if (sims.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Список SIM пуст — выдайте разрешение «Телефон» в настройках приложения.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EthernetFields(draft: Profile, onChange: (Profile) -> Unit) {
    Column {
        OutlinedTextField(
            value = draft.ethernetInterface,
            onValueChange = { onChange(draft.copy(ethernetInterface = it)) },
            label = { Text("Интерфейс (например eth0)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Профиль отключает Wi-Fi (и по желанию мобильные данные), " +
                "чтобы шлюзом по умолчанию стал проводной адаптер. Поднять интерфейс " +
                "командой ip link получится только с root-правами.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun <T> SelectorRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val haptics = rememberClickHaptics()
    Column(Modifier.fillMaxWidth()) {
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { index, (value, label) ->
                    if (index > 0) Spacer(Modifier.width(8.dp))
                    val pick = {
                        haptics()
                        onSelect(value)
                    }
                    if (value == selected) {
                        Button(onClick = pick, modifier = Modifier.weight(1f)) {
                            Text(label, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(onClick = pick, modifier = Modifier.weight(1f)) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(Modifier.width(8.dp))
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = rememberClickHaptics()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics()
                onChange(it)
            },
        )
    }
}

private fun kindLabel(kind: ProfileKind): String = when (kind) {
    ProfileKind.WIFI -> "Wi-Fi сеть"
    ProfileKind.CELLULAR -> "LTE / SIM"
    ProfileKind.ETHERNET -> "Ethernet"
    ProfileKind.WIFI_OFF -> "Выключить Wi-Fi"
    ProfileKind.WIFI_ON -> "Включить Wi-Fi"
    ProfileKind.WIFI_TOGGLE -> "⇄ Wi-Fi"
    ProfileKind.CELLULAR_TOGGLE -> "⇄ Моб. данные"
    ProfileKind.ETHERNET_TOGGLE -> "⇄ Ethernet"
    ProfileKind.AIRPLANE_TOGGLE -> "⇄ Авиарежим"
}

private fun mobileDataLabel(action: MobileDataAction): String = when (action) {
    MobileDataAction.KEEP -> "Не трогать"
    MobileDataAction.ENABLE -> "Включить"
    MobileDataAction.DISABLE -> "Выключить"
}

private fun applyKindDefaults(profile: Profile, kind: ProfileKind): Profile = when (kind) {
    ProfileKind.WIFI -> profile.copy(kind = kind, mobileData = MobileDataAction.KEEP)
    ProfileKind.CELLULAR ->
        profile.copy(kind = kind, mobileData = MobileDataAction.ENABLE, disableWifi = true)

    ProfileKind.ETHERNET ->
        profile.copy(kind = kind, mobileData = MobileDataAction.DISABLE, disableWifi = true)

    ProfileKind.WIFI_OFF ->
        profile.copy(kind = kind, mobileData = MobileDataAction.KEEP, disableWifi = true)

    ProfileKind.WIFI_ON ->
        profile.copy(kind = kind, mobileData = MobileDataAction.KEEP, disableWifi = false)

    ProfileKind.WIFI_TOGGLE, ProfileKind.CELLULAR_TOGGLE,
    ProfileKind.ETHERNET_TOGGLE, ProfileKind.AIRPLANE_TOGGLE,
        -> profile.copy(kind = kind, mobileData = MobileDataAction.KEEP, disableWifi = false)
}
