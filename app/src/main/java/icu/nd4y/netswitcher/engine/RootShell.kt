package icu.nd4y.netswitcher.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Plain `su -c` backend, for rooted devices. */
object RootShell : PrivilegedShell {

    override val label = "root"

    @Volatile
    private var cachedAvailable: Boolean? = null

    override suspend fun isAvailable(): Boolean {
        cachedAvailable?.let { return it }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                // A root manager may pop a grant dialog and keep `su` alive until the
                // user answers — an unbounded read here froze the status card on
                // "checking privileges". Bound the probe; a denied/hung su is "no root".
                val process = ProcessBuilder("su", "-c", "id").start()
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    false
                } else {
                    process.exitValue() == 0 &&
                        process.inputStream.bufferedReader().use { it.readText() }
                            .contains("uid=0")
                }
            }.getOrDefault(false)
        }
        cachedAvailable = result
        return result
    }

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("su", "-c", command).start()
            drain(command, process)
        }.getOrElse { error ->
            ShellResult.failure(command, "root: ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    fun invalidate() {
        cachedAvailable = null
    }
}
