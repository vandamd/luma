package com.vandam.luma.data

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import android.telephony.ServiceState
import com.vandam.luma.style.FontSizeOption
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator

private const val PREFS_FILENAME = "com.vandam.luma"

private const val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
private const val FIRST_RUN_DEFAULTS = "FIRST_RUN_DEFAULTS"
private const val LEGACY_ACCOUNT_NUMBER = "ACCOUNT_NUMBER"
private const val ONBOARDING_STARTED = "ONBOARDING_STARTED"
private const val ONBOARDING_LOGIN_STARTED = "ONBOARDING_LOGIN_STARTED"
private const val ENABLED_MANAGED_APP_IDS = "ENABLED_MANAGED_APP_IDS"
private const val ENABLED_ANDROID_APPS = "ENABLED_ANDROID_APPS"
private const val HOME_ITEM_ORDER = "HOME_ITEM_ORDER"
private const val HIDDEN_HOME_ITEM_KEYS = "HIDDEN_HOME_ITEM_KEYS"
private const val HOME_ITEM_LABEL_OVERRIDE_PREFIX = "HOME_ITEM_LABEL_OVERRIDE_"
private const val HOME_PAGES = "HOME_PAGES"
private const val HOME_APPS_PER_PAGE = "HOME_APPS_PER_PAGE_"

private const val THEME_MODE = "theme_mode"

private const val APP_NAME = "APP_NAME"
private const val APP_PACKAGE = "APP_PACKAGE"
private const val APP_ACTIVITY = "APP_ACTIVITY"
private const val APP_USER_SERIAL = "APP_USER_SERIAL"
private const val APP_ENTRY_TYPE = "APP_ENTRY_TYPE"

enum class GestureType(
    val actionKey: String,
    val appKey: String,
    val defaultAction: Constants.Action,
) {
    SWIPE_LEFT("SWIPE_LEFT_ACTION", "SWIPE_LEFT", Constants.Action.Disabled),
    SWIPE_RIGHT("SWIPE_RIGHT_ACTION", "SWIPE_RIGHT", Constants.Action.Disabled),
    SWIPE_DOWN("SWIPE_DOWN_ACTION", "SWIPE_DOWN", Constants.Action.ShowNotificationList),
    SWIPE_UP("SWIPE_UP_ACTION", "SWIPE_UP", Constants.Action.Disabled),
    DOUBLE_TAP("DOUBLE_TAP_ACTION", "DOUBLE_TAP", Constants.Action.Disabled),
}

enum class GestureScope {
    Homescreen,
    Lockscreen,
}

enum class StatusBarSectionType(
    val actionKey: String,
    val appKey: String,
    val defaultAction: Constants.Action,
) {
    CELLULAR("SB_CELLULAR_ACTION", "SB_CELLULAR_APP", Constants.Action.NetworkShortcutLight),
    BATTERY("SB_BATTERY_ACTION", "SB_BATTERY_APP", Constants.Action.Disabled),
}

