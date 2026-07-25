package icu.nd4y.netswitcher.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import icu.nd4y.netswitcher.data.Config
import icu.nd4y.netswitcher.data.ConfigRepository
import icu.nd4y.netswitcher.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** Bumped whenever Shizuku's binder or permission state changes, to re-render status. */
object ShizukuEvents {
    val ticks = MutableStateFlow(0)
    fun bump() {
        ticks.value = ticks.value + 1
    }
}

class MainActivity : ComponentActivity() {

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> ShizukuEvents.bump() }
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { ShizukuEvents.bump() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { ShizukuEvents.bump() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        }
        setContent {
            NetSwitcherTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuEvents.bump()
    }

    override fun onDestroy() {
        runCatching {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        }
        super.onDestroy()
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ConfigRepository.get(context) }
    val config by repo.flow.collectAsStateWithLifecycle(initialValue = Config.default())
    val controller = rememberConfigController(scope)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        // READ_PHONE_STATE lists the SIMs; POST_NOTIFICATIONS carries the progress
        // notification that acknowledges presses from outside the app; without
        // ACCESS_FINE_LOCATION Android hides the current SSID, so the "connected"
        // highlight on network cards, tiles and the widget never lights up.
        val wanted = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }

    val haptics = rememberClickHaptics()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { haptics(); tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Сети") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { haptics(); tab = 1 },
                    icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                    label = { Text("Кнопки") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { haptics(); tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Настройки") },
                )
            }
        }
    ) { insets ->
        val inner = Modifier.padding(insets)
        when (tab) {
            0 -> HomeScreen(config, controller, inner) { editing = it }
            1 -> ButtonsScreen(config, controller, inner)
            else -> SettingsScreen(config, controller, inner)
        }
    }

    editing?.let { profile ->
        val exists = config.profiles.any { it.id == profile.id }
        EditProfileDialog(
            profile = profile,
            onDismiss = { editing = null },
            onSave = { updated ->
                controller.edit { current ->
                    val known = current.profiles.any { it.id == updated.id }
                    val profiles =
                        if (known) current.profiles.map { if (it.id == updated.id) updated else it }
                        else current.profiles + updated
                    // A newly added network lands on the main screen right away —
                    // otherwise it would silently drop into "Остальные профили".
                    val homeIds =
                        if (!known && updated.kind.rendersOnHomeScreen &&
                            updated.id !in current.homeIds
                        ) current.homeIds + updated.id
                        else current.homeIds
                    current.copy(profiles = profiles, homeIds = homeIds)
                }
                editing = null
            },
            onDelete = if (exists) {
                {
                    // Snapshot the whole config so "Отменить" restores the profile and
                    // every button binding it had, exactly as before the delete.
                    val before = config
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
                    editing = null
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Профиль «${profile.name}» удалён",
                            actionLabel = "Отменить",
                            withDismissAction = true,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            controller.edit { before }
                        }
                    }
                }
            } else null,
        )
    }
}
