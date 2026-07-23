package icu.nd4y.netswitcher.ui

import android.app.StatusBarManager
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import icu.nd4y.netswitcher.R
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.tile.tileComponent

@Composable
fun ButtonsScreen(
    config: Config,
    controller: ConfigController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        Section("Ярлыки (удержание иконки приложения)") {
            Text(
                text = "Лаунчер обычно показывает 4–5 верхних ярлыков. " +
                    "Порядок задаётся стрелками.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            OrderedPicker(
                all = config.profiles,
                selected = config.shortcutIds,
                onChange = { ids -> controller.edit { it.copy(shortcutIds = ids) } },
            )
        }

        Section("Виджет на рабочем столе") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Колонок: ${config.widgetColumns}", Modifier.weight(1f))
                listOf(1, 2, 3, 4).forEach { columns ->
                    TextButton(onClick = {
                        controller.edit { it.copy(widgetColumns = columns) }
                    }) { Text("$columns") }
                }
            }
            Spacer(Modifier.height(4.dp))
            OrderedPicker(
                all = config.profiles,
                selected = config.widgetIds,
                onChange = { ids -> controller.edit { it.copy(widgetIds = ids) } },
            )
        }

        Section("Плитки в шторке / центре управления") {
            Text(
                text = "Восемь плиток NetSwitcher 1…8 уже зарегистрированы в системе. " +
                    "Назначьте каждой профиль и добавьте нужные в шторку.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            for (slot in 1..Config.TILE_COUNT) {
                TileRow(
                    slot = slot,
                    bound = config.profile(config.tileBindings[slot.toString()]),
                    profiles = config.profiles,
                    onBind = { profile ->
                        controller.edit { current ->
                            val bindings = current.tileBindings.toMutableMap()
                            if (profile == null) bindings.remove(slot.toString())
                            else bindings[slot.toString()] = profile.id
                            current.copy(tileBindings = bindings)
                        }
                    },
                    onAddToPanel = {
                        val component = tileComponent(context, slot)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            component != null
                        ) {
                            val manager = context.getSystemService(StatusBarManager::class.java)
                            val label = config.profile(config.tileBindings[slot.toString()])
                                ?.name ?: "NetSwitcher $slot"
                            runCatching {
                                manager?.requestAddTileService(
                                    component,
                                    label,
                                    Icon.createWithResource(context, R.drawable.ic_tile),
                                    context.mainExecutor,
                                ) { }
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Добавьте плитку вручную через редактор шторки",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                )
                if (slot < Config.TILE_COUNT) HorizontalDivider()
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            content()
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun OrderedPicker(
    all: List<Profile>,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        selected.mapNotNull { id -> all.firstOrNull { it.id == id } }
            .forEachIndexed { index, profile ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = true,
                        onCheckedChange = { onChange(selected - profile.id) },
                    )
                    Text(
                        text = profile.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { onChange(selected.moved(index, index - 1)) },
                        enabled = index > 0,
                    ) { M3Icon(Icons.Filled.KeyboardArrowUp, "Выше") }
                    IconButton(
                        onClick = { onChange(selected.moved(index, index + 1)) },
                        enabled = index < selected.size - 1,
                    ) { M3Icon(Icons.Filled.KeyboardArrowDown, "Ниже") }
                }
            }

        val unselected = all.filterNot { it.id in selected }
        if (unselected.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            unselected.forEach { profile ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = false,
                        onCheckedChange = { onChange(selected + profile.id) },
                    )
                    Text(
                        text = profile.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TileRow(
    slot: Int,
    bound: Profile?,
    profiles: List<Profile>,
    onBind: (Profile?) -> Unit,
    onAddToPanel: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$slot", Modifier.width(20.dp))
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }) {
                Text(bound?.name ?: "не задано", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("— не задано —") },
                    onClick = {
                        onBind(null)
                        expanded = false
                    },
                )
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.name) },
                        onClick = {
                            onBind(profile)
                            expanded = false
                        },
                    )
                }
            }
        }
        TextButton(onClick = onAddToPanel) { Text("В шторку") }
    }
}

private fun List<String>.moved(from: Int, to: Int): List<String> {
    if (from == to || from !in indices || to !in indices) return this
    val copy = toMutableList()
    val item = copy.removeAt(from)
    copy.add(to, item)
    return copy
}
