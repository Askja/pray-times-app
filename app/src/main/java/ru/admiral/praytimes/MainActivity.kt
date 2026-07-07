package ru.admiral.praytimes

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.text.BidiFormatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ru.admiral.praytimes.adhan.AsrMethod
import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.adhan.Prayer
import ru.admiral.praytimes.adhan.PrayerAdjustments
import ru.admiral.praytimes.adhan.PrayerTimes
import ru.admiral.praytimes.adhan.Qibla
import ru.admiral.praytimes.data.LocationDatabase
import ru.admiral.praytimes.data.LocationProviderSelector
import ru.admiral.praytimes.data.LocationVisual
import ru.admiral.praytimes.data.Mosque
import ru.admiral.praytimes.data.MosqueSearchService
import ru.admiral.praytimes.data.SavedLocation
import ru.admiral.praytimes.data.TimeZoneResolver
import ru.admiral.praytimes.domain.ActivePrayerWindow
import ru.admiral.praytimes.domain.CalculationMethodSelector
import ru.admiral.praytimes.domain.CalculationMethodSelection
import ru.admiral.praytimes.domain.NearbyLocationSwitchResolver
import ru.admiral.praytimes.domain.PrayerDay
import ru.admiral.praytimes.domain.PrayerDayCalculator
import ru.admiral.praytimes.domain.PrayerTimePoint
import ru.admiral.praytimes.holiday.HijriDate
import ru.admiral.praytimes.holiday.HolidayCalendar
import ru.admiral.praytimes.holiday.IslamicCalendar
import ru.admiral.praytimes.notification.PrayerNotificationSyncController
import ru.admiral.praytimes.settings.AppSettings
import ru.admiral.praytimes.settings.HomeBlock
import ru.admiral.praytimes.sunnah.SunnahFastDay
import ru.admiral.praytimes.sunnah.SunnahPrayerWindow
import ru.admiral.praytimes.ui.QiblaCompassView
import ru.admiral.praytimes.ui.LocationVisualStyle
import ru.admiral.praytimes.ui.PrayerProgressView
import ru.admiral.praytimes.ui.SystemInsets
import ru.admiral.praytimes.ui.UiText
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity(), SensorEventListener {
    private lateinit var rootRefresh: SwipeRefreshLayout
    private lateinit var rootScroll: View
    private lateinit var dateText: TextView
    private lateinit var calculationMethodText: TextView
    private lateinit var locationPickerFrame: View
    private lateinit var locationPickerIcon: ImageView
    private lateinit var locationSelectedText: TextView
    private lateinit var locationSelectedMetaText: TextView
    private lateinit var qiblaText: TextView
    private lateinit var qiblaCompass: QiblaCompassView
    private lateinit var prayerProgressView: PrayerProgressView
    private lateinit var currentPrayerNameText: TextView
    private lateinit var currentPrayerTimeText: TextView
    private lateinit var ramadanPanel: View
    private lateinit var ramadanSuhoorText: TextView
    private lateinit var ramadanIftarText: TextView
    private lateinit var ramadanTarawihText: TextView
    private lateinit var holidayPreviewTitle: TextView
    private lateinit var holidayPreviewDate: TextView
    private lateinit var nextHolidayText: TextView
    private lateinit var sunnahPanelHeader: View
    private lateinit var sunnahPanelBody: View
    private lateinit var sunnahPanelChevron: ImageView
    private lateinit var sunnahPrayerList: LinearLayout
    private lateinit var mosqueStatusText: TextView
    private lateinit var mosqueList: RecyclerView
    private lateinit var mosqueAdapter: MosqueAdapter
    private lateinit var refreshMosquesButton: Button

    private lateinit var database: LocationDatabase
    private val mosqueSearchService = MosqueSearchService()
    private val notificationSyncController = PrayerNotificationSyncController()
    private val backgroundExecutor: ExecutorService = Executors.newFixedThreadPool(BACKGROUND_THREAD_COUNT)
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var magneticSensor: Sensor? = null
    private val gravityValues = FloatArray(SENSOR_VECTOR_SIZE)
    private val magneticValues = FloatArray(SENSOR_VECTOR_SIZE)
    private val rotationMatrix = FloatArray(ROTATION_MATRIX_SIZE)
    private val orientationValues = FloatArray(SENSOR_VECTOR_SIZE)
    private var hasGravityValues = false
    private var hasMagneticValues = false
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            tickCurrentPrayerState()
            timerHandler.postDelayed(this, TIMER_REFRESH_MS)
        }
    }

    private var savedLocations: List<SavedLocation> = emptyList()
    private var selectedLocationId: Long? = null
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedCoordinates = Coordinates(36.8121, 34.6415)
    private var selectedZoneId: ZoneId = ZoneId.of("Europe/Istanbul")
    private var selectedLocationLabel = ""
    private var selectedLocationSource = SEED_LOCATION_SOURCE
    private var selectedMethod = CalculationMethod.TURKEY_DIYANET
    private var selectedMethodSelection = CalculationMethodSelection(CalculationMethod.TURKEY_DIYANET, isAutomatic = true)
    private var selectedAsrMethod = AsrMethod.STANDARD
    private var selectedIqamaOffsets = PrayerAdjustments()
    private var sunnahPanelExpanded = true
    private var settingsRevision = 0
    private var lastMosqueSearchKey = ""
    private var pendingMosqueSearchKey = ""
    private var lastMosqueSearchFailureKey = ""
    private var lastMosqueSearchFailureAtMillis = 0L
    private var lastManualMosqueRefreshAtMillis = 0L
    private var mosqueSearchGeneration = 0
    private var reverseLocationGeneration = 0
    private var lastSuggestedLocationId: Long? = null
    private var renderedPrayerDay: PrayerDay? = null

    private lateinit var prayerRows: Map<Prayer, PrayerRow>

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SystemInsets.applyTo(findViewById(R.id.rootRefresh))

        database = LocationDatabase(this)
        settingsRevision = AppSettings.revision(this)
        selectedMethod = AppSettings.calculationMethod(this)
        selectedAsrMethod = AppSettings.asrMethod(this)
        sensorManager = getSystemService(SensorManager::class.java)
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        bindViews()
        arrangeMainSections()
        selectedLocationLabel = getString(R.string.location_detecting)
        setupSwipeRefresh()
        setupLocationPicker()
        setupButtons()
        reloadSavedLocations()
        selectInitialLocation()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (settingsRevision != AppSettings.revision(this)) {
            recreate()
            return
        }
        registerCompassSensors()
        if ((AppSettings.autoSwitchLocation(this) || AppSettings.suggestLocationSwitch(this)) && hasLocationPermission()) {
            handleNearbySavedLocation()
        }
        timerHandler.post(timerRunnable)
    }

    override fun onPause() {
        timerHandler.removeCallbacks(timerRunnable)
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        mosqueSearchGeneration++
        reverseLocationGeneration++
        backgroundExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> updateAzimuthFromRotationVector(event.values)
            Sensor.TYPE_ACCELEROMETER -> {
                event.values.copyInto(gravityValues, endIndex = SENSOR_VECTOR_SIZE)
                hasGravityValues = true
                updateAzimuthFromGravityAndMagnetic()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                event.values.copyInto(magneticValues, endIndex = SENSOR_VECTOR_SIZE)
                hasMagneticValues = true
                updateAzimuthFromGravityAndMagnetic()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            readCurrentLocation()
        } else if (requestCode == LOCATION_REQUEST_CODE) {
            showToast(R.string.location_permission_required)
            applyBestFallbackLocation()
        }
    }

    private fun arrangeMainSections() {
        val root = findViewById<LinearLayout>(R.id.contentRoot)
        val anchor = findViewById<View>(R.id.dayNavigationBar)
        val blockViews = mapOf(
            HomeBlock.RAMADAN to findViewById<View>(R.id.ramadanPanel),
            HomeBlock.PRAYER to findViewById<View>(R.id.prayerPanel),
            HomeBlock.SUNNAH to findViewById<View>(R.id.sunnahPanel),
            HomeBlock.QIBLA to findViewById<View>(R.id.summaryPanel),
            HomeBlock.MOSQUES to findViewById<View>(R.id.mosquePanel),
            HomeBlock.HOLIDAYS to findViewById<View>(R.id.holidayPanel),
        )
        blockViews.values.forEach(root::removeView)
        val insertIndex = root.indexOfChild(anchor) + 1
        AppSettings.homeBlockOrder(this)
            .mapNotNull(blockViews::get)
            .forEachIndexed { index, view -> root.addView(view, insertIndex + index) }
    }

    private fun setupLocationPicker() {
        locationPickerFrame.setOnClickListener { showLocationPickerDialog() }
    }

    private fun bindViews() {
        rootRefresh = findViewById(R.id.rootRefresh)
        rootScroll = findViewById(R.id.rootScroll)
        dateText = findViewById(R.id.dateText)
        calculationMethodText = findViewById(R.id.calculationMethodText)
        locationPickerFrame = findViewById(R.id.locationPickerFrame)
        locationPickerIcon = findViewById(R.id.locationPickerIcon)
        locationSelectedText = findViewById(R.id.locationSelectedText)
        locationSelectedMetaText = findViewById(R.id.locationSelectedMetaText)
        qiblaText = findViewById(R.id.qiblaText)
        qiblaCompass = findViewById(R.id.qiblaCompass)
        prayerProgressView = findViewById(R.id.prayerProgressView)
        currentPrayerNameText = findViewById(R.id.currentPrayerNameText)
        currentPrayerTimeText = findViewById(R.id.currentPrayerTimeText)
        ramadanPanel = findViewById(R.id.ramadanPanel)
        ramadanSuhoorText = findViewById(R.id.ramadanSuhoorText)
        ramadanIftarText = findViewById(R.id.ramadanIftarText)
        ramadanTarawihText = findViewById(R.id.ramadanTarawihText)
        holidayPreviewTitle = findViewById(R.id.holidayPreviewTitle)
        holidayPreviewDate = findViewById(R.id.holidayPreviewDate)
        nextHolidayText = findViewById(R.id.nextHolidayText)
        sunnahPanelHeader = findViewById(R.id.sunnahPanelHeader)
        sunnahPanelBody = findViewById(R.id.sunnahPanelBody)
        sunnahPanelChevron = findViewById(R.id.sunnahPanelChevron)
        sunnahPrayerList = findViewById(R.id.sunnahPrayerList)
        mosqueStatusText = findViewById(R.id.mosqueStatusText)
        refreshMosquesButton = findViewById(R.id.refreshMosquesButton)
        mosqueList = findViewById(R.id.mosqueList)
        mosqueAdapter = MosqueAdapter()
        mosqueList.layoutManager = LinearLayoutManager(this)
        mosqueList.adapter = mosqueAdapter
        mosqueList.itemAnimator = null
        val fajrDivider = findViewById<View>(R.id.fajrDivider)
        val sunriseDivider = findViewById<View>(R.id.sunriseDivider)
        val dhuhrDivider = findViewById<View>(R.id.dhuhrDivider)
        val asrDivider = findViewById<View>(R.id.asrDivider)
        val maghribDivider = findViewById<View>(R.id.maghribDivider)
        prayerRows = mapOf(
            Prayer.FAJR to PrayerRow(
                findViewById(R.id.fajrRow),
                findViewById(R.id.fajrLabel),
                findViewById(R.id.fajrTime),
                findViewById(R.id.fajrTimer),
                beforeDivider = null,
                afterDivider = fajrDivider,
            ),
            Prayer.SUNRISE to PrayerRow(
                findViewById(R.id.sunriseRow),
                findViewById(R.id.sunriseLabel),
                findViewById(R.id.sunriseTime),
                findViewById(R.id.sunriseTimer),
                beforeDivider = fajrDivider,
                afterDivider = sunriseDivider,
            ),
            Prayer.DHUHR to PrayerRow(
                findViewById(R.id.dhuhrRow),
                findViewById(R.id.dhuhrLabel),
                findViewById(R.id.dhuhrTime),
                findViewById(R.id.dhuhrTimer),
                beforeDivider = sunriseDivider,
                afterDivider = dhuhrDivider,
            ),
            Prayer.ASR to PrayerRow(
                findViewById(R.id.asrRow),
                findViewById(R.id.asrLabel),
                findViewById(R.id.asrTime),
                findViewById(R.id.asrTimer),
                beforeDivider = dhuhrDivider,
                afterDivider = asrDivider,
            ),
            Prayer.MAGHRIB to PrayerRow(
                findViewById(R.id.maghribRow),
                findViewById(R.id.maghribLabel),
                findViewById(R.id.maghribTime),
                findViewById(R.id.maghribTimer),
                beforeDivider = asrDivider,
                afterDivider = maghribDivider,
            ),
            Prayer.ISHA to PrayerRow(
                findViewById(R.id.ishaRow),
                findViewById(R.id.ishaLabel),
                findViewById(R.id.ishaTime),
                findViewById(R.id.ishaTimer),
                beforeDivider = maghribDivider,
                afterDivider = null,
            ),
        )
    }

    private fun setupSwipeRefresh() {
        rootRefresh.setColorSchemeColors(getColor(R.color.color_primary), getColor(R.color.color_accent))
        rootRefresh.setProgressBackgroundColorSchemeColor(getColor(R.color.color_surface))
        rootRefresh.setOnRefreshListener { refreshFromPull() }
    }

    private fun refreshFromPull() {
        resetMosqueSearchCache()
        reloadSavedLocations()
        val activeLocation = AppSettings.selectedLocationId(this)
            ?.let(database::locationById)
            ?: selectedLocationId?.let(database::locationById)

        if (activeLocation != null) {
            applySavedLocation(activeLocation, persistSelection = false, resetDate = false)
        } else {
            if (selectedLocationSource == DEVICE_LOCATION_SOURCE && hasLocationPermission()) {
                readCurrentLocation()
            } else {
                refresh()
            }
        }
        rootRefresh.postDelayed({ rootRefresh.isRefreshing = false }, PULL_REFRESH_FINISH_DELAY_MS)
    }

    private fun setupButtons() {
        sunnahPanelHeader.setOnClickListener { toggleSunnahPanel() }
        updateSunnahPanelState()
        findViewById<View>(R.id.openHolidayCalendarButton).setOnClickListener { openHolidayCalendar() }
        findViewById<View>(R.id.openMonthTimesButton).setOnClickListener { openMonthPrayerTimes() }
        findViewById<View>(R.id.openSettingsButton).setOnClickListener { openSettings() }
        refreshMosquesButton.setOnClickListener { refreshCityMosquesManually() }
        findViewById<Button>(R.id.previousDayButton).setOnClickListener {
            selectedDate = selectedDate.minusDays(1)
            refresh()
        }
        findViewById<Button>(R.id.todayButton).setOnClickListener {
            selectedDate = LocalDate.now(selectedZoneId)
            refresh()
        }
        findViewById<Button>(R.id.nextDayButton).setOnClickListener {
            selectedDate = selectedDate.plusDays(1)
            refresh()
        }
    }

    private fun selectInitialLocation() {
        selectedLocationId = AppSettings.selectedLocationId(this)
        selectedLocationId?.let(database::locationById)?.let { location ->
            applySavedLocation(location, persistSelection = false)
            return
        }

        savedLocations.firstOrNull()?.let { location ->
            applySavedLocation(location, persistSelection = true)
            return
        }

        if (hasLocationPermission()) {
            readCurrentLocation()
            return
        }

        requestLocationPermission()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun toggleSunnahPanel() {
        sunnahPanelExpanded = !sunnahPanelExpanded
        updateSunnahPanelState()
    }

    private fun updateSunnahPanelState() {
        sunnahPanelBody.visibility = if (sunnahPanelExpanded) View.VISIBLE else View.GONE
        sunnahPanelChevron.rotation = if (sunnahPanelExpanded) 90f else 0f
        sunnahPanelHeader.contentDescription = getString(
            if (sunnahPanelExpanded) R.string.sunnah_collapse else R.string.sunnah_expand,
        )
    }

    private fun openHolidayCalendar() {
        startActivity(
            Intent(this, HolidayCalendarActivity::class.java).putExtra(
                HolidayCalendarActivity.EXTRA_YEAR,
                selectedDate.year,
            ),
        )
    }

    private fun openMonthPrayerTimes() {
        startActivity(
            MonthPrayerTimesActivity.intent(
                context = this,
                coordinates = selectedCoordinates,
                zoneId = selectedZoneId,
                method = selectedMethod,
                asrMethod = selectedAsrMethod,
                adjustments = selectedPrayerAdjustments(),
                hijriDayAdjustment = AppSettings.hijriDayAdjustment(this),
                locationLabel = selectedLocationLabel,
                date = selectedDate,
            ),
        )
    }

    private fun registerCompassSensors() {
        rotationVectorSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            return
        }

        accelerometerSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        magneticSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun updateAzimuthFromRotationVector(values: FloatArray) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        updateCompassAzimuth(rotationMatrix)
    }

    private fun updateAzimuthFromGravityAndMagnetic() {
        if (!hasGravityValues || !hasMagneticValues) {
            return
        }

        val hasRotationMatrix = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            gravityValues,
            magneticValues,
        )
        if (hasRotationMatrix) {
            updateCompassAzimuth(rotationMatrix)
        }
    }

    private fun updateCompassAzimuth(matrix: FloatArray) {
        SensorManager.getOrientation(matrix, orientationValues)
        qiblaCompass.deviceAzimuth = normalizeDegrees(
            Math.toDegrees(orientationValues[0].toDouble()).toFloat(),
        )
    }

    private fun reloadSavedLocations() {
        savedLocations = database.listLocations()
        selectedLocationId = AppSettings.selectedLocationId(this)
        updateLocationPicker()
    }

    private fun applySavedLocation(location: SavedLocation, persistSelection: Boolean, resetDate: Boolean = true) {
        selectedLocationId = location.id
        selectedCoordinates = location.coordinates
        selectedZoneId = location.zoneId
        selectedLocationLabel = savedLocationName(location)
        selectedLocationSource = location.source
        if (resetDate) {
            selectedDate = LocalDate.now(selectedZoneId)
        }
        if (persistSelection) {
            if (AppSettings.saveSelectedLocationId(this, location.id)) {
                settingsRevision = AppSettings.revision(this)
            } else {
                showToast(R.string.location_save_failed)
            }
        }
        resetMosqueSearchCache()
        updateLocationPicker()
        refresh()
    }

    private fun savedLocationName(location: SavedLocation): String =
        if (location.source == SEED_LOCATION_SOURCE) getString(R.string.default_location_name) else location.name

    private fun primaryLocationLabel(): String =
        BidiFormatter.getInstance().unicodeWrap(selectedLocationLabel)

    private fun updateLocationPicker() {
        if (!::locationSelectedText.isInitialized) {
            return
        }

        val selectedLocation = selectedLocationId?.let { id -> savedLocations.firstOrNull { it.id == id } }
        val label = selectedLocation
            ?.let(::savedLocationName)
            ?: primaryLocationLabel().ifBlank { getString(R.string.location_detecting) }
        val meta = selectedLocation
            ?.let { it.zoneId.id }
            ?: selectedZoneId.id

        locationSelectedText.text = label
        locationSelectedMetaText.text = BidiFormatter.getInstance().unicodeWrap(meta)
        locationPickerFrame.contentDescription = getString(R.string.label_value, getString(R.string.location_picker_title), label)
        applyLocationVisual(
            iconView = locationPickerIcon,
            colorKey = selectedLocation?.colorKey ?: LocationVisual.DEFAULT_COLOR_KEY,
            iconKey = selectedLocation?.iconKey ?: LocationVisual.DEFAULT_ICON_KEY,
            sizeDp = 38,
        )
    }

    private fun showLocationPickerDialog() {
        lateinit var dialog: AlertDialog
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val currentLabel = if (selectedLocationId == null) {
            primaryLocationLabel().ifBlank { getString(R.string.current_location) }
        } else {
            getString(R.string.use_location)
        }
        container.addView(
            locationChoiceRow(
                title = currentLabel,
                subtitle = if (selectedLocationId == null) selectedZoneId.id else getString(R.string.current_location),
                colorKey = LocationVisual.DEFAULT_COLOR_KEY,
                iconKey = LocationVisual.DEFAULT_ICON_KEY,
                selected = selectedLocationId == null,
            ) {
                dialog.dismiss()
                selectCurrentLocationFromPicker()
            },
        )

        if (savedLocations.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.no_saved_locations)
                setTextColor(getColor(R.color.color_muted))
                textSize = 14f
                setPadding(dp(12), dp(10), dp(12), dp(10))
            })
        } else {
            savedLocations.forEach { location ->
                container.addView(
                    locationChoiceRow(
                        title = savedLocationName(location),
                        subtitle = locationSubtitle(location),
                        colorKey = location.colorKey,
                        iconKey = location.iconKey,
                        selected = location.id == selectedLocationId,
                    ) {
                        dialog.dismiss()
                        if (location.id != selectedLocationId) {
                            applySavedLocation(location, persistSelection = true)
                        }
                    },
                )
            }
        }

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.location_picker_title)
            .setView(scrollView)
            .setNegativeButton(R.string.location_manage) { _, _ ->
                startActivity(Intent(this, LocationActivity::class.java))
            }
            .setPositiveButton(android.R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun selectCurrentLocationFromPicker() {
        if (!AppSettings.saveSelectedLocationId(this, null)) {
            showToast(R.string.location_save_failed)
            return
        }
        settingsRevision = AppSettings.revision(this)
        if (hasLocationPermission()) {
            readCurrentLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun locationChoiceRow(
        title: String,
        subtitle: String,
        colorKey: String,
        iconKey: String,
        selected: Boolean,
        action: () -> Unit,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(if (selected) R.drawable.current_prayer_background else R.drawable.row_background)
            isClickable = true
            isFocusable = true
            applySelectableForeground(this)
            setPaddingRelative(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener { action() }

            val icon = ImageView(this@MainActivity).apply {
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginEnd = dp(12)
                }
            }
            applyLocationVisual(icon, colorKey, iconKey, sizeDp = 40)

            val textColumn = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textColumn.addView(TextView(this@MainActivity).apply {
                text = BidiFormatter.getInstance().unicodeWrap(title)
                setTextColor(getColor(R.color.color_text))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
            })
            textColumn.addView(TextView(this@MainActivity).apply {
                text = BidiFormatter.getInstance().unicodeWrap(subtitle)
                setTextColor(getColor(R.color.color_muted))
                textSize = 12f
                setPadding(0, dp(3), 0, 0)
                maxLines = 1
            })

            addView(icon)
            addView(textColumn)
        }

    private fun locationSubtitle(location: SavedLocation): String =
        String.format(
            Locale.getDefault(),
            "%.5f, %.5f - %s",
            location.latitude,
            location.longitude,
            location.timeZone,
        )

    private fun applyLocationVisual(iconView: ImageView, colorKey: String, iconKey: String, sizeDp: Int) {
        val color = LocationVisualStyle.color(colorKey)
        val icon = LocationVisualStyle.icon(iconKey)
        iconView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color.color)
        }
        iconView.setImageResource(icon.drawableRes)
        iconView.setColorFilter(getColor(android.R.color.white))
        val padding = (sizeDp * LOCATION_ICON_PADDING_RATIO).toInt()
        iconView.setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    private fun applySelectableForeground(view: View) {
        android.util.TypedValue().also { value ->
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
                view.foreground = getDrawable(value.resourceId)
            }
        }
    }

    private fun labelValue(label: String, value: String): String =
        getString(R.string.label_value, label, BidiFormatter.getInstance().unicodeWrap(value))

    private fun syncPrayerNotifications() {
        notificationSyncController.syncIfNeeded(
            context = this,
            coordinates = selectedCoordinates,
            zoneId = selectedZoneId,
            method = selectedMethod,
            asrMethod = selectedAsrMethod,
            adjustments = selectedPrayerAdjustments(),
        )
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            LOCATION_REQUEST_CODE,
        )
    }

    @Suppress("MissingPermission")
    private fun handleNearbySavedLocation() {
        val manager = getSystemService(LocationManager::class.java)
        val location = LocationProviderSelector.enabledProviders(this, manager)
            .asSequence()
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull(Location::getTime)
            ?: return
        val switch = NearbyLocationSwitchResolver.resolve(
            savedLocations = savedLocations,
            activeLocationId = selectedLocationId,
            activeCoordinates = selectedCoordinates,
            activeLocationSource = selectedLocationSource,
            deviceCoordinates = Coordinates(location.latitude, location.longitude),
        ) ?: return
        val nearestLocation = switch.location

        if (AppSettings.autoSwitchLocation(this)) {
            applySavedLocation(nearestLocation, persistSelection = true, resetDate = false)
            showToast(getString(R.string.auto_location_switched, savedLocationName(nearestLocation)))
        } else if (AppSettings.suggestLocationSwitch(this)) {
            suggestNearbyLocationSwitch(nearestLocation)
        }
    }

    private fun suggestNearbyLocationSwitch(location: SavedLocation) {
        if (lastSuggestedLocationId == location.id || isFinishing || isDestroyed) {
            return
        }
        lastSuggestedLocationId = location.id
        AlertDialog.Builder(this)
            .setTitle(R.string.nearby_location_title)
            .setMessage(getString(R.string.nearby_location_message, savedLocationName(location)))
            .setPositiveButton(R.string.nearby_location_switch) { _, _ ->
                applySavedLocation(location, persistSelection = true, resetDate = false)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @Suppress("MissingPermission")
    private fun readCurrentLocation() {
        val manager = getSystemService(LocationManager::class.java)
        val providers = LocationProviderSelector.enabledProviders(this, manager)
        val lastLocation = providers
            .asSequence()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull(Location::getTime)

        if (lastLocation != null) {
            applyDeviceLocation(lastLocation)
            return
        }

        val provider = LocationProviderSelector.preferredCurrentProvider(providers)

        if (provider == null) {
            showToast(R.string.location_unavailable)
            applyBestFallbackLocation()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                manager.getCurrentLocation(provider, CancellationSignal(), mainExecutor) { location ->
                    if (location == null) {
                        showToast(R.string.location_unavailable)
                        applyBestFallbackLocation()
                    } else {
                        applyDeviceLocation(location)
                    }
                }
            }.onFailure {
                showToast(R.string.location_unavailable)
                applyBestFallbackLocation()
            }
        } else {
            requestSingleLocation(manager, provider)
        }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun requestSingleLocation(manager: LocationManager, provider: String) {
        runCatching {
            manager.requestSingleUpdate(
                provider,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        applyDeviceLocation(location)
                    }
                },
                mainLooper,
            )
        }.onFailure {
            showToast(R.string.location_unavailable)
            applyBestFallbackLocation()
        }
    }

    private fun applyDeviceLocation(location: Location) {
        selectedLocationId = null
        selectedCoordinates = Coordinates(location.latitude, location.longitude)
        selectedZoneId = TimeZoneResolver.byCountryOrCoordinates(null, selectedCoordinates)
        selectedLocationLabel = getString(R.string.current_location)
        selectedLocationSource = DEVICE_LOCATION_SOURCE
        selectedDate = LocalDate.now(selectedZoneId)
        resetMosqueSearchCache()
        updateLocationPicker()
        refresh()
        resolveDeviceLocationName(location)
    }

    private fun resolveDeviceLocationName(location: Location) {
        val generation = ++reverseLocationGeneration
        backgroundExecutor.execute {
            val resolved = runCatching {
                if (!Geocoder.isPresent()) {
                    return@runCatching null
                }
                @Suppress("DEPRECATION")
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.let { address ->
                        val name = listOfNotNull(
                            address.locality,
                            address.subAdminArea,
                            address.adminArea,
                            address.countryName,
                        ).distinct().take(2).joinToString()
                            .takeIf { it.isNotBlank() }
                        DeviceLocationResolution(
                            name = name,
                            zoneId = TimeZoneResolver.byCountryOrCoordinates(
                                address.countryCode,
                                Coordinates(location.latitude, location.longitude),
                            ),
                        )
                    }
            }.getOrNull()

            if (resolved != null) {
                runOnUiThread {
                    if (
                        generation == reverseLocationGeneration &&
                        !isFinishing &&
                        !isDestroyed &&
                        selectedLocationSource == DEVICE_LOCATION_SOURCE &&
                        sameCoordinates(location)
                    ) {
                        resolved.name?.let { selectedLocationLabel = it }
                        selectedZoneId = resolved.zoneId
                        resetMosqueSearchCache()
                        updateLocationPicker()
                        refresh()
                    }
                }
            }
        }
    }

    private fun sameCoordinates(location: Location): Boolean =
        kotlin.math.abs(selectedCoordinates.latitude - location.latitude) < LOCATION_EPSILON &&
            kotlin.math.abs(selectedCoordinates.longitude - location.longitude) < LOCATION_EPSILON

    private fun applyBestFallbackLocation() {
        savedLocations.firstOrNull()?.let {
            applySavedLocation(it, persistSelection = true)
            return
        }

        selectedLocationId = null
        selectedCoordinates = Coordinates(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        selectedZoneId = ZoneId.of(DEFAULT_ZONE_ID)
        selectedLocationLabel = getString(R.string.default_location_name)
        selectedLocationSource = "fallback"
        selectedDate = LocalDate.now(selectedZoneId)
        resetMosqueSearchCache()
        updateLocationPicker()
        refresh()
    }

    private fun refresh() {
        updateCalculationMethodForLocation()
        selectedIqamaOffsets = selectedLocationId?.let(database::iqamaOffsets) ?: PrayerAdjustments()
        val locale = Locale.getDefault()
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        val now = ZonedDateTime.now(selectedZoneId)
        val prayerDay = PrayerDayCalculator.calculate(
            coordinates = selectedCoordinates,
            date = selectedDate,
            zoneId = selectedZoneId,
            parameters = calculationParameters(),
            hijriDayAdjustment = AppSettings.hijriDayAdjustment(this),
            now = now,
        )
        renderedPrayerDay = prayerDay

        dateText.text = dateFormatter.format(selectedDate)
        val qiblaDirection = Qibla(selectedCoordinates).direction.toFloat()
        qiblaCompass.qiblaDirection = qiblaDirection
        qiblaText.text = labelValue(getString(R.string.qibla), getString(R.string.qibla_degrees, qiblaDirection))
        refreshCityMosquesIfNeeded()

        val prayerTimes = prayerDay.prayerTimes
        if (prayerTimes == null) {
            showUnavailableTimes(prayerDay, now)
        } else {
            showPrayerTimes(prayerDay, prayerTimes, now)
        }

        showHolidays()
        PrayerWidgetProvider.updateAll(this)
        syncPrayerNotifications()
    }

    private fun tickCurrentPrayerState() {
        val today = LocalDate.now(selectedZoneId)
        if (selectedDate != today) {
            return
        }

        val prayerDay = renderedPrayerDay
        if (prayerDay == null || prayerDay.date != today) {
            selectedDate = today
            refresh()
            return
        }

        val prayerTimes = prayerDay.prayerTimes ?: return
        val now = ZonedDateTime.now(selectedZoneId)
        if (now.toLocalDate() != prayerDay.date) {
            selectedDate = now.toLocalDate()
            refresh()
            return
        }

        val window = PrayerDayCalculator.activePrayerWindow(
            prayerTimes = prayerTimes,
            previousPrayerTimes = prayerDay.previousPrayerTimes,
            nextPrayerTimes = prayerDay.nextPrayerTimes,
            now = now,
        )
        updatePrayerTimers(prayerDay.timeline, isToday = true, now = now)
        highlightPrayer(window.currentPrayer)
        updateCurrentPrayerProgress(window, prayerDay.isRamadan)
    }

    private fun showPrayerTimes(
        prayerDay: PrayerDay,
        prayerTimes: PrayerTimes,
        now: ZonedDateTime,
    ) {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        val today = LocalDate.now(selectedZoneId)
        val showJumuah = shouldShowJumuah()
        setPrayerRow(Prayer.FAJR, prayerLabel(Prayer.FAJR, prayerDay.isRamadan), timeFormatter.format(prayerTimes.fajr))
        setPrayerRow(Prayer.SUNRISE, prayerLabel(Prayer.SUNRISE, prayerDay.isRamadan), timeFormatter.format(prayerTimes.sunrise))
        setPrayerRow(Prayer.DHUHR, prayerLabel(Prayer.DHUHR, prayerDay.isRamadan, showJumuah), timeFormatter.format(prayerTimes.dhuhr))
        setPrayerRow(Prayer.ASR, prayerLabel(Prayer.ASR, prayerDay.isRamadan), timeFormatter.format(prayerTimes.asr))
        setPrayerRow(Prayer.MAGHRIB, prayerLabel(Prayer.MAGHRIB, prayerDay.isRamadan), timeFormatter.format(prayerTimes.maghrib))
        setPrayerRow(Prayer.ISHA, prayerLabel(Prayer.ISHA, prayerDay.isRamadan), timeFormatter.format(prayerTimes.isha))
        showRamadanPanel(prayerTimes, prayerDay.isRamadan)
        updatePrayerTimers(prayerDay.timeline, selectedDate == today, now)
        showSunnahItems(
            prayerWindows = prayerDay.sunnahPrayerWindows,
            fastDays = prayerDay.sunnahFastDays,
            isToday = selectedDate == today,
            now = now,
        )

        if (selectedDate != today) {
            resetPrayerHighlight()
            showSelectedDayProgress(formatHijri(prayerDay.hijriDate))
            return
        }

        val window = prayerDay.activeWindow ?: return
        highlightPrayer(window.currentPrayer)
        updateCurrentPrayerProgress(window, prayerDay.isRamadan)
    }

    private fun setPrayerRow(prayer: Prayer, label: String, time: String) {
        val row = prayerRows.getValue(prayer)
        row.label.text = label
        row.time.text = time
    }

    private fun showUnavailableTimes(prayerDay: PrayerDay, now: ZonedDateTime) {
        val showJumuah = shouldShowJumuah()
        prayerRows.forEach { (prayer, row) ->
            row.label.text = prayerLabel(prayer, prayerDay.isRamadan, prayer == Prayer.DHUHR && showJumuah)
            row.time.text = "--:--"
            row.timer.text = getString(R.string.timer_selected_day)
        }
        resetPrayerHighlight()
        showSunnahItems(
            prayerWindows = emptyList(),
            fastDays = prayerDay.sunnahFastDays,
            isToday = selectedDate == LocalDate.now(selectedZoneId),
            now = now,
        )
        ramadanPanel.visibility = View.GONE
        showUnavailableProgress()
    }

    private fun showRamadanPanel(prayerTimes: PrayerTimes, isRamadan: Boolean) {
        if (!isRamadan || !AppSettings.ramadanMode(this)) {
            ramadanPanel.visibility = View.GONE
            return
        }
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        ramadanPanel.visibility = View.VISIBLE
        ramadanSuhoorText.text = getString(R.string.ramadan_suhoor_until, formatter.format(prayerTimes.fajr))
        ramadanIftarText.text = getString(R.string.ramadan_iftar_at, formatter.format(prayerTimes.maghrib))
        ramadanTarawihText.text = getString(R.string.ramadan_tarawih_after, formatter.format(prayerTimes.isha))
    }

    private fun updateCurrentPrayerProgress(window: ActivePrayerWindow, isRamadan: Boolean) {
        val currentLabel = prayerLabel(window.currentPrayer, isRamadan, window.currentPrayer == Prayer.DHUHR && shouldShowJumuah())
        val nextLabel = prayerLabel(window.nextPrayer, isRamadan, window.nextPrayer == Prayer.DHUHR && shouldShowJumuah())
        val color = prayerColor(window.currentPrayer)
        val progress = window.currentStartedAt?.let { startedAt ->
            val elapsedMillis = Duration.between(startedAt, ZonedDateTime.now(selectedZoneId)).toMillis().coerceAtLeast(0L)
            val leftMillis = window.currentLeft.toMillis().coerceAtLeast(0L)
            val totalMillis = elapsedMillis + leftMillis
            if (totalMillis <= 0L) 0f else elapsedMillis.toFloat() / totalMillis.toFloat()
        } ?: 0f

        prayerProgressView.progressColor = color
        prayerProgressView.progress = progress
        currentPrayerNameText.setTextColor(color)
        currentPrayerNameText.text = if (window.currentPrayer == Prayer.NONE) {
            getString(R.string.next_prayer, nextLabel)
        } else {
            currentLabel
        }
        currentPrayerTimeText.text = if (window.currentPrayer == Prayer.NONE) {
            getString(R.string.next_in, formatDuration(window.untilNext), nextLabel)
        } else {
            getString(R.string.active_left, formatDuration(window.currentLeft))
        }
    }

    private fun showSelectedDayProgress(hijriDateText: String) {
        prayerProgressView.progressColor = getColor(R.color.color_primary)
        prayerProgressView.progress = 0f
        currentPrayerNameText.setTextColor(getColor(R.color.color_text))
        currentPrayerNameText.text = getString(R.string.timer_selected_day)
        currentPrayerTimeText.text = getString(R.string.hijri_date_text, hijriDateText)
    }

    private fun showUnavailableProgress() {
        prayerProgressView.progressColor = getColor(R.color.color_muted)
        prayerProgressView.progress = 0f
        currentPrayerNameText.setTextColor(getColor(R.color.color_text))
        currentPrayerNameText.text = getString(R.string.calculation_unavailable)
        currentPrayerTimeText.text = "--:--"
    }

    private fun showSunnahItems(
        prayerWindows: List<SunnahPrayerWindow>,
        fastDays: List<SunnahFastDay>,
        isToday: Boolean,
        now: ZonedDateTime,
    ) {
        sunnahPrayerList.removeAllViews()
        if (prayerWindows.isEmpty() && fastDays.isEmpty()) {
            showSunnahUnavailable()
            return
        }

        prayerWindows.forEachIndexed { index, window ->
            sunnahPrayerList.addView(sunnahPrayerRow(window, isToday, now, index > 0))
        }
        fastDays.forEachIndexed { index, fastDay ->
            sunnahPrayerList.addView(
                sunnahFastRow(
                    fastDay = fastDay,
                    isToday = isToday,
                    hasTopMargin = prayerWindows.isNotEmpty() || index > 0,
                ),
            )
        }
    }

    private fun showSunnahUnavailable() {
        sunnahPrayerList.removeAllViews()
        sunnahPrayerList.addView(TextView(this).apply {
            text = getString(R.string.sunnah_unavailable)
            setTextColor(getColor(R.color.color_muted))
            textSize = 13f
            setPadding(0, dp(6), 0, 0)
        })
    }

    private fun sunnahPrayerRow(
        window: SunnahPrayerWindow,
        isToday: Boolean,
        now: ZonedDateTime,
        hasTopMargin: Boolean,
    ): LinearLayout {
        val active = isToday && !now.isBefore(window.start) && now.isBefore(window.end)
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        val timeRange = getString(
            R.string.sunnah_time_range,
            timeFormatter.format(window.start),
            timeFormatter.format(window.end),
        )

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(
                if (active) R.drawable.current_prayer_emphasis_background else R.drawable.row_background,
            )
            setPaddingRelative(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (hasTopMargin) {
                    topMargin = dp(8)
                }
            }

            addView(ImageView(context).apply {
                setImageResource(window.prayer.iconRes)
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    marginEnd = dp(12)
                }
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = getString(window.prayer.titleRes)
                    setTextColor(getColor(if (active) R.color.color_primary_dark else R.color.color_text))
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = getString(window.prayer.noteRes)
                    setTextColor(getColor(R.color.color_muted))
                    textSize = 12f
                    setPadding(0, dp(2), 0, 0)
                })
                addView(TextView(context).apply {
                    text = timeRange
                    setTextColor(getColor(R.color.color_primary_dark))
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, dp(4), 0, 0)
                })
            })

            addView(TextView(context).apply {
                text = sunnahStatusText(window, isToday, now)
                setTextColor(getColor(if (active) R.color.color_primary_dark else R.color.color_muted))
                textSize = 12f
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(8)
                }
            })
        }
    }

    private fun sunnahFastRow(
        fastDay: SunnahFastDay,
        isToday: Boolean,
        hasTopMargin: Boolean,
    ): LinearLayout {
        val active = isToday

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(
                if (active) R.drawable.current_prayer_emphasis_background else R.drawable.row_background,
            )
            setPaddingRelative(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (hasTopMargin) {
                    topMargin = dp(8)
                }
            }

            addView(ImageView(context).apply {
                setImageResource(fastDay.fast.iconRes)
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    marginEnd = dp(12)
                }
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = getString(fastDay.fast.titleRes)
                    setTextColor(getColor(if (active) R.color.color_primary_dark else R.color.color_text))
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = getString(fastDay.fast.noteRes)
                    setTextColor(getColor(R.color.color_muted))
                    textSize = 12f
                    setPadding(0, dp(2), 0, 0)
                })
                addView(TextView(context).apply {
                    text = getString(R.string.sunnah_fast_label)
                    setTextColor(getColor(R.color.color_primary_dark))
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, dp(4), 0, 0)
                })
            })

            addView(TextView(context).apply {
                text = getString(if (isToday) R.string.sunnah_fast_today else R.string.sunnah_fast_selected_day)
                setTextColor(getColor(if (active) R.color.color_primary_dark else R.color.color_muted))
                textSize = 12f
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(8)
                }
            })
        }
    }

    private fun sunnahStatusText(window: SunnahPrayerWindow, isToday: Boolean, now: ZonedDateTime): String =
        when {
            !isToday -> getString(R.string.sunnah_timer_selected_day)
            now.isBefore(window.start) -> getString(
                R.string.sunnah_timer_starts_in,
                formatDuration(Duration.between(now, window.start)),
            )
            now.isBefore(window.end) -> getString(
                R.string.sunnah_timer_active,
                formatDuration(Duration.between(now, window.end)),
            )
            else -> getString(R.string.sunnah_timer_passed)
        }

    private fun resetPrayerHighlight() {
        showPrayerDividers()
        prayerRows.values.forEach { row ->
            row.container.background = null
            updatePrayerRowLayout(row, PRAYER_ROW_HEIGHT_DP, verticalMarginDp = 0)
            row.label.setTextColor(getColor(R.color.color_text))
            row.label.textSize = 17f
            row.time.setTextColor(getColor(R.color.color_primary_dark))
            row.time.textSize = 22f
            row.timer.setTextColor(getColor(R.color.color_muted))
            row.timer.textSize = 12f
        }
    }

    private fun highlightPrayer(prayer: Prayer) {
        resetPrayerHighlight()
        prayerRows[prayer]?.let { row ->
            row.container.setBackgroundResource(R.drawable.current_prayer_emphasis_background)
            row.beforeDivider?.visibility = View.GONE
            row.afterDivider?.visibility = View.GONE
            updatePrayerRowLayout(row, CURRENT_PRAYER_ROW_HEIGHT_DP, CURRENT_PRAYER_ROW_VERTICAL_MARGIN_DP)
            row.label.setTextColor(getColor(R.color.color_primary_dark))
            row.label.textSize = 21f
            row.time.setTextColor(getColor(R.color.color_primary_dark))
            row.time.textSize = 30f
            row.timer.setTextColor(getColor(R.color.color_primary_dark))
            row.timer.textSize = 14f
        }
    }

    private fun updateCalculationMethodForLocation() {
        val selection = CalculationMethodSelector.select(
            automatic = AppSettings.automaticCalculationMethod(this),
            manualMethod = AppSettings.calculationMethod(this),
            coordinates = selectedCoordinates,
            zoneId = selectedZoneId,
            locationLabel = selectedLocationLabel,
        )
        val previousSelection = selectedMethodSelection
        val previousMethod = selectedMethod
        selectedMethodSelection = selection
        selectedMethod = selection.method
        if (previousSelection != selection || previousMethod != selection.method) {
            notificationSyncController.invalidate()
        }
        updateCalculationMethodText(selection)
    }

    private fun updateCalculationMethodText(selection: CalculationMethodSelection) {
        val methodLabel = UiText.methodLabel(this, selection.method)
        calculationMethodText.text = getString(
            if (selection.isAutomatic) R.string.calculation_method_current_auto else R.string.calculation_method_current_manual,
            methodLabel,
        )
    }

    private fun showPrayerDividers() {
        prayerRows.values.forEach { row ->
            row.beforeDivider?.visibility = View.VISIBLE
            row.afterDivider?.visibility = View.VISIBLE
        }
    }

    private fun updatePrayerRowLayout(row: PrayerRow, heightDp: Int, verticalMarginDp: Int) {
        val params = row.container.layoutParams
        params.height = dp(heightDp)
        if (params is ViewGroup.MarginLayoutParams) {
            val verticalMargin = dp(verticalMarginDp)
            params.topMargin = verticalMargin
            params.bottomMargin = verticalMargin
        }
        row.container.layoutParams = params
    }

    private fun showHolidays() {
        val hijriAdjustment = AppSettings.hijriDayAdjustment(this)
        val todayHolidays = HolidayCalendar.holidaysOn(selectedDate, hijriAdjustment)
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        val hijriDate = IslamicCalendar.hijriDate(selectedDate, hijriAdjustment)
        updateAppBackground(todayHolidays)

        if (todayHolidays.isEmpty()) {
            holidayPreviewTitle.text = getString(R.string.no_holiday_today)
            holidayPreviewDate.text = getString(R.string.hijri_date_text, formatHijri(hijriDate))
        } else {
            holidayPreviewTitle.text = todayHolidays.joinToString { holidayTitle(it) }
            holidayPreviewDate.text = getString(
                R.string.today_holiday_preview,
                formatter.format(selectedDate),
                formatHijri(hijriDate),
            )
        }

        val nextHoliday = HolidayCalendar.nextHolidayAfter(selectedDate, hijriAdjustment)
        nextHolidayText.text = if (nextHoliday == null) {
            getString(R.string.no_holidays_year)
        } else {
            getString(
                R.string.next_holiday_value,
                holidayTitle(nextHoliday),
                formatter.format(nextHoliday.date),
            )
        }
    }

    private fun updateAppBackground(todayHolidays: List<ru.admiral.praytimes.holiday.HolidayOccurrence>) {
        val backgroundRes = if (AppSettings.holidayBackgrounds(this)) {
            todayHolidays.firstOrNull()?.holiday?.backgroundRes ?: R.drawable.app_background_texture
        } else {
            R.drawable.app_background_texture
        }
        rootRefresh.setBackgroundResource(backgroundRes)
        rootScroll.setBackgroundResource(backgroundRes)
    }

    private fun refreshCityMosquesManually() {
        val now = System.currentTimeMillis()
        if (now - lastManualMosqueRefreshAtMillis < MANUAL_MOSQUE_REFRESH_INTERVAL_MS) {
            showToast(R.string.mosques_refresh_limited)
            return
        }

        lastManualMosqueRefreshAtMillis = now
        resetMosqueSearchCache()
        refreshCityMosquesIfNeeded()
    }

    private fun refreshCityMosquesIfNeeded() {
        val key = mosqueSearchKey()
        val now = System.currentTimeMillis()
        if (key == lastMosqueSearchKey || key == pendingMosqueSearchKey) {
            return
        }
        if (
            key == lastMosqueSearchFailureKey &&
            now - lastMosqueSearchFailureAtMillis < MOSQUE_SEARCH_RETRY_DELAY_MS
        ) {
            return
        }

        pendingMosqueSearchKey = key
        val generation = ++mosqueSearchGeneration
        val defaultMosqueName = getString(R.string.mosque_default_name)
        mosqueStatusText.visibility = View.VISIBLE
        mosqueStatusText.text = getString(R.string.mosques_loading)
        mosqueAdapter.submit(emptyList())

        backgroundExecutor.execute {
            val result = runCatching {
                mosqueSearchService.cityMosques(
                    coordinates = selectedCoordinates,
                    locale = Locale.getDefault(),
                    defaultName = defaultMosqueName,
                    locationLabel = selectedLocationLabel,
                )
            }
            runOnUiThread {
                if (generation != mosqueSearchGeneration || isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                pendingMosqueSearchKey = ""
                result.fold(
                    onSuccess = { mosques ->
                        lastMosqueSearchKey = key
                        lastMosqueSearchFailureKey = ""
                        showCityMosques(mosques)
                    },
                    onFailure = {
                        lastMosqueSearchFailureKey = key
                        lastMosqueSearchFailureAtMillis = System.currentTimeMillis()
                        showMosqueStatus(R.string.mosques_error)
                    },
                )
            }
        }
    }

    private fun resetMosqueSearchCache() {
        lastMosqueSearchKey = ""
        pendingMosqueSearchKey = ""
        lastMosqueSearchFailureKey = ""
        mosqueSearchGeneration++
    }

    private fun showCityMosques(mosques: List<Mosque>) {
        if (mosques.isEmpty()) {
            showMosqueStatus(R.string.mosques_empty)
            return
        }

        mosqueStatusText.visibility = View.GONE
        mosqueList.visibility = View.VISIBLE
        mosqueAdapter.submit(mosques)
    }

    private fun showMosqueStatus(messageRes: Int) {
        mosqueAdapter.submit(emptyList())
        mosqueList.visibility = View.GONE
        mosqueStatusText.visibility = View.VISIBLE
        mosqueStatusText.text = getString(messageRes)
    }

    private inner class MosqueAdapter : RecyclerView.Adapter<MosqueViewHolder>() {
        private var items: List<Mosque> = emptyList()

        fun submit(mosques: List<Mosque>) {
            items = mosques
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MosqueViewHolder {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = getDrawable(R.drawable.row_background)
                isClickable = true
                isFocusable = true
                setPaddingRelative(dp(12), dp(10), dp(12), dp(10))
                android.util.TypedValue().also { value ->
                    if (theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
                        foreground = getDrawable(value.resourceId)
                    }
                }
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(8)
                }
            }

            val icon = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_mosque)
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    marginEnd = dp(12)
                }
            }
            val textColumn = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val name = TextView(this@MainActivity).apply {
                setTextColor(getColor(R.color.color_text))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            }
            val address = TextView(this@MainActivity).apply {
                setTextColor(getColor(R.color.color_muted))
                textSize = 12f
                setPadding(0, dp(2), 0, 0)
                maxLines = 2
            }
            val distance = TextView(this@MainActivity).apply {
                setTextColor(getColor(R.color.color_primary_dark))
                textSize = 12f
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(dp(86), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(8)
                }
            }

            textColumn.addView(name)
            textColumn.addView(address)
            row.addView(icon)
            row.addView(textColumn)
            row.addView(distance)

            return MosqueViewHolder(row, name, address, distance)
        }

        override fun onBindViewHolder(holder: MosqueViewHolder, position: Int) {
            val mosque = items[position]
            holder.name.text = mosque.name
            holder.address.text = mosque.address.ifBlank { getString(R.string.mosque_no_address) }
            holder.distance.text = getString(R.string.mosque_distance, formatDistance(mosque.distanceMeters))
            holder.itemView.setOnClickListener { openMosqueMap(mosque) }
        }

        override fun getItemCount(): Int = items.size
    }

    private class MosqueViewHolder(
        itemView: View,
        val name: TextView,
        val address: TextView,
        val distance: TextView,
    ) : RecyclerView.ViewHolder(itemView)

    private fun mosqueSearchKey(): String =
        String.format(
            Locale.US,
            "%.4f|%.4f|%s|%s",
            selectedCoordinates.latitude,
            selectedCoordinates.longitude,
            Locale.getDefault().toLanguageTag(),
            selectedLocationLabel,
        )

    private fun openMosqueMap(mosque: Mosque) {
        val opened = mosqueMapIntents(mosque).any(::startExternalActivity)
        if (!opened) {
            showToast(R.string.mosque_map_unavailable)
        }
    }

    private fun mosqueMapIntents(mosque: Mosque): List<Intent> {
        val latitude = mosque.coordinates.latitude
        val longitude = mosque.coordinates.longitude
        val coordinateQuery = "$latitude,$longitude"
        val coordinateGeoUri = Uri.parse(
            "geo:$coordinateQuery?q=$coordinateQuery(${Uri.encode(mosque.name)})",
        )
        val addressQuery = mosqueAddressQuery(mosque)
        val webUri = if (addressQuery.isBlank()) {
            Uri.parse("https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=17/$latitude/$longitude")
        } else {
            Uri.parse("https://www.openstreetmap.org/search?query=${Uri.encode(addressQuery)}")
        }

        return buildList {
            if (addressQuery.isNotBlank()) {
                add(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(addressQuery)}")))
            }
            add(Intent(Intent.ACTION_VIEW, coordinateGeoUri))
            add(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun mosqueAddressQuery(mosque: Mosque): String =
        listOf(mosque.address, mosque.name, mosqueLocationLabelForSearch())
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(", ")

    private fun mosqueLocationLabelForSearch(): String =
        selectedLocationLabel.takeUnless { label ->
            label == getString(R.string.current_location) || label == getString(R.string.location_detecting)
        }.orEmpty()

    private fun startExternalActivity(intent: Intent): Boolean {
        return runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun formatDistance(distanceMeters: Double): String =
        if (distanceMeters < 1000.0) {
            String.format(Locale.getDefault(), "%.0f m", distanceMeters)
        } else {
            String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000.0)
        }

    private fun holidayTitle(holiday: ru.admiral.praytimes.holiday.HolidayOccurrence): String =
        UiText.holidayTitle(this, holiday)

    private fun formatHijri(hijriDate: HijriDate): String = UiText.formatHijri(this, hijriDate)

    private fun prayerLabel(prayer: Prayer, isRamadan: Boolean, isJumuah: Boolean = false): String =
        UiText.prayerLabel(this, prayer, isRamadan, isJumuah)

    private fun shouldShowJumuah(): Boolean =
        selectedDate.dayOfWeek == DayOfWeek.FRIDAY && AppSettings.showJumuahPrayer(this)

    private fun calculationParameters() =
        selectedMethod.parameters()
            .withAsrMethod(selectedAsrMethod)
            .copy(adjustments = selectedPrayerAdjustments())

    private fun selectedPrayerAdjustments() =
        selectedLocationId?.let(database::prayerAdjustments) ?: AppSettings.globalPrayerAdjustments(this)

    private fun prayerColor(prayer: Prayer): Int = getColor(
        when (prayer) {
            Prayer.FAJR -> R.color.color_info
            Prayer.SUNRISE -> R.color.color_accent
            Prayer.DHUHR -> R.color.color_primary
            Prayer.ASR -> R.color.color_rose
            Prayer.MAGHRIB -> R.color.color_accent
            Prayer.ISHA -> R.color.color_primary_dark
            Prayer.NONE -> R.color.color_muted
        },
    )

    private fun formatDuration(duration: Duration): String {
        val seconds = duration.seconds.coerceAtLeast(0)
        return getString(
            R.string.duration_hours_minutes_seconds,
            seconds / 3600,
            seconds % 3600 / 60,
            seconds % 60,
        )
    }

    private fun updatePrayerTimers(
        timeline: List<PrayerTimePoint>,
        isToday: Boolean,
        now: ZonedDateTime,
    ) {
        if (!isToday) {
            prayerRows.values.forEach { row ->
                row.timer.text = getString(R.string.timer_selected_day)
            }
            return
        }

        timeline.dropLast(1).forEachIndexed { index, point ->
            val row = prayerRows[point.prayer] ?: return@forEachIndexed
            val next = timeline[index + 1]
            val timerText = when {
                now.isBefore(point.time) -> getString(
                    R.string.timer_starts_in,
                    formatDuration(Duration.between(now, point.time)),
                )
                now.isBefore(next.time) -> getString(
                    R.string.timer_active,
                    formatDuration(Duration.between(now, next.time)),
                )
                else -> getString(R.string.timer_passed)
            }
            row.timer.text = timerWithIqama(timerText, point)
        }
    }

    private fun timerWithIqama(timerText: String, point: PrayerTimePoint): String {
        val offset = when (point.prayer) {
            Prayer.FAJR -> selectedIqamaOffsets.fajr
            Prayer.DHUHR -> selectedIqamaOffsets.dhuhr
            Prayer.ASR -> selectedIqamaOffsets.asr
            Prayer.MAGHRIB -> selectedIqamaOffsets.maghrib
            Prayer.ISHA -> selectedIqamaOffsets.isha
            Prayer.NONE,
            Prayer.SUNRISE,
            -> 0
        }
        if (offset <= 0) {
            return timerText
        }
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        val iqamaText = getString(R.string.iqama_time, formatter.format(point.time.plusMinutes(offset.toLong())))
        return getString(R.string.timer_with_iqama, timerText, iqamaText)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun normalizeDegrees(value: Float): Float {
        val normalized = value % FULL_ROTATION
        return if (normalized < 0f) normalized + FULL_ROTATION else normalized
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private data class PrayerRow(
        val container: View,
        val label: TextView,
        val time: TextView,
        val timer: TextView,
        val beforeDivider: View?,
        val afterDivider: View?,
    )

    private data class DeviceLocationResolution(
        val name: String?,
        val zoneId: ZoneId,
    )

    private companion object {
        const val LOCATION_REQUEST_CODE = 613
        const val TIMER_REFRESH_MS = 1_000L
        const val BACKGROUND_THREAD_COUNT = 2
        const val SENSOR_VECTOR_SIZE = 3
        const val ROTATION_MATRIX_SIZE = 9
        const val FULL_ROTATION = 360f
        const val PRAYER_ROW_HEIGHT_DP = 58
        const val CURRENT_PRAYER_ROW_HEIGHT_DP = 82
        const val CURRENT_PRAYER_ROW_VERTICAL_MARGIN_DP = 4
        const val LOCATION_ICON_PADDING_RATIO = 0.22
        const val PULL_REFRESH_FINISH_DELAY_MS = 450L
        const val MOSQUE_SEARCH_RETRY_DELAY_MS = 120_000L
        const val MANUAL_MOSQUE_REFRESH_INTERVAL_MS = 60_000L
        const val SEED_LOCATION_SOURCE = "seed"
        const val DEVICE_LOCATION_SOURCE = "device"
        const val DEFAULT_LATITUDE = 36.8121
        const val DEFAULT_LONGITUDE = 34.6415
        const val DEFAULT_ZONE_ID = "Europe/Istanbul"
        const val LOCATION_EPSILON = 0.00001
    }
}
