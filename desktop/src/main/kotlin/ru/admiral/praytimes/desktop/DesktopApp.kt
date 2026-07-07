package ru.admiral.praytimes.desktop

import ru.admiral.praytimes.adhan.AsrMethod
import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.adhan.Prayer
import ru.admiral.praytimes.adhan.PrayerAdjustments
import ru.admiral.praytimes.adhan.Qibla
import ru.admiral.praytimes.domain.CalculationMethodSelector
import ru.admiral.praytimes.domain.PrayerDay
import ru.admiral.praytimes.domain.PrayerDayCalculator
import ru.admiral.praytimes.holiday.HijriDate
import ru.admiral.praytimes.holiday.HolidayCalendar
import ru.admiral.praytimes.holiday.HolidayOccurrence
import ru.admiral.praytimes.holiday.IslamicHoliday
import ru.admiral.praytimes.sunnah.SunnahFast
import ru.admiral.praytimes.sunnah.SunnahPrayer
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.image.BufferedImage
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

fun main() {
    SwingUtilities.invokeLater {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        DesktopPrayerFrame().isVisible = true
    }
}

private class DesktopPrayerFrame : JFrame("Prayer Times Desktop") {
    private val backgroundPanel = TexturedPanel()
    private val locationCombo = JComboBox(defaultLocations.toTypedArray())
    private val locationNameField = JTextField(defaultLocations.first().name)
    private val latitudeField = JTextField(defaultLocations.first().coordinates.latitude.toString())
    private val longitudeField = JTextField(defaultLocations.first().coordinates.longitude.toString())
    private val zoneField = JTextField(defaultLocations.first().zoneId.id)
    private val dateField = JTextField(LocalDate.now(defaultLocations.first().zoneId).toString())
    private val methodCombo = JComboBox(methodOptions.toTypedArray())
    private val asrCombo = JComboBox(asrOptions.toTypedArray())
    private val hijriAdjustmentSpinner = JSpinner(SpinnerNumberModel(0, -2, 2, 1))
    private val holidayBackgroundCheck = JCheckBox("Использовать праздничный фон")
    private val methodLabel = JLabel()
    private val currentPrayerLabel = JLabel()
    private val qiblaLabel = JLabel()
    private val hijriLabel = JLabel()
    private val holidayArea = infoArea()
    private val sunnahArea = infoArea()
    private val prayerModel = object : DefaultTableModel(arrayOf("Намаз", "Время", "Статус"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val prayerTable = JTable(prayerModel)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(1040, 760)
        contentPane = backgroundPanel
        backgroundPanel.layout = BorderLayout(16, 16)
        backgroundPanel.border = EmptyBorder(18, 18, 18, 18)

        backgroundPanel.add(header(), BorderLayout.NORTH)
        backgroundPanel.add(content(), BorderLayout.CENTER)

        locationCombo.addActionListener(::applySelectedLocation)
        holidayBackgroundCheck.addActionListener { render() }
        Timer(TIMER_REFRESH_MS) { render(showValidationErrors = false) }.start()

        render()
        setLocationRelativeTo(null)
    }

    private fun header(): JPanel =
        CardPanel().apply {
            layout = GridBagLayout()
            add(titleBlock(), constraints(0, 0, width = 5, weightX = 1.0, fill = GridBagConstraints.HORIZONTAL))
            add(label("Город"), constraints(0, 1))
            add(locationCombo, constraints(1, 1, width = 2, weightX = 1.0, fill = GridBagConstraints.HORIZONTAL))
            add(label("Название"), constraints(3, 1))
            add(locationNameField, constraints(4, 1, weightX = 1.0, fill = GridBagConstraints.HORIZONTAL))
            add(label("Широта"), constraints(0, 2))
            add(latitudeField, constraints(1, 2, weightX = 0.5, fill = GridBagConstraints.HORIZONTAL))
            add(label("Долгота"), constraints(2, 2))
            add(longitudeField, constraints(3, 2, weightX = 0.5, fill = GridBagConstraints.HORIZONTAL))
            add(label("Часовой пояс"), constraints(4, 2))
            add(zoneField, constraints(5, 2, weightX = 0.7, fill = GridBagConstraints.HORIZONTAL))
            add(label("Дата"), constraints(0, 3))
            add(dateField, constraints(1, 3, weightX = 0.5, fill = GridBagConstraints.HORIZONTAL))
            add(dayButton("-1", -1), constraints(2, 3, fill = GridBagConstraints.HORIZONTAL))
            add(todayButton(), constraints(3, 3, fill = GridBagConstraints.HORIZONTAL))
            add(dayButton("+1", 1), constraints(4, 3, fill = GridBagConstraints.HORIZONTAL))
            add(renderButton(), constraints(5, 3, fill = GridBagConstraints.HORIZONTAL))
            add(label("Метод"), constraints(0, 4))
            add(methodCombo, constraints(1, 4, width = 2, weightX = 1.0, fill = GridBagConstraints.HORIZONTAL))
            add(label("Аср"), constraints(3, 4))
            add(asrCombo, constraints(4, 4, weightX = 1.0, fill = GridBagConstraints.HORIZONTAL))
            add(label("Хиджра +/-"), constraints(5, 4))
            add(hijriAdjustmentSpinner, constraints(6, 4, fill = GridBagConstraints.HORIZONTAL))
            add(holidayBackgroundCheck, constraints(0, 5, width = 3, fill = GridBagConstraints.HORIZONTAL))
        }

    private fun titleBlock(): JPanel =
        JPanel(BorderLayout()).apply {
            isOpaque = false
            add(
                JLabel("Времена намазов").apply {
                    font = font.deriveFont(Font.BOLD, 28f)
                    foreground = ink
                },
                BorderLayout.WEST,
            )
            add(
                JLabel("Desktop 0.1.1").apply {
                    horizontalAlignment = SwingConstants.RIGHT
                    foreground = muted
                },
                BorderLayout.EAST,
            )
        }

    private fun content(): JSplitPane {
        preparePrayerTable()
        val left = CardPanel().apply {
            layout = BorderLayout(0, 10)
            add(sectionTitle("Расписание"), BorderLayout.NORTH)
            add(JScrollPane(prayerTable).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        }
        val right = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(summaryCard())
            add(Box.createVerticalStrut(12))
            add(textCard("Праздники", holidayArea))
            add(Box.createVerticalStrut(12))
            add(textCard("Сунна", sunnahArea))
        }
        return JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right).apply {
            resizeWeight = 0.55
            dividerSize = 8
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            leftComponent.minimumSize = Dimension(500, 300)
            rightComponent.minimumSize = Dimension(390, 300)
        }
    }

