package ru.admiral.praytimes.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import ru.admiral.praytimes.MainActivity
import ru.admiral.praytimes.PrayerWidgetProvider
import ru.admiral.praytimes.R
import ru.admiral.praytimes.settings.AppSettings

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val localized = AppSettings.localizedContext(context)
        if (!canPostNotifications(localized)) {
            return
        }

        val event = runCatching {
            PrayerNotificationEvent.valueOf(intent.getStringExtra(EXTRA_EVENT).orEmpty())
        }.getOrNull() ?: return
        val prayerLabel = intent.getStringExtra(EXTRA_PRAYER_LABEL).orEmpty()
        if (prayerLabel.isBlank()) {
            return
        }

        val playAdhan = intent.getBooleanExtra(EXTRA_PLAY_ADHAN, false)
        val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, false)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, prayerLabel.hashCode())
        PrayerNotificationChannels.ensure(localized)

        val channelId = if (event == PrayerNotificationEvent.START && playAdhan) {
            PrayerNotificationChannels.adhanChannelId(localized)
        } else {
            PrayerNotificationChannels.PRAYER_REMINDERS
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(localized, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(localized).apply {
                if (event == PrayerNotificationEvent.START && playAdhan) {
                    PrayerNotificationChannels.adhanUri(localized)?.let(::setSound)
                }
            }
        }

        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title(localized, event, prayerLabel))
            .setContentText(text(localized, event, prayerLabel))
            .setContentIntent(openAppIntent(localized))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .build()

        localized.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
        PrayerWidgetProvider.updateAll(localized)
        if (vibrate) {
            vibrate(localized)
        }
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun title(context: Context, event: PrayerNotificationEvent, prayerLabel: String): String = when (event) {
        PrayerNotificationEvent.BEFORE_START -> context.getString(R.string.notification_before_start_title, prayerLabel)
        PrayerNotificationEvent.START -> context.getString(R.string.notification_prayer_start_title, prayerLabel)
        PrayerNotificationEvent.BEFORE_END -> context.getString(R.string.notification_before_end_title, prayerLabel)
    }

    private fun text(context: Context, event: PrayerNotificationEvent, prayerLabel: String): String = when (event) {
        PrayerNotificationEvent.BEFORE_START -> context.getString(R.string.notification_before_start_text, prayerLabel)
        PrayerNotificationEvent.START -> context.getString(R.string.notification_prayer_start_text, prayerLabel)
        PrayerNotificationEvent.BEFORE_END -> context.getString(R.string.notification_before_end_text, prayerLabel)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATION_MS)
        }
    }

    companion object {
        const val ACTION_PRAYER_NOTIFICATION = "ru.admiral.praytimes.action.PRAYER_NOTIFICATION"
        const val EXTRA_EVENT = "ru.admiral.praytimes.extra.NOTIFICATION_EVENT"
        const val EXTRA_PRAYER_LABEL = "ru.admiral.praytimes.extra.PRAYER_LABEL"
        const val EXTRA_PLAY_ADHAN = "ru.admiral.praytimes.extra.PLAY_ADHAN"
        const val EXTRA_VIBRATE = "ru.admiral.praytimes.extra.VIBRATE"
        const val EXTRA_NOTIFICATION_ID = "ru.admiral.praytimes.extra.NOTIFICATION_ID"

        private const val OPEN_APP_REQUEST_CODE = 3001
        private const val VIBRATION_MS = 700L
    }
}
