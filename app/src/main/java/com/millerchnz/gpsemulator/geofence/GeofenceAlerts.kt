package com.millerchnz.gpsemulator.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.millerchnz.gpsemulator.AppForegroundTracker
import com.millerchnz.gpsemulator.MainActivity
import com.millerchnz.gpsemulator.R
import com.millerchnz.gpsemulator.data.db.GeofenceTransition
import com.millerchnz.gpsemulator.setup.SetupRequirements
import java.util.concurrent.atomic.AtomicInteger

/**
 * Geofence transitions arrive in a broadcast receiver, which can fire while the app is not
 * visible. Text toasts posted from the background are unreliable on newer Android versions,
 * so an alert becomes a toast only when an activity is on screen and a notification otherwise.
 */
object GeofenceAlerts {
    private const val CHANNEL_ID = "geofence_alerts"
    private const val FIRST_NOTIFICATION_ID = 4_200
    private val nextId = AtomicInteger(FIRST_NOTIFICATION_ID)

    fun alert(
        context: Context,
        transition: GeofenceTransition,
        fenceName: String,
        detail: String,
    ) {
        val title = "${transition.label} $fenceName"
        if (AppForegroundTracker.isForeground) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "$title\n$detail", Toast.LENGTH_LONG).show()
            }
        } else {
            notify(context, title, detail)
        }
    }

    private fun notify(context: Context, title: String, detail: String) {
        if (!SetupRequirements.hasNotifications(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Geofence alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Posted when the device crosses one of your geofences" },
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        val id = nextId.updateAndGet { if (it > FIRST_NOTIFICATION_ID + 50) FIRST_NOTIFICATION_ID else it + 1 }
        manager.notify(id, notification)
    }
}
