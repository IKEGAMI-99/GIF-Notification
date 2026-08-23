package com.ikegami99.gifnotification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object GifNotificationManager {
    private const val CHANNEL_ID = "gif_notification"
    private const val CHANNEL_NAME = "GIF表示"
    private const val NOTIFICATION_ID = 1001

    suspend fun show(context: Context, item: GifItem) = withContext(Dispatchers.IO) {
        createChannel(context)
        val file = download(context, item)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        runCatching {
            context.grantUriPermission(
                "com.android.systemui",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val gifIcon = Icon.createWithContentUri(uri)
        val style = Notification.BigPictureStyle()
            .bigPicture(gifIcon)
            .showBigPictureWhenCollapsed(true)
            .setBigContentTitle(null)
            .setSummaryText(null)
            .setContentDescription("GIF")

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gif_notification)
            .setContentIntent(pendingIntent)
            .setStyle(style)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "選択したGIFを通知センターに表示します"
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun download(context: Context, item: GifItem): File {
        val dir = File(context.cacheDir, "gifs").apply { mkdirs() }
        val file = File(dir, "notification-${item.id}.gif")
        if (file.exists() && file.length() > 0) return file

        val connection = URL(item.originalUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GIF download failed: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        return file
    }
}
