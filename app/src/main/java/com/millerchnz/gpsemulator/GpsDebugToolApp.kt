package com.millerchnz.gpsemulator

import android.app.Application
import com.millerchnz.gpsemulator.data.AppGraph
import org.osmdroid.config.Configuration
import java.io.File

class GpsDebugToolApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val cfg = Configuration.getInstance()
        cfg.userAgentValue = "GPSDebugTool/${BuildConfig.VERSION_NAME} (location debug)"
        val base = File(cacheDir, "osmdroid")
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles")

        AppGraph.init(this)
        AppForegroundTracker.register(this)
    }
}
