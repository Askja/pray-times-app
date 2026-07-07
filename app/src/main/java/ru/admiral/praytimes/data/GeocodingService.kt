package ru.admiral.praytimes.data

import ru.admiral.praytimes.BuildConfig
import ru.admiral.praytimes.adhan.Coordinates
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class GeocodingService {
    fun search(query: String, locale: Locale): GeocodedLocation? {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            return null
        }

        return providers
            .asSequence()
            .filter { it.isEnabled }
            .mapNotNull { provider -> runCatching { provider.search(normalized, locale) }.getOrNull() }
            .firstOrNull()
    }

    companion object {
        val providers: List<GeocodingProvider> = listOf(
            NominatimProvider,
            PhotonProvider,
            YandexProvider(BuildConfig.YANDEX_GEOCODER_API_KEY),
            TwoGisProvider(BuildConfig.TWOGIS_API_KEY),
            OpenCageProvider(BuildConfig.OPENCAGE_API_KEY),
        )
    }
}

sealed class GeocodingProvider(
    val title: String,
    val endpoint: String,
    protected val apiKey: String = "",
) {
    val isEnabled: Boolean
        get() = apiKey.isNotBlank() || !requiresKey

    protected open val requiresKey: Boolean = false

    abstract fun search(query: String, locale: Locale): GeocodedLocation?

    protected fun encoded(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    protected fun readJson(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val responseCode = connection.responseCode
            if (responseCode !in HTTP_OK until HTTP_MULTIPLE_CHOICES) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("$title returned HTTP $responseCode: $message")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    protected fun zoneByCountryOrCoordinates(countryCode: String?, coordinates: Coordinates): ZoneId =
        TimeZoneResolver.byCountryOrCoordinates(countryCode, coordinates)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 8_000
        const val HTTP_OK = 200
        const val HTTP_MULTIPLE_CHOICES = 300
        const val USER_AGENT = "PrayTimesApp/0.1 Android geocoder"
    }
}

private object NominatimProvider : GeocodingProvider(
    title = "OpenStreetMap Nominatim",
    endpoint = "https://nominatim.openstreetmap.org/search",
) {
    override fun search(query: String, locale: Locale): GeocodedLocation? {
        val url = "$endpoint?format=jsonv2&addressdetails=1&limit=1&accept-language=" +
            "${encoded(locale.toLanguageTag())}&q=${encoded(query)}"
        val item = JSONArray(readJson(url)).optJSONObject(0) ?: return null
        val coordinates = Coordinates(item.getString("lat").toDouble(), item.getString("lon").toDouble())
        return GeocodedLocation(
            name = item.optString("display_name", query),
            coordinates = coordinates,
            zoneId = zoneByCountryOrCoordinates(
                item.optJSONObject("address")?.optString("country_code"),
                coordinates,
            ),
            provider = title,
        )
    }
}

private object PhotonProvider : GeocodingProvider(
    title = "Komoot Photon",
    endpoint = "https://photon.komoot.io/api",
) {
    override fun search(query: String, locale: Locale): GeocodedLocation? {
        val url = "$endpoint/?q=${encoded(query)}&limit=1&lang=${encoded(locale.language)}"
        val feature = JSONObject(readJson(url)).optJSONArray("features")?.optJSONObject(0) ?: return null
        val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
        val properties = feature.getJSONObject("properties")
        val label = listOf(
            properties.optString("name"),
            properties.optString("city"),
            properties.optString("country"),
        ).filter { it.isNotBlank() }.distinct().joinToString(", ")

        return GeocodedLocation(
            name = label.ifBlank { query },
            coordinates = Coordinates(coordinates.getDouble(1), coordinates.getDouble(0)),
            zoneId = zoneByCountryOrCoordinates(
                properties.optString("countrycode"),
                Coordinates(coordinates.getDouble(1), coordinates.getDouble(0)),
            ),
            provider = title,
        )
    }
}

private class YandexProvider(apiKey: String) : GeocodingProvider(
    title = "Yandex Geocoder",
    endpoint = "https://geocode-maps.yandex.ru/v1/",
    apiKey = apiKey,
) {
    override val requiresKey: Boolean = true

    override fun search(query: String, locale: Locale): GeocodedLocation? {
        val url = "$endpoint?apikey=${encoded(apiKey)}&format=json&results=1&lang=${encoded(locale.toLanguageTag())}" +
            "&geocode=${encoded(query)}"
        val members = JSONObject(readJson(url))
            .getJSONObject("response")
            .getJSONObject("GeoObjectCollection")
            .optJSONArray("featureMember")
        val geoObject = members?.optJSONObject(0)?.optJSONObject("GeoObject") ?: return null
        val point = geoObject.getJSONObject("Point").getString("pos").split(' ')
        val coordinates = Coordinates(point[1].toDouble(), point[0].toDouble())
        val text = geoObject
            .optJSONObject("metaDataProperty")
            ?.optJSONObject("GeocoderMetaData")
            ?.optString("text")
            .orEmpty()

        return GeocodedLocation(
            name = text.ifBlank { query },
            coordinates = coordinates,
            zoneId = zoneByCountryOrCoordinates(null, coordinates),
            provider = title,
        )
    }
}

private class TwoGisProvider(apiKey: String) : GeocodingProvider(
    title = "2GIS Geocoder",
    endpoint = "https://catalog.api.2gis.com/3.0/items/geocode",
    apiKey = apiKey,
) {
    override val requiresKey: Boolean = true

    override fun search(query: String, locale: Locale): GeocodedLocation? {
        val url = "$endpoint?q=${encoded(query)}&fields=items.point&locale=${encoded(locale.toLanguageTag())}" +
            "&key=${encoded(apiKey)}"
        val item = JSONObject(readJson(url)).getJSONObject("result").optJSONArray("items")?.optJSONObject(0)
            ?: return null
        val point = item.getJSONObject("point")
        val coordinates = Coordinates(point.getDouble("lat"), point.getDouble("lon"))

        return GeocodedLocation(
            name = item.optString("full_name", item.optString("name", query)),
            coordinates = coordinates,
            zoneId = zoneByCountryOrCoordinates(null, coordinates),
            provider = title,
        )
    }
}

private class OpenCageProvider(apiKey: String) : GeocodingProvider(
    title = "OpenCage",
    endpoint = "https://api.opencagedata.com/geocode/v1/json",
    apiKey = apiKey,
) {
    override val requiresKey: Boolean = true

    override fun search(query: String, locale: Locale): GeocodedLocation? {
        val url = "$endpoint?q=${encoded(query)}&limit=1&no_annotations=0&language=${encoded(locale.language)}" +
            "&key=${encoded(apiKey)}"
        val result = JSONObject(readJson(url)).optJSONArray("results")?.optJSONObject(0) ?: return null
        val geometry = result.getJSONObject("geometry")
        val coordinates = Coordinates(geometry.getDouble("lat"), geometry.getDouble("lng"))
        val timeZone = result
            .optJSONObject("annotations")
            ?.optJSONObject("timezone")
            ?.optString("name")

        return GeocodedLocation(
            name = result.optString("formatted", query),
            coordinates = coordinates,
            zoneId = timeZone
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: zoneByCountryOrCoordinates(null, coordinates),
            provider = title,
        )
    }
}
