package ru.admiral.praytimes.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ru.admiral.praytimes.adhan.PrayerAdjustments
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

class LocationDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        createTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureSchema(db)
        runCatching {
            db.delete("saved_locations", "source = ?", arrayOf("seed"))
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            ensureSchema(db)
        }
    }

    private fun createTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_locations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                time_zone TEXT NOT NULL,
                source TEXT NOT NULL,
                color_key TEXT NOT NULL DEFAULT '$DEFAULT_COLOR_KEY',
                icon_key TEXT NOT NULL DEFAULT '$DEFAULT_ICON_KEY',
                note TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS location_prayer_adjustments (
                location_id INTEGER PRIMARY KEY,
                fajr INTEGER NOT NULL DEFAULT 0,
                sunrise INTEGER NOT NULL DEFAULT 0,
                dhuhr INTEGER NOT NULL DEFAULT 0,
                asr INTEGER NOT NULL DEFAULT 0,
                maghrib INTEGER NOT NULL DEFAULT 0,
                isha INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS location_iqama_offsets (
                location_id INTEGER PRIMARY KEY,
                fajr INTEGER NOT NULL DEFAULT 0,
                dhuhr INTEGER NOT NULL DEFAULT 0,
                asr INTEGER NOT NULL DEFAULT 0,
                maghrib INTEGER NOT NULL DEFAULT 0,
                isha INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    private fun ensureSchema(db: SQLiteDatabase) {
        createTable(db)
        val columns = tableColumns(db)
        if ("time_zone" !in columns) {
            db.execSQL("ALTER TABLE saved_locations ADD COLUMN time_zone TEXT NOT NULL DEFAULT '$DEFAULT_TIME_ZONE'")
        }
        if ("source" !in columns) {
            db.execSQL("ALTER TABLE saved_locations ADD COLUMN source TEXT NOT NULL DEFAULT '$DEFAULT_SOURCE'")
        }
        if ("created_at" !in columns) {
            db.execSQL("ALTER TABLE saved_locations ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE saved_locations SET created_at = id WHERE created_at = 0")
        }
        if ("color_key" !in columns) {
            db.execSQL("ALTER TABLE saved_locations ADD COLUMN color_key TEXT NOT NULL DEFAULT '$DEFAULT_COLOR_KEY'")
        }
        if ("icon_key" !in columns) {
            db.execSQL("ALTER TABLE saved_locations ADD COLUMN icon_key TEXT NOT NULL DEFAULT '$DEFAULT_ICON_KEY'")
        }
        if ("note" !in columns) {
            db.execSQL("ALTER TABLE saved_locations ADD COLUMN note TEXT NOT NULL DEFAULT ''")
        }
    }

    private fun tableColumns(db: SQLiteDatabase): Set<String> =
        db.rawQuery("PRAGMA table_info(saved_locations)", null).use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameIndex))
                }
            }
        }

    fun listLocations(): List<SavedLocation> =
        readableDatabase.query(
            "saved_locations",
            LOCATION_COLUMNS,
            null,
            null,
            null,
            null,
            "created_at ASC, name ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SavedLocation(
                            id = cursor.getLong(0),
                            name = cursor.getString(1),
                            latitude = cursor.getDouble(2),
                            longitude = cursor.getDouble(3),
                            timeZone = cursor.getString(4),
                            source = cursor.getString(5),
                            colorKey = LocationVisual.safeColorKey(cursor.getString(6)),
                            iconKey = LocationVisual.safeIconKey(cursor.getString(7)),
                            note = cursor.getString(8),
                        ),
                    )
                }
            }
        }

    fun locationById(id: Long): SavedLocation? =
        readableDatabase.query(
            "saved_locations",
            LOCATION_COLUMNS,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                SavedLocation(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    latitude = cursor.getDouble(2),
                    longitude = cursor.getDouble(3),
                    timeZone = cursor.getString(4),
                    source = cursor.getString(5),
                    colorKey = LocationVisual.safeColorKey(cursor.getString(6)),
                    iconKey = LocationVisual.safeIconKey(cursor.getString(7)),
                    note = cursor.getString(8),
                )
            } else {
                null
            }
        }

    fun saveLocation(
        name: String,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId,
        source: String,
        colorKey: String = LocationVisual.DEFAULT_COLOR_KEY,
        iconKey: String = LocationVisual.DEFAULT_ICON_KEY,
        note: String = "",
    ): Long? = insertLocation(writableDatabase, name, latitude, longitude, zoneId.id, source, colorKey, iconKey, note)
        .takeIf { it != INSERT_FAILED }

    fun updateLocation(
        id: Long,
        name: String,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId,
        source: String,
        colorKey: String,
        iconKey: String,
        note: String,
    ): Boolean {
        val values = ContentValues().apply {
            put("name", name)
            put("latitude", latitude)
            put("longitude", longitude)
            put("time_zone", zoneId.id)
            put("source", source)
            put("color_key", LocationVisual.safeColorKey(colorKey))
            put("icon_key", LocationVisual.safeIconKey(iconKey))
            put("note", note)
        }
        return writableDatabase.update("saved_locations", values, "id = ?", arrayOf(id.toString())) > 0
    }

    fun deleteLocation(id: Long): Boolean =
        writableDatabase.run {
            delete("location_prayer_adjustments", "location_id = ?", arrayOf(id.toString()))
            delete("location_iqama_offsets", "location_id = ?", arrayOf(id.toString()))
            delete("saved_locations", "id = ?", arrayOf(id.toString())) > 0
        }

    fun prayerAdjustments(locationId: Long): PrayerAdjustments? =
        readableDatabase.query(
            "location_prayer_adjustments",
            arrayOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"),
            "location_id = ?",
            arrayOf(locationId.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                PrayerAdjustments(
                    fajr = cursor.getInt(0),
                    sunrise = cursor.getInt(1),
                    dhuhr = cursor.getInt(2),
                    asr = cursor.getInt(3),
                    maghrib = cursor.getInt(4),
                    isha = cursor.getInt(5),
                )
            } else {
                null
            }
        }

    fun savePrayerAdjustments(locationId: Long, adjustments: PrayerAdjustments): Boolean {
        val values = ContentValues().apply {
            put("location_id", locationId)
            put("fajr", adjustments.fajr)
            put("sunrise", adjustments.sunrise)
            put("dhuhr", adjustments.dhuhr)
            put("asr", adjustments.asr)
            put("maghrib", adjustments.maghrib)
            put("isha", adjustments.isha)
        }
        return writableDatabase.replace("location_prayer_adjustments", null, values) != INSERT_FAILED
    }

    fun iqamaOffsets(locationId: Long): PrayerAdjustments? =
        readableDatabase.query(
            "location_iqama_offsets",
            arrayOf("fajr", "dhuhr", "asr", "maghrib", "isha"),
            "location_id = ?",
            arrayOf(locationId.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                PrayerAdjustments(
                    fajr = cursor.getInt(0),
                    dhuhr = cursor.getInt(1),
                    asr = cursor.getInt(2),
                    maghrib = cursor.getInt(3),
                    isha = cursor.getInt(4),
                )
            } else {
                null
            }
        }

    fun saveIqamaOffsets(locationId: Long, offsets: PrayerAdjustments): Boolean {
        val values = ContentValues().apply {
            put("location_id", locationId)
            put("fajr", offsets.fajr.coerceAtLeast(0))
            put("dhuhr", offsets.dhuhr.coerceAtLeast(0))
            put("asr", offsets.asr.coerceAtLeast(0))
            put("maghrib", offsets.maghrib.coerceAtLeast(0))
            put("isha", offsets.isha.coerceAtLeast(0))
        }
        return writableDatabase.replace("location_iqama_offsets", null, values) != INSERT_FAILED
    }

    fun exportLocations(): JSONArray =
        JSONArray().also { output ->
            listLocations().forEach { location ->
                val item = JSONObject()
                    .put("id", location.id)
                    .put("name", location.name)
                    .put("latitude", location.latitude)
                    .put("longitude", location.longitude)
                    .put("timeZone", location.timeZone)
                    .put("source", location.source)
                    .put("colorKey", location.colorKey)
                    .put("iconKey", location.iconKey)
                    .put("note", location.note)
                prayerAdjustments(location.id)?.let { item.put("prayerAdjustments", it.toJson(includeSunrise = true)) }
                iqamaOffsets(location.id)?.let { item.put("iqamaOffsets", it.toJson(includeSunrise = false)) }
                output.put(item)
            }
        }

    fun importLocations(items: JSONArray): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return runCatching {
            db.delete("location_iqama_offsets", null, null)
            db.delete("location_prayer_adjustments", null, null)
            db.delete("saved_locations", null, null)
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val id = item.optLong("id").takeIf { it > 0 }
                val locationId = insertLocation(
                    db = db,
                    id = id,
                    name = item.getString("name"),
                    latitude = item.getDouble("latitude"),
                    longitude = item.getDouble("longitude"),
                    timeZone = item.getString("timeZone"),
                    source = item.optString("source", DEFAULT_SOURCE),
                    colorKey = item.optString("colorKey", DEFAULT_COLOR_KEY),
                    iconKey = item.optString("iconKey", DEFAULT_ICON_KEY),
                    note = item.optString("note", ""),
                )
                if (locationId == INSERT_FAILED) {
                    error("Location import failed")
                }
                item.optJSONObject("prayerAdjustments")?.let { adjustments ->
                    replacePrayerAdjustments(db, locationId, adjustments.toPrayerAdjustments(includeSunrise = true))
                }
                item.optJSONObject("iqamaOffsets")?.let { offsets ->
                    replaceIqamaOffsets(db, locationId, offsets.toPrayerAdjustments(includeSunrise = false))
                }
            }
            db.setTransactionSuccessful()
            true
        }.getOrDefault(false).also {
            db.endTransaction()
        }
    }

    private fun insertLocation(
        db: SQLiteDatabase,
        name: String,
        latitude: Double,
        longitude: Double,
        timeZone: String,
        source: String,
        colorKey: String,
        iconKey: String,
        note: String,
    ): Long =
        insertLocation(db, null, name, latitude, longitude, timeZone, source, colorKey, iconKey, note)

    private fun insertLocation(
        db: SQLiteDatabase,
        id: Long?,
        name: String,
        latitude: Double,
        longitude: Double,
        timeZone: String,
        source: String,
        colorKey: String,
        iconKey: String,
        note: String,
    ): Long {
        val values = ContentValues().apply {
            id?.let { put("id", it) }
            put("name", name)
            put("latitude", latitude)
            put("longitude", longitude)
            put("time_zone", timeZone)
            put("source", source)
            put("color_key", LocationVisual.safeColorKey(colorKey))
            put("icon_key", LocationVisual.safeIconKey(iconKey))
            put("note", note)
            put("created_at", System.currentTimeMillis())
        }

        return db.insert("saved_locations", null, values)
    }

    private fun replacePrayerAdjustments(db: SQLiteDatabase, locationId: Long, adjustments: PrayerAdjustments) {
        val values = ContentValues().apply {
            put("location_id", locationId)
            put("fajr", adjustments.fajr)
            put("sunrise", adjustments.sunrise)
            put("dhuhr", adjustments.dhuhr)
            put("asr", adjustments.asr)
            put("maghrib", adjustments.maghrib)
            put("isha", adjustments.isha)
        }
        db.replace("location_prayer_adjustments", null, values)
    }

    private fun replaceIqamaOffsets(db: SQLiteDatabase, locationId: Long, offsets: PrayerAdjustments) {
        val values = ContentValues().apply {
            put("location_id", locationId)
            put("fajr", offsets.fajr.coerceAtLeast(0))
            put("dhuhr", offsets.dhuhr.coerceAtLeast(0))
            put("asr", offsets.asr.coerceAtLeast(0))
            put("maghrib", offsets.maghrib.coerceAtLeast(0))
            put("isha", offsets.isha.coerceAtLeast(0))
        }
        db.replace("location_iqama_offsets", null, values)
    }

    private fun PrayerAdjustments.toJson(includeSunrise: Boolean): JSONObject =
        JSONObject()
            .put("fajr", fajr)
            .also { if (includeSunrise) it.put("sunrise", sunrise) }
            .put("dhuhr", dhuhr)
            .put("asr", asr)
            .put("maghrib", maghrib)
            .put("isha", isha)

    private fun JSONObject.toPrayerAdjustments(includeSunrise: Boolean): PrayerAdjustments =
        PrayerAdjustments(
            fajr = optInt("fajr", 0),
            sunrise = if (includeSunrise) optInt("sunrise", 0) else 0,
            dhuhr = optInt("dhuhr", 0),
            asr = optInt("asr", 0),
            maghrib = optInt("maghrib", 0),
            isha = optInt("isha", 0),
        )

    companion object {
        private const val DATABASE_NAME = "pray_times.db"
        private const val DATABASE_VERSION = 6
        private const val DEFAULT_TIME_ZONE = "Europe/Istanbul"
        private const val DEFAULT_SOURCE = "manual"
        private const val DEFAULT_COLOR_KEY = LocationVisual.DEFAULT_COLOR_KEY
        private const val DEFAULT_ICON_KEY = LocationVisual.DEFAULT_ICON_KEY
        private const val INSERT_FAILED = -1L
        private val LOCATION_COLUMNS = arrayOf(
            "id",
            "name",
            "latitude",
            "longitude",
            "time_zone",
            "source",
            "color_key",
            "icon_key",
            "note",
        )
    }
}