    private fun summaryCard(): JPanel =
        CardPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(sectionTitle("Сейчас"))
            add(currentPrayerLabel)
            add(Box.createVerticalStrut(8))
            add(methodLabel)
            add(Box.createVerticalStrut(8))
            add(qiblaLabel)
            add(Box.createVerticalStrut(8))
            add(hijriLabel)
            components.filterIsInstance<JLabel>().forEach {
                it.foreground = ink
                it.font = it.font.deriveFont(15f)
            }
        }

    private fun textCard(title: String, area: JTextArea): JPanel =
        CardPanel().apply {
            layout = BorderLayout(0, 8)
            add(sectionTitle(title), BorderLayout.NORTH)
            add(JScrollPane(area).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        }

    private fun preparePrayerTable() {
        prayerTable.rowHeight = 36
        prayerTable.font = prayerTable.font.deriveFont(15f)
        prayerTable.tableHeader.font = prayerTable.tableHeader.font.deriveFont(Font.BOLD, 14f)
        prayerTable.gridColor = Color(214, 226, 219)
        prayerTable.selectionBackground = Color(222, 240, 232)
        prayerTable.fillsViewportHeight = true
        val renderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int,
            ): Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                component.foreground = ink
                border = EmptyBorder(0, 10, 0, 10)
                return component
            }
        }
        repeat(prayerTable.columnCount) { index -> prayerTable.columnModel.getColumn(index).cellRenderer = renderer }
    }

    private fun render(showValidationErrors: Boolean = true) {
        val coordinates = readCoordinates(showValidationErrors) ?: return
        val zoneId = readZoneId(showValidationErrors) ?: return
        val date = readDate(showValidationErrors) ?: return
        val methodOption = methodCombo.selectedItem as MethodOption
        val asrOption = asrCombo.selectedItem as AsrOption
        val hijriAdjustment = (hijriAdjustmentSpinner.value as Number).toInt()
        val methodSelection = CalculationMethodSelector.select(
            automatic = methodOption.method == null,
            manualMethod = methodOption.method ?: CalculationMethod.MUSLIM_WORLD_LEAGUE,
            coordinates = coordinates,
            zoneId = zoneId,
            locationLabel = locationNameField.text.trim(),
        )
        val now = ZonedDateTime.now(zoneId)
        val prayerDay = PrayerDayCalculator.calculate(
            coordinates = coordinates,
            date = date,
            zoneId = zoneId,
            parameters = methodSelection.method.parameters()
                .withAsrMethod(asrOption.method)
                .copy(adjustments = PrayerAdjustments()),
            hijriDayAdjustment = hijriAdjustment,
            now = now,
        )
        val holidays = HolidayCalendar.holidaysOn(date, hijriAdjustment)

        backgroundPanel.useHolidayTexture = holidayBackgroundCheck.isSelected && holidays.isNotEmpty()
        backgroundPanel.repaint()
        methodLabel.text = "Метод: ${methodLabel(methodSelection.method)} (${if (methodSelection.isAutomatic) "авто" else "ручной"})"
        qiblaLabel.text = "Кибла: ${String.format(Locale.getDefault(), "%.1f°", Qibla(coordinates).direction)}"
        hijriLabel.text = "Хиджра: ${formatHijri(prayerDay.hijriDate)}"
        renderPrayerTable(prayerDay, now, date == now.toLocalDate())
        renderSummary(prayerDay)
        renderHolidayText(holidays, date, hijriAdjustment)
        renderSunnahText(prayerDay)
    }

    private fun renderPrayerTable(prayerDay: PrayerDay, now: ZonedDateTime, isToday: Boolean) {
        prayerModel.rowCount = 0
        val prayerTimes = prayerDay.prayerTimes
        if (prayerTimes == null) {
            prayerModel.addRow(arrayOf("Недоступно", "--:--", "Солнце не дает нормальный расчет для этой даты"))
            return
        }
        val rows = listOf(
            Prayer.FAJR to prayerTimes.fajr,
            Prayer.SUNRISE to prayerTimes.sunrise,
            Prayer.DHUHR to prayerTimes.dhuhr,
            Prayer.ASR to prayerTimes.asr,
            Prayer.MAGHRIB to prayerTimes.maghrib,
            Prayer.ISHA to prayerTimes.isha,
        )
        rows.forEach { (prayer, time) ->
            prayerModel.addRow(arrayOf(prayerLabel(prayer), timeFormatter.format(time), prayerStatus(prayerDay, prayer, time, now, isToday)))
        }
    }

    private fun prayerStatus(prayerDay: PrayerDay, prayer: Prayer, time: ZonedDateTime, now: ZonedDateTime, isToday: Boolean): String {
        if (!isToday) {
            return ""
        }
        val activeWindow = prayerDay.activeWindow
        return when {
            activeWindow?.currentPrayer == prayer -> "сейчас, осталось ${formatDuration(activeWindow.currentLeft)}"
            now.isBefore(time) -> "через ${formatDuration(Duration.between(now, time))}"
            else -> "прошел"
        }
    }

    private fun renderSummary(prayerDay: PrayerDay) {
        val active = prayerDay.activeWindow
        currentPrayerLabel.text = if (active == null || active.currentPrayer == Prayer.NONE) {
            "До следующего: ${formatDuration(active?.untilNext ?: Duration.ZERO)}"
        } else {
            "${prayerLabel(active.currentPrayer)}: осталось ${formatDuration(active.currentLeft)}"
        }
    }

    private fun renderHolidayText(holidays: List<HolidayOccurrence>, date: LocalDate, hijriAdjustment: Int) {
        val todayText = if (holidays.isEmpty()) {
            "На выбранную дату праздников нет."
        } else {
            holidays.joinToString("\n") { holidayTitle(it.holiday) }
        }
        val next = HolidayCalendar.nextHolidayAfter(date, hijriAdjustment)
        holidayArea.text = buildString {
            append(todayText)
            append("\n\n")
            if (next == null) {
                append("Следующий праздник не найден.")
            } else {
                append("Следующий: ")
                append(holidayTitle(next.holiday))
                append(" — ")
                append(dateFormatter.format(next.date))
            }
        }
    }

    private fun renderSunnahText(prayerDay: PrayerDay) {
        val fasts = prayerDay.sunnahFastDays.joinToString("\n") { fastDay ->
            "${sunnahFastTitle(fastDay.fast)} — ${sunnahFastNote(fastDay.fast)}"
        }.ifBlank { "Сунна-постов на выбранную дату нет." }
        val prayers = prayerDay.sunnahPrayerWindows.joinToString("\n") { window ->
            "${sunnahPrayerTitle(window.prayer)}: ${timeFormatter.format(window.start)}-${timeFormatter.format(window.end)}"
        }.ifBlank { "Сунна-окна на выбранную дату не рассчитались." }
        sunnahArea.text = "$fasts\n\n$prayers"
    }

    private fun applySelectedLocation(event: ActionEvent) {
        val location = (event.source as JComboBox<*>).selectedItem as? DesktopLocation ?: return
        locationNameField.text = location.name
        latitudeField.text = location.coordinates.latitude.toString()
        longitudeField.text = location.coordinates.longitude.toString()
        zoneField.text = location.zoneId.id
        dateField.text = LocalDate.now(location.zoneId).toString()
        render()
    }

    private fun readCoordinates(showValidationErrors: Boolean = true): Coordinates? =
        runCatching {
            Coordinates(latitudeField.text.trim().toDouble(), longitudeField.text.trim().toDouble())
        }.getOrElse {
            if (showValidationErrors) {
                showError("Координаты должны быть числами.")
            }
            null
        }

    private fun readZoneId(showValidationErrors: Boolean = true): ZoneId? =
        runCatching { ZoneId.of(zoneField.text.trim()) }.getOrElse {
            if (showValidationErrors) {
                showError("Некорректный часовой пояс.")
            }
            null
        }

    private fun readDate(showValidationErrors: Boolean = true): LocalDate? =
        runCatching { LocalDate.parse(dateField.text.trim()) }.getOrElse {
            if (showValidationErrors) {
                showError("Дата должна быть в формате YYYY-MM-DD.")
            }
            null
        }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE)
    }

    private fun dayButton(label: String, days: Long): JButton =
        JButton(label).apply {
            addActionListener {
                readDate()?.let { date ->
                    dateField.text = date.plusDays(days).toString()
                    render()
                }
            }
        }

    private fun todayButton(): JButton =
        JButton("Сегодня").apply {
            addActionListener {
                val zoneId = readZoneId() ?: return@addActionListener
                dateField.text = LocalDate.now(zoneId).toString()
                render()
            }
        }

    private fun renderButton(): JButton =
        JButton("Пересчитать").apply { addActionListener { render() } }

    private fun constraints(
        x: Int,
        y: Int,
        width: Int = 1,
        weightX: Double = 0.0,
        fill: Int = GridBagConstraints.NONE,
    ): GridBagConstraints =
        GridBagConstraints().apply {
            gridx = x
            gridy = y
            gridwidth = width
            this.weightx = weightX
            this.fill = fill
            insets = Insets(5, 5, 5, 5)
            anchor = GridBagConstraints.WEST
        }
}

