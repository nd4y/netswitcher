package icu.nd4y.netswitcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.netswitcher.R
import icu.nd4y.netswitcher.action.ActionDispatcher
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.ProfileKind
import icu.nd4y.netswitcher.engine.NetworkSnapshot
import icu.nd4y.netswitcher.engine.NetworkStatus
import icu.nd4y.netswitcher.engine.PrivilegeManager
import icu.nd4y.netswitcher.engine.PrivilegeState
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    config: Config,
    controller: ConfigController,
    modifier: Modifier = Modifier,
    onEdit: (Profile) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberClickHaptics()
    var sharing by remember { mutableStateOf<Profile?>(null) }

    var snapshot by remember { mutableStateOf<NetworkSnapshot?>(null) }
    var privilege by remember { mutableStateOf<PrivilegeState?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var states by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    val tick by ShizukuEvents.ticks.collectAsStateWithLifecycle()
    val running by ActionDispatcher.running.collectAsStateWithLifecycle()
    val lastResult by ActionDispatcher.lastResult.collectAsStateWithLifecycle()

    LaunchedEffect(tick, refreshKey, config, running) {
        if (running != null) return@LaunchedEffect
        states = config.profiles.associate { it.id to NetworkStatus.quickActive(context, it) }
        val state = PrivilegeManager.resolve(config.backend)
        privilege = state
        snapshot = NetworkStatus.read(context, state.shell)
    }

    val shown = config.homeProfiles()
    val toggles = shown.filter { it.kind.isToggle }
    val networks = shown.filter { it.kind == ProfileKind.WIFI }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        StatusCard(
            snapshot = snapshot,
            privilege = privilege,
            onRefresh = { refreshKey++ },
        )

        if (toggles.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            toggles.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { index, profile ->
                        if (index > 0) Spacer(Modifier.width(8.dp))
                        ToggleButton(
                            profile = profile,
                            isOn = states[profile.id] == true,
                            busy = running == profile.id,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch { ActionDispatcher.runNow(context, profile) }
                            },
                        )
                    }
                    repeat(4 - row.size) {
                        Spacer(Modifier.width(8.dp))
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Сети", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Удержание — перетащить, карандаш — изменить",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = {
                haptics()
                onEdit(
                    Profile(
                        id = UUID.randomUUID().toString().take(8),
                        name = "Новая сеть",
                        kind = ProfileKind.WIFI,
                    )
                )
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить профиль")
            }
        }
        Spacer(Modifier.height(4.dp))

        if (networks.isNotEmpty()) {
            ReorderableColumn(
                count = networks.size,
                onMove = { from, to ->
                    val order = networks.map { it.id }.moveItem(from, to)
                    controller.edit { it.reordered(order) }
                },
            ) { index, isDragging ->
                val profile = networks[index]
                NetworkCard(
                    profile = profile,
                    busy = running == profile.id,
                    isActive = states[profile.id] == true,
                    isDragging = isDragging,
                    onClick = { scope.launch { ActionDispatcher.runNow(context, profile) } },
                    onShare = if (profile.ssid.isNotBlank()) {
                        { haptics(); sharing = profile }
                    } else null,
                    onEditClick = { haptics(); onEdit(profile) },
                )
            }
        }

        // Profiles the main screen doesn't draw as buttons: one-shot actions plus
        // anything deselected from `home` in the buttons picker. They are still
        // usable from the widget, shortcuts and tiles — this section is where they
        // get edited now that the separate "Профили" tab is gone.
        val others = config.profiles.filter { it.id !in config.homeIds }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Остальные профили", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "На главном экране не показываются — нажмите, чтобы изменить",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            others.forEach { profile ->
                OtherProfileRow(
                    profile = profile,
                    onClick = { haptics(); onEdit(profile) },
                    onShare = if (profile.kind == ProfileKind.WIFI && profile.ssid.isNotBlank()) {
                        { haptics(); sharing = profile }
                    } else null,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        if (config.verboseLog) {
            lastResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(result.message, style = MaterialTheme.typography.bodyLarge)
                        if (result.log.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            result.log.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall
                                        .copy(fontFamily = FontFamily.Monospace),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    sharing?.let { profile ->
        ShareNetworkDialog(profile = profile, onDismiss = { sharing = null })
    }
}

/** Where the phone is connected right now — the first thing on the screen. */
@Composable
private fun StatusCard(
    snapshot: NetworkSnapshot?,
    privilege: PrivilegeState?,
    onRefresh: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = snapshot?.detail ?: "определяю состояние",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = snapshot?.transport ?: "…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = privilege?.description ?: "проверяю привилегии",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (privilege?.hasPrivileges == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
            }
        }
    }
}

/** Compact square button that both reports and flips a piece of connectivity state. */
@Composable
private fun ToggleButton(
    profile: Profile,
    isOn: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container =
        if (isOn) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (isOn) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    painter = painterResource(profile.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = profile.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isOn) "вкл." else "выкл.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun NetworkCard(
    profile: Profile,
    busy: Boolean,
    isActive: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onShare: (() -> Unit)?,
    onEditClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isDragging) 8.dp else 1.dp
        ),
        colors = if (isActive) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            CardDefaults.elevatedCardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    painter = painterResource(profile.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        isActive && profile.tapAgainDisconnects ->
                            "подключено · нажмите, чтобы отключиться"

                        isActive -> "подключено"
                        else -> profile.subtitle
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onShare != null) {
                IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = "Поделиться",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(onClick = onEditClick, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Изменить",
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Перетащить",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A profile that never draws on the main screen; tapping opens the editor. */
@Composable
private fun OtherProfileRow(
    profile: Profile,
    onClick: () -> Unit,
    onShare: (() -> Unit)?,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(profile.iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onShare != null) {
                IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = "Поделиться",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Изменить",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
