package icu.nd4y.netswitcher.engine

import icu.nd4y.netswitcher.data.Backend

data class PrivilegeState(
    val shell: PrivilegedShell?,
    val description: String,
) {
    val hasPrivileges: Boolean get() = shell != null
}

object PrivilegeManager {

    suspend fun resolve(preferred: Backend): PrivilegeState = when (preferred) {
        Backend.NONE -> PrivilegeState(null, "Привилегии отключены в настройках")

        Backend.SHIZUKU ->
            if (ShizukuShell.isAvailable()) {
                PrivilegeState(ShizukuShell, "Shizuku — ${ShizukuShell.uidLabel()}")
            } else {
                PrivilegeState(null, shizukuProblem())
            }

        Backend.ROOT ->
            if (RootShell.isAvailable()) PrivilegeState(RootShell, "root")
            else PrivilegeState(null, "su недоступен")

        Backend.AUTO -> when {
            ShizukuShell.isAvailable() -> PrivilegeState(ShizukuShell, "Shizuku — ${ShizukuShell.uidLabel()}")
            RootShell.isAvailable() -> PrivilegeState(RootShell, "root")
            else -> PrivilegeState(null, shizukuProblem())
        }
    }

    fun shizukuProblem(): String = when {
        !ShizukuShell.binderAlive() -> "Shizuku не запущен"
        ShizukuShell.isPreV11() -> "Слишком старая версия Shizuku (нужна v11+)"
        !ShizukuShell.hasPermission() -> "Shizuku: разрешение не выдано"
        else -> "Привилегии недоступны"
    }
}
