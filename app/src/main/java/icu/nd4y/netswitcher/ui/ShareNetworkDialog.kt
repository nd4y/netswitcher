package icu.nd4y.netswitcher.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.netswitcher.data.Profile
import icu.nd4y.netswitcher.share.NfcWriter
import icu.nd4y.netswitcher.share.ShareSheet
import icu.nd4y.netswitcher.share.WifiCredentials
import icu.nd4y.netswitcher.share.WifiHceService
import icu.nd4y.netswitcher.share.WifiNdef
import icu.nd4y.netswitcher.share.WifiQrCode

/**
 * Three ways to hand a Wi-Fi network to someone: a QR code (shown here, scannable by the
 * stock camera), writing an NFC tag, or the system share sheet (text or the QR image).
 */
@Composable
fun ShareNetworkDialog(profile: Profile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptics = rememberClickHaptics()

    val qr = remember(profile.ssid, profile.password, profile.security, profile.hiddenSsid) {
        runCatching { WifiQrCode.render(WifiCredentials.qrPayload(profile)) }.getOrNull()
    }

    val nfcAvailable = activity != null && NfcWriter.isAvailable(activity)
    var nfcActive by remember { mutableStateOf(false) }
    var nfcStatus by remember { mutableStateOf<NfcWriter.Status?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val hceAvailable = nfcAvailable &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
    var tagMode by remember { mutableStateOf(false) }
    val hceReads by WifiHceService.reads.collectAsStateWithLifecycle()
    var readBaseline by remember { mutableStateOf(0) }

    // While tag mode is on (and the dialog is open) the phone emulates a Wi-Fi tag.
    DisposableEffect(tagMode) {
        if (tagMode) {
            readBaseline = WifiHceService.reads.value
            WifiHceService.arm(WifiNdef.messageBytes(profile))
            onDispose { WifiHceService.disarm() }
        } else {
            WifiHceService.disarm()
            onDispose { }
        }
    }

    DisposableEffect(nfcActive) {
        if (nfcActive && activity != null) {
            val session = NfcWriter.start(activity, profile) { status ->
                mainHandler.post {
                    nfcStatus = status
                    // A finished write leaves reader mode; failures stay armed for a retry.
                    if (status == NfcWriter.Status.WRITTEN ||
                        status == NfcWriter.Status.NO_NFC ||
                        status == NfcWriter.Status.NFC_OFF
                    ) {
                        nfcActive = false
                    }
                }
            }
            onDispose { session.stop() }
        } else {
            onDispose { }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Поделиться сетью", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = profile.ssid.ifBlank { "SSID не задан" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(16.dp))

                if (qr != null) {
                    // QR readers need a light background, so the code always sits on white,
                    // even in dark theme.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "QR-код сети ${profile.ssid}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Наведите камеру другого телефона — система предложит подключиться.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = "Не удалось построить QR-код для этой сети.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // --- NFC ---
                if (nfcAvailable) {
                    FilledTonalButton(
                        onClick = {
                            haptics()
                            nfcStatus = null
                            tagMode = false
                            nfcActive = !nfcActive
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (nfcActive) "Отмена записи на NFC" else "Записать на NFC-метку")
                    }
                    val hint = nfcStatusText(nfcStatus, nfcActive)
                    if (hint != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (nfcStatus == NfcWriter.Status.WRITTEN) {
                                MaterialTheme.colorScheme.primary
                            } else if (nfcStatus == NfcWriter.Status.FAILED ||
                                nfcStatus == NfcWriter.Status.NOT_WRITABLE
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(
                        text = "NFC на устройстве недоступно — запись на метку невозможна.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // --- Tag emulation (HCE) ---
                if (hceAvailable) {
                    FilledTonalButton(
                        onClick = {
                            haptics()
                            nfcActive = false
                            tagMode = !tagMode
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (tagMode) "Выключить режим метки" else "Режим метки (эмуляция NFC)")
                    }
                    if (tagMode) {
                        val wasRead = hceReads > readBaseline
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (wasRead) {
                                "Второй телефон считал сеть ✓ — режим можно выключить."
                            } else {
                                "Телефон изображает NFC-метку. Поднесите второй телефон " +
                                    "вплотную задними панелями. Экран этого телефона должен " +
                                    "быть включён и разблокирован, NFC на обоих — включён."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (wasRead) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // --- Share sheet ---
                Row(Modifier.fillMaxWidth()) {
                    // The labels wrap to two lines on narrow screens; without an explicit
                    // textAlign the wrapped lines are start-aligned and look off-center.
                    OutlinedButton(
                        onClick = { haptics(); ShareSheet.shareText(context, profile) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Отправить текст", textAlign = TextAlign.Center) }
                    if (qr != null) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { haptics(); ShareSheet.shareQrImage(context, profile, qr) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Отправить QR", textAlign = TextAlign.Center) }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onDismiss) { Text("Закрыть") }
                }
            }
        }
    }
}

private fun nfcStatusText(status: NfcWriter.Status?, active: Boolean): String? = when (status) {
    NfcWriter.Status.WAITING -> "Поднесите NFC-метку к телефону…"
    NfcWriter.Status.WRITTEN -> "Готово — метка записана."
    NfcWriter.Status.NOT_WRITABLE -> "Метка защищена от записи или слишком мала."
    NfcWriter.Status.FAILED -> "Не удалось записать. Поднесите метку ещё раз."
    NfcWriter.Status.NO_NFC -> "NFC недоступно."
    NfcWriter.Status.NFC_OFF -> "NFC выключено — включите его в настройках системы."
    null -> if (active) "Поднесите NFC-метку к телефону…" else null
}
