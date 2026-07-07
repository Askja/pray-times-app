package ru.admiral.praytimes.data

import android.content.Context
import org.json.JSONObject
import ru.admiral.praytimes.BuildConfig
import ru.admiral.praytimes.settings.AppSettings

object AppBackup {
    fun export(context: Context, database: LocationDatabase): String =
        JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("versionName", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("createdAt", System.currentTimeMillis())
            .put("settings", AppSettings.exportPreferences(context))
            .put("locations", database.exportLocations())
            .toString(2)

    fun import(context: Context, database: LocationDatabase, json: String): Boolean {
        val root = JSONObject(json)
        val settings = root.optJSONObject("settings") ?: return false
        val locations = root.optJSONArray("locations") ?: return false
        return database.importLocations(locations) && AppSettings.importPreferences(context, settings)
    }

    const val FILE_NAME = "pray-times-backup.json"

    private const val SCHEMA_VERSION = 1
}
