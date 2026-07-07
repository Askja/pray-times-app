package ru.admiral.praytimes.data

import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.domain.GeoDistance
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONObject

class MosqueSearchService {
    fun cityMosques(
        coordinates: Coordinates,
        locale: Locale,
        defaultName: String = DEFAULT_MOSQUE_NAME,
        locationLabel: String = "",
    ): List<Mosque> {
        val cityAreaId = runCatching {
            resolveCityAreaId(coordinates, locale, locationLabel)
        }.getOrNull()
        if (cityAreaId != null) {
            val cityMosques = runCatching {
                readMosquesAcrossEndpoints(cityMosqueQuery(cityAreaId), coordinates, locale, defaultName)
            }.getOrDefault(emptyList())
            if (cityMosques.isNotEmpty()) {
                return cityMosques
            }
        }

        return fallbackByRadius(
            coordinates = coordinates,
            locale = locale,
            radiusMeters = FALLBACK_RADIUS_METERS,
            defaultName = defaultName,
        )
    }

    private fun resolveCityAreaId(coordinates: Coordinates, locale: Locale, locationLabel: String): Long? {
        val query = """
            [out:json][timeout:12];
            is_in(${coordinates.latitude},${coordinates.longitude})->.containingAreas;
            (
              area.containingAreas[boundary="administrative"][admin_level~"^(4|5|6|7|8|9|10)$"];
              area.containingAreas[place~"^(city|town|municipality|village|borough|suburb)$"];
            );
            out tags 40;
        """.trimIndent()
        val root = JSONObject(readJson(query, locale))
        val elements = root.optJSONArray("elements") ?: return null
        val candidates = buildList {
            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                add(areaCandidate(element, locale) ?: continue)
            }
        }
        return MosqueAreaSelector.select(candidates, locationLabel)?.id
    }

    private fun areaCandidate(element: JSONObject, locale: Locale): MosqueAreaCandidate? {
        val id = element.optLong("id").takeIf { it > 0 } ?: return null
        val tags = element.optJSONObject("tags") ?: return null
        val language = locale.language.takeIf(String::isNotBlank)
        return MosqueAreaCandidate(
            id = id,
            names = listOfNotNull(
                language?.let { tags.optString("name:$it") },
                tags.optString("name"),
                tags.optString("name:en"),
            ).filter(String::isNotBlank).distinct(),
            adminLevel = tags.optString("admin_level").toIntOrNull(),
            place = tags.optString("place").lowercase(Locale.ROOT),
            borderType = tags.optString("border_type").lowercase(Locale.ROOT),
        )
    }

    private fun cityMosqueQuery(cityAreaId: Long): String =
        """
        [out:json][timeout:20];
        area($cityAreaId)->.cityArea;
        (
          nwr(area.cityArea)["amenity"="place_of_worship"]["religion"="muslim"];
          nwr(area.cityArea)["building"="mosque"];
        );
        out center $MAX_RESULTS;
        """.trimIndent()

    private fun fallbackByRadius(
        coordinates: Coordinates,
        locale: Locale,
        radiusMeters: Int,
        defaultName: String,
    ): List<Mosque> {
        val query = """
            [out:json][timeout:12];
            (
              nwr["amenity"="place_of_worship"]["religion"="muslim"](around:$radiusMeters,${coordinates.latitude},${coordinates.longitude});
              nwr["building"="mosque"](around:$radiusMeters,${coordinates.latitude},${coordinates.longitude});
            );
            out center $MAX_RESULTS;
        """.trimIndent()
        return readMosquesAcrossEndpoints(query, coordinates, locale, defaultName)
    }

    private fun readMosquesAcrossEndpoints(
        query: String,
        origin: Coordinates,
        locale: Locale,
        defaultName: String,
    ): List<Mosque> {
        var lastError: IOException? = null
        var sawEmptyResponse = false
        endpoints().forEach { endpoint ->
            try {
                val mosques = parseMosques(readJson(endpoint, query, locale), origin, locale, defaultName)
                if (mosques.isNotEmpty()) {
                    preferredEndpoint = endpoint
                    return mosques
                }
                sawEmptyResponse = true
            } catch (error: IOException) {
                lastError = error
            }
        }
        if (sawEmptyResponse) {
            return emptyList()
        }
        throw lastError ?: IOException("Overpass API is unavailable")
    }

    private fun parseMosques(
        json: String,
        origin: Coordinates,
        locale: Locale,
        defaultName: String,
    ): List<Mosque> =
        parseMosques(JSONObject(json), origin, locale, defaultName)

    private fun parseMosques(
        root: JSONObject,
        origin: Coordinates,
        locale: Locale,
        defaultName: String,
    ): List<Mosque> {
        val elements = root.optJSONArray("elements") ?: return emptyList()

        return buildList {
            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                val tags = element.optJSONObject("tags") ?: JSONObject()
                val itemCoordinates = coordinatesFor(element) ?: continue
                val name = name(tags, locale, defaultName)
                add(
                    Mosque(
                        id = "${element.optString("type")}:${element.optLong("id")}",
                        name = name,
                        address = address(tags),
                        coordinates = itemCoordinates,
                        distanceMeters = GeoDistance.meters(origin, itemCoordinates),
                    ),
                )
            }
        }
            .distinctBy(Mosque::id)
            .sortedBy(Mosque::distanceMeters)
            .take(MAX_RESULTS)
    }

    private fun readJson(query: String, locale: Locale): String {
        var lastError: IOException? = null
        endpoints().forEach { endpoint ->
            try {
                return readJson(endpoint, query, locale).also {
                    preferredEndpoint = endpoint
                }
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException("Overpass API is unavailable")
    }

    private fun readJson(endpoint: String, query: String, locale: Locale): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        val body = "data=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}"
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", locale.toLanguageTag())
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode !in HTTP_OK until HTTP_MULTIPLE_CHOICES) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Overpass API returned HTTP $responseCode: $message")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun coordinatesFor(element: JSONObject): Coordinates? {
        if (element.has("lat") && element.has("lon")) {
            return Coordinates(element.getDouble("lat"), element.getDouble("lon"))
        }
        val center = element.optJSONObject("center") ?: return null
        return Coordinates(center.getDouble("lat"), center.getDouble("lon"))
    }

    private fun name(tags: JSONObject, locale: Locale, defaultName: String): String {
        val localizedName = locale.language
            .takeIf(String::isNotBlank)
            ?.let { language -> tags.optString("name:$language") }
            .orEmpty()
        return localizedName
            .ifBlank { tags.optString("name") }
            .ifBlank { tags.optString("name:en") }
            .ifBlank { defaultName }
    }

    private fun address(tags: JSONObject): String {
        val street = tags.optString("addr:street")
        val houseNumber = tags.optString("addr:housenumber")
        val city = tags.optString("addr:city")
        val district = tags.optString("addr:district").ifBlank { tags.optString("addr:suburb") }
        val fullAddress = tags.optString("addr:full")
        val assembled = listOf(
            listOf(street, houseNumber).filter(String::isNotBlank).joinToString(" "),
            district,
            city,
        ).filter(String::isNotBlank).distinct().joinToString(", ")
        return fullAddress.ifBlank { assembled }
    }

    private fun endpoints(): List<String> =
        (listOfNotNull(preferredEndpoint) + OVERPASS_ENDPOINTS).distinct()

    private companion object {
        val OVERPASS_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.osm.ch/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
        )
        const val FALLBACK_RADIUS_METERS = 50_000
        const val MAX_RESULTS = 200
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 10_000
        const val HTTP_OK = 200
        const val HTTP_MULTIPLE_CHOICES = 300
        const val DEFAULT_MOSQUE_NAME = "Mosque"
        const val USER_AGENT = "PrayTimesApp/0.1.1 Android mosque-search"
        var preferredEndpoint: String? = null
    }
}