private class TexturedPanel : JPanel() {
    var useHolidayTexture: Boolean = false
    private val defaultTexture = loadTexture("islamic_texture_tile.png")
    private val holidayTexture = loadTexture("holiday_texture_tile.png")

    init {
        isOpaque = false
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.color = Color(237, 246, 240)
            graphics2D.fillRect(0, 0, width, height)
            val image = if (useHolidayTexture) holidayTexture else defaultTexture
            if (image != null) {
                graphics2D.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.42f)
                for (x in 0 until width step image.width) {
                    for (y in 0 until height step image.height) {
                        graphics2D.drawImage(image, x, y, null)
                    }
                }
            }
        } finally {
            graphics2D.dispose()
        }
    }

    companion object {
        private fun loadTexture(name: String): BufferedImage? =
            TexturedPanel::class.java.classLoader.getResource(name)?.let(ImageIO::read)
    }
}

private class CardPanel : JPanel() {
    init {
        isOpaque = false
        border = EmptyBorder(16, 16, 16, 16)
    }

    override fun paintComponent(graphics: Graphics) {
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics2D.color = Color(255, 252, 245, 232)
            graphics2D.fillRoundRect(0, 0, width, height, 16, 16)
            graphics2D.color = Color(203, 221, 211)
            graphics2D.drawRoundRect(0, 0, width - 1, height - 1, 16, 16)
        } finally {
            graphics2D.dispose()
        }
        super.paintComponent(graphics)
    }
}

