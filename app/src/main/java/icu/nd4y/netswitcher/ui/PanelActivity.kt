package icu.nd4y.netswitcher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.netswitcher.R
import icu.nd4y.netswitcher.action.ActionDispatcher
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.ProfileKind
import icu.nd4y.netswitcher.engine.NetworkStatus

/**
 * The pop-up panel opened by the Quick Settings tile. It draws the same toggles and
 * Wi-Fi networks as the main screen's home set, but from a translucent dialog window
 * so it can appear over the shade. Unlike the system "Internet" panel it renders a
 * fixed list of configured profiles and never runs a Wi-Fi scan, so it shows instantly.
 *
 * Presses funnel through [ActionDispatcher.dispatch], the same fire-and-forget path
 * every other off-app surface uses: the work runs on the application scope and survives
 * the panel being dismissed, and the press is acknowledged by the usual notification.
 */
class PanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetSwitcherTheme {
                PanelRoot(onDismiss = { finish() })
            }
        }
    }
}

@Composable
private fun PanelRoot(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ConfigRepository.get(context) }
    val config by repo.flow.collectAsStateWithLifecycle(initialValue = Config.default())

    val tick by ShizukuEvents.ticks.collectAsStateWithLifecycle()
    val running by ActionDispatcher.running.collectAsStateWithLifecycle()

    var states by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    // Keep the notification style in sync so a press acknowledges the way the user chose.
    LaunchedEffect(config.startNotification) {
        ActionDispatcher.rememberNotificationPreference(config.startNotification)
    }

    LaunchedEffect(tick, running, config) {
        if (running != null) return@LaunchedEffect
        states = config.profiles.associate { it.id to NetworkStatus.quickActive(context, it) }
    }

    // Play the slide-up once, on first composition.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val shown = config.homeProfiles()
    val toggles = shown.filter { it.kind.isToggle }
    val networks = shown.filter { it.kind == ProfileKind.WIFI }

    val onPress: (Profile) -> Unit = { profile ->
        ActionDispatcher.dispatch(context, profile.id, profile.name)
    }

    Box(Modifier.fillMaxSize()) {
        // Scrim: a tap anywhere outside the card closes the panel.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .padding(horizontal = 16.dp)
                        // Swallow taps on the card so they don't fall through to the scrim.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.panel_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                        }
                    }

                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (toggles.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            toggles.chunked(4).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEachIndexed { index, profile ->
                                        if (index > 0) Spacer(Modifier.width(8.dp))
                                        PanelToggle(
                                            profile = profile,
                                            isOn = states[profile.id] == true,
                                            busy = running == profile.id,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onPress(profile) },
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

                        if (networks.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                networks.forEach { profile ->
                                    PanelNetwork(
                                        profile = profile,
                                        isActive = states[profile.id] == true,
                                        busy = running == profile.id,
                                        onClick = { onPress(profile) },
                                    )
                                }
                            }
                        }

                        if (toggles.isEmpty() && networks.isEmpty()) {
                            Text(
                                text = "На главном экране нет ни переключателей, ни сетей. " +
                                    "Добавьте их на вкладке «Кнопки».",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelToggle(
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
private fun PanelNetwork(
    profile: Profile,
    isActive: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            CardDefaults.cardColors()
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
        }
    }
}
