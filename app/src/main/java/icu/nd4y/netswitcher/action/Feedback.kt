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
import icu.nd4y.netswitcher.ui.MainActivity

/**
 * Everything the user sees when a button is pressed.
 *
 * A press from the widget, a launcher shortcut or a Quick Settings tile can land on a
 * cold process, and the privileged commands themselves take a couple of seconds — so
 * the press is acknowledged immediately (haptic + toast + ongoing notification) and
 * the same notification is then rewritten with the outcome.
 */
object Feedback {

    private const val CHANNEL_ID = "netswitcher_actions"
    private const val NOTIFICATION_ID = 1001

    private val mainHandler = Handler(Looper.getMainLooper())

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Переключение сети",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ход выполнения переключения сети"
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** Called the instant a press is registered, before any slow work starts. */
    fun announceStart(context: Context, label: String, withToast: Boolean) {
        val app = context.applicationContext
        haptic(app)
        if (withToast) toast(app, "Переключаю: $label")
        notify(app) {
            setContentTitle("Переключаю: $label")
            setContentText("Выполняю команды…")
            setProgress(0, 0, true)
            setOngoing(true)
        }
    }

    fun announceResult(context: Context, result: ActionResult, withToast: Boolean) {
        val app = context.applicationContext
        if (withToast) toast(app, result.message)
        notify(app) {
            setContentTitle(if (result.success) "Готово" else "Не получилось")
            setContentText(result.message)
            setStyle(NotificationCompat.BigTextStyle().bigText(result.message))
            setOngoing(false)
            setAutoCancel(true)
            setTimeoutAfter(if (result.success) 6_000 else 20_000)
        }
    }

    private fun notify(context: Context, block: NotificationCompat.Builder.() -> Unit) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .apply(block)

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

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
