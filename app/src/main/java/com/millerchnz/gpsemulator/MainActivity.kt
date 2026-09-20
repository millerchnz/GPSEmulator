package com.millerchnz.gpsemulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.millerchnz.gpsemulator.logging.LocationLoggingService
import com.millerchnz.gpsemulator.setup.SetupRequirements
import com.millerchnz.gpsemulator.setup.SetupScreen
import com.millerchnz.gpsemulator.setup.rememberSetupSnapshot
import com.millerchnz.gpsemulator.ui.nav.AppNavHost
import com.millerchnz.gpsemulator.ui.theme.GpsDebugToolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Picks logging back up if the setting is on but the process was killed.
        LocationLoggingService.sync(this)
        setContent {
            GpsDebugToolTheme {
                val context = LocalContext.current
                val snapshot = rememberSetupSnapshot()
                var extraTick by remember { mutableStateOf(0) }
                val current = remember(snapshot, extraTick) {
                    SetupRequirements.snapshot(context)
                }
                // Only the checks needed to read the device's own GPS gate the app; the extra
                // mock-location requirements are surfaced on the mock screen itself.
                if (!current.coreReady) {
                    SetupScreen(
                        snapshot = current,
                        onRefresh = { extraTick++ },
                    )
                } else {
                    AppNavHost(snapshot = current)
                }
            }
        }
    }
}
