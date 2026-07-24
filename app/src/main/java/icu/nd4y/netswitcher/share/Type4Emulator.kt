package icu.nd4y.netswitcher.share

/**
 * The APDU state machine of an NFC Forum Type 4 Tag, kept free of Android types so it can
 * be unit-tested. [WifiHceService] is the thin Host Card Emulation wrapper that feeds it
 * the reader's commands.
 *
 * A reader walks a Type 4 tag in a fixed sequence: SELECT the NDEF application by AID,
 * SELECT + READ the Capability Container to learn the file layout, then SELECT + READ the
 * NDEF file. The NDEF file is `[NLEN(2 bytes)][NDEF message]`, so the reader first reads
 * two bytes for the length and then that many bytes of message.
 */
class Type4Emulator {

    private enum class Selected { NONE, CC, NDEF }

    private var selected = Selected.NONE

    /** `[NLEN][message]`; set by the service from the currently armed network, or null. */
    var ndefFile: ByteArray? = null

    /** True right after a [process] call that served actual NDEF message bytes (offset ≥ 2). */
    var servedContent = false
        private set

    fun reset() {
        selected = Selected.NONE
    }

    fun process(apdu: ByteArray): ByteArray {
        servedContent = false
        return when {
            apdu.startsWith(SELECT_APP) -> {
                selected = Selected.NONE
                if (ndefFile != null) SW_OK else SW_FILE_NOT_FOUND
            }

            apdu.startsWith(SELECT_CC) -> {
                selected = Selected.CC
                SW_OK
            }

            apdu.startsWith(SELECT_NDEF) -> {
                selected = Selected.NDEF
                SW_OK
            }

            apdu.size >= 5 && apdu[0].toInt() and 0xFF == 0x00 &&
                apdu[1].toInt() and 0xFF == 0xB0 -> readBinary(apdu)

            else -> SW_INS_NOT_SUPPORTED
        }
    }

    private fun readBinary(apdu: ByteArray): ByteArray {
        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        val requested = apdu[4].toInt() and 0xFF
        val file = when (selected) {
            Selected.CC -> CC_FILE
            Selected.NDEF -> ndefFile ?: return SW_FILE_NOT_FOUND
            Selected.NONE -> return SW_FILE_NOT_FOUND
        }
        if (offset > file.size) return SW_END_OF_FILE
        val length = minOf(requested, file.size - offset)
        val response = ByteArray(length + 2)
        System.arraycopy(file, offset, response, 0, length)
        response[length] = 0x90.toByte()
        response[length + 1] = 0x00
        if (selected == Selected.NDEF && offset >= 2 && length > 0) servedContent = true
        return response
    }

    companion object {
        // SELECT (by name) the NDEF Tag Application, AID D2760000850101. The reader appends
        // an Le byte, so we match on the prefix through the AID only.
        private val SELECT_APP = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )
        private val SELECT_CC =
            byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03)
        private val SELECT_NDEF =
            byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        private val SW_END_OF_FILE = byteArrayOf(0x6B, 0x00)
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)

        /**
         * Capability Container: version 2.0, and one NDEF File Control TLV pointing at file
         * 0xE104, max size 0x0400, read-only.
         */
        val CC_FILE = byteArrayOf(
            0x00, 0x0F,                 // CCLEN = 15
            0x20,                       // mapping version 2.0
            0x00, 0xFB.toByte(),        // MLe (max bytes we return)
            0x00, 0xFF.toByte(),        // MLc (max bytes we accept)
            0x04, 0x06,                 // NDEF File Control TLV: type 0x04, length 6
            0xE1.toByte(), 0x04,        // NDEF file id 0xE104
            0x04, 0x00,                 // max NDEF file size = 0x0400
            0x00,                       // read access granted
            0xFF.toByte(),              // write access denied
        )

        /** Wraps a raw NDEF message in the `[NLEN][message]` file layout a reader expects. */
        fun ndefFileFor(message: ByteArray): ByteArray {
            val file = ByteArray(message.size + 2)
            file[0] = ((message.size ushr 8) and 0xFF).toByte()
            file[1] = (message.size and 0xFF).toByte()
            System.arraycopy(message, 0, file, 2, message.size)
            return file
        }

        internal fun ByteArray.startsWith(prefix: ByteArray): Boolean {
            if (size < prefix.size) return false
            for (i in prefix.indices) if (this[i] != prefix[i]) return false
            return true
        }
    }
}
