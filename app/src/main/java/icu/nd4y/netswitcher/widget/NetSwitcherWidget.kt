package icu.nd4y.netswitcher.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import icu.nd4y.netswitcher.action.ActionDispatcher
import icu.nd4y.netswitcher.action.Feedback
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.engine.NetworkStatus
import icu.nd4y.netswitcher.ui.MainActivity
import kotlinx.coroutines.flow.first

class NetSwitcherWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = ConfigRepository.get(context).flow.first()
        val buttons = config.resolve(config.widgetIds)
        val active = buttons.associate { it.id to NetworkStatus.quickActive(context, it) }
        val busy = ActionDispatcher.running.value
        provideContent {
            GlanceTheme {
                WidgetBody(config, buttons, active, busy)
            }
        }
    }
}

@Composable
private fun WidgetBody(
    config: Config,
    buttons: List<Profile>,
    active: Map<String, Boolean>,
    busyId: String?,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (buttons.isEmpty()) {
            Text(
                text = "Откройте NetSwitcher и выберите кнопки для виджета",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            )
            return@Column
        }

        val columns = config.widgetColumns.coerceIn(1, 4)
        buttons.chunked(columns).forEachIndexed { rowIndex, rowItems ->
            if (rowIndex > 0) Spacer(GlanceModifier.size(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                rowItems.forEachIndexed { columnIndex, profile ->
                    if (columnIndex > 0) Spacer(GlanceModifier.size(6.dp))
                    WidgetButton(
                        profile = profile,
                        isActive = active[profile.id] == true,
                        isBusy = busyId == profile.id,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
                // Keep the last row's cells the same width as the full rows.
                repeat(columns - rowItems.size) {
                    Spacer(GlanceModifier.size(6.dp))
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun WidgetButton(
    profile: Profile,
    isActive: Boolean,
    isBusy: Boolean,
    modifier: GlanceModifier,
) {
    val background = when {
        isBusy -> GlanceTheme.colors.tertiaryContainer
        isActive -> GlanceTheme.colors.primaryContainer
        else -> GlanceTheme.colors.secondaryContainer
    }
    val foreground = when {
        isBusy -> GlanceTheme.colors.onTertiaryContainer
        isActive -> GlanceTheme.colors.onPrimaryContainer
        else -> GlanceTheme.colors.onSecondaryContainer
    }

    Column(
        modifier = modifier
            .background(background)
            .cornerRadius(16.dp)
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .clickable(
                actionRunCallback<RunProfileAction>(
                    actionParametersOf(
                        RunProfileAction.profileIdKey to profile.id,
                        RunProfileAction.profileNameKey to profile.name,
                    )
                )
            ),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(profile.iconRes),
            contentDescription = profile.name,
            colorFilter = ColorFilter.tint(foreground),
            modifier = GlanceModifier.size(22.dp),
        )
        Spacer(GlanceModifier.size(4.dp))
        Text(
            text = if (isBusy) "переключаю…" else profile.name,
            maxLines = 1,
            style = TextStyle(
                color = foreground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

class RunProfileAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val profileId = parameters[profileIdKey] ?: return
        val label = parameters[profileNameKey]
        if (label != null) {
            Feedback.announceStart(context, label, ActionDispatcher.toastsEnabled)
        }
        ActionDispatcher.runNow(context, profileId, alreadyAnnounced = label != null)
        NetSwitcherWidget().updateAll(context)
    }

    companion object {
        val profileIdKey = ActionParameters.Key<String>("profileId")
        val profileNameKey = ActionParameters.Key<String>("profileName")
    }
}

class NetSwitcherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NetSwitcherWidget()
}
