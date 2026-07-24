package icu.nd4y.netswitcher.ui

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.data.ProfileKind
import java.util.UUID

@Composable
fun ProfilesScreen(
    config: Config,
    controller: ConfigController,
    modifier: Modifier = Modifier,
    onEdit: (Profile) -> Unit,
) {
    val haptics = rememberClickHaptics()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Профили сетей", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Порядок общий с вкладкой «Сети»",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = {
                haptics()
                onEdit(
                    Profile(
                        id = UUID.randomUUID().toString().take(8),
                        name = "Новая сеть",
                        kind = ProfileKind.WIFI,
                    )
                )
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Добавить")
            }
        }

        ReorderableColumn(
            count = config.profiles.size,
            spacing = 8.dp,
            onMove = { from, to ->
                val order = config.profiles.map { it.id }.moveItem(from, to)
                controller.edit { it.reordered(order) }
            },
        ) { index, isDragging ->
            val profile = config.profiles[index]
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = if (isDragging) 8.dp else 1.dp
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(profile.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
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
                    IconButton(onClick = {
                        haptics()
                        onEdit(profile)
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Изменить")
                    }
                    IconButton(onClick = {
                        haptics()
                        controller.edit { current ->
                            current.copy(
                                profiles = current.profiles.filterNot { it.id == profile.id },
                                homeIds = current.homeIds - profile.id,
                                shortcutIds = current.shortcutIds - profile.id,
                                widgetIds = current.widgetIds - profile.id,
                                tileBindings = current.tileBindings
                                    .filterValues { it != profile.id },
                            )
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                    }
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Перетащить",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
