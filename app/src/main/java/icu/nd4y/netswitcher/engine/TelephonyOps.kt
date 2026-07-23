package icu.nd4y.netswitcher.engine

import android.content.Context
import android.os.IBinder
import android.telephony.SubscriptionManager
import kotlinx.coroutines.delay
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

data class SimSlot(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrier: String,
) {
    val label: String
        get() = buildString {
            append("SIM ${slotIndex + 1}")
            if (displayName.isNotBlank()) append(" · $displayName")
            else if (carrier.isNotBlank()) append(" · $carrier")
        }
}

object TelephonyOps {

    fun currentDefaultDataSubId(): Int =
        runCatching { SubscriptionManager.getDefaultDataSubscriptionId() }
            .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)

    /** Requires READ_PHONE_STATE; returns an empty list when it is not granted. */
    fun readSims(context: Context): List<SimSlot> = runCatching {
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        @Suppress("MissingPermission")
        val list = manager.activeSubscriptionInfoList ?: return emptyList()
        list.map { info ->
            SimSlot(
                subscriptionId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                displayName = info.displayName?.toString().orEmpty(),
                carrier = info.carrierName?.toString().orEmpty(),
            )
        }
    }.getOrDefault(emptyList())

    /**
     * Switching the default data SIM is a privileged operation with no single stable
     * entry point, so we try the cheapest route first and verify after each attempt.
     */
    suspend fun setDefaultDataSub(
        shell: PrivilegedShell,
        subId: Int,
        log: MutableList<String>,
    ): Boolean {
        if (subId < 0) return true
        if (currentDefaultDataSubId() == subId) {
            log += "SIM $subId уже выбрана для передачи данных"
            return true
        }

        if (shell is ShizukuShell && trySetViaBinder(subId, log) && verify(subId, log)) return true

        val viaCmd = shell.exec("cmd phone set-default-data-sub $subId")
        log += "$ ${viaCmd.command} -> ${viaCmd.exitCode} ${viaCmd.output.take(160)}"
        if (verify(subId, log)) return true

        val viaSettings = shell.exec("settings put global multi_sim_data_call $subId")
        log += "$ ${viaSettings.command} -> ${viaSettings.exitCode} ${viaSettings.output.take(160)}"
        return verify(subId, log)
    }

    private fun trySetViaBinder(subId: Int, log: MutableList<String>): Boolean = runCatching {
        val raw: IBinder = SystemServiceHelper.getSystemService("isub")
            ?: error("сервис isub не найден")
        val binder = ShizukuBinderWrapper(raw)
        val stub = Class.forName("com.android.internal.telephony.ISub\$Stub")
        val iSub = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("ISub.asInterface вернул null")

        val method = iSub.javaClass.methods.firstOrNull {
            it.name == "setDefaultDataSubId" && it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType
        } ?: error("setDefaultDataSubId(int) не найден")

        method.invoke(iSub, subId)
        log += "ISub.setDefaultDataSubId($subId) вызван через Shizuku"
        true
    }.getOrElse { error ->
        log += "ISub через Shizuku не сработал: ${error.cause?.message ?: error.message}"
        false
    }

    private suspend fun verify(subId: Int, log: MutableList<String>): Boolean {
        repeat(6) {
            delay(300)
            if (currentDefaultDataSubId() == subId) {
                log += "Дефолтная SIM для данных = $subId"
                return true
            }
        }
        return false
    }
}
