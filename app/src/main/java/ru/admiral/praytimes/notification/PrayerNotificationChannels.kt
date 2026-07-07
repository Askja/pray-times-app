package ru.admiral.praytimes.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.media.RingtoneManager
import ru.admiral.praytimes.R
import ru.admiral.praytimes.settings.AdhanSound
import ru.admiral.praytimes.settings.AppSettings

object PrayerNotificationChannels {
    const val PRAYER_REMINDERS = "prayer_reminders"
    const val PRAYER_ADHAN_BUILT_IN = "prayer_adhan_builtin"
    const val PRAYER_ADHAN_SYSTEM = "prayer_adhan_system"
    const val PRAYER_ADHAN_SILENT = "prayer_adhan_silent"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val soundAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val reminderChannel = NotificationChannel(
            PRAYER_REMINDERS,
            context.getString(R.string.notification_channel_prayers),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableVibration(true)
        }

        val adhanChannels = listOf(
            NotificationChannel(
                PRAYER_ADHAN_BUILT_IN,
                context.getString(R.string.notification_channel_adhan_built_in),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                setSound(builtInAdhanUri(context), soundAttributes)
            },
            NotificationChannel(
                PRAYER_ADHAN_SYSTEM,
                context.getString(R.string.notification_channel_adhan_system),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), soundAttributes)
            },
            NotificationChannel(
                PRAYER_ADHAN_SILENT,
                context.getString(R.string.notification_channel_adhan_silent),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                setSound(null, null)
            },
        )

        manager.createNotificationChannels(listOf(reminderChannel) + adhanChannels)
    }

    fun adhanChannelId(context: Context): String = when (AppSettings.adhanSound(context)) {
        AdhanSound.BUILT_IN -> PRAYER_ADHAN_BUILT_IN
        AdhanSound.SYSTEM -> PRAYER_ADHAN_SYSTEM
        AdhanSound.SILENT -> PRAYER_ADHAN_SILENT
    }

    fun adhanUri(context: Context): Uri? = when (AppSettings.adhanSound(context)) {
        AdhanSound.BUILT_IN -> builtInAdhanUri(context)
        AdhanSound.SYSTEM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        AdhanSound.SILENT -> null
    }

    private fun builtInAdhanUri(context: Context): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.adhan}")
}
