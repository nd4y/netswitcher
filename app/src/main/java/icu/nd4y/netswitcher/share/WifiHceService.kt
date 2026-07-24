package icu.nd4y.netswitcher.share

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Host Card Emulation front-end that makes the phone act like an NFC Wi-Fi tag: another
 * phone tapped against it reads the network the same way it would read a physical tag.
 *
 * This is the only way to do phone-to-phone NFC now that Android Beam is gone — but it is
 * inherently finicky (both phones must have NFC on, this phone's screen on and unlocked,
 * and OEM behaviour varies). The credential is armed via [arm] while the share dialog is
 * open; the actual Type 4 APDU handling lives in the pure [Type4Emulator].
 */
class WifiHceService : HostApduService() {

    private val emulator = Type4Emulator()

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        emulator.ndefFile = ndefFile
        val apdu = commandApdu ?: return byteArrayOf(0x6F, 0x00)
        val response = emulator.process(apdu)
        if (emulator.servedContent) reads.value = reads.value + 1
        return response
    }

    override fun onDeactivated(reason: Int) {
        emulator.reset()
    }

    companion object {
        /** The `[NLEN][message]` NDEF file currently offered to readers, or null when idle. */
        @Volatile
        private var ndefFile: ByteArray? = null

        /** Bumped every time a reader pulls the NDEF message — the UI shows "read ✓". */
        val reads = MutableStateFlow(0)

        fun arm(messageBytes: ByteArray) {
            ndefFile = Type4Emulator.ndefFileFor(messageBytes)
        }

        fun disarm() {
            ndefFile = null
        }
    }
}
