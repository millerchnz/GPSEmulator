package com.millerchnz.gpsemulator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.millerchnz.gpsemulator.data.AppGraph
import com.millerchnz.gpsemulator.logging.LocationLoggingService

/**
 * Starts the process after reboot or an app update so logging can resume. Geofence
 * re-registration happens from [AppGraph.init].
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                val app = context.applicationContext
                AppGraph.init(app)
                LocationLoggingService.sync(app)
            }
        }
    }
}
