package app.tidalshelf.scrobbler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Notificación de estado opcional (silenciosa, prioridad baja): confirma que
 * el scrobbler vive en segundo plano y muestra qué canción va sonando y si ya
 * cruzó el umbral de scrobble. Se apaga/enciende desde la pantalla principal.
 */
object StatusNotifier {

    private const val CHANNEL_ID = "status"
    private const val NOTIF_ID = 1

    private fun ensureChannel(ctx: Context) {
        val manager = ctx.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW, // sin sonido ni vibración
                ).apply {
                    description = ctx.getString(R.string.notif_channel_desc)
                    setShowBadge(false)
                }
            )
        }
    }

    fun update(ctx: Context, artist: String, title: String, playing: Boolean, scrobbled: Boolean) {
        if (!Prefs.statusNotifEnabled(ctx)) return
        ensureChannel(ctx)

        val stateIcon = if (playing) "▶" else "⏸"
        val text = if (scrobbled) {
            ctx.getString(R.string.notif_scrobbled)
        } else {
            ctx.getString(R.string.notif_waiting_threshold)
        }
        val tapIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("$stateIcon $artist — $title")
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notification)
        } catch (e: SecurityException) {
            // sin permiso POST_NOTIFICATIONS (Android 13+): se pide desde la UI
        }
    }

    fun cancel(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
    }
}
