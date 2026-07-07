package ru.admiral.praytimes.data

import java.text.Normalizer
import java.util.Locale

internal data class MosqueAreaCandidate(
    val id: Long,
    val names: List<String>,
    val adminLevel: Int?,
    val place: String,
    val borderType: String,
)

internal object MosqueAreaSelector {
    fun select(candidates: List<MosqueAreaCandidate>, locationLabel: String): MosqueAreaCandidate? =
        candidates.maxWithOrNull(
            compareBy<MosqueAreaCandidate> { score(it, locationHints(locationLabel)) }
                .thenBy { it.adminLevel ?: -1 },
        )

    private fun score(candidate: MosqueAreaCandidate, hints: List<String>): Int {
        var score = adminLevelScore(candidate.adminLevel)
        val normalizedNames = candidate.names.map(::normalize).filter(String::isNotBlank)
        val matchedHintIndex = hints.indexOfFirst { hint ->
            normalizedNames.any { name -> name == hint || name.contains(hint) || hint.contains(name) }
        }
        if (matchedHintIndex >= 0) {
            score += 120 - matchedHintIndex * 12
        }

        if (candidate.place in CITY_TYPES) {
            score += 80
        }
        if (candidate.borderType in CITY_TYPES) {
            score += 80
        }
        if (candidate.place in REGION_TYPES || candidate.borderType in REGION_TYPES) {
            score -= 70
        }
        if (candidate.place in DISTRICT_TYPES || candidate.borderType in DISTRICT_TYPES) {
            score -= 45
        }
        if (normalizedNames.any(::hasDistrictName)) {
            score -= 45
        }
        return score
    }

    private fun adminLevelScore(level: Int?): Int =
        when (level) {
            5 -> 14
            8 -> 12
            6 -> 10
            4 -> 8
            7 -> 8
            9 -> 4
            10 -> 2
            else -> 0
        }

    private fun locationHints(locationLabel: String): List<String> =
        locationLabel
            .split(',', ';', '\n')
            .map(::normalize)
            .filter { it.length > 1 }
            .distinct()
            .take(MAX_HINTS)

    private fun hasDistrictName(name: String): Boolean =
        DISTRICT_NAME_PARTS.any { part -> name.contains(part) }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(COMBINING_MARKS_REGEX, "")
            .replace(NON_WORD_REGEX, " ")
            .trim()

    private val CITY_TYPES = setOf("city", "town", "municipality", "village")
    private val REGION_TYPES = setOf("state", "province", "region", "oblast")
    private val DISTRICT_TYPES = setOf("borough", "county", "district", "suburb", "quarter", "neighbourhood")
    private val DISTRICT_NAME_PARTS = setOf(
        "district",
        "county",
        "borough",
        "suburb",
        "neighbourhood",
        "neighborhood",
        "arrondissement",
        "район",
        "раион",
        "округ",
    )
    private val COMBINING_MARKS_REGEX = Regex("\\p{Mn}+")
    private val NON_WORD_REGEX = Regex("[^\\p{L}\\p{N}]+")
    private const val MAX_HINTS = 4
}
