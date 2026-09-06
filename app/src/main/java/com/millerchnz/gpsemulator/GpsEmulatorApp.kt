package com.millerchnz.gpsemulator

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class GpsEmulatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val cfg = Configuration.getInstance()
        cfg.userAgentValue = "GPSEmulator/${BuildConfig.VERSION_NAME} (dev mock location)"
        val base = File(cacheDir, "osmdroid")
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles")
    }
}