private data class DesktopLocation(
    val name: String,
    val coordinates: Coordinates,
    val zoneId: ZoneId,
) {
    override fun toString(): String = name
}

private data class MethodOption(val method: CalculationMethod?, val title: String) {
    override fun toString(): String = title
}

private data class AsrOption(val method: AsrMethod, val title: String) {
    override fun toString(): String = title
}

private fun label(text: String): JLabel =
    JLabel(text).apply {
        foreground = muted
        font = font.deriveFont(Font.BOLD, 12f)
    }

private fun sectionTitle(text: String): JLabel =
    JLabel(text).apply {
        foreground = ink
        font = font.deriveFont(Font.BOLD, 19f)
    }

private fun infoArea(): JTextArea =
    JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = Color(255, 252, 245)
        foreground = ink
        font = font.deriveFont(14f)
        border = EmptyBorder(8, 8, 8, 8)
    }

private fun formatHijri(hijriDate: HijriDate): String =
    "${hijriDate.day} ${hijriMonth(hijriDate.month)} ${hijriDate.year}"

private fun formatDuration(duration: Duration): String {
    val seconds = duration.seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
}

private fun prayerLabel(prayer: Prayer): String = when (prayer) {
    Prayer.FAJR -> "Фаджр"
    Prayer.SUNRISE -> "Восход"
    Prayer.DHUHR -> "Зухр"
    Prayer.ASR -> "Аср"
    Prayer.MAGHRIB -> "Магриб"
    Prayer.ISHA -> "Иша"
    Prayer.NONE -> "Ночь"
}

