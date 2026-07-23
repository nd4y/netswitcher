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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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

    val phonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) phonePermission.launch(Manifest.permission.READ_PHONE_STATE)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Сети") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Профили") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                    label = { Text("Кнопки") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Настройки") },
                )
            }
        }
    ) { insets ->
        val inner = Modifier.padding(insets)
        when (tab) {
            0 -> HomeScreen(config, inner)
            1 -> ProfilesScreen(config, controller, inner) { editing = it }
            2 -> ButtonsScreen(config, controller, inner)
            else -> SettingsScreen(config, controller, inner)
        }
    }

    editing?.let { profile ->
        EditProfileDialog(
            profile = profile,
            onDismiss = { editing = null },
            onSave = { updated ->
                controller.edit { current ->
                    val exists = current.profiles.any { it.id == updated.id }
                    val profiles =
                        if (exists) current.profiles.map { if (it.id == updated.id) updated else it }
                        else current.profiles + updated
                    current.copy(profiles = profiles)
                }
                editing = null
            },
        )
    }
}
