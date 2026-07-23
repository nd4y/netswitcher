package icu.nd4y.netswitcher.engine

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Runs commands through the Shizuku service, i.e. with the same privileges adb has.
 * `Shizuku.newProcess` is marked @RestrictTo in the public API artifact, so it is
 * reached reflectively — this is the standard way third-party apps use it.
 */
object ShizukuShell : PrivilegedShell {

    override val label = "Shizuku"

    const val PERMISSION_REQUEST_CODE = 4711

    fun binderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isPreV11(): Boolean = runCatching { Shizuku.isPreV11() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        binderAlive() && !isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
    }

    /** "adb" or "root" depending on how Shizuku itself was started. */
    fun uidLabel(): String = runCatching {
        when (Shizuku.getUid()) {
            0 -> "root"
            2000 -> "adb (shell)"
            else -> "uid ${Shizuku.getUid()}"
        }
    }.getOrDefault("неизвестно")

    override suspend fun isAvailable(): Boolean = hasPermission()

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext ShellResult.failure(command, "Shizuku недоступен или не выдано разрешение")
        }
        runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null,
            ) as Process
            drain(command, process)
        }.getOrElse { error ->
            ShellResult.failure(command, "Shizuku: ${error.javaClass.simpleName}: ${error.message}")
        }
    }
}
