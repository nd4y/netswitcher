package icu.nd4y.netswitcher.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class ShellResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean get() = exitCode == 0

    val output: String get() = buildString {
        append(stdout.trim())
        if (stderr.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(stderr.trim())
        }
    }

    companion object {
        fun failure(command: String, reason: String) =
            ShellResult(command, -1, "", reason)
    }
}

/** A shell that runs with more privileges than the app's own uid. */
interface PrivilegedShell {
    val label: String
    suspend fun isAvailable(): Boolean
    suspend fun exec(command: String): ShellResult
}

/**
 * Drains a [Process], reading stderr on a side thread so a chatty command
 * cannot fill a pipe buffer and wedge us.
 */
internal suspend fun drain(command: String, process: Process): ShellResult =
    withContext(Dispatchers.IO) {
        val errBuffer = StringBuilder()
        val errThread = Thread {
            runCatching {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.forEachLine { errBuffer.appendLine(it) }
                }
            }
        }
        errThread.start()

        val out = runCatching {
            BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        }.getOrDefault("")

        val code = runCatching { process.waitFor() }.getOrDefault(-1)
        runCatching { errThread.join(2_000) }
        runCatching { process.destroy() }

        ShellResult(command, code, out, errBuffer.toString())
    }

/** Wraps a value in single quotes for `sh -c`, escaping embedded quotes. */
fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
