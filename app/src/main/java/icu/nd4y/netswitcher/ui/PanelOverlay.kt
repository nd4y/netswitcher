package icu.nd4y.netswitcher.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Shows [PanelRoot] as a `TYPE_APPLICATION_OVERLAY` window instead of an Activity — the
 * only way for a third-party app to draw above the notification shade. [PanelTile] only
 * calls into this when `Settings.canDrawOverlays()` is true; otherwise it falls back to
 * launching [PanelActivity], which requires (and triggers) the usual shade collapse.
 *
 * A single overlay host at a time; calling [show] while one is already up is a no-op.
 */
object PanelOverlayController {

    private var host: OverlayHost? = null

    fun show(context: Context) {
        if (host != null) return
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return
        host = OverlayHost(context, windowManager) { host = null }.also { it.attach() }
    }
}

/**
 * Owns the WindowManager-attached view and the bare-bones [LifecycleOwner] /
 * [SavedStateRegistryOwner] / [OnBackPressedDispatcherOwner] a [ComposeView] needs when
 * it isn't hosted by an Activity or Fragment — none of that infrastructure exists for a
 * view added directly via [WindowManager.addView].
 */
private class OverlayHost(
    context: Context,
    private val windowManager: WindowManager,
    private val onDetached: () -> Unit,
) : SavedStateRegistryOwner, OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    private val appContext = context.applicationContext
    private val root: FrameLayout

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        val composeView = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(this@OverlayHost)
            setViewTreeSavedStateRegistryOwner(this@OverlayHost)
            setViewTreeOnBackPressedDispatcherOwner(this@OverlayHost)
            setContent {
                NetSwitcherTheme {
                    PanelRoot(onFinish = { detach() })
                }
            }
        }
        root = object : FrameLayout(appContext) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onBackPressedDispatcher.onBackPressed()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusableInTouchMode = true
            addView(
                composeView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
    }

    fun attach() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { windowManager.addView(root, params) }
            .onFailure { detach() }
            .onSuccess {
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                root.requestFocus()
            }
    }

    private fun detach() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        runCatching { windowManager.removeView(root) }
        onDetached()
    }
}