private const val PAGE_INDICATOR_POSITION = "page_indicator_position"
private const val SHOW_NOTIFICATION_INDICATOR = "show_notification_indicator"
private const val NOTIFICATION_INDICATOR_SECTION = "notification_indicator_section"
private const val NOTIFICATION_INDICATOR_ALIGNMENT = "notification_indicator_alignment"
private const val TIME_FORMAT = "time_format"
private const val SHOW_SECONDS = "show_seconds"
private const val LEADING_ZERO = "leading_zero"
private const val BATTERY_PERCENTAGE = "battery_percentage"
private const val BATTERY_ICON = "battery_icon"
private const val CELLULAR_ENABLED = "cellular_enabled"
private const val WIFI_ENABLED = "wifi_enabled"
private const val BLUETOOTH_ENABLED = "bluetooth_enabled"
private const val LAST_CELLULAR_SIGNAL_LEVEL = "last_cellular_signal_level"
private const val LAST_CELLULAR_NETWORK_TYPE = "last_cellular_network_type"
private const val CELLULAR_SERVICE_STATE = "cellular_service_state"
private const val FONT_SIZE_OPTION = "font_size_option"
private const val HAPTICS_ENABLED = "haptics_enabled"
private const val HAPTICS_APP_TAP_ENABLED = "haptics_app_tap_enabled"
private const val HAPTICS_LONG_PRESS_ENABLED = "haptics_long_press_enabled"
private const val HAPTICS_GESTURE_ACTIONS_ENABLED = "haptics_gesture_actions_enabled"
private const val HAPTICS_STATUS_BAR_PRESS_ENABLED = "haptics_status_bar_press_enabled"
private const val HAPTICS_KEYMAPS_ENABLED = "haptics_keymaps_enabled"
private const val CAMERA_KEY_PRESS_ACTION = "camera_key_press_action"
private const val CAMERA_KEY_PRESS_APP = "camera_key_press_app"
private const val CAMERA_KEY_LONG_PRESS_ACTION = "camera_key_long_press_action"
private const val CAMERA_KEY_LONG_PRESS_APP = "camera_key_long_press_app"
private const val SCROLLWHEEL_BUTTON_PRESS_ACTION = "scrollwheel_button_press_action"
private const val SCROLLWHEEL_BUTTON_PRESS_APP = "scrollwheel_button_press_app"
private const val SCROLLWHEEL_BUTTON_LONG_PRESS_ACTION = "scrollwheel_button_long_press_action"
private const val SCROLLWHEEL_BUTTON_LONG_PRESS_APP = "scrollwheel_button_long_press_app"
private const val LOCKSCREEN_DATE_FORMAT = "lockscreen_date_format"
private const val LOCKSCREEN_CLOCK_TAP_ACTION = "lockscreen_clock_tap_action"
private const val LOCKSCREEN_CLOCK_TAP_APP = "lockscreen_clock_tap_app"
private const val LOCKSCREEN_DATE_TAP_ACTION = "lockscreen_date_tap_action"
private const val LOCKSCREEN_DATE_TAP_APP = "lockscreen_date_tap_app"
private const val LOCKSCREEN_CLOCK_NOTIFICATION_INDICATOR = "lockscreen_clock_notification_indicator"
private const val LOCKSCREEN_SHORTCUT_ACTION = "lockscreen_shortcut_action"
private const val LOCKSCREEN_SHORTCUT_APP = "lockscreen_shortcut_app"
private const val LOCKSCREEN_SHORTCUT_ICON = "lockscreen_shortcut_icon"

