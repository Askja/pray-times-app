package ru.admiral.praytimes.data

import ru.admiral.praytimes.adhan.Coordinates
import java.time.ZoneId
import java.util.Locale

object TimeZoneResolver {
    fun byCountryOrCoordinates(countryCode: String?, coordinates: Coordinates): ZoneId {
        val normalized = countryCode?.lowercase(Locale.US)
        val zoneName = coordinateZones.firstOrNull { coordinates.inBox(it) }?.zoneName
            ?: normalized?.let(countryZones::get)
        if (zoneName != null) {
            return ZoneId.of(zoneName)
        }

        return byLongitude(coordinates.longitude)
    }

    fun byCountryOrLongitude(countryCode: String?, longitude: Double): ZoneId {
        val normalized = countryCode?.lowercase(Locale.US)
        val zoneName = normalized?.let(countryZones::get)
        if (zoneName != null) {
            return ZoneId.of(zoneName)
        }

        return byLongitude(longitude)
    }

    private fun byLongitude(longitude: Double): ZoneId {
        val offset = (longitude / 15.0).toInt().coerceIn(-12, 14)
        return when {
            offset == 0 -> ZoneId.of("UTC")
            offset > 0 -> ZoneId.of("Etc/GMT-$offset")
            else -> ZoneId.of("Etc/GMT+${-offset}")
        }
    }

    private fun Coordinates.inBox(zone: CoordinateZone): Boolean =
        latitude in zone.minLatitude..zone.maxLatitude && longitude in zone.minLongitude..zone.maxLongitude

    private data class CoordinateZone(
        val minLatitude: Double,
        val maxLatitude: Double,
        val minLongitude: Double,
        val maxLongitude: Double,
        val zoneName: String,
    )

    private val coordinateZones = listOf(
        CoordinateZone(35.0, 43.0, 25.0, 45.5, "Europe/Istanbul"),
        CoordinateZone(41.0, 52.0, -6.0, 10.0, "Europe/Paris"),
        CoordinateZone(27.0, 36.5, -13.5, -0.5, "Africa/Casablanca"),
        CoordinateZone(18.5, 37.5, -9.0, 12.5, "Africa/Algiers"),
        CoordinateZone(30.0, 38.0, 7.0, 12.5, "Africa/Tunis"),
        CoordinateZone(22.0, 27.0, 51.0, 57.0, "Asia/Dubai"),
        CoordinateZone(24.0, 27.0, 50.5, 52.0, "Asia/Qatar"),
        CoordinateZone(28.0, 31.0, 46.0, 49.0, "Asia/Kuwait"),
        CoordinateZone(16.0, 33.0, 34.0, 56.0, "Asia/Riyadh"),
        CoordinateZone(21.5, 32.0, 24.5, 37.0, "Africa/Cairo"),
        CoordinateZone(23.0, 38.0, 60.0, 78.0, "Asia/Karachi"),
        CoordinateZone(24.0, 40.0, 44.0, 64.0, "Asia/Tehran"),
        CoordinateZone(1.0, 1.6, 103.5, 104.2, "Asia/Singapore"),
        CoordinateZone(0.5, 7.5, 99.0, 120.0, "Asia/Kuala_Lumpur"),
        CoordinateZone(54.0, 56.5, 19.0, 23.5, "Europe/Kaliningrad"),
        CoordinateZone(41.0, 71.0, 27.0, 48.5, "Europe/Moscow"),
        CoordinateZone(50.0, 57.0, 48.5, 54.5, "Europe/Samara"),
        CoordinateZone(53.0, 69.0, 54.5, 73.0, "Asia/Yekaterinburg"),
        CoordinateZone(53.0, 58.5, 73.0, 77.0, "Asia/Omsk"),
        CoordinateZone(51.0, 60.0, 77.0, 86.0, "Asia/Novosibirsk"),
        CoordinateZone(51.0, 72.0, 86.0, 103.0, "Asia/Krasnoyarsk"),
        CoordinateZone(49.0, 62.0, 103.0, 116.0, "Asia/Irkutsk"),
        CoordinateZone(50.0, 73.0, 116.0, 136.0, "Asia/Yakutsk"),
        CoordinateZone(42.0, 53.0, 129.0, 141.0, "Asia/Vladivostok"),
        CoordinateZone(56.0, 65.0, 145.0, 158.0, "Asia/Magadan"),
        CoordinateZone(51.0, 64.0, 158.0, 174.0, "Asia/Kamchatka"),
        CoordinateZone(18.0, 23.0, -161.0, -154.0, "Pacific/Honolulu"),
        CoordinateZone(51.0, 72.0, -170.0, -129.0, "America/Anchorage"),
        CoordinateZone(32.0, 49.5, -125.0, -114.0, "America/Los_Angeles"),
        CoordinateZone(31.0, 49.5, -114.0, -101.0, "America/Denver"),
        CoordinateZone(25.0, 49.5, -101.0, -85.0, "America/Chicago"),
        CoordinateZone(24.0, 49.5, -85.0, -66.0, "America/New_York"),
        CoordinateZone(42.0, 84.0, -141.0, -113.0, "America/Vancouver"),
        CoordinateZone(42.0, 84.0, -113.0, -96.0, "America/Edmonton"),
        CoordinateZone(42.0, 84.0, -96.0, -80.0, "America/Winnipeg"),
        CoordinateZone(42.0, 84.0, -80.0, -52.0, "America/Toronto"),
        CoordinateZone(-44.0, -10.0, 112.0, 129.0, "Australia/Perth"),
        CoordinateZone(-44.0, -10.0, 129.0, 142.0, "Australia/Adelaide"),
        CoordinateZone(-44.0, -10.0, 142.0, 154.0, "Australia/Sydney"),
    )

