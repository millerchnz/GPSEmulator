package com.millerchnz.gpsemulator.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LocationLogEntity::class,
        GeofenceEntity::class,
        GeofenceEventEntity::class,
        WebhookQueueEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class GpsLogDatabase : RoomDatabase() {
    abstract fun locationLogs(): LocationLogDao

    abstract fun geofences(): GeofenceDao

    abstract fun geofenceEvents(): GeofenceEventDao

    abstract fun webhookQueue(): WebhookQueueDao

    companion object {
        private const val NAME = "gps_explorer.db"

        fun build(context: Context): GpsLogDatabase {
            return Room.databaseBuilder(context.applicationContext, GpsLogDatabase::class.java, NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
