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
import icu.nd4y.netswitcher.action.ActionDispatcher
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.engine.NetworkSnapshot
import icu.nd4y.netswitcher.engine.NetworkStatus
import icu.nd4y.netswitcher.engine.PrivilegeManager
import icu.nd4y.netswitcher.engine.PrivilegeState
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(config: Config, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf<NetworkSnapshot?>(null) }
    var privilege by remember { mutableStateOf<PrivilegeState?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val tick by ShizukuEvents.ticks.collectAsStateWithLifecycle()
    val running by ActionDispatcher.running.collectAsStateWithLifecycle()
    val lastResult by ActionDispatcher.lastResult.collectAsStateWithLifecycle()

    LaunchedEffect(tick, refreshKey, config.backend, running) {
        if (running != null) return@LaunchedEffect
        val state = PrivilegeManager.resolve(config.backend)
        privilege = state
        snapshot = NetworkStatus.read(context, state.shell)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = snapshot?.transport ?: "…",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = snapshot?.detail ?: "определяю состояние",
                        style = MaterialTheme.typography.bodyMedium,
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
                IconButton(onClick = { refreshKey++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Быстрое переключение", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // A plain chunked Row grid: a lazy grid inside a scrolling column is more
        // trouble than it is worth for a handful of buttons.
        config.profiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEachIndexed { index, profile ->
                    if (index > 0) Spacer(Modifier.width(10.dp))
                    ProfileButton(
                        profile = profile,
                        busy = running == profile.id,
                        modifier = Modifier.weight(1f),
                        onClick = { scope.launch { ActionDispatcher.runNow(context, profile) } },
                    )
                }
                if (pair.size == 1) {
                    Spacer(Modifier.width(10.dp))
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(6.dp))

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
                    if (config.verboseLog && result.log.isNotEmpty()) {
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

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileButton(
    profile: Profile,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
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
        }
    }
}
