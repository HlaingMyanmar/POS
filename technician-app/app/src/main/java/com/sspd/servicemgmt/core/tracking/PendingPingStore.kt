package com.sspd.servicemgmt.core.tracking

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.sspd.servicemgmt.core.network.LocationPingRequest

class PendingPingStore(context: Context) : SQLiteOpenHelper(context, "technician_tracking.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE pending_location_pings (
              client_ping_id TEXT PRIMARY KEY,
              visit_id INTEGER NOT NULL,
              latitude REAL NOT NULL,
              longitude REAL NOT NULL,
              accuracy REAL,
              recorded_at TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun enqueue(visitId: Long, ping: LocationPingRequest) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO pending_location_pings VALUES (?,?,?,?,?,?)",
            arrayOf(ping.clientPingId, visitId, ping.latitude, ping.longitude, ping.accuracy, ping.recordedAt)
        )
    }

    fun pending(visitId: Long): List<LocationPingRequest> {
        val rows = mutableListOf<LocationPingRequest>()
        readableDatabase.rawQuery(
            "SELECT client_ping_id, latitude, longitude, accuracy, recorded_at FROM pending_location_pings WHERE visit_id = ? ORDER BY recorded_at",
            arrayOf(visitId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += LocationPingRequest(
                    clientPingId = cursor.getString(0),
                    latitude = cursor.getDouble(1),
                    longitude = cursor.getDouble(2),
                    accuracy = if (cursor.isNull(3)) null else cursor.getDouble(3),
                    recordedAt = cursor.getString(4)
                )
            }
        }
        return rows
    }

    fun remove(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val placeholders = ids.joinToString(",") { "?" }
        writableDatabase.execSQL(
            "DELETE FROM pending_location_pings WHERE client_ping_id IN ($placeholders)",
            ids.toTypedArray()
        )
    }
}
