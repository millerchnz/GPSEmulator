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
import com.millerchnz.gpsemulator.setup.SetupRequirements
import com.millerchnz.gpsemulator.setup.SetupScreen
import com.millerchnz.gpsemulator.setup.rememberSetupSnapshot
import com.millerchnz.gpsemulator.ui.MapScreen
import com.millerchnz.gpsemulator.ui.theme.GpsEmulatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GpsEmulatorTheme {
                val context = LocalContext.current
                val snapshot = rememberSetupSnapshot()
                var extraTick by remember { mutableStateOf(0) }
                val current = remember(snapshot, extraTick) {
                    SetupRequirements.snapshot(context)
                }
                if (!current.allReady) {
                    SetupScreen(
                        snapshot = current,
                        onRefresh = { extraTick++ },
                    )
                } else {
                    MapScreen()
                }
            }
        }
    }
}
