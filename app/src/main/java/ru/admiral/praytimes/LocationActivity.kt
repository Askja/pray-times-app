package ru.admiral.praytimes

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.text.BidiFormatter
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.data.City
import ru.admiral.praytimes.data.CityRepository
import ru.admiral.praytimes.data.GeocodedLocation
import ru.admiral.praytimes.data.GeocodingService
import ru.admiral.praytimes.data.LocationDatabase
import ru.admiral.praytimes.data.LocationProviderSelector
import ru.admiral.praytimes.data.LocationVisual
import ru.admiral.praytimes.data.SavedLocation
import ru.admiral.praytimes.data.TimeZoneResolver
import ru.admiral.praytimes.settings.AppSettings
import ru.admiral.praytimes.ui.LocationVisualStyle
import ru.admiral.praytimes.ui.SystemInsets
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class LocationActivity : Activity() {
    private lateinit var savedLocationSpinner: Spinner
    private lateinit var mapView: WebView
    private lateinit var mapStatusText: TextView
    private lateinit var citySearch: AutoCompleteTextView
    private lateinit var selectedLocationText: TextView
    private lateinit var selectedCoordinatesText: TextView
    private lateinit var sourceText: TextView
    private lateinit var locationNameEdit: EditText
    private lateinit var locationNoteEdit: EditText
    private lateinit var locationColorList: LinearLayout
    private lateinit var locationIconList: LinearLayout
    private lateinit var locationList: LinearLayout
    private lateinit var makeActiveButton: Button
    private lateinit var updateSelectedButton: Button
    private lateinit var database: LocationDatabase

    private val geocodingService = GeocodingService()
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cities: List<City> = emptyList()
    private var savedLocations: List<SavedLocation> = emptyList()
    private var selectedSavedLocationId: Long? = null
    private var suppressSpinnerCallback = false
    private var mapReady = false
    private var locationSearchGeneration = 0
    private var reverseGeocodeGeneration = 0
    private var draft = LocationDraft(
        name = "",
        coordinates = Coordinates(DEFAULT_LATITUDE, DEFAULT_LONGITUDE),
        zoneId = ZoneId.of(DEFAULT_ZONE_ID),
        source = SOURCE_MAP,
        colorKey = LocationVisual.DEFAULT_COLOR_KEY,
        iconKey = LocationVisual.DEFAULT_ICON_KEY,
        note = "",
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)
        SystemInsets.applyTo(findViewById(R.id.locationRoot))

        database = LocationDatabase(this)
        selectedSavedLocationId = AppSettings.selectedLocationId(this)
        cities = CityRepository.load(this)
        bindViews()
        setupSavedLocations()
        setupCitySearch()
        setupButtons()
        reloadSavedLocations()
        selectInitialDraft()
        setupMap()
        renderDraft(updateNameField = true)
        renderLocationList()
    }

    override fun onDestroy() {
        locationSearchGeneration++
        reverseGeocodeGeneration++
        backgroundExecutor.shutdownNow()
        if (::mapView.isInitialized) {
            mapView.removeJavascriptInterface(MAP_BRIDGE_NAME)
            mapView.stopLoading()
            mapView.destroy()
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            readPhoneLocation()
        } else if (requestCode == LOCATION_REQUEST_CODE) {
            showToast(R.string.location_permission_required)
        }
    }

    private fun bindViews() {
        savedLocationSpinner = findViewById(R.id.savedLocationSpinner)
        mapView = findViewById(R.id.mapView)
        mapStatusText = findViewById(R.id.mapStatusText)
        citySearch = findViewById(R.id.citySearch)
        selectedLocationText = findViewById(R.id.selectedLocationText)
        selectedCoordinatesText = findViewById(R.id.selectedCoordinatesText)
        sourceText = findViewById(R.id.sourceText)
        locationNameEdit = findViewById(R.id.locationNameEdit)
        locationNoteEdit = findViewById(R.id.locationNoteEdit)
        locationColorList = findViewById(R.id.locationColorList)
        locationIconList = findViewById(R.id.locationIconList)
        locationList = findViewById(R.id.locationList)
        makeActiveButton = findViewById(R.id.makeActiveButton)
        updateSelectedButton = findViewById(R.id.updateSelectedButton)
    }

    private fun setupSavedLocations() {
        savedLocationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!suppressSpinnerCallback) {
                    savedLocations.getOrNull(position)?.let(::selectSavedLocation)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupCitySearch() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            cities.map { it.displayName },
        )
        citySearch.setAdapter(adapter)
        citySearch.setOnItemClickListener { _, _, position, _ ->
            CityRepository.findBestMatch(cities, adapter.getItem(position).orEmpty())?.let(::applyCity)
        }
    }

    private fun setupButtons() {
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        makeActiveButton.setOnClickListener { makeSelectedLocationActive() }
        findViewById<Button>(R.id.findCityButton).setOnClickListener { findLocation(citySearch.text.toString()) }
        findViewById<Button>(R.id.usePhoneLocationButton).setOnClickListener { usePhoneLocation() }
        findViewById<Button>(R.id.saveNewButton).setOnClickListener { saveDraftAsNew() }
        updateSelectedButton.setOnClickListener { updateSelectedLocation() }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMap() {
        mapStatusText.text = getString(R.string.location_map_loading)
        mapView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = false
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
        mapView.addJavascriptInterface(MapBridge(), MAP_BRIDGE_NAME)
        mapView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                request?.isForMainFrame == true && request.url.toString() != MAP_BASE_URL

            override fun onPageFinished(view: WebView?, url: String?) {
                mapReady = true
                mapStatusText.text = getString(R.string.location_map_hint)
                updateMapMarker()
            }
        }
        mapView.loadDataWithBaseURL(MAP_BASE_URL, mapHtml(), "text/html", "UTF-8", null)
    }

    private fun reloadSavedLocations() {
        savedLocations = database.listLocations()
        if (selectedSavedLocationId != null && savedLocations.none { it.id == selectedSavedLocationId }) {
            selectedSavedLocationId = null
        }

        suppressSpinnerCallback = true
        savedLocationSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            savedLocations.map { it.name }.ifEmpty { listOf(getString(R.string.no_saved_locations)) },
        )
        val index = selectedSavedLocationId
            ?.let { id -> savedLocations.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        savedLocationSpinner.setSelection(index)
        suppressSpinnerCallback = false
    }

    private fun selectInitialDraft() {
        val active = selectedSavedLocationId?.let(database::locationById)
        val fallback = active ?: savedLocations.firstOrNull()
        if (fallback == null) {
            draft = draft.copy(name = getString(R.string.location_selected_from_map))
            return
        }

        selectedSavedLocationId = fallback.id
        draft = fallback.toDraft()
    }

    private fun selectSavedLocation(location: SavedLocation) {
        selectedSavedLocationId = location.id
        draft = location.toDraft()
        renderDraft(updateNameField = true)
        updateMapMarker()
        renderLocationList()
    }

    private fun makeSelectedLocationActive() {
        val id = selectedSavedLocationId
        if (id == null || savedLocations.none { it.id == id }) {
            showToast(R.string.location_select_saved_first)
            return
        }

        if (!activateSavedLocation(id)) {
            showToast(R.string.location_save_failed)
            return
        }
        showToast(R.string.location_updated)
    }

    private fun activateSavedLocation(id: Long): Boolean {
        if (!AppSettings.saveSelectedLocationId(this, id)) {
            return false
        }

        selectedSavedLocationId = id
        reloadSavedLocations()
        renderLocationList()
        return true
    }

    private fun findLocation(query: String) {
        if (query.trim().isEmpty()) {
            showToast(R.string.city_not_found)
            return
        }

        val city = CityRepository.findBestMatch(cities, query)
        if (city != null) {
            applyCity(city)
            return
        }

        showToast(R.string.location_searching)
        val generation = ++locationSearchGeneration
        backgroundExecutor.execute {
            val result = geocodingService.search(query, Locale.getDefault())
            runOnUiThread {
                if (generation != locationSearchGeneration || isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                if (result == null) {
                    showToast(R.string.geocoder_unavailable)
                } else {
                    applyGeocodedLocation(result)
                }
            }
        }
    }

    private fun applyCity(city: City) {
        selectedSavedLocationId = null
        draft = LocationDraft(
            name = city.displayName,
            coordinates = city.coordinates,
            zoneId = city.zoneId,
            source = SOURCE_CITY_DATABASE,
            colorKey = LocationVisual.DEFAULT_COLOR_KEY,
            iconKey = "city",
            note = "",
        )
        renderDraft(updateNameField = true)
        updateMapMarker()
        renderLocationList()
    }

    private fun applyGeocodedLocation(location: GeocodedLocation) {
        selectedSavedLocationId = null
        draft = LocationDraft(
            name = location.name,
            coordinates = location.coordinates,
            zoneId = location.zoneId,
            source = SOURCE_GEOCODER,
            colorKey = LocationVisual.DEFAULT_COLOR_KEY,
            iconKey = LocationVisual.DEFAULT_ICON_KEY,
            note = "",
        )
        renderDraft(updateNameField = true)
        updateMapMarker()
        renderLocationList()
    }

    private fun usePhoneLocation() {
        if (!hasLocationPermission()) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                LOCATION_REQUEST_CODE,
            )
            return
        }

        readPhoneLocation()
    }

    @Suppress("MissingPermission")
    private fun readPhoneLocation() {
        val manager = getSystemService(LocationManager::class.java)
        val providers = LocationProviderSelector.enabledProviders(this, manager)
        val lastLocation = providers
            .asSequence()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull(Location::getTime)

        if (lastLocation != null) {
            applyPhoneLocation(lastLocation)
            return
        }

        val provider = LocationProviderSelector.preferredCurrentProvider(providers)

        if (provider == null) {
            showToast(R.string.location_unavailable)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                manager.getCurrentLocation(provider, CancellationSignal(), mainExecutor) { location ->
                    if (location == null) {
                        showToast(R.string.location_unavailable)
                    } else {
                        applyPhoneLocation(location)
                    }
                }
            }.onFailure {
                showToast(R.string.location_unavailable)
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
                        applyPhoneLocation(location)
                    }
                },
                mainLooper,
            )
        }.onFailure {
            showToast(R.string.location_unavailable)
        }
    }

    private fun applyPhoneLocation(location: Location) {
        val coordinates = Coordinates(location.latitude, location.longitude)
        selectedSavedLocationId = null
        draft = LocationDraft(
            name = getString(R.string.current_location),
            coordinates = coordinates,
            zoneId = TimeZoneResolver.byCountryOrCoordinates(null, coordinates),
            source = SOURCE_PHONE,
            colorKey = LocationVisual.DEFAULT_COLOR_KEY,
            iconKey = LocationVisual.DEFAULT_ICON_KEY,
            note = "",
        )
        renderDraft(updateNameField = true)
        updateMapMarker()
        renderLocationList()
        showToast(R.string.location_phone_applied)
        resolveDraftNameFromCoordinates(draft.coordinates)
    }

    private fun applyMapLocation(latitude: Double, longitude: Double) {
        if (latitude !in MIN_LATITUDE..MAX_LATITUDE || longitude !in MIN_LONGITUDE..MAX_LONGITUDE) {
            return
        }
        val coordinates = Coordinates(latitude, longitude)
        val existingMapLocation = selectedSavedLocationId?.let { id ->
            savedLocations.firstOrNull { it.id == id && it.source == SOURCE_MAP }
        }
        selectedSavedLocationId = existingMapLocation?.id
        draft = LocationDraft(
            name = getString(R.string.location_selected_from_map),
            coordinates = coordinates,
            zoneId = TimeZoneResolver.byCountryOrCoordinates(null, coordinates),
            source = SOURCE_MAP,
            colorKey = existingMapLocation?.colorKey ?: LocationVisual.DEFAULT_COLOR_KEY,
            iconKey = existingMapLocation?.iconKey ?: LocationVisual.DEFAULT_ICON_KEY,
            note = existingMapLocation?.note.orEmpty(),
        )
        renderDraft(updateNameField = true)
        saveDraftAsActive(showToast = true)
        resolveDraftNameFromCoordinates(draft.coordinates)
    }

    private fun resolveDraftNameFromCoordinates(coordinates: Coordinates) {
        val generation = ++reverseGeocodeGeneration
        backgroundExecutor.execute {
            val resolved = runCatching {
                if (!Geocoder.isPresent()) {
                    return@runCatching null
                }
                @Suppress("DEPRECATION")
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(coordinates.latitude, coordinates.longitude, 1)
                    ?.firstOrNull()
                    ?.let { address ->
                        val label = listOfNotNull(
                            address.locality,
                            address.subAdminArea,
                            address.adminArea,
                            address.countryName,
                        ).filter { it.isNotBlank() }.distinct().take(2).joinToString()
                        val zoneId = TimeZoneResolver.byCountryOrCoordinates(
                            address.countryCode,
                            coordinates,
                        )
                        ReverseLocation(label.ifBlank { null }, zoneId)
                    }
            }.getOrNull()

            if (resolved != null) {
                runOnUiThread {
                    if (
                        generation == reverseGeocodeGeneration &&
                        !isFinishing &&
                        !isDestroyed &&
                        sameCoordinates(draft.coordinates, coordinates)
                    ) {
                        draft = draft.copy(
                            name = resolved.name ?: draft.name,
                            zoneId = resolved.zoneId,
                        )
                        renderDraft(updateNameField = resolved.name != null)
                        updateSelectedDraft()
                    }
                }
            }
        }
    }

    private fun saveDraftAsActive(showToast: Boolean): Boolean {
        val name = locationNameEdit.text.toString().trim().ifBlank { draft.name }
        val note = locationNoteEdit.text.toString().trim()
        if (name.isBlank()) {
            showToast(R.string.location_empty_name)
            return false
        }

        draft = draft.copy(name = name, note = note)
        val existingId = selectedSavedLocationId?.takeIf { selectedId ->
            savedLocations.any { it.id == selectedId }
        }
        val id = if (existingId == null) {
            database.saveLocation(
                name = name,
                latitude = draft.coordinates.latitude,
                longitude = draft.coordinates.longitude,
                zoneId = draft.zoneId,
                source = draft.source,
                colorKey = draft.colorKey,
                iconKey = draft.iconKey,
                note = draft.note,
            ) ?: run {
                showToast(R.string.location_save_failed)
                return false
            }
        } else {
            if (!database.updateLocation(
                    id = existingId,
                    name = name,
                    latitude = draft.coordinates.latitude,
                    longitude = draft.coordinates.longitude,
                    zoneId = draft.zoneId,
                    source = draft.source,
                    colorKey = draft.colorKey,
                    iconKey = draft.iconKey,
                    note = draft.note,
                )
            ) {
                showToast(R.string.location_save_failed)
                return false
            }
            existingId
        }

        selectedSavedLocationId = id
        reloadSavedLocations()
        if (!AppSettings.saveSelectedLocationId(this, id)) {
            showToast(R.string.location_save_failed)
            return false
        }
        renderLocationList()
        if (showToast) {
            showToast(R.string.location_updated)
        }
        return true
    }

    private fun updateSelectedDraft() {
        val id = selectedSavedLocationId?.takeIf { selectedId ->
            savedLocations.any { it.id == selectedId }
        } ?: return
        if (!database.updateLocation(
                id = id,
                name = draft.name,
                latitude = draft.coordinates.latitude,
                longitude = draft.coordinates.longitude,
                zoneId = draft.zoneId,
                source = draft.source,
                colorKey = draft.colorKey,
                iconKey = draft.iconKey,
                note = draft.note,
            )
        ) {
            return
        }
        reloadSavedLocations()
        renderLocationList()
    }

    private fun saveDraftAsNew() {
        val name = locationNameEdit.text.toString().trim()
        val note = locationNoteEdit.text.toString().trim()
        if (name.isEmpty()) {
            showToast(R.string.location_empty_name)
            return
        }

        draft = draft.copy(name = name, note = note)
        val id = database.saveLocation(
            name = name,
            latitude = draft.coordinates.latitude,
            longitude = draft.coordinates.longitude,
            zoneId = draft.zoneId,
            source = draft.source,
            colorKey = draft.colorKey,
            iconKey = draft.iconKey,
            note = draft.note,
        ) ?: run {
            showToast(R.string.location_save_failed)
            return
        }
        selectedSavedLocationId = id
        reloadSavedLocations()
        renderLocationList()
        if (!AppSettings.saveSelectedLocationId(this, id)) {
            showToast(R.string.location_save_failed)
            return
        }
        showToast(R.string.location_saved)
    }

    private fun updateSelectedLocation() {
        val id = selectedSavedLocationId
        if (id == null || savedLocations.none { it.id == id }) {
            showToast(R.string.location_select_saved_first)
            return
        }

        val name = locationNameEdit.text.toString().trim()
        val note = locationNoteEdit.text.toString().trim()
        if (name.isEmpty()) {
            showToast(R.string.location_empty_name)
            return
        }

        draft = draft.copy(name = name, note = note)
        val updated = database.updateLocation(
            id = id,
            name = name,
            latitude = draft.coordinates.latitude,
            longitude = draft.coordinates.longitude,
            zoneId = draft.zoneId,
            source = draft.source,
            colorKey = draft.colorKey,
            iconKey = draft.iconKey,
            note = draft.note,
        )
        if (!updated) {
            showToast(R.string.location_save_failed)
            return
        }
        if (!AppSettings.saveSelectedLocationId(this, id)) {
            showToast(R.string.location_save_failed)
            return
        }
        reloadSavedLocations()
        renderLocationList()
        showToast(R.string.location_updated)
    }

    private fun deleteLocation(location: SavedLocation) {
        if (!database.deleteLocation(location.id)) {
            return
        }

        if (selectedSavedLocationId == location.id) {
            selectedSavedLocationId = null
        }
        if (AppSettings.selectedLocationId(this) == location.id) {
            AppSettings.saveSelectedLocationId(this, null)
        }
        reloadSavedLocations()
        selectInitialDraft()
        renderDraft(updateNameField = true)
        updateMapMarker()
        renderLocationList()
        showToast(R.string.location_deleted)
    }

    private fun renderDraft(updateNameField: Boolean) {
        selectedLocationText.text = BidiFormatter.getInstance().unicodeWrap(draft.name)
        selectedCoordinatesText.text = getString(
            R.string.label_value,
            getString(R.string.coordinates),
            String.format(Locale.getDefault(), "%.5f, %.5f", draft.coordinates.latitude, draft.coordinates.longitude),
        )
        sourceText.text = getString(R.string.label_value, getString(R.string.location_source), sourceLabel(draft.source))
        if (updateNameField) {
            locationNameEdit.setText(draft.name)
            locationNameEdit.setSelection(locationNameEdit.text.length)
            locationNoteEdit.setText(draft.note)
            locationNoteEdit.setSelection(locationNoteEdit.text.length)
        }
        renderAppearanceSelectors()
    }

    private fun renderAppearanceSelectors() {
        locationColorList.removeAllViews()
        LocationVisualStyle.colors.forEach { color ->
            locationColorList.addView(colorChip(color.key, color.color, color.key == draft.colorKey))
        }

        locationIconList.removeAllViews()
        LocationVisualStyle.icons.forEach { icon ->
            locationIconList.addView(iconChip(icon.key, icon.drawableRes, icon.key == draft.iconKey))
        }
    }

    private fun colorChip(colorKey: String, color: Int, selected: Boolean): View =
        View(this).apply {
            background = chipBackground(color, selected)
            contentDescription = getString(R.string.location_color)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                draft = draft.copy(colorKey = colorKey)
                renderAppearanceSelectors()
                renderLocationList()
            }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginEnd = dp(8)
            }
        }

    private fun iconChip(iconKey: String, drawableRes: Int, selected: Boolean): ImageView =
        ImageView(this).apply {
            background = chipBackground(getColor(R.color.color_surface), selected)
            setImageResource(drawableRes)
            setColorFilter(LocationVisualStyle.color(draft.colorKey).color)
            contentDescription = getString(R.string.location_icon)
            isClickable = true
            isFocusable = true
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                draft = draft.copy(iconKey = iconKey)
                renderAppearanceSelectors()
                renderLocationList()
            }
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                marginEnd = dp(8)
            }
        }

    private fun chipBackground(color: Int, selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(color)
            setStroke(dp(if (selected) 3 else 1), getColor(if (selected) R.color.color_primary else R.color.color_divider))
        }

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

    private fun renderLocationList() {
        syncActionButtons()
        locationList.removeAllViews()
        if (savedLocations.isEmpty()) {
            locationList.addView(
                TextView(this).apply {
                    text = getString(R.string.location_no_saved_manage)
                    setTextColor(getColor(R.color.color_muted))
                    textSize = 14f
                    setPadding(0, dp(6), 0, dp(6))
                },
            )
            return
        }

        savedLocations.forEach { location ->
            locationList.addView(locationRow(location))
        }
    }

    private fun syncActionButtons() {
        val hasSelectedSavedLocation = selectedSavedLocationId?.let { selectedId ->
            savedLocations.any { it.id == selectedId }
        } ?: false
        val canMakeActive = hasSelectedSavedLocation && selectedSavedLocationId != AppSettings.selectedLocationId(this)
        makeActiveButton.visibility = if (canMakeActive) View.VISIBLE else View.GONE
        setButtonEnabled(makeActiveButton, canMakeActive)
        setButtonEnabled(updateSelectedButton, hasSelectedSavedLocation)
    }

    private fun setButtonEnabled(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1.0f else 0.55f
    }

    private fun locationRow(location: SavedLocation): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(
                if (location.id == selectedSavedLocationId) {
                    R.drawable.current_prayer_background
                } else {
                    R.drawable.row_background
                },
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(8)
            }

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(ImageView(context).apply {
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                        marginEnd = dp(10)
                    }
                    applyLocationVisual(this, location.colorKey, location.iconKey, sizeDp = 38)
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(context).apply {
                        text = location.name
                        setTextColor(getColor(R.color.color_text))
                        textSize = 16f
                        setTypeface(typeface, Typeface.BOLD)
                        maxLines = 1
                    })
                    addView(TextView(context).apply {
                        text = String.format(
                            Locale.getDefault(),
                            "%.5f, %.5f - %s",
                            location.latitude,
                            location.longitude,
                            location.timeZone,
                        )
                        setTextColor(getColor(R.color.color_muted))
                        textSize = 12f
                        setPadding(0, dp(3), 0, 0)
                        maxLines = 1
                    })
                    if (location.note.isNotBlank()) {
                        addView(TextView(context).apply {
                            text = location.note
                            setTextColor(getColor(R.color.color_muted))
                            textSize = 12f
                            setPadding(0, dp(3), 0, 0)
                            maxLines = 2
                        })
                    }
                })
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
                val activeLocationId = AppSettings.selectedLocationId(this@LocationActivity)
                if (location.id != activeLocationId) {
                    addView(rowButton(getString(R.string.location_make_active), true) {
                        selectSavedLocation(location)
                        if (activateSavedLocation(location.id)) {
                            showToast(R.string.location_updated)
                        } else {
                            showToast(R.string.location_save_failed)
                        }
                    })
                }
                addView(rowButton(getString(R.string.location_delete), false) {
                    deleteLocation(location)
                }.apply {
                    if (location.id != activeLocationId) {
                        (layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
                    }
                })
            })
        }

    private fun rowButton(label: String, primary: Boolean, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(getColor(if (primary) android.R.color.white else R.color.color_primary))
            background = getDrawable(if (primary) R.drawable.button_primary else R.drawable.button_outline)
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(44)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            setOnClickListener { action() }
        }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun updateMapMarker() {
        if (!mapReady) {
            return
        }
        val js = String.format(
            Locale.US,
            "if (window.setMarker) window.setMarker(%.8f, %.8f);",
            draft.coordinates.latitude,
            draft.coordinates.longitude,
        )
        mapView.evaluateJavascript(js, null)
    }

    private fun mapHtml(): String {
        val latitude = String.format(Locale.US, "%.8f", draft.coordinates.latitude)
        val longitude = String.format(Locale.US, "%.8f", draft.coordinates.longitude)
        return """
            <!doctype html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
                <style>
                    html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; overflow: hidden; background: #DCEFE8; }
                    #map { position: relative; font-family: sans-serif; touch-action: manipulation; }
                    #tiles { position: absolute; inset: 0; overflow: hidden; }
                    #tiles img { position: absolute; width: 256px; height: 256px; user-select: none; }
                    #marker {
                        position: absolute;
                        width: 26px;
                        height: 26px;
                        margin-left: -13px;
                        margin-top: -26px;
                        border-radius: 13px 13px 13px 0;
                        background: #0E6A58;
                        border: 3px solid #FFFCF5;
                        box-sizing: border-box;
                        transform: rotate(-45deg);
                        box-shadow: 0 3px 8px rgba(0, 0, 0, .25);
                    }
                    #marker::after {
                        content: "";
                        position: absolute;
                        width: 8px;
                        height: 8px;
                        left: 6px;
                        top: 6px;
                        border-radius: 50%;
                        background: #F4C66E;
                    }
                    #hint {
                        position: absolute;
                        left: 8px;
                        bottom: 8px;
                        padding: 4px 7px;
                        border-radius: 6px;
                        background: rgba(255, 252, 245, .92);
                        color: #12201C;
                        font-size: 10px;
                    }
                </style>
            </head>
            <body>
                <div id="map">
                    <div id="tiles"></div>
                    <div id="marker"></div>
                    <div id="hint">OpenStreetMap</div>
                </div>
                <script>
                    const tileSize = 256;
                    const zoom = 12;
                    const map = document.getElementById('map');
                    const tiles = document.getElementById('tiles');
                    const marker = document.getElementById('marker');
                    let centerLat = Number($latitude);
                    let centerLng = Number($longitude);
                    let markerLat = centerLat;
                    let markerLng = centerLng;

                    function clampLat(lat) {
                        return Math.max(-85.05112878, Math.min(85.05112878, lat));
                    }

                    function project(lat, lng) {
                        const sin = Math.sin(clampLat(lat) * Math.PI / 180);
                        const scale = tileSize * Math.pow(2, zoom);
                        return {
                            x: (lng + 180) / 360 * scale,
                            y: (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * scale
                        };
                    }

                    function unproject(x, y) {
                        const scale = tileSize * Math.pow(2, zoom);
                        const lng = x / scale * 360 - 180;
                        const n = Math.PI - 2 * Math.PI * y / scale;
                        const lat = 180 / Math.PI * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
                        return { lat: clampLat(lat), lng: Math.max(-180, Math.min(180, lng)) };
                    }

                    function send(lat, lng) {
                        AndroidMap.onMapPicked(Number(lat), Number(lng));
                    }

                    function render() {
                        const width = map.clientWidth || 1;
                        const height = map.clientHeight || 1;
                        const center = project(centerLat, centerLng);
                        const topLeft = { x: center.x - width / 2, y: center.y - height / 2 };
                        const count = Math.pow(2, zoom);
                        tiles.innerHTML = '';
                        const startX = Math.floor(topLeft.x / tileSize);
                        const endX = Math.floor((topLeft.x + width) / tileSize);
                        const startY = Math.max(0, Math.floor(topLeft.y / tileSize));
                        const endY = Math.min(count - 1, Math.floor((topLeft.y + height) / tileSize));
                        for (let x = startX; x <= endX; x++) {
                            const wrappedX = ((x % count) + count) % count;
                            for (let y = startY; y <= endY; y++) {
                                const image = document.createElement('img');
                                image.alt = '';
                                image.draggable = false;
                                image.src = 'https://tile.openstreetmap.org/' + zoom + '/' + wrappedX + '/' + y + '.png';
                                image.style.left = Math.round(x * tileSize - topLeft.x) + 'px';
                                image.style.top = Math.round(y * tileSize - topLeft.y) + 'px';
                                tiles.appendChild(image);
                            }
                        }
                        const markerPoint = project(markerLat, markerLng);
                        marker.style.left = Math.round(markerPoint.x - topLeft.x) + 'px';
                        marker.style.top = Math.round(markerPoint.y - topLeft.y) + 'px';
                    }

                    function pick(event) {
                        const rect = map.getBoundingClientRect();
                        const center = project(centerLat, centerLng);
                        const point = unproject(
                            center.x - rect.width / 2 + event.clientX - rect.left,
                            center.y - rect.height / 2 + event.clientY - rect.top
                        );
                        centerLat = point.lat;
                        centerLng = point.lng;
                        markerLat = point.lat;
                        markerLng = point.lng;
                        render();
                        send(point.lat, point.lng);
                    }

                    map.addEventListener('click', pick);
                    window.setMarker = function(lat, lng) {
                        centerLat = clampLat(Number(lat));
                        centerLng = Math.max(-180, Math.min(180, Number(lng)));
                        markerLat = centerLat;
                        markerLng = centerLng;
                        render();
                    };
                    window.addEventListener('resize', render);
                    render();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun sourceLabel(source: String): String = when (source) {
        SOURCE_MAP -> getString(R.string.location_source_map)
        SOURCE_PHONE -> getString(R.string.location_source_phone)
        SOURCE_CITY_DATABASE -> getString(R.string.location_source_city_database)
        SOURCE_GEOCODER -> getString(R.string.location_source_geocoder)
        else -> source
    }

    private fun SavedLocation.toDraft(): LocationDraft =
        LocationDraft(
            name = name,
            coordinates = coordinates,
            zoneId = zoneId,
            source = source,
            colorKey = colorKey,
            iconKey = iconKey,
            note = note,
        )

    private fun sameCoordinates(left: Coordinates, right: Coordinates): Boolean =
        abs(left.latitude - right.latitude) < LOCATION_EPSILON &&
            abs(left.longitude - right.longitude) < LOCATION_EPSILON

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private inner class MapBridge {
        @JavascriptInterface
        fun onMapPicked(latitude: Double, longitude: Double) {
            if (latitude !in MIN_LATITUDE..MAX_LATITUDE || longitude !in MIN_LONGITUDE..MAX_LONGITUDE) {
                return
            }
            runOnUiThread {
                applyMapLocation(latitude, longitude)
            }
        }
    }

    private data class LocationDraft(
        val name: String,
        val coordinates: Coordinates,
        val zoneId: ZoneId,
        val source: String,
        val colorKey: String,
        val iconKey: String,
        val note: String,
    )

    private data class ReverseLocation(
        val name: String?,
        val zoneId: ZoneId,
    )

    private companion object {
        const val LOCATION_REQUEST_CODE = 614
        const val DEFAULT_LATITUDE = 36.8121
        const val DEFAULT_LONGITUDE = 34.6415
        const val DEFAULT_ZONE_ID = "Europe/Istanbul"
        const val LOCATION_EPSILON = 0.00001
        const val SOURCE_MAP = "map"
        const val SOURCE_PHONE = "phone"
        const val SOURCE_CITY_DATABASE = "city-db"
        const val SOURCE_GEOCODER = "geocoder"
        const val MAP_BASE_URL = "https://map.local/"
        const val MAP_BRIDGE_NAME = "AndroidMap"
        const val LOCATION_ICON_PADDING_RATIO = 0.22
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }
}
