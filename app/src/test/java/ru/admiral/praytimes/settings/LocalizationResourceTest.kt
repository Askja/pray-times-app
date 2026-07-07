package ru.admiral.praytimes.settings

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

class LocalizationResourceTest {
    @Test
    fun configuredLanguagesMatchLocaleConfig() {
        val languages = AppSettings.supportedLanguageTags.filter { it.isNotBlank() }

        assertTrue(languages.size >= MIN_LANGUAGE_COUNT)
        assertEquals(languages, localeConfigLanguages())
        assertTrue(RTL_LANGUAGES.all { it in languages })
    }

    @Test
    fun localizedFilesCoverBaseStringsAndFormats() {
        val baseStrings = readStrings(resourceFile("en"))
        val baseArrays = readStringArrays(resourceFile("en"))

        for (language in AppSettings.supportedLanguageTags.filter { it.isNotBlank() }) {
            val file = resourceFile(language)
            assertTrue(Files.exists(file), "Нет strings.xml для $language: $file")

            val localizedStrings = readStrings(file)
            val localizedArrays = readStringArrays(file)

            assertEquals(baseStrings.keys, localizedStrings.keys, "Набор строк сломан для $language")
            assertEquals(baseArrays.keys, localizedArrays.keys, "Набор массивов сломан для $language")

            for ((name, baseValue) in baseStrings) {
                val localizedValue = localizedStrings.getValue(name)
                assertEquals(
                    formatTokens(baseValue).sorted(),
                    formatTokens(localizedValue).sorted(),
                    "Форматтеры сломаны для $language/$name",
                )
            }

            for ((name, baseItems) in baseArrays) {
                assertEquals(baseItems.size, localizedArrays.getValue(name).size, "Размер массива сломан для $language/$name")
            }

            if (language != "en") {
                val sameAsEnglish = baseStrings.count { (name, baseValue) ->
                    localizedStrings[name] == baseValue && baseValue.any { it.isLetter() }
                }
                assertTrue(
                    sameAsEnglish <= MAX_ALLOWED_ENGLISH_FALLBACKS,
                    "В $language осталось слишком много английских заглушек: $sameAsEnglish",
                )
            }
        }
    }

    @Test
    fun rtlSupportIsEnabled() {
        val manifest = String(Files.readAllBytes(resRoot().parent.resolve("AndroidManifest.xml")), Charsets.UTF_8)

        assertTrue(manifest.contains("android:supportsRtl=\"true\""))
        assertTrue(RTL_LANGUAGES.all { it in localeConfigLanguages() })
    }

    private fun localeConfigLanguages(): List<String> {
        val document = xml(resRoot().resolve("xml/locales_config.xml"))
        val nodes = document.documentElement.getElementsByTagName("locale")
        return (0 until nodes.length).map { index ->
            (nodes.item(index) as Element).getAttribute("android:name")
        }
    }

    private fun readStrings(path: Path): Map<String, String> {
        val document = xml(path)
        val nodes = document.documentElement.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private fun readStringArrays(path: Path): Map<String, List<String>> {
        val document = xml(path)
        val nodes = document.documentElement.getElementsByTagName("string-array")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as Element
            val items = element.getElementsByTagName("item")
            element.getAttribute("name") to (0 until items.length).map { itemIndex -> items.item(itemIndex).textContent }
        }
    }

    private fun xml(path: Path) =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(path.toFile())

    private fun resourceFile(language: String): Path {
        val directory = when (language) {
            "en" -> "values"
            "id" -> "values-in"
            else -> "values-$language"
        }
        return resRoot().resolve("$directory/strings.xml")
    }

    private fun resRoot(): Path =
        RES_ROOT_CANDIDATES.first { Files.isDirectory(it) }

    private fun formatTokens(value: String): List<String> =
        FORMAT_PATTERN.findAll(value).map { it.value }.toList()

    private companion object {
        const val MIN_LANGUAGE_COUNT = 25
        const val MAX_ALLOWED_ENGLISH_FALLBACKS = 80

        val RTL_LANGUAGES = setOf("ar", "fa", "ur")
        val RES_ROOT_CANDIDATES = listOf(
            Paths.get("src/main/res"),
            Paths.get("app/src/main/res"),
        )
        val FORMAT_PATTERN = Regex("%(?:\\d+\\$)?[+\\-#0,( ]*(?:\\d+)?(?:\\.\\d+)?[a-zA-Z%]")
    }
}
