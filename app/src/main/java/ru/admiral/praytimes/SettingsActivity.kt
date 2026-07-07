package ru.admiral.praytimes

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Collections
import ru.admiral.praytimes.adhan.AsrMethod
import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Prayer
import ru.admiral.praytimes.adhan.PrayerAdjustments
import ru.admiral.praytimes.data.AppBackup
import ru.admiral.praytimes.data.LocationDatabase
import ru.admiral.praytimes.notification.PrayerNotificationScheduler
import ru.admiral.praytimes.settings.AdhanSound
import ru.admiral.praytimes.settings.AppSettings
import ru.admiral.praytimes.settings.ColorMode
import ru.admiral.praytimes.settings.HomeBlock
import ru.admiral.praytimes.ui.SystemInsets
import ru.admiral.praytimes.ui.UiText
import java.util.Locale

class SettingsActivity : Activity() {
    private lateinit var batteryOptimizationPanel: View
    private lateinit var languageSpinner: Spinner
    private lateinit var colorModeSpinner: Spinner
    private lateinit var uiScaleSpinner: Spinner
    private lateinit var hijriAdjustmentSpinner: Spinner
    private lateinit var calculationMethodSpinner: Spinner
    private lateinit var asrMethodSpinner: Spinner
    private lateinit var ramadanModeCheckBox: CheckBox
    private lateinit var holidayBackgroundsCheckBox: CheckBox
    private lateinit var notifyRamadanImsakCheckBox: CheckBox
    private lateinit var notifyAtPrayerStartCheckBox: CheckBox
    private lateinit var notifyBeforePrayerStartCheckBox: CheckBox
    private lateinit var notifyBeforePrayerEndCheckBox: CheckBox
    private lateinit var playAdhanAtPrayerStartCheckBox: CheckBox
    private lateinit var adhanSoundSpinner: Spinner
    private lateinit var notifySunnahPrayersCheckBox: CheckBox
    private lateinit var showJumuahPrayerCheckBox: CheckBox
    private lateinit var autoSwitchLocationCheckBox: CheckBox
    private lateinit var suggestLocationSwitchCheckBox: CheckBox
    private lateinit var vibratePrayerNotificationsCheckBox: CheckBox
    private lateinit var homeBlockOrderList: RecyclerView
    private lateinit var homeBlockOrderAdapter: HomeBlockOrderAdapter
    private lateinit var homeBlockTouchHelper: ItemTouchHelper
    private lateinit var notificationProfileList: LinearLayout
    private lateinit var prayerAdjustmentsScopeText: TextView
    private lateinit var fajrAdjustmentEditText: EditText
    private lateinit var sunriseAdjustmentEditText: EditText
    private lateinit var dhuhrAdjustmentEditText: EditText
    private lateinit var asrAdjustmentEditText: EditText
    private lateinit var maghribAdjustmentEditText: EditText
    private lateinit var ishaAdjustmentEditText: EditText
    private lateinit var iqamaOffsetsScopeText: TextView
    private lateinit var fajrIqamaEditText: EditText
    private lateinit var dhuhrIqamaEditText: EditText
    private lateinit var asrIqamaEditText: EditText
    private lateinit var maghribIqamaEditText: EditText
    private lateinit var ishaIqamaEditText: EditText
    private lateinit var database: LocationDatabase
    private val colorModes = ColorMode.entries.toList()
    private val adhanSounds = AdhanSound.entries.toList()
    private val calculationMethods = CalculationMethod.entries.filter { it != CalculationMethod.OTHER }
    private val asrMethods = AsrMethod.entries.toList()
    private val hijriAdjustments = (-2..2).toList()
    private val notificationChecks = mutableMapOf<Prayer, CheckBox>()
    private val adhanChecks = mutableMapOf<Prayer, CheckBox>()
    private val homeBlockOrder = mutableListOf<HomeBlock>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        SystemInsets.applyTo(findViewById(R.id.settingsRoot))

