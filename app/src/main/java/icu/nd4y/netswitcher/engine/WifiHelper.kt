package icu.nd4y.netswitcher.engine

import android.net.wifi.WifiConfiguration
import android.os.Bundle
import android.os.IBinder
import kotlin.system.exitProcess

/**
 * Privileged helper: the one thing `cmd wifi` cannot do is join an already-saved
 * network *without* rewriting its configuration — `connect-network` always re-adds
 * the network and the framework then copies the fresh defaults (MAC randomization,
 * metered override, auto-join, DHCP hostname…) over whatever the user set in Android's
 * Wi-Fi settings. `IWifiManager.connect(null, netId)` — what a tap in Settings does —
 * leaves the saved entry alone, so that is what this class calls.
 *
 * It runs as the shell (or root) user inside `app_process`, launched by the privileged
 * shell with the app's own APK on the classpath — the same way the system's `svc` tool
 * works. That gives it the shell's NETWORK_SETTINGS privilege and a runtime without
 * hidden-API enforcement, so `IWifiManager` is reached by plain reflection; the AIDL
 * signatures drift between releases, which is why parameters are filled by type.
 *
 * Usage (see [SavedNetworks]):
 *   CLASSPATH=<apk> app_process /system/bin icu.nd4y.netswitcher.engine.WifiHelper \
 *       connect <ssid> <securityType> <autojoin 1|0|keep>
 *
 * Exit codes: 0 — connect requested (stdout: `ok id=<netId>` + a summary line),
 * 2 — no such saved network, 1 — error (stderr has the reason).
 */
object WifiHelper {

    private const val SHELL_PACKAGE = "com.android.shell"

    private val INT = Int::class.javaPrimitiveType!!
    private val BOOL = Boolean::class.javaPrimitiveType!!

    @JvmStatic
    fun main(args: Array<String>) {
        val code = runCatching { run(args) }.getOrElse { error ->
            System.err.println("error: ${error.cause ?: error}")
            1
        }
        System.out.flush()
        System.err.flush()
        exitProcess(code)
    }

    private fun run(args: Array<String>): Int {
        if (args.getOrNull(0) != "connect") return usage()
        val ssid = args.getOrNull(1) ?: return usage()
        val securityType = args.getOrNull(2)?.toIntOrNull() ?: return usage()
        val autoJoin = when (args.getOrNull(3)) {
            "1", "true" -> true
            "0", "false" -> false
            else -> null
        }

        val service = wifiService()
        val saved = findSaved(service, ssid, securityType)
        if (saved == null) {
            println("notfound")
            return 2
        }

        // Null listener is what WifiManager.connect(netId, null) sends too.
        invoke(
            service, "connect",
            mapOf<Class<*>, Any?>(WifiConfiguration::class.java to null, INT to saved.networkId),
        )
        if (autoJoin != null) {
            invoke(service, "allowAutojoin", mapOf<Class<*>, Any?>(INT to saved.networkId, BOOL to autoJoin))
        }
        println("ok id=${saved.networkId}")
        describe(saved)?.let { println(it) }
        return 0
    }

    private fun usage(): Int {
        System.err.println("usage: connect <ssid> <securityType> <autojoin 1|0|keep>")
        return 1
    }

    private fun wifiService(): Any {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "wifi") as? IBinder
            ?: error("сервис wifi не найден")
        return Class.forName("android.net.wifi.IWifiManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
            ?: error("IWifiManager.asInterface вернул null")
    }

    /**
     * Calls [name] on the service proxy, supplying [values] by parameter type and
     * filling the rest with neutral defaults: the first String is the package name,
     * a Bundle is empty, listeners are null. Android 12 → 16 all fit this shape.
     */
    private fun invoke(service: Any, name: String, values: Map<Class<*>, Any?>): Any? {
        val method = service.javaClass.methods
            .filter { it.name == name }
            .maxByOrNull { it.parameterTypes.size }
            ?: error("IWifiManager.$name не найден")
        var strings = 0
        val args = method.parameterTypes.map { type ->
            when {
                values.containsKey(type) -> values[type]
                type == String::class.java -> if (strings++ == 0) SHELL_PACKAGE else null
                type == Bundle::class.java -> Bundle()
                type == INT -> 0
                type == BOOL -> false
                else -> null
            }
        }
        return method.invoke(service, *args.toTypedArray())
    }

    private fun findSaved(service: Any, ssid: String, securityType: Int): WifiConfiguration? {
        val slice = invoke(service, "getConfiguredNetworks", mapOf<Class<*>, Any?>(BOOL to false))
            ?: return null
        val list = slice.javaClass.getMethod("getList").invoke(slice) as? List<*> ?: return null
        return list.filterIsInstance<WifiConfiguration>()
            .filter { plainSsid(it.SSID) == ssid }
            .firstOrNull { hasSecurity(it, securityType) }
    }

    /** The framework stores UTF-8 SSIDs in quotes; non-UTF-8 ones as bare hex. */
    private fun plainSsid(raw: String?): String? =
        raw?.let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1) else it }

    /**
     * `isSecurityType` sees the whole security-params list, so a WPA2 network that the
     * framework auto-upgraded to WPA3 matches either profile flavour. The legacy
     * key-management bitset is the fallback for builds without it.
     */
    @Suppress("DEPRECATION")
    private fun hasSecurity(config: WifiConfiguration, securityType: Int): Boolean = runCatching {
        config.javaClass.getMethod("isSecurityType", INT).invoke(config, securityType) as Boolean
    }.getOrElse {
        val bit = when (securityType) {
            WifiConfiguration.SECURITY_TYPE_OPEN -> WifiConfiguration.KeyMgmt.NONE
            WifiConfiguration.SECURITY_TYPE_PSK -> WifiConfiguration.KeyMgmt.WPA_PSK
            WifiConfiguration.SECURITY_TYPE_SAE -> WifiConfiguration.KeyMgmt.SAE
            WifiConfiguration.SECURITY_TYPE_OWE -> WifiConfiguration.KeyMgmt.OWE
            else -> -1
        }
        bit >= 0 && config.allowedKeyManagement.get(bit)
    }

    /** What Android has stored for the network — goes to the on-screen log, best effort. */
    private fun describe(config: WifiConfiguration): String? = runCatching {
        val cls = config.javaClass
        fun field(name: String): Any? = runCatching { cls.getField(name).get(config) }.getOrNull()
        fun call(name: String): Any? = runCatching { cls.getMethod(name).invoke(config) }.getOrNull()

        val mac = when (field("macRandomizationSetting")) {
            WifiConfiguration.RANDOMIZATION_NONE -> "устройства"
            WifiConfiguration.RANDOMIZATION_PERSISTENT -> "случайный"
            WifiConfiguration.RANDOMIZATION_NON_PERSISTENT -> "случайный, непостоянный"
            WifiConfiguration.RANDOMIZATION_AUTO -> "авто"
            else -> "?"
        }
        val metered = when (field("meteredOverride")) {
            0 -> "авто"
            1 -> "лимитная"
            2 -> "безлимитная"
            else -> "?"
        }
        val ip = call("getIpAssignment")?.toString()?.lowercase() ?: "?"
        val proxy = call("getProxySettings")?.toString()?.lowercase() ?: "?"
        "В Android: MAC $mac · трафик $metered · IP $ip · прокси $proxy · " +
            "автоподключение ${field("allowAutojoin")} · скрытая ${config.hiddenSSID}"
    }.getOrNull()
}
