package icu.nd4y.netswitcher.action

import android.app.Activity
import android.os.Bundle

/**
 * Invisible trampoline used by launcher shortcuts: kicks the action off on the
 * application scope and disappears immediately, so nothing flashes on screen.
 */
class ActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
        if (!profileId.isNullOrBlank()) {
            // The label travels in the shortcut itself, so the press can be
            // acknowledged before the configuration has even been read back.
            ActionDispatcher.dispatch(
                applicationContext,
                profileId,
                intent?.getStringExtra(EXTRA_PROFILE_NAME),
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "icu.nd4y.netswitcher.PROFILE_ID"
        const val EXTRA_PROFILE_NAME = "icu.nd4y.netswitcher.PROFILE_NAME"
        const val ACTION_RUN = "icu.nd4y.netswitcher.action.RUN"
    }
}
