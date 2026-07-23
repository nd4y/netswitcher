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
            ActionDispatcher.dispatch(applicationContext, profileId)
        }
        finish()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "icu.nd4y.netswitcher.PROFILE_ID"
        const val ACTION_RUN = "icu.nd4y.netswitcher.action.RUN"
    }
}
