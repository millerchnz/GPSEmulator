package com.millerchnz.gpsemulator.setup

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.millerchnz.gpsemulator.BuildConfig

data class SetupSnapshot(
    val hasFineLocation: Boolean,
    val hasNotifications: Boolean,
    val locationEnabled: Boolean,
    val developerOptionsEnabled: Boolean,
    val isMockLocationApp: Boolean,
) {
    val allReady: Boolean
        get() = hasFineLocation &&
            hasNotifications &&
            locationEnabled &&
            developerOptionsEnabled &&
            isMockLocationApp
}

object SetupRequirements {
    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun snapshot(context: Context): SetupSnapshot {
        return SetupSnapshot(
            hasFineLocation = hasFineLocation(context),
            hasNotifications = hasNotifications(context),
            locationEnabled = isLocationEnabled(context),
            developerOptionsEnabled = isDeveloperOptionsEnabled(context),
            isMockLocationApp = isSelectedAsMockLocationApp(context),
        )
    }

    fun hasFineLocation(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0,
        ) == 1
    }

    fun isSelectedAsMockLocationApp(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            Process.myUid(),
            BuildConfig.APPLICATION_ID,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
