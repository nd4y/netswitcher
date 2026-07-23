package icu.nd4y.netswitcher.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plain `su -c` backend, for rooted devices. */
object RootShell : PrivilegedShell {

    override val label = "root"

    @Volatile
    private var cachedAvailable: Boolean? = null

    override suspend fun isAvailable(): Boolean {
        cachedAvailable?.let { return it }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(false).start()
                drain("id", process).stdout.contains("uid=0")
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
