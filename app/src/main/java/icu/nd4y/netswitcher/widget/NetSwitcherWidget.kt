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
import androidx.glance.LocalSize
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
import androidx.glance.layout.Box
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
        // RemoteViews allows at most 10 children per container. Rows used to be
        // interleaved with Spacer siblings, which halved that budget: past ~5 rows
        // the composition failed and the widget silently kept its previous layout —
        // "stretched it, but the extra rows never appeared". Spacing is padding now,
        // and anything beyond MAX_ROWS rows is dropped instead of breaking the render.
        val rows = buttons.chunked(columns).take(MAX_ROWS)

        // When the widget is squeezed, full cells (icon + label) get clipped from the
        // bottom row up; below this per-row height the cells drop the icon and keep
        // the label, which is the part that tells the networks apart.
        val size = LocalSize.current
        val perRowDp = (size.height.value - 16f - 6f * (rows.size - 1)) / rows.size
        val compact = perRowDp < 56f

        rows.forEachIndexed { rowIndex, rowItems ->
            val rowModifier =
                if (rowIndex > 0) GlanceModifier.fillMaxWidth().padding(top = 6.dp)
                else GlanceModifier.fillMaxWidth()
            Row(modifier = rowModifier) {
                rowItems.forEachIndexed { columnIndex, profile ->
                    if (columnIndex > 0) Spacer(GlanceModifier.size(6.dp))
                    WidgetButton(
                        profile = profile,
                        isActive = active[profile.id] == true,
                        isBusy = busyId == profile.id,
                        compact = compact,
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

/** RemoteViews' 10-children-per-container cap, now spent entirely on rows. */
private const val MAX_ROWS = 10

@Composable
private fun WidgetButton(
    profile: Profile,
    isActive: Boolean,
    isBusy: Boolean,
    compact: Boolean,
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
    val action = actionRunCallback<RunProfileAction>(
        actionParametersOf(
            RunProfileAction.profileIdKey to profile.id,
            RunProfileAction.profileNameKey to profile.name,
        )
    )
    val label: @Composable () -> Unit = {
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

    if (compact) {
        Box(
            modifier = modifier
                .background(background)
                .cornerRadius(12.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            label()
        }
    } else {
        Column(
            modifier = modifier
                .background(background)
                .cornerRadius(16.dp)
                .padding(horizontal = 6.dp, vertical = 10.dp)
                .clickable(action),
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
            label()
        }
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
            Feedback.announceStart(context, label, ActionDispatcher.startNotification)
        }
        // No explicit updateAll() here: NetSwitcherApp's ActionDispatcher.running
        // collector already refreshes the widget on both the busy->true and busy->false
        // transitions. A second, unsynchronized updateAll() call from this separate
        // coroutine used to race it — an out-of-order render could leave a button stuck
        // on "переключаю…" after the switch had actually finished.
        ActionDispatcher.runNow(context, profileId, alreadyAnnounced = label != null)
    }

    companion object {
        val profileIdKey = ActionParameters.Key<String>("profileId")
        val profileNameKey = ActionParameters.Key<String>("profileName")
    }
}

class NetSwitcherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NetSwitcherWidget()
}
