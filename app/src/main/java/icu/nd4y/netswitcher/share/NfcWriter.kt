package icu.nd4y.netswitcher.share

import android.app.Activity
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import icu.nd4y.netswitcher.data.Profile
import java.io.IOException

/**
 * Writes a network's WSC credential to an NFC tag using reader mode. Reader mode keeps
 * the write inside the app instead of firing the system tag dispatcher, so tapping a tag
 * while the share sheet is open writes to it rather than trying to read it.
 *
 * Phone-to-phone NFC (Android Beam) was removed in Android 10, so writing a tag is the
 * only NFC path left — the payload matches what the OS itself writes when you "share Wi-Fi
 * over NFC", so any phone that taps the finished tag is offered the network.
 */
object NfcWriter {

    enum class Status { WAITING, WRITTEN, NOT_WRITABLE, FAILED, NO_NFC, NFC_OFF }

    fun isAvailable(activity: Activity): Boolean =
        NfcAdapter.getDefaultAdapter(activity) != null

    /**
     * Starts listening for a tag and writes [profile]'s credential to the first one tapped.
     * [onStatus] is invoked on a binder thread; marshal to the UI yourself. Returns a
     * handle whose [Session.stop] must be called (e.g. when the dialog closes) to leave
     * reader mode.
     */
    fun start(activity: Activity, profile: Profile, onStatus: (Status) -> Unit): Session {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null) {
            onStatus(Status.NO_NFC)
            return Session.NONE
        }
        if (!adapter.isEnabled) {
            onStatus(Status.NFC_OFF)
            return Session.NONE
        }

        val message = ndefMessage(activity, profile)
        val callback = NfcAdapter.ReaderCallback { tag -> onStatus(write(tag, message)) }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        adapter.enableReaderMode(activity, callback, flags, null)
        onStatus(Status.WAITING)
        return Session { runCatching { adapter.disableReaderMode(activity) } }
    }

    private fun ndefMessage(activity: Activity, profile: Profile): NdefMessage {
        val mime = NdefRecord.createMime(
            WifiCredentials.NFC_MIME_TYPE,
            WifiCredentials.wscPayload(profile),
        )
        // A trailing Android Application Record makes an unconfigured tap open NetSwitcher
        // rather than doing nothing, without disturbing phones that read the Wi-Fi record.
        val aar = NdefRecord.createApplicationRecord(activity.packageName)
        return NdefMessage(arrayOf(mime, aar))
    }

    private fun write(tag: Tag, message: NdefMessage): Status {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) return Status.NOT_WRITABLE
                if (ndef.maxSize < message.toByteArray().size) return Status.NOT_WRITABLE
                ndef.writeNdefMessage(message)
                Status.WRITTEN
            } catch (e: IOException) {
                Status.FAILED
            } catch (e: FormatException) {
                Status.FAILED
            } finally {
                runCatching { ndef.close() }
            }
        }
        val formatable = NdefFormatable.get(tag) ?: return Status.NOT_WRITABLE
        return try {
            formatable.connect()
            formatable.format(message)
            Status.WRITTEN
        } catch (e: IOException) {
            Status.FAILED
        } catch (e: FormatException) {
            Status.FAILED
        } finally {
            runCatching { formatable.close() }
        }
    }

    /** Handle returned by [start]; call [stop] to leave reader mode. */
    fun interface Session {
        fun stop()

        companion object {
            val NONE = Session {}
        }
    }
}
