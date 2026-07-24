package icu.nd4y.netswitcher.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import icu.nd4y.netswitcher.data.Profile
import java.io.File
import java.io.FileOutputStream

/** Hands the credentials off to the system share sheet, as text or as the QR image. */
object ShareSheet {

    fun shareText(context: Context, profile: Profile) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "Wi-Fi ${profile.ssid}")
            putExtra(Intent.EXTRA_TEXT, WifiCredentials.shareText(profile))
        }
        launch(context, intent)
    }

    fun shareQrImage(context: Context, profile: Profile, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "wifi-qr-${sanitize(profile.ssid)}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, WifiCredentials.shareText(profile))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(context, intent)
    }

    private fun launch(context: Context, intent: Intent) {
        val chooser = Intent.createChooser(intent, "Поделиться сетью")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun sanitize(ssid: String): String =
        ssid.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(24)
}