        database = LocationDatabase(this)
        bindViews()
        setupContent()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        if (::batteryOptimizationPanel.isInitialized) {
            setupBatteryOptimizationPanel()
        }
        if (::prayerAdjustmentsScopeText.isInitialized) {
            setupPrayerAdjustments()
        }
        if (::iqamaOffsetsScopeText.isInitialized) {
            setupIqamaOffsets()
        }
    }

    private fun bindViews() {
        batteryOptimizationPanel = findViewById(R.id.batteryOptimizationPanel)
        languageSpinner = findViewById(R.id.languageSpinner)
        colorModeSpinner = findViewById(R.id.colorModeSpinner)
        uiScaleSpinner = findViewById(R.id.uiScaleSpinner)
        hijriAdjustmentSpinner = findViewById(R.id.hijriAdjustmentSpinner)
        calculationMethodSpinner = findViewById(R.id.calculationMethodSpinner)
        asrMethodSpinner = findViewById(R.id.asrMethodSpinner)
        ramadanModeCheckBox = findViewById(R.id.ramadanModeCheckBox)
        holidayBackgroundsCheckBox = findViewById(R.id.holidayBackgroundsCheckBox)
        notifyRamadanImsakCheckBox = findViewById(R.id.notifyRamadanImsakCheckBox)
        notifyAtPrayerStartCheckBox = findViewById(R.id.notifyAtPrayerStartCheckBox)
        notifyBeforePrayerStartCheckBox = findViewById(R.id.notifyBeforePrayerStartCheckBox)
        notifyBeforePrayerEndCheckBox = findViewById(R.id.notifyBeforePrayerEndCheckBox)
        playAdhanAtPrayerStartCheckBox = findViewById(R.id.playAdhanAtPrayerStartCheckBox)
        adhanSoundSpinner = findViewById(R.id.adhanSoundSpinner)
        notifySunnahPrayersCheckBox = findViewById(R.id.notifySunnahPrayersCheckBox)
        showJumuahPrayerCheckBox = findViewById(R.id.showJumuahPrayerCheckBox)
        autoSwitchLocationCheckBox = findViewById(R.id.autoSwitchLocationCheckBox)
        suggestLocationSwitchCheckBox = findViewById(R.id.suggestLocationSwitchCheckBox)
        vibratePrayerNotificationsCheckBox = findViewById(R.id.vibratePrayerNotificationsCheckBox)
        homeBlockOrderList = findViewById(R.id.homeBlockOrderList)
        notificationProfileList = findViewById(R.id.notificationProfileList)
        prayerAdjustmentsScopeText = findViewById(R.id.prayerAdjustmentsScopeText)
        fajrAdjustmentEditText = findViewById(R.id.fajrAdjustmentEditText)
        sunriseAdjustmentEditText = findViewById(R.id.sunriseAdjustmentEditText)
        dhuhrAdjustmentEditText = findViewById(R.id.dhuhrAdjustmentEditText)
        asrAdjustmentEditText = findViewById(R.id.asrAdjustmentEditText)
        maghribAdjustmentEditText = findViewById(R.id.maghribAdjustmentEditText)
        ishaAdjustmentEditText = findViewById(R.id.ishaAdjustmentEditText)
        iqamaOffsetsScopeText = findViewById(R.id.iqamaOffsetsScopeText)
        fajrIqamaEditText = findViewById(R.id.fajrIqamaEditText)
        dhuhrIqamaEditText = findViewById(R.id.dhuhrIqamaEditText)
        asrIqamaEditText = findViewById(R.id.asrIqamaEditText)
        maghribIqamaEditText = findViewById(R.id.maghribIqamaEditText)
        ishaIqamaEditText = findViewById(R.id.ishaIqamaEditText)
    }

    private fun setupContent() {
        setupBatteryOptimizationPanel()
        findViewById<TextView>(R.id.versionText).text = getString(
            R.string.settings_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
        languageSpinner.adapter = spinnerAdapter(languageNames())
        languageSpinner.setSelection(
            AppSettings.supportedLanguageTags.indexOf(AppSettings.languageTag(this)).coerceAtLeast(0),
        )

        colorModeSpinner.adapter = spinnerAdapter(colorModes.map(::colorModeLabel))
        colorModeSpinner.setSelection(colorModes.indexOf(AppSettings.colorMode(this)).coerceAtLeast(0))

        uiScaleSpinner.adapter = spinnerAdapter(AppSettings.uiScales.map(::scaleLabel))
        val currentScaleIndex = AppSettings.uiScales.indexOfFirst { it == AppSettings.uiScale(this) }
        uiScaleSpinner.setSelection(currentScaleIndex.coerceAtLeast(DEFAULT_SCALE_INDEX))

        hijriAdjustmentSpinner.adapter = spinnerAdapter(
            hijriAdjustments.map { value -> getString(R.string.hijri_day_adjustment_value, value) },
        )
        hijriAdjustmentSpinner.setSelection(
            hijriAdjustments.indexOf(AppSettings.hijriDayAdjustment(this)).coerceAtLeast(DEFAULT_HIJRI_INDEX),
        )

        calculationMethodSpinner.adapter = spinnerAdapter(
            listOf(getString(R.string.calculation_method_auto)) +
                calculationMethods.map { UiText.methodLabel(this, it) },
        )
        val methodIndex = calculationMethods.indexOf(AppSettings.calculationMethod(this)).coerceAtLeast(0) + 1
        calculationMethodSpinner.setSelection(
            if (AppSettings.automaticCalculationMethod(this)) AUTO_METHOD_INDEX else methodIndex,
        )

        asrMethodSpinner.adapter = spinnerAdapter(asrMethods.map { UiText.asrMethodLabel(this, it) })
        asrMethodSpinner.setSelection(asrMethods.indexOf(AppSettings.asrMethod(this)).coerceAtLeast(0))

        ramadanModeCheckBox.isChecked = AppSettings.ramadanMode(this)
        holidayBackgroundsCheckBox.isChecked = AppSettings.holidayBackgrounds(this)
        notifyAtPrayerStartCheckBox.isChecked = AppSettings.notifyAtPrayerStart(this)
        notifyBeforePrayerStartCheckBox.isChecked = AppSettings.notifyBeforePrayerStart(this)
        notifyBeforePrayerEndCheckBox.isChecked = AppSettings.notifyBeforePrayerEnd(this)
        playAdhanAtPrayerStartCheckBox.isChecked = AppSettings.playAdhanAtPrayerStart(this)
        adhanSoundSpinner.adapter = spinnerAdapter(adhanSounds.map(::adhanSoundLabel))
        adhanSoundSpinner.setSelection(adhanSounds.indexOf(AppSettings.adhanSound(this)).coerceAtLeast(0))
        notifySunnahPrayersCheckBox.isChecked = AppSettings.notifySunnahPrayers(this)
        showJumuahPrayerCheckBox.isChecked = AppSettings.showJumuahPrayer(this)
        notifyRamadanImsakCheckBox.isChecked = AppSettings.notifyRamadanImsak(this)
        autoSwitchLocationCheckBox.isChecked = AppSettings.autoSwitchLocation(this)
        suggestLocationSwitchCheckBox.isChecked = AppSettings.suggestLocationSwitch(this)
        vibratePrayerNotificationsCheckBox.isChecked = AppSettings.vibratePrayerNotifications(this)
        setupHomeBlockOrder()
        setupPrayerAdjustments()
        setupIqamaOffsets()
        setupNotificationProfiles()
    }

    private fun setupButtons() {
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.openLocationSettingsButton).setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }
        findViewById<View>(R.id.openMethodComparisonButton).setOnClickListener {
            startActivity(Intent(this, MethodComparisonActivity::class.java))
        }
        findViewById<View>(R.id.openAccuracyButton).setOnClickListener {
            startActivity(Intent(this, AccuracyActivity::class.java))
        }
        findViewById<View>(R.id.openBatterySettingsButton).setOnClickListener {
            openBatteryOptimizationSettings()
        }
        findViewById<Button>(R.id.exportBackupButton).setOnClickListener {
            createBackupDocument()
        }
        findViewById<Button>(R.id.importBackupButton).setOnClickListener {
            openBackupDocument()
        }
        findViewById<Button>(R.id.applySettingsButton).setOnClickListener {
            val adjustments = readPrayerAdjustments()
            val iqamaOffsets = readIqamaOffsets()
            val saved = AppSettings.save(
                context = this,
                languageTag = AppSettings.supportedLanguageTags[languageSpinner.selectedItemPosition],
                colorMode = colorModes[colorModeSpinner.selectedItemPosition],
                uiScale = AppSettings.uiScales[uiScaleSpinner.selectedItemPosition],
                automaticCalculationMethod = calculationMethodSpinner.selectedItemPosition == AUTO_METHOD_INDEX,
                calculationMethod = calculationMethods[
                    (calculationMethodSpinner.selectedItemPosition - 1).coerceAtLeast(0),
                ],
                asrMethod = asrMethods[asrMethodSpinner.selectedItemPosition],
                notifyAtPrayerStart = notifyAtPrayerStartCheckBox.isChecked,
                notifyBeforePrayerStart = notifyBeforePrayerStartCheckBox.isChecked,
                notifyBeforePrayerEnd = notifyBeforePrayerEndCheckBox.isChecked,
                playAdhanAtPrayerStart = playAdhanAtPrayerStartCheckBox.isChecked,
                adhanSound = adhanSounds[adhanSoundSpinner.selectedItemPosition],
                notifySunnahPrayers = notifySunnahPrayersCheckBox.isChecked,
                showJumuahPrayer = showJumuahPrayerCheckBox.isChecked,
                ramadanMode = ramadanModeCheckBox.isChecked,
                holidayBackgrounds = holidayBackgroundsCheckBox.isChecked,
                notifyRamadanImsak = notifyRamadanImsakCheckBox.isChecked,
                hijriDayAdjustment = hijriAdjustments[hijriAdjustmentSpinner.selectedItemPosition],
                autoSwitchLocation = autoSwitchLocationCheckBox.isChecked,
                suggestLocationSwitch = suggestLocationSwitchCheckBox.isChecked,
                vibratePrayerNotifications = vibratePrayerNotificationsCheckBox.isChecked,
                homeBlockOrder = homeBlockOrder.toList(),
                notificationEnabledPrayers = notificationChecks
                    .filterValues(CheckBox::isChecked)
                    .keys,
                adhanEnabledPrayers = adhanChecks
                    .filterValues(CheckBox::isChecked)
                    .keys,
                globalPrayerAdjustments = adjustments,
            )
            val savedLocationAdjustments = AppSettings.selectedLocationId(this)
                ?.let { database.savePrayerAdjustments(it, adjustments) }
                ?: true
            val savedIqamaOffsets = AppSettings.selectedLocationId(this)
                ?.let { database.saveIqamaOffsets(it, iqamaOffsets) }
                ?: true
            if (!saved || !savedLocationAdjustments || !savedIqamaOffsets) {
                Toast.makeText(this, R.string.settings_save_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!AppSettings.notificationsEnabled(this)) {
                PrayerNotificationScheduler.cancelUpcoming(this)
            }
            requestNotificationPermissionIfNeeded()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            recreate()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            return
        }
        val uri = data?.data ?: return
        when (requestCode) {
            EXPORT_BACKUP_REQUEST_CODE -> exportBackup(uri)
            IMPORT_BACKUP_REQUEST_CODE -> importBackup(uri)
        }
    }

    private fun setupBatteryOptimizationPanel() {
        batteryOptimizationPanel.visibility = if (isBatteryOptimizationActive()) View.VISIBLE else View.GONE
    }

    private fun isBatteryOptimizationActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        val powerManager = getSystemService(PowerManager::class.java)
        return !powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching {
            startActivity(appSettingsIntent)
        }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun setupHomeBlockOrder() {
        homeBlockOrder.clear()
        homeBlockOrder.addAll(AppSettings.homeBlockOrder(this))
        homeBlockOrderAdapter = HomeBlockOrderAdapter()
        homeBlockOrderList.layoutManager = LinearLayoutManager(this)
        homeBlockOrderList.adapter = homeBlockOrderAdapter
        homeBlockOrderList.itemAnimator = null
        homeBlockTouchHelper = ItemTouchHelper(HomeBlockMoveCallback())
        homeBlockTouchHelper.attachToRecyclerView(homeBlockOrderList)
    }

    private fun homeBlockLabel(block: HomeBlock): String = getString(
        when (block) {
            HomeBlock.RAMADAN -> R.string.home_block_ramadan
            HomeBlock.PRAYER -> R.string.home_block_prayer
            HomeBlock.SUNNAH -> R.string.home_block_sunnah
            HomeBlock.QIBLA -> R.string.home_block_qibla
            HomeBlock.MOSQUES -> R.string.home_block_mosques
            HomeBlock.HOLIDAYS -> R.string.home_block_holidays
        },
    )

    private fun homeBlockIcon(block: HomeBlock): Int =
        when (block) {
            HomeBlock.RAMADAN -> R.drawable.ic_moon
            HomeBlock.PRAYER -> R.drawable.ic_clock
            HomeBlock.SUNNAH -> R.drawable.ic_star
            HomeBlock.QIBLA -> R.drawable.ic_qibla
            HomeBlock.MOSQUES -> R.drawable.ic_mosque
            HomeBlock.HOLIDAYS -> R.drawable.ic_calendar
        }

    private inner class HomeBlockOrderAdapter : RecyclerView.Adapter<HomeBlockViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeBlockViewHolder {
            val row = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = getDrawable(R.drawable.row_background)
                minimumHeight = dp(58)
                setPadding(dp(12), dp(10), dp(10), dp(10))
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(8)
                }
            }
            val icon = ImageView(this@SettingsActivity).apply {
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                    marginEnd = dp(12)
                }
            }
            val label = TextView(this@SettingsActivity).apply {
                setTextColor(getColor(R.color.color_text))
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val dragHandle = ImageView(this@SettingsActivity).apply {
                contentDescription = getString(R.string.home_block_drag_handle)
                isClickable = true
                isFocusable = true
                background = getDrawable(R.drawable.reorder_handle_background)
                setImageResource(R.drawable.ic_drag_handle)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                    marginStart = dp(12)
                }
            }
            row.addView(icon)
            row.addView(label)
            row.addView(dragHandle)
            return HomeBlockViewHolder(row, icon, label, dragHandle)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: HomeBlockViewHolder, position: Int) {
            val block = homeBlockOrder[position]
            holder.icon.setImageResource(homeBlockIcon(block))
            holder.label.text = homeBlockLabel(block)
            holder.dragHandle.setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    view.performClick()
                    homeBlockTouchHelper.startDrag(holder)
                    true
                } else {
                    false
                }
            }
        }

        override fun getItemCount(): Int = homeBlockOrder.size
    }

    private inner class HomeBlockMoveCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0,
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || from == to) {
                return false
            }
            if (from < to) {
                for (index in from until to) {
                    Collections.swap(homeBlockOrder, index, index + 1)
                }
            } else {
                for (index in from downTo to + 1) {
                    Collections.swap(homeBlockOrder, index, index - 1)
                }
            }
            homeBlockOrderAdapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun isLongPressDragEnabled(): Boolean = true

        override fun isItemViewSwipeEnabled(): Boolean = false
    }

    private class HomeBlockViewHolder(
        itemView: View,
        val icon: ImageView,
        val label: TextView,
        val dragHandle: ImageView,
    ) : RecyclerView.ViewHolder(itemView)

    private fun setupPrayerAdjustments() {
        val activeLocation = AppSettings.selectedLocationId(this)?.let(database::locationById)
        val adjustments = activeLocation
            ?.let { location -> database.prayerAdjustments(location.id) }
            ?: AppSettings.globalPrayerAdjustments(this)
        prayerAdjustmentsScopeText.text = if (activeLocation == null) {
            getString(R.string.prayer_adjustments_global_scope)
        } else {
            getString(R.string.prayer_adjustments_location_scope, activeLocation.name)
        }
        fajrAdjustmentEditText.setText(formatInteger(adjustments.fajr))
        sunriseAdjustmentEditText.setText(formatInteger(adjustments.sunrise))
        dhuhrAdjustmentEditText.setText(formatInteger(adjustments.dhuhr))
        asrAdjustmentEditText.setText(formatInteger(adjustments.asr))
        maghribAdjustmentEditText.setText(formatInteger(adjustments.maghrib))
        ishaAdjustmentEditText.setText(formatInteger(adjustments.isha))
    }

    private fun setupIqamaOffsets() {
        val activeLocation = AppSettings.selectedLocationId(this)?.let(database::locationById)
        val offsets = activeLocation
            ?.let { location -> database.iqamaOffsets(location.id) }
            ?: PrayerAdjustments()
        iqamaOffsetsScopeText.text = if (activeLocation == null) {
            getString(R.string.iqama_offsets_no_location)
        } else {
            getString(R.string.iqama_offsets_scope, activeLocation.name)
        }
        val enabled = activeLocation != null
        listOf(fajrIqamaEditText, dhuhrIqamaEditText, asrIqamaEditText, maghribIqamaEditText, ishaIqamaEditText)
            .forEach { editText ->
                editText.isEnabled = enabled
                editText.alpha = if (enabled) 1.0f else 0.55f
            }
        fajrIqamaEditText.setText(formatInteger(offsets.fajr))
        dhuhrIqamaEditText.setText(formatInteger(offsets.dhuhr))
        asrIqamaEditText.setText(formatInteger(offsets.asr))
        maghribIqamaEditText.setText(formatInteger(offsets.maghrib))
        ishaIqamaEditText.setText(formatInteger(offsets.isha))
    }

    private fun readPrayerAdjustments(): PrayerAdjustments =
        PrayerAdjustments(
            fajr = fajrAdjustmentEditText.intValue(),
            sunrise = sunriseAdjustmentEditText.intValue(),
            dhuhr = dhuhrAdjustmentEditText.intValue(),
            asr = asrAdjustmentEditText.intValue(),
            maghrib = maghribAdjustmentEditText.intValue(),
            isha = ishaAdjustmentEditText.intValue(),
        )

    private fun readIqamaOffsets(): PrayerAdjustments =
        PrayerAdjustments(
            fajr = fajrIqamaEditText.intValue(min = MIN_IQAMA_MINUTES, max = MAX_IQAMA_MINUTES),
            dhuhr = dhuhrIqamaEditText.intValue(min = MIN_IQAMA_MINUTES, max = MAX_IQAMA_MINUTES),
            asr = asrIqamaEditText.intValue(min = MIN_IQAMA_MINUTES, max = MAX_IQAMA_MINUTES),
            maghrib = maghribIqamaEditText.intValue(min = MIN_IQAMA_MINUTES, max = MAX_IQAMA_MINUTES),
            isha = ishaIqamaEditText.intValue(min = MIN_IQAMA_MINUTES, max = MAX_IQAMA_MINUTES),
        )

    private fun setupNotificationProfiles() {
        notificationProfileList.removeAllViews()
        notificationChecks.clear()
        adhanChecks.clear()
        AppSettings.notificationProfilePrayers.forEach { prayer ->
            notificationProfileList.addView(notificationProfileRow(prayer))
        }
    }

    private fun notificationProfileRow(prayer: Prayer): LinearLayout {
        val notifyCheckBox = CheckBox(this).apply {
            buttonTintList = getColorStateList(R.color.color_primary)
            isChecked = AppSettings.prayerNotificationEnabled(this@SettingsActivity, prayer)
            text = getString(R.string.notification_profile_notify)
            setTextColor(getColor(R.color.color_text))
            textSize = 13f
            setPadding(dp(8), 0, dp(8), 0)
        }
        val adhanCheckBox = CheckBox(this).apply {
            buttonTintList = getColorStateList(R.color.color_primary)
            isChecked = AppSettings.prayerAdhanEnabled(this@SettingsActivity, prayer)
            text = getString(R.string.notification_profile_adhan)
            setTextColor(getColor(R.color.color_text))
            textSize = 13f
            setPadding(dp(8), 0, dp(8), 0)
        }
        notificationChecks[prayer] = notifyCheckBox
        adhanChecks[prayer] = adhanCheckBox

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.row_background)
            setPadding(dp(12), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(8)
            }

            addView(TextView(context).apply {
                text = UiText.prayerLabel(this@SettingsActivity, prayer, isRamadan = false)
                setTextColor(getColor(R.color.color_text))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(notifyCheckBox)
            addView(adhanCheckBox)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !AppSettings.notificationsEnabled(this)) {
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun spinnerAdapter(values: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)

    private fun languageNames(): List<String> {
        val systemLanguage = Locale.getDefault().displayLanguage.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return listOf(getString(R.string.settings_language_system, systemLanguage)) +
            AppSettings.supportedLanguageTags.drop(1).map { tag ->
                val locale = Locale.forLanguageTag(tag)
                locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
            }
    }

    private fun colorModeLabel(mode: ColorMode): String = when (mode) {
        ColorMode.SYSTEM -> getString(R.string.color_mode_system)
        ColorMode.LIGHT -> getString(R.string.color_mode_light)
        ColorMode.DARK -> getString(R.string.color_mode_dark)
    }

    private fun adhanSoundLabel(sound: AdhanSound): String = when (sound) {
        AdhanSound.BUILT_IN -> getString(R.string.adhan_sound_builtin)
        AdhanSound.SYSTEM -> getString(R.string.adhan_sound_system)
        AdhanSound.SILENT -> getString(R.string.adhan_sound_silent)
    }

    private fun scaleLabel(scale: Float): String =
        getString(R.string.ui_scale_value, String.format(Locale.getDefault(), "%.0f", scale * 100f))

    private fun formatInteger(value: Int): String =
        String.format(Locale.getDefault(), "%d", value)

    @Suppress("DEPRECATION")
    private fun createBackupDocument() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = BACKUP_MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, AppBackup.FILE_NAME)
        }
        startActivityForResult(intent, EXPORT_BACKUP_REQUEST_CODE)
    }

    @Suppress("DEPRECATION")
    private fun openBackupDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = BACKUP_MIME_TYPE
        }
        startActivityForResult(intent, IMPORT_BACKUP_REQUEST_CODE)
    }

    private fun exportBackup(uri: Uri) {
        runCatching {
            contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    writer.write(AppBackup.export(this, database))
                }
            } ?: error("Output stream is null")
        }.fold(
            onSuccess = { Toast.makeText(this, R.string.backup_saved, Toast.LENGTH_SHORT).show() },
            onFailure = { Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show() },
        )
    }

    private fun importBackup(uri: Uri) {
        val imported = runCatching {
            val json = contentResolver.openInputStream(uri)?.use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader -> reader.readText() }
            } ?: error("Input stream is null")
            AppBackup.import(this, database, json)
        }.getOrDefault(false)
        Toast.makeText(this, if (imported) R.string.backup_imported else R.string.backup_failed, Toast.LENGTH_SHORT).show()
        if (imported) {
            recreate()
        }
    }

    private fun EditText.intValue(
        min: Int = MIN_ADJUSTMENT_MINUTES,
        max: Int = MAX_ADJUSTMENT_MINUTES,
    ): Int =
        text.toString().trim().toIntOrNull()?.coerceIn(min, max) ?: 0

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val DEFAULT_SCALE_INDEX = 1
        const val DEFAULT_HIJRI_INDEX = 2
        const val AUTO_METHOD_INDEX = 0
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 91
        const val EXPORT_BACKUP_REQUEST_CODE = 92
        const val IMPORT_BACKUP_REQUEST_CODE = 93
        const val MIN_ADJUSTMENT_MINUTES = -60
        const val MAX_ADJUSTMENT_MINUTES = 60
        const val MIN_IQAMA_MINUTES = 0
        const val MAX_IQAMA_MINUTES = 180
        const val BACKUP_MIME_TYPE = "application/json"
    }
}