private fun methodLabel(method: CalculationMethod): String = when (method) {
    CalculationMethod.MUSLIM_WORLD_LEAGUE -> "Muslim World League"
    CalculationMethod.EGYPTIAN -> "Egyptian"
    CalculationMethod.KARACHI -> "Karachi"
    CalculationMethod.UMM_AL_QURA -> "Umm al-Qura"
    CalculationMethod.DUBAI -> "Dubai"
    CalculationMethod.MOON_SIGHTING_COMMITTEE -> "Moon Sighting Committee"
    CalculationMethod.NORTH_AMERICA -> "North America"
    CalculationMethod.KUWAIT -> "Kuwait"
    CalculationMethod.QATAR -> "Qatar"
    CalculationMethod.SINGAPORE -> "Singapore"
    CalculationMethod.TURKEY_DIYANET -> "Turkey Diyanet"
    CalculationMethod.RUSSIA -> "Russia"
    CalculationMethod.FRANCE_UOIF -> "France UOIF"
    CalculationMethod.JAFARI -> "Jafari"
    CalculationMethod.TEHRAN -> "Tehran"
    CalculationMethod.INDONESIA -> "Indonesia"
    CalculationMethod.MALAYSIA -> "Malaysia"
    CalculationMethod.MOROCCO -> "Morocco"
    CalculationMethod.ALGERIA -> "Algeria"
    CalculationMethod.TUNISIA -> "Tunisia"
    CalculationMethod.OTHER -> "Other"
}

private fun holidayTitle(holiday: IslamicHoliday): String = when (holiday) {
    IslamicHoliday.ISLAMIC_NEW_YEAR -> "Исламский Новый год"
    IslamicHoliday.ASHURA -> "Ашура"
    IslamicHoliday.MAWLID -> "Маулид"
    IslamicHoliday.ISRA_MIRAJ -> "Исра и Мирадж"
    IslamicHoliday.NISF_SHABAN -> "Ночь Бараат"
    IslamicHoliday.RAMADAN_BEGINS -> "Начало Рамадана"
    IslamicHoliday.LAYLAT_AL_QADR -> "Ляйлятуль-Кадр"
    IslamicHoliday.EID_AL_FITR -> "Ураза-байрам"
    IslamicHoliday.ARAFAH -> "День Арафа"
    IslamicHoliday.EID_AL_ADHA -> "Курбан-байрам"
}

