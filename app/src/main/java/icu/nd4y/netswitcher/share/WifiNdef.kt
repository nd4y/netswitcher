package icu.nd4y.netswitcher.share

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import icu.nd4y.netswitcher.data.Profile

/** Builds the NDEF message a Wi-Fi share carries — a single WSC MIME record. */
object WifiNdef {

    fun message(profile: Profile): NdefMessage =
        NdefMessage(
            arrayOf(
                NdefRecord.createMime(
                    WifiCredentials.NFC_MIME_TYPE,
                    WifiCredentials.wscPayload(profile),
                ),
            ),
        )

    fun messageBytes(profile: Profile): ByteArray = message(profile).toByteArray()
}
