package ru.admiral.praytimes.domain

import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Coordinates
import java.time.ZoneId
import java.util.Locale

object LocationCalculationMethodResolver {
    fun resolve(coordinates: Coordinates, zoneId: ZoneId, label: String): CalculationMethod {
        val zone = zoneId.id
        val normalizedLabel = label.lowercase(Locale.ROOT)
        return when {
            isTurkey(coordinates, zone, normalizedLabel) -> CalculationMethod.TURKEY_DIYANET
            isRussia(coordinates, zone, normalizedLabel) -> CalculationMethod.RUSSIA
            isFrance(coordinates, zone, normalizedLabel) -> CalculationMethod.FRANCE_UOIF
            isMorocco(coordinates, zone, normalizedLabel) -> CalculationMethod.MOROCCO
            isAlgeria(coordinates, zone, normalizedLabel) -> CalculationMethod.ALGERIA
            isTunisia(coordinates, zone, normalizedLabel) -> CalculationMethod.TUNISIA
            isMalaysia(zone, normalizedLabel) -> CalculationMethod.MALAYSIA
            isIndonesia(zone, normalizedLabel) -> CalculationMethod.INDONESIA
            isSingapore(zone, normalizedLabel) -> CalculationMethod.SINGAPORE
            isKuwait(zone, normalizedLabel) -> CalculationMethod.KUWAIT
            isQatar(zone, normalizedLabel) -> CalculationMethod.QATAR
            isUnitedArabEmirates(coordinates, zone, normalizedLabel) -> CalculationMethod.DUBAI
            isSaudiArabia(coordinates, zone, normalizedLabel) -> CalculationMethod.UMM_AL_QURA
            isEgypt(zone, normalizedLabel) -> CalculationMethod.EGYPTIAN
            isPakistan(zone, normalizedLabel) -> CalculationMethod.KARACHI
            isIran(zone, normalizedLabel) -> CalculationMethod.TEHRAN
            isNorthAmerica(coordinates, zone) -> CalculationMethod.NORTH_AMERICA
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
    }

    private fun isTurkey(coordinates: Coordinates, zone: String, label: String): Boolean =
        zone == "Europe/Istanbul" ||
            coordinates.inBox(35.0, 43.0, 25.0, 45.5) ||
            label.hasAny("turkey", "türkiye", "турция")

    private fun isRussia(coordinates: Coordinates, zone: String, label: String): Boolean =
        zone in russianZones ||
            label.hasAny("russia", "россия", "российская федерация") ||
            coordinates.inRussiaBox()

    private fun isFrance(coordinates: Coordinates, zone: String, label: String): Boolean =
        (zone == "Europe/Paris" && coordinates.inBox(41.0, 52.0, -6.0, 10.0)) ||
            label.hasAny("france", "français", "франция")

    private fun isMorocco(coordinates: Coordinates, zone: String, label: String): Boolean =
        zone == "Africa/Casablanca" ||
            coordinates.inBox(27.0, 36.5, -13.5, -0.5) ||
            label.hasAny("morocco", "maroc", "марокко")

    private fun isAlgeria(coordinates: Coordinates, zone: String, label: String): Boolean =
        zone == "Africa/Algiers" ||
            coordinates.inBox(18.5, 37.5, -9.0, 12.5) ||
            label.hasAny("algeria", "algérie", "алжир")

    private fun isTunisia(coordinates: Coordinates, zone: String, label: String): Boolean =
        zone == "Africa/Tunis" ||
            coordinates.inBox(30.0, 38.0, 7.0, 12.5) ||
            label.hasAny("tunisia", "tunisie", "тунис")

    private fun isMalaysia(zone: String, label: String): Boolean =
        zone in setOf("Asia/Kuala_Lumpur", "Asia/Kuching") ||
            label.hasAny("malaysia", "малайзия")

    private fun isIndonesia(zone: String, label: String): Boolean =
        zone in setOf("Asia/Jakarta", "Asia/Pontianak", "Asia/Makassar", "Asia/Jayapura") ||
            label.hasAny("indonesia", "индонезия")

    private fun isSingapore(zone: String, label: String): Boolean =
        zone == "Asia/Singapore" || label.hasAny("singapore", "сингапур")

    private fun isKuwait(zone: String, label: String): Boolean =
        zone == "Asia/Kuwait" || label.hasAny("kuwait", "кувейт")

    private fun isQatar(zone: String, label: String): Boolean =
        zone == "Asia/Qatar" || label.hasAny("qatar", "катар")

    private fun isUnitedArabEmirates(coordinates: Coordinates, zone: String, label: String): Boolean =
        (zone == "Asia/Dubai" && coordinates.inBox(22.0, 27.0, 51.0, 57.0)) ||
            label.hasAny("united arab emirates", "uae", "оаэ", "эмираты")

    private fun isSaudiArabia(coordinates: Coordinates, zone: String, label: String): Boolean =
        (zone == "Asia/Riyadh" && coordinates.inBox(16.0, 33.0, 34.0, 56.0)) ||
            label.hasAny("saudi arabia", "السعودية", "саудовская аравия")

    private fun isEgypt(zone: String, label: String): Boolean =
        zone == "Africa/Cairo" || label.hasAny("egypt", "مصر", "египет")

    private fun isPakistan(zone: String, label: String): Boolean =
        zone == "Asia/Karachi" || label.hasAny("pakistan", "пакистан")

    private fun isIran(zone: String, label: String): Boolean =
        zone == "Asia/Tehran" || label.hasAny("iran", "ایران", "иран")

    private fun isNorthAmerica(coordinates: Coordinates, zone: String): Boolean =
        zone.startsWith("America/") && coordinates.latitude in 5.0..75.0 && coordinates.longitude in -170.0..-50.0

    private fun Coordinates.inBox(
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
    ): Boolean = latitude in minLatitude..maxLatitude && longitude in minLongitude..maxLongitude

    private fun Coordinates.inRussiaBox(): Boolean =
        latitude in 41.0..82.0 && (longitude in 19.0..180.0 || longitude in -180.0..-169.0)

    private fun String.hasAny(vararg needles: String): Boolean =
        needles.any { contains(it) }

    private val russianZones = setOf(
        "Europe/Kaliningrad",
        "Europe/Moscow",
        "Europe/Samara",
        "Asia/Yekaterinburg",
        "Asia/Omsk",
        "Asia/Novosibirsk",
        "Asia/Barnaul",
        "Asia/Tomsk",
        "Asia/Novokuznetsk",
        "Asia/Krasnoyarsk",
        "Asia/Irkutsk",
        "Asia/Chita",
        "Asia/Yakutsk",
        "Asia/Khandyga",
        "Asia/Vladivostok",
        "Asia/Ust-Nera",
        "Asia/Magadan",
        "Asia/Sakhalin",
        "Asia/Srednekolymsk",
        "Asia/Kamchatka",
        "Asia/Anadyr",
    )
}