    private val countryZones = mapOf(
        "ru" to "Europe/Moscow",
        "tr" to "Europe/Istanbul",
        "de" to "Europe/Berlin",
        "fr" to "Europe/Paris",
        "gb" to "Europe/London",
        "uk" to "Europe/London",
        "es" to "Europe/Madrid",
        "it" to "Europe/Rome",
        "nl" to "Europe/Amsterdam",
        "be" to "Europe/Brussels",
        "ch" to "Europe/Zurich",
        "at" to "Europe/Vienna",
        "se" to "Europe/Stockholm",
        "no" to "Europe/Oslo",
        "dk" to "Europe/Copenhagen",
        "fi" to "Europe/Helsinki",
        "pl" to "Europe/Warsaw",
        "cz" to "Europe/Prague",
        "sk" to "Europe/Bratislava",
        "hu" to "Europe/Budapest",
        "ro" to "Europe/Bucharest",
        "bg" to "Europe/Sofia",
        "gr" to "Europe/Athens",
        "ba" to "Europe/Sarajevo",
        "al" to "Europe/Tirane",
        "mk" to "Europe/Skopje",
        "rs" to "Europe/Belgrade",
        "xk" to "Europe/Belgrade",
        "pt" to "Europe/Lisbon",
        "ie" to "Europe/Dublin",
        "ua" to "Europe/Kyiv",
        "by" to "Europe/Minsk",
        "az" to "Asia/Baku",
        "kz" to "Asia/Almaty",
        "uz" to "Asia/Tashkent",
        "kg" to "Asia/Bishkek",
        "tj" to "Asia/Dushanbe",
        "tm" to "Asia/Ashgabat",
        "sa" to "Asia/Riyadh",
        "ae" to "Asia/Dubai",
        "qa" to "Asia/Qatar",
        "kw" to "Asia/Kuwait",
        "eg" to "Africa/Cairo",
        "ma" to "Africa/Casablanca",
        "dz" to "Africa/Algiers",
        "tn" to "Africa/Tunis",
        "id" to "Asia/Jakarta",
        "my" to "Asia/Kuala_Lumpur",
        "pk" to "Asia/Karachi",
        "bd" to "Asia/Dhaka",
        "in" to "Asia/Kolkata",
        "us" to "America/New_York",
        "ca" to "America/Toronto",
    )
}