private fun sunnahFastTitle(fast: SunnahFast): String = when (fast) {
    SunnahFast.MONDAY_THURSDAY -> "Понедельник/четверг"
    SunnahFast.WHITE_DAYS -> "Белые дни"
    SunnahFast.ARAFAH -> "День Арафа"
    SunnahFast.TASUA -> "Тасуа"
    SunnahFast.ASHURA -> "Ашура"
    SunnahFast.SHAWWAL_SIX -> "6 дней Шавваля"
}

private fun sunnahFastNote(fast: SunnahFast): String = when (fast) {
    SunnahFast.MONDAY_THURSDAY -> "желательный пост"
    SunnahFast.WHITE_DAYS -> "13, 14, 15 числа хиджры"
    SunnahFast.ARAFAH -> "особо желательный пост"
    SunnahFast.TASUA -> "9 Мухаррама"
    SunnahFast.ASHURA -> "10 Мухаррама"
    SunnahFast.SHAWWAL_SIX -> "дни после Ураза-байрама"
}

private fun sunnahPrayerTitle(prayer: SunnahPrayer): String = when (prayer) {
    SunnahPrayer.FAJR_SUNNAH -> "Сунна Фаджра"
    SunnahPrayer.ISHRAQ -> "Ишрак"
    SunnahPrayer.DUHA -> "Духа"
    SunnahPrayer.AWWABIN -> "Аввабин"
    SunnahPrayer.WITR -> "Витр"
    SunnahPrayer.TAHAJJUD -> "Тахаджуд"
    SunnahPrayer.TARAWIH -> "Таравих"
    SunnahPrayer.EID -> "Праздничный намаз"
}

private fun hijriMonth(month: Int): String = when (month) {
    1 -> "Мухаррам"
    2 -> "Сафар"
    3 -> "Раби аль-авваль"
    4 -> "Раби ас-сани"
    5 -> "Джумада аль-уля"
    6 -> "Джумада ас-сани"
    7 -> "Раджаб"
    8 -> "Шаабан"
    9 -> "Рамадан"
    10 -> "Шавваль"
    11 -> "Зуль-каада"
    12 -> "Зуль-хиджа"
    else -> "месяц $month"
}

private val defaultLocations = listOf(
    DesktopLocation("Москва, Россия", Coordinates(55.7558, 37.6173), ZoneId.of("Europe/Moscow")),
    DesktopLocation("Мерсин, Турция", Coordinates(36.8121, 34.6415), ZoneId.of("Europe/Istanbul")),
    DesktopLocation("Казань, Россия", Coordinates(55.7961, 49.1064), ZoneId.of("Europe/Moscow")),
    DesktopLocation("Мекка, Саудовская Аравия", Coordinates(21.3891, 39.8579), ZoneId.of("Asia/Riyadh")),
    DesktopLocation("Стамбул, Турция", Coordinates(41.0082, 28.9784), ZoneId.of("Europe/Istanbul")),
)

private val methodOptions = listOf(MethodOption(null, "Авто")) +
    CalculationMethod.entries
        .filter { it != CalculationMethod.OTHER }
        .map { MethodOption(it, methodLabel(it)) }

private val asrOptions = AsrMethod.entries.map { method ->
    AsrOption(
        method,
        when (method) {
            AsrMethod.STANDARD -> "Стандарт"
            AsrMethod.HANAFI -> "Ханафи"
            AsrMethod.SHADOW_1_25 -> "Тень 1.25"
            AsrMethod.SHADOW_1_50 -> "Тень 1.50"
            AsrMethod.SHADOW_1_75 -> "Тень 1.75"
            AsrMethod.SHADOW_2_50 -> "Тень 2.50"
            AsrMethod.SHADOW_3_00 -> "Тень 3.00"
        },
    )
}

private val ink = Color(18, 32, 28)
private val muted = Color(82, 103, 96)
private const val TIMER_REFRESH_MS = 60_000
