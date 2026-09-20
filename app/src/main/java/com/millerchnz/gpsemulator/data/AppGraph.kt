package com.millerchnz.gpsemulator.data

import android.annotation.SuppressLint
import android.content.Context
import com.millerchnz.gpsemulator.data.db.GpsLogDatabase
import com.millerchnz.gpsemulator.data.net.WebhookClient
import com.millerchnz.gpsemulator.data.net.WebhookDispatcher
import com.millerchnz.gpsemulator.data.settings.SettingsStore
import com.millerchnz.gpsemulator.geofence.GeofenceRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-rolled service locator. The app is small enough that a DI framework would be more
 * machinery than it earns, but services, broadcast receivers and view models all need the
 * same database and settings, so they share these.
 *
 * [init] runs from Application.onCreate and is idempotent, so a receiver woken in a fresh
 * process can safely call it before touching anything else.
 */
@SuppressLint("StaticFieldLeak") // Everything below is built from the application context.
object AppGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var initialized = false

    lateinit var database: GpsLogDatabase
        private set

    lateinit var settings: SettingsStore
        private set

    lateinit var webhooks: WebhookDispatcher
        private set

    lateinit var geofences: GeofenceRegistrar
        private set

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        database = GpsLogDatabase.build(app)
        settings = SettingsStore(app)
        webhooks = WebhookDispatcher(app, database.webhookQueue(), WebhookClient(), scope)
        geofences = GeofenceRegistrar(app, settings, database.geofences())
        initialized = true

        webhooks.watchConnectivity()
        scope.launch {
            webhooks.drain()
            // Registrations do not survive a reboot or a Play Services update, so re-assert them.
            if (settings.geofence.value.enabled) geofences.sync()
        }
    }
}