class Prefs(
    val context: Context,
) {
    companion object {
        @Volatile private var instance: Prefs? = null

        fun getInstance(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also {
                    instance = it
                    it.clearLegacySensitivePrefs()
                    it.initDefaults()
                }
            }
    }

    enum class TimeFormat { Standard, TwentyFourHour }

    enum class ThemeMode { Dark, Light }

    enum class PageIndicatorPosition { Left, Right, Hidden }

    enum class NotificationIndicatorSection { Connectivity, Time, Battery }

    enum class NotificationIndicatorAlignment { Before, After }

    enum class LockscreenDateFormat { None, ShortWeekday, LongWeekday, SlashedDMY, SlashedMDY, ISO8601 }

    enum class LockscreenShortcutIcon { Ring, Star, Camera, Phone, Heart, Flashlight, Music, Message }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)
    private val secureSessionStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SecureSessionStore.getInstance(context)
    }
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    @Volatile
    private var cachedEnabledAndroidAppsRaw: String? = null
    @Volatile
    private var cachedEnabledAndroidApps: List<AndroidLauncherApp> = emptyList()
    @Volatile
    private var cachedEnabledAndroidAppLabels: Map<String, String> = emptyMap()

    private inline fun <reified T : Enum<T>> enumPref(
        key: String,
        default: T,
    ): T {
        val stored = prefs.getString(key, null) ?: return default
        return try {
            enumValueOf<T>(stored)
        } catch (_: Exception) {
            default
        }
    }

    fun firstSettingsOpen(): Boolean = firstTrueFalseAfter(FIRST_SETTINGS_OPEN)

    var accountNumber: String
        get() = secureSessionStore.accountNumber
        set(value) {
            secureSessionStore.accountNumber = value
        }

    val installationId: String
        get() = secureSessionStore.installationId

    var onboardingStarted: Boolean
        get() = prefs.getBoolean(ONBOARDING_STARTED, false)
        set(value) = prefs.edit().putBoolean(ONBOARDING_STARTED, value).apply()

    var onboardingLoginStarted: Boolean
        get() = prefs.getBoolean(ONBOARDING_LOGIN_STARTED, false)
        set(value) = prefs.edit().putBoolean(ONBOARDING_LOGIN_STARTED, value).apply()

    var enabledManagedAppIds: Set<String>
        get() = prefs.getStringSet(ENABLED_MANAGED_APP_IDS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(ENABLED_MANAGED_APP_IDS, value).apply()

    var enabledAndroidApps: List<AndroidLauncherApp>
        get() = readEnabledAndroidApps()
        set(value) {
            val normalized = linkedMapOf<String, AndroidLauncherApp>()
            value.forEach { app ->
                val normalizedApp =
                    AndroidLauncherApp(
                        label = app.label.trim().ifEmpty { app.packageName },
                        packageName = app.packageName.trim(),
                        activityName = app.activityName.trim(),
                    )
                if (normalizedApp.packageName.isEmpty() || normalizedApp.activityName.isEmpty()) return@forEach
                normalized.putIfAbsent(normalizedApp.key, normalizedApp)
            }

            val array = JSONArray()
            normalized.values.forEach { app ->
                val obj = JSONObject()
                obj.put("l", app.label)
                obj.put("p", app.packageName)
                obj.put("a", app.activityName)
                array.put(obj)
            }

            val raw = array.toString()
            prefs.edit().putString(ENABLED_ANDROID_APPS, raw).apply()
            updateEnabledAndroidAppsCache(raw, normalized.values.toList())
        }

    var homeItemOrderKeys: List<String>
        get() {
            val raw = prefs.getString(HOME_ITEM_ORDER, null) ?: return emptyList()
            return try {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        val key = array.optString(i, "").trim()
                        if (key.isNotEmpty()) add(key)
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        set(value) {
            val array = JSONArray()
            value
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .forEach(array::put)
            prefs.edit().putString(HOME_ITEM_ORDER, array.toString()).apply()
        }

    var hiddenHomeItemKeys: Set<String>
        get() = prefs.getStringSet(HIDDEN_HOME_ITEM_KEYS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(HIDDEN_HOME_ITEM_KEYS, value.toSet()).apply()

    var homePages: Int
        get() = prefs.getInt(HOME_PAGES, 1)
        set(value) = prefs.edit().putInt(HOME_PAGES, value.coerceIn(HomeLayout.MIN_PAGES, HomeLayout.MAX_PAGES)).apply()

    fun getAppsPerPage(page: Int): Int = prefs.getInt("${HOME_APPS_PER_PAGE}$page", if (page == 1) 2 else 4)

    fun setAppsPerPage(
        page: Int,
        count: Int,
    ) {
        prefs.edit().putInt("${HOME_APPS_PER_PAGE}$page", count).apply()
    }

    private fun loadAction(
        prefString: String,
        default: Constants.Action,
    ): Constants.Action {
        val string = prefs.getString(prefString, default.name) ?: default.name
        return try {
            Constants.Action.valueOf(string)
        } catch (_: Exception) {
            default
        }
    }

    private fun storeAction(
        prefString: String,
        value: Constants.Action,
    ) {
        prefs.edit().putString(prefString, value.name).apply()
    }

    var themeMode: ThemeMode
        get() = enumPref(THEME_MODE, ThemeMode.Dark)
        set(value) = prefs.edit().putString(THEME_MODE, value.name).apply()

    fun isDarkTheme(): Boolean =
        when (themeMode) {
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
        }

    var invertColours: Boolean
        get() = themeMode == ThemeMode.Light
        set(value) {
            themeMode = if (value) ThemeMode.Light else ThemeMode.Dark
        }

    fun getHomeAppModel(i: Int): AppModel = loadApp("$i")

    fun setHomeAppModel(
        i: Int,
        appModel: AppModel,
    ) {
        storeApp("$i", appModel)
    }

    fun getGestureApp(
        type: GestureType,
        scope: GestureScope = GestureScope.Homescreen,
    ): AppModel = loadApp(gestureAppKey(type, scope))

    fun setGestureApp(
        type: GestureType,
        appModel: AppModel,
        scope: GestureScope = GestureScope.Homescreen,
    ) {
        storeApp(gestureAppKey(type, scope), appModel)
    }

    fun getGestureAction(
        type: GestureType,
        scope: GestureScope = GestureScope.Homescreen,
    ): Constants.Action = loadAction(gestureActionKey(type, scope), defaultGestureAction(type, scope))

    fun setGestureAction(
        type: GestureType,
        action: Constants.Action,
        scope: GestureScope = GestureScope.Homescreen,
    ) {
        storeAction(gestureActionKey(type, scope), action)
    }

    private fun validateCameraAction(action: Constants.Action): Constants.Action =
        if (
            action == Constants.Action.Disabled ||
            action == Constants.Action.OpenApp ||
            action == Constants.Action.GoBack
        ) {
            action
        } else {
            Constants.Action.Disabled
        }

    fun getCameraKeyPressAction(): Constants.Action = validateCameraAction(loadAction(CAMERA_KEY_PRESS_ACTION, Constants.Action.OpenApp))

    fun setCameraKeyPressAction(action: Constants.Action) {
        storeAction(CAMERA_KEY_PRESS_ACTION, validateCameraAction(action))
    }

    fun getCameraKeyPressApp(): AppModel =
        loadApp(CAMERA_KEY_PRESS_APP)
            .let { storedApp ->
                if (storedApp.appPackage.isBlank()) defaultCameraToolApp() else storedApp
            }

    fun setCameraKeyPressApp(appModel: AppModel) {
        storeApp(CAMERA_KEY_PRESS_APP, appModel)
    }

    fun getCameraKeyLongPressAction(): Constants.Action =
        validateCameraAction(loadAction(CAMERA_KEY_LONG_PRESS_ACTION, Constants.Action.Disabled))

    fun setCameraKeyLongPressAction(action: Constants.Action) {
        storeAction(CAMERA_KEY_LONG_PRESS_ACTION, validateCameraAction(action))
    }

    fun getCameraKeyLongPressApp(): AppModel =
        loadApp(CAMERA_KEY_LONG_PRESS_APP).let { storedApp ->
            if (storedApp.appPackage.isBlank()) defaultCameraToolApp() else storedApp
        }

    fun setCameraKeyLongPressApp(appModel: AppModel) {
        storeApp(CAMERA_KEY_LONG_PRESS_APP, appModel)
    }

    private fun validateScrollwheelAction(action: Constants.Action): Constants.Action =
        if (
            action == Constants.Action.Disabled ||
            action == Constants.Action.OpenApp ||
            action == Constants.Action.ToggleFlashlight ||
            action == Constants.Action.GoBack
        ) {
            action
        } else {
            Constants.Action.Disabled
        }

    fun getScrollwheelButtonPressAction(): Constants.Action =
        validateScrollwheelAction(loadAction(SCROLLWHEEL_BUTTON_PRESS_ACTION, Constants.Action.ToggleFlashlight))

    fun setScrollwheelButtonPressAction(action: Constants.Action) {
        storeAction(SCROLLWHEEL_BUTTON_PRESS_ACTION, validateScrollwheelAction(action))
    }

    fun getScrollwheelButtonPressApp(): AppModel = loadApp(SCROLLWHEEL_BUTTON_PRESS_APP)

    fun setScrollwheelButtonPressApp(appModel: AppModel) {
        storeApp(SCROLLWHEEL_BUTTON_PRESS_APP, appModel)
    }

    fun getScrollwheelButtonLongPressAction(): Constants.Action =
        validateScrollwheelAction(loadAction(SCROLLWHEEL_BUTTON_LONG_PRESS_ACTION, Constants.Action.Disabled))

    fun setScrollwheelButtonLongPressAction(action: Constants.Action) {
        storeAction(SCROLLWHEEL_BUTTON_LONG_PRESS_ACTION, validateScrollwheelAction(action))
    }

    fun getScrollwheelButtonLongPressApp(): AppModel = loadApp(SCROLLWHEEL_BUTTON_LONG_PRESS_APP)

    fun setScrollwheelButtonLongPressApp(appModel: AppModel) {
        storeApp(SCROLLWHEEL_BUTTON_LONG_PRESS_APP, appModel)
    }

    fun isToolEnabled(tool: Tool): Boolean = prefs.getBoolean(tool.prefKey, tool == Tool.Phone || tool == Tool.Settings)

    fun setToolEnabled(
        tool: Tool,
        enabled: Boolean,
    ) {
        prefs.edit().putBoolean(tool.prefKey, enabled).apply()
    }

    fun getSectionApp(type: StatusBarSectionType): AppModel = loadApp(type.appKey)

    fun setSectionApp(
        type: StatusBarSectionType,
        appModel: AppModel,
    ) {
        storeApp(type.appKey, appModel)
    }

    fun getSectionAction(type: StatusBarSectionType): Constants.Action = loadAction(type.actionKey, type.defaultAction)

    fun setSectionAction(
        type: StatusBarSectionType,
        action: Constants.Action,
    ) {
        storeAction(type.actionKey, action)
    }

    var lockscreenDateFormat: LockscreenDateFormat
        get() = enumPref(LOCKSCREEN_DATE_FORMAT, LockscreenDateFormat.None)
        set(value) = prefs.edit().putString(LOCKSCREEN_DATE_FORMAT, value.name).apply()

    var lockscreenShortcutIcon: LockscreenShortcutIcon
        get() = enumPref(LOCKSCREEN_SHORTCUT_ICON, LockscreenShortcutIcon.Ring)
        set(value) = prefs.edit().putString(LOCKSCREEN_SHORTCUT_ICON, value.name).apply()

    fun getLockscreenClockTapAction(): Constants.Action = loadAction(LOCKSCREEN_CLOCK_TAP_ACTION, Constants.Action.Disabled)

    fun setLockscreenClockTapAction(action: Constants.Action) {
        val resolvedAction =
            when (action) {
                Constants.Action.LockScreen,
                -> Constants.Action.Disabled

                else -> action
            }
        storeAction(LOCKSCREEN_CLOCK_TAP_ACTION, resolvedAction)
    }

    fun getLockscreenClockTapApp(): AppModel = loadApp(LOCKSCREEN_CLOCK_TAP_APP)

    fun setLockscreenClockTapApp(appModel: AppModel) {
        storeApp(LOCKSCREEN_CLOCK_TAP_APP, appModel)
    }

    fun getLockscreenDateTapAction(): Constants.Action = loadAction(LOCKSCREEN_DATE_TAP_ACTION, Constants.Action.Disabled)

    fun setLockscreenDateTapAction(action: Constants.Action) {
        val resolvedAction =
            when (action) {
                Constants.Action.LockScreen,
                -> Constants.Action.Disabled

                else -> action
            }
        storeAction(LOCKSCREEN_DATE_TAP_ACTION, resolvedAction)
    }

    fun getLockscreenDateTapApp(): AppModel = loadApp(LOCKSCREEN_DATE_TAP_APP)

    fun setLockscreenDateTapApp(appModel: AppModel) {
        storeApp(LOCKSCREEN_DATE_TAP_APP, appModel)
    }

    var lockscreenClockNotificationIndicator: Boolean
        get() = prefs.getBoolean(LOCKSCREEN_CLOCK_NOTIFICATION_INDICATOR, true)
        set(value) = prefs.edit().putBoolean(LOCKSCREEN_CLOCK_NOTIFICATION_INDICATOR, value).apply()

    fun getLockscreenShortcutAction(): Constants.Action {
        val action = loadAction(LOCKSCREEN_SHORTCUT_ACTION, Constants.Action.OpenApp)
        return if (
            action == Constants.Action.LockScreen ||
            action == Constants.Action.Disabled
        ) {
            Constants.Action.OpenApp
        } else {
            action
        }
    }

    fun setLockscreenShortcutAction(action: Constants.Action) {
        val resolvedAction =
            when (action) {
                Constants.Action.Disabled,
                Constants.Action.LockScreen,
                -> Constants.Action.OpenApp

                else -> action
            }
        storeAction(LOCKSCREEN_SHORTCUT_ACTION, resolvedAction)
    }

    fun getLockscreenShortcutApp(): AppModel {
        val storedApp = loadApp(LOCKSCREEN_SHORTCUT_APP)
        return if (storedApp.appPackage.isBlank()) {
            defaultLockscreenShortcutApp()
        } else {
            storedApp
        }
    }

    fun setLockscreenShortcutApp(appModel: AppModel) {
        storeApp(LOCKSCREEN_SHORTCUT_APP, appModel)
    }

    private fun gestureActionKey(
        type: GestureType,
        scope: GestureScope,
    ): String =
        when (scope) {
            GestureScope.Homescreen -> type.actionKey
            GestureScope.Lockscreen -> "LOCKSCREEN_${type.actionKey}"
        }

    private fun gestureAppKey(
        type: GestureType,
        scope: GestureScope,
    ): String =
        when (scope) {
            GestureScope.Homescreen -> type.appKey
            GestureScope.Lockscreen -> "LOCKSCREEN_${type.appKey}"
        }

    private fun defaultGestureAction(
        type: GestureType,
        scope: GestureScope,
    ): Constants.Action =
        when (scope) {
            GestureScope.Homescreen -> {
                type.defaultAction
            }

            GestureScope.Lockscreen -> {
                if (type == GestureType.SWIPE_DOWN) {
                    Constants.Action.ShowNotificationList
                } else {
                    Constants.Action.Disabled
                }
            }
        }

    private fun loadApp(id: String): AppModel {
        val name = prefs.getString("${APP_NAME}_$id", "") ?: ""
        val pack = prefs.getString("${APP_PACKAGE}_$id", "") ?: ""
        val activity = prefs.getString("${APP_ACTIVITY}_$id", "") ?: ""
        val serial = prefs.getLong("${APP_USER_SERIAL}_$id", -1L)
        val storedType = enumPref("${APP_ENTRY_TYPE}_$id", AppEntryType.LauncherApp)
        val tool = Tool.fromPackageName(pack)
        val entryType =
            when {
                tool != null -> AppEntryType.Tool
                else -> storedType
            }
        val myHandle = android.os.Process.myUserHandle()
        val userHandle =
            if (serial >= 0) {
                userManager.getUserForSerialNumber(serial) ?: myHandle
            } else {
                myHandle
            }
        val resolvedName = resolveHomeItemLabel(pack, activity, name)

        return AppModel(
            appLabel = resolvedName,
            appPackage = pack,
            appActivityName = activity,
            user = userHandle,
            key = null,
            entryType = entryType,
        )
    }

    private fun storeApp(
        id: String,
        appModel: AppModel,
    ) {
        val serial = userManager.getSerialNumberForUser(appModel.user)
        prefs
            .edit()
            .putString("${APP_NAME}_$id", appModel.appLabel)
            .putString("${APP_PACKAGE}_$id", appModel.appPackage)
            .putString("${APP_ACTIVITY}_$id", appModel.appActivityName)
            .putLong("${APP_USER_SERIAL}_$id", serial)
            .putString("${APP_ENTRY_TYPE}_$id", appModel.entryType.name)
            .apply()
    }

    private fun defaultLockscreenShortcutApp(): AppModel =
        Tool.Phone.toAppModel(
            context = context,
            collator = Collator.getInstance(),
        )

    private fun defaultCameraToolApp(): AppModel =
        Tool.Camera.toAppModel(
            context = context,
            collator = Collator.getInstance(),
        )

    var fontSizeOption: FontSizeOption
        get() {
            val key = prefs.getString(FONT_SIZE_OPTION, FontSizeOption.Medium.name)
            return FontSizeOption.fromKey(key)
        }
        set(value) = prefs.edit().putString(FONT_SIZE_OPTION, value.name).apply()

    var pageIndicatorPosition: PageIndicatorPosition
        get() = enumPref(PAGE_INDICATOR_POSITION, PageIndicatorPosition.Right)
        set(value) = prefs.edit().putString(PAGE_INDICATOR_POSITION, value.name).apply()

    var showNotificationIndicator: Boolean
        get() = prefs.getBoolean(SHOW_NOTIFICATION_INDICATOR, true)
        set(value) = prefs.edit().putBoolean(SHOW_NOTIFICATION_INDICATOR, value).apply()

    var notificationIndicatorSection: NotificationIndicatorSection
        get() = enumPref(NOTIFICATION_INDICATOR_SECTION, NotificationIndicatorSection.Time)
        set(value) = prefs.edit().putString(NOTIFICATION_INDICATOR_SECTION, value.name).apply()

    var notificationIndicatorAlignment: NotificationIndicatorAlignment
        get() = enumPref(NOTIFICATION_INDICATOR_ALIGNMENT, NotificationIndicatorAlignment.After)
        set(value) = prefs.edit().putString(NOTIFICATION_INDICATOR_ALIGNMENT, value.name).apply()

    fun isStatusBarVisibleOnHomescreen(): Boolean = false

    fun isStatusBarVisibleOnLockscreen(): Boolean = true

    fun showsLumaStatusBarOnHomescreen(): Boolean = false

    fun showsLumaStatusBarOnLockscreen(): Boolean = true

    fun showsLumaStatusBarAnywhere(): Boolean = true

    fun showsAndroidStatusBarOnHomescreen(): Boolean = false

    fun showsAndroidStatusBarOnLockscreen(): Boolean = false

    var timeFormat: TimeFormat
        get() = enumPref(TIME_FORMAT, TimeFormat.TwentyFourHour)
        set(value) = prefs.edit().putString(TIME_FORMAT, value.name).apply()

    var showSeconds: Boolean
        get() = prefs.getBoolean(SHOW_SECONDS, false)
        set(value) = prefs.edit().putBoolean(SHOW_SECONDS, value).apply()

    var leadingZero: Boolean
        get() = prefs.getBoolean(LEADING_ZERO, false)
        set(value) = prefs.edit().putBoolean(LEADING_ZERO, value).apply()

    var batteryPercentage: Boolean
        get() = true
        set(value) {}

    var batteryIcon: Boolean
        get() = true
        set(value) {}

    var cellularEnabled: Boolean
        get() = true
        set(value) {}

    var wifiEnabled: Boolean
        get() = true
        set(value) {}

    var bluetoothEnabled: Boolean
        get() = true
        set(value) {}

    var lastCellularSignalLevel: Int?
        get() = prefs.getInt(LAST_CELLULAR_SIGNAL_LEVEL, -1).takeIf { it >= 0 }
        set(value) = prefs.edit().putInt(LAST_CELLULAR_SIGNAL_LEVEL, value?.coerceIn(0, 4) ?: -1).apply()

    var lastCellularNetworkType: Int?
        get() = prefs.getInt(LAST_CELLULAR_NETWORK_TYPE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        set(value) = prefs.edit().putInt(LAST_CELLULAR_NETWORK_TYPE, value ?: Int.MIN_VALUE).apply()

    var cellularServiceState: Int
        get() = prefs.getInt(CELLULAR_SERVICE_STATE, ServiceState.STATE_OUT_OF_SERVICE)
        set(value) = prefs.edit().putInt(CELLULAR_SERVICE_STATE, value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(HAPTICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(HAPTICS_ENABLED, value).apply()

    var hapticsAppTapEnabled: Boolean
        get() = prefs.getBoolean(HAPTICS_APP_TAP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(HAPTICS_APP_TAP_ENABLED, value).apply()

    var hapticsLongPressEnabled: Boolean
        get() = prefs.getBoolean(HAPTICS_LONG_PRESS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(HAPTICS_LONG_PRESS_ENABLED, value).apply()

    var hapticsGestureActionsEnabled: Boolean
        get() = prefs.getBoolean(HAPTICS_GESTURE_ACTIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(HAPTICS_GESTURE_ACTIONS_ENABLED, value).apply()

    var hapticsStatusBarPressEnabled: Boolean
        get() = prefs.getBoolean(HAPTICS_STATUS_BAR_PRESS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(HAPTICS_STATUS_BAR_PRESS_ENABLED, value).apply()

    var hapticsKeymapsEnabled: Boolean
        get() = prefs.getBoolean(HAPTICS_KEYMAPS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(HAPTICS_KEYMAPS_ENABLED, value).apply()

    var scrollwheelBrightnessEnabled: Boolean
        get() = true
        set(value) {}

    fun isManagedAppEnabled(packageName: String): Boolean {
        val appId = ManagedAppCatalog.fromPackageName(packageName)?.id ?: return false
        return enabledManagedAppIds.contains(appId)
    }

    fun homeItemKey(
        appPackage: String,
        appActivityName: String,
    ): String = "$appPackage|$appActivityName"

    fun homeItemKey(appModel: AppModel): String = homeItemKey(appModel.appPackage, appModel.appActivityName)

    fun getHomeItemLabelOverride(
        appPackage: String,
        appActivityName: String,
    ): String? {
        if (appPackage.isBlank() || appActivityName.isBlank()) return null
        return prefs.getString(homeItemLabelOverrideKey(homeItemKey(appPackage, appActivityName)), null)
    }

    fun resolveBaseHomeItemLabel(
        appPackage: String,
        appActivityName: String,
        fallbackLabel: String = "",
    ): String {
        Tool.fromPackageName(appPackage)?.let { return it.defaultLabel(context) }
        ManagedAppCatalog.fromPackageName(appPackage)?.let { return it.label }
        enabledAndroidAppLabel(appPackage, appActivityName)?.let { return it }
        return fallbackLabel
    }

    fun resolveHomeItemLabel(
        appPackage: String,
        appActivityName: String,
        fallbackLabel: String = "",
    ): String = getHomeItemLabelOverride(appPackage, appActivityName) ?: resolveBaseHomeItemLabel(appPackage, appActivityName, fallbackLabel)

    fun setHomeItemLabelOverride(
        appPackage: String,
        appActivityName: String,
        label: String,
    ) {
        if (appPackage.isBlank() || appActivityName.isBlank()) return

        val normalizedLabel = label.trim()
        val key = homeItemLabelOverrideKey(homeItemKey(appPackage, appActivityName))
        if (normalizedLabel.isBlank()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, normalizedLabel).apply()
        }
    }

    fun clearHomeItemLabelOverride(
        appPackage: String,
        appActivityName: String,
    ) {
        if (appPackage.isBlank() || appActivityName.isBlank()) return
        prefs.edit().remove(homeItemLabelOverrideKey(homeItemKey(appPackage, appActivityName))).apply()
    }

    fun isHomeItemHidden(appModel: AppModel): Boolean = hiddenHomeItemKeys.contains(homeItemKey(appModel))

    fun setHomeItemHidden(
        appModel: AppModel,
        hidden: Boolean,
    ) {
        val key = homeItemKey(appModel)
        if (appModel.appPackage.isBlank() || appModel.appActivityName.isBlank()) return
        hiddenHomeItemKeys =
            if (hidden) {
                hiddenHomeItemKeys + key
            } else {
                hiddenHomeItemKeys - key
            }
    }

    private fun firstTrueFalseAfter(key: String): Boolean {
        val first = prefs.getBoolean(key, true)
        if (first) {
            prefs.edit().putBoolean(key, false).apply()
        }
        return first
    }

    private fun homeItemLabelOverrideKey(itemKey: String): String = "$HOME_ITEM_LABEL_OVERRIDE_PREFIX$itemKey"

    private fun clearLegacySensitivePrefs() {
        if (!prefs.contains(LEGACY_ACCOUNT_NUMBER)) {
            return
        }

        prefs.edit().remove(LEGACY_ACCOUNT_NUMBER).apply()
    }

    private fun initDefaults() {
        if (!firstTrueFalseAfter(FIRST_RUN_DEFAULTS)) return
        val collator = Collator.getInstance()
        homePages = 1
        setAppsPerPage(1, 2)
        setHomeAppModel(0, Tool.Phone.toAppModel(context, collator))
        setHomeAppModel(1, Tool.Settings.toAppModel(context, collator))
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun readEnabledAndroidApps(): List<AndroidLauncherApp> {
        val raw = prefs.getString(ENABLED_ANDROID_APPS, null)
        if (raw == cachedEnabledAndroidAppsRaw) {
            return cachedEnabledAndroidApps
        }

        val parsedApps = parseEnabledAndroidApps(raw)
        updateEnabledAndroidAppsCache(raw, parsedApps)
        return parsedApps
    }

    private fun parseEnabledAndroidApps(raw: String?): List<AndroidLauncherApp> {
        if (raw == null) {
            return emptyList()
        }

        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val label = obj.optString("l", "").trim()
                    val packageName = obj.optString("p", "").trim()
                    val activityName = obj.optString("a", "").trim()
                    if (packageName.isEmpty() || activityName.isEmpty()) continue
                    add(
                        AndroidLauncherApp(
                            label = label.ifEmpty { packageName },
                            packageName = packageName,
                            activityName = activityName,
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun updateEnabledAndroidAppsCache(
        raw: String?,
        apps: List<AndroidLauncherApp>,
    ) {
        cachedEnabledAndroidAppsRaw = raw
        cachedEnabledAndroidApps = apps
        cachedEnabledAndroidAppLabels = apps.associate { it.key to it.label }
    }

    private fun enabledAndroidAppLabel(
        appPackage: String,
        appActivityName: String,
    ): String? = readEnabledAndroidApps().let { cachedEnabledAndroidAppLabels[homeItemKey(appPackage, appActivityName)] }
}
