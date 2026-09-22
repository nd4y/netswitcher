package icu.nd4y.netswitcher.engine

import android.content.Context
import icu.nd4y.netswitcher.data.Profile

/** Outcome of asking [WifiHelper] to join a network Android already has saved. */
sealed class SavedConnect {
    /** The connect request went out; the saved entry was left untouched. */
    data class Connected(val netId: Int) : SavedConnect()

    /** Nothing saved under that SSID / security — the caller has to add it. */
    object NotSaved : SavedConnect()

    /** The helper could not run or the service refused — no fallback, see [reason]. */
    data class Failed(val reason: String) : SavedConnect()
}

/**
 * Runs [WifiHelper] through the privileged shell. The helper lives in this very APK;
 * `app_process` loads it from there, which is why the command carries the APK path.
 */
object SavedNetworks {

    suspend fun connect(
        context: Context,
        shell: PrivilegedShell,
        profile: Profile,
        log: MutableList<String>,
    ): SavedConnect {
        val apk = context.applicationContext.applicationInfo.sourceDir
        val command = buildString {
            append("CLASSPATH=").append(shQuote(apk))
            append(" /system/bin/app_process /system/bin ")
            append(WifiHelper::class.java.name)
            append(" connect ").append(shQuote(profile.ssid))
            append(' ').append(profile.security.frameworkType)
            append(' ').append(if (profile.autoJoin) 1 else 0)
        }
        val result = shell.exec(command)

        val lines = result.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val verdict = lines.firstOrNull().orEmpty()
        log += "$ helper connect ${profile.ssid} -> ${result.exitCode}" +
            if (verdict.isEmpty()) "" else " | $verdict"
        lines.drop(1).forEach { log += it }
        if (result.stderr.isNotBlank()) log += result.stderr.trim().take(300)

        return when {
            result.exitCode == 0 && verdict.startsWith("ok") -> {
                val netId = verdict.substringAfter("id=", "").trim().toIntOrNull() ?: -1
                log += "Подключение к сохранённой сети #$netId — её настройки в Android не тронуты"
                SavedConnect.Connected(netId)
            }

            result.exitCode == 2 || verdict == "notfound" -> SavedConnect.NotSaved

            else -> SavedConnect.Failed(
                result.stderr.trim().ifBlank { result.stdout.trim() }.ifBlank { "код ${result.exitCode}" }.take(200)
            )
        }
    }
}
