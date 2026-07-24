package icu.nd4y.netswitcher.action

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import icu.nd4y.netswitcher.R
import icu.nd4y.netswitcher.data.ActionResult
import icu.nd4y.netswitcher.data.StartNotification
import icu.nd4y.netswitcher.ui.MainActivity

/**
 * Everything the user sees when a button is pressed.
 *
 * A press from the widget, a launcher shortcut or a Quick Settings tile can land on a
 * cold process, and the privileged commands themselves take a couple of seconds — so
 * the press is acknowledged the moment it arrives.
 *
 * Start: a silent progress notification, either quiet in the shade or as a heads-up
 * banner (the user's choice). On Android 16+ it is additionally promoted to a status
 * bar chip. Finish: the notification is dismissed and the outcome comes as a toast.
 */
object Feedback {

    // A channel's importance is fixed once created, so each behaviour needs its own.
    private const val CHANNEL_SHADE = "netswitcher_progress_shade"
    private const val CHANNEL_HEADS_UP = "netswitcher_progress_headsup"
    private const val NOTIFICATION_ID = 1001

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Android 16 (API 36) is where promoted ongoing notifications appeared. */
    val liveUpdatesSupported: Boolean
        get() = Build.VERSION.SDK_INT >= 36

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            channel(CHANNEL_SHADE, "Переключение сети", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            channel(
                CHANNEL_HEADS_UP,
                "Переключение сети (всплывающее)",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    /** Both channels are deliberately mute: the haptic tick is the only "alert". */
    private fun channel(id: String, name: String, importance: Int) =
        NotificationChannel(id, name, importance).apply {
            description = "Ход выполнения переключения сети"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }

    /** Called the instant a press is registered, before any slow work starts. */
    fun announceStart(context: Context, label: String, style: StartNotification) {
        val app = context.applicationContext
        haptic(app)

        val channelId =
            if (style == StartNotification.HEADS_UP) CHANNEL_HEADS_UP else CHANNEL_SHADE
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return

        val builder = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Переключаю: $label")
            .setContentText("Выполняю команды…")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                if (style == StartNotification.HEADS_UP) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_LOW
            )
            .setContentIntent(openApp(app))

        if (liveUpdatesSupported) {
            builder
                .setStyle(NotificationCompat.ProgressStyle().setProgressIndeterminate(true))
                .setShortCriticalText(label.take(12))
                .setRequestPromotedOngoing(true)
        } else {
            builder.setProgress(0, 0, true)
        }

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    /** The operation is over: drop the notification, report the outcome as a toast. */
    fun announceResult(context: Context, result: ActionResult) {
        val app = context.applicationContext
        runCatching { NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID) }
        toast(app, result.message)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun toast(context: Context, text: String) {
        mainHandler.post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    /** The fastest possible acknowledgement — lands before the process is even warm. */
    private fun haptic(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }
    }
}
