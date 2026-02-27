package app.luma.data

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import app.luma.style.FontSizeOption
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_FILENAME = "app.luma"

private const val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
private const val HOME_PAGES = "HOME_PAGES"
private const val HOME_APPS_PER_PAGE = "HOME_APPS_PER_PAGE_"

private const val HIDDEN_APPS = "HIDDEN_APPS"
private const val HIDDEN_SHORTCUT_IDS = "HIDDEN_SHORTCUT_IDS"
private const val INVERT_COLOURS = "INVERT_COLOURS"
private const val THEME_MODE = "theme_mode"
private const val PINNED_SHORTCUTS = "PINNED_SHORTCUTS"
private const val PINNED_APPS = "PINNED_APPS"

data class ShortcutEntry(
    val packageName: String,
    val shortcutId: String,
    val label: String,
) {
    val payload: String get() = "$packageName|$shortcutId"

    fun serialize(): String = "$packageName|$shortcutId|$label"

    companion object {
        fun parse(entry: String): ShortcutEntry? {
            val parts = entry.split("|")
            return if (parts.size >= 3) {
                ShortcutEntry(parts[0], parts[1], parts.drop(2).joinToString("|"))
            } else {
                null
            }
        }
    }
}

data class PinnedAppEntry(
    val packageName: String,
    val activityName: String,
    val userSerial: Long,
)

private const val APP_NAME = "APP_NAME"
private const val APP_PACKAGE = "APP_PACKAGE"
private const val APP_ALIAS = "APP_ALIAS"
private const val APP_ACTIVITY = "APP_ACTIVITY"
private const val APP_USER_SERIAL = "APP_USER_SERIAL"

enum class GestureType(
    val actionKey: String,
    val appKey: String,
    val defaultAction: Constants.Action,
) {
    SWIPE_LEFT("SWIPE_LEFT_ACTION", "SWIPE_LEFT", Constants.Action.ShowAppList),
    SWIPE_RIGHT("SWIPE_RIGHT_ACTION", "SWIPE_RIGHT", Constants.Action.Disabled),
    SWIPE_DOWN("SWIPE_DOWN_ACTION", "SWIPE_DOWN", Constants.Action.ShowNotificationList),
    SWIPE_UP("SWIPE_UP_ACTION", "SWIPE_UP", Constants.Action.Disabled),
    DOUBLE_TAP("DOUBLE_TAP_ACTION", "DOUBLE_TAP", Constants.Action.Disabled),
}

enum class StatusBarSectionType(
    val actionKey: String,
    val appKey: String,
    val defaultAction: Constants.Action,
) {
    CELLULAR("SB_CELLULAR_ACTION", "SB_CELLULAR_APP", Constants.Action.Disabled),
    TIME("SB_TIME_ACTION", "SB_TIME_APP", Constants.Action.ShowNotificationList),
    BATTERY("SB_BATTERY_ACTION", "SB_BATTERY_APP", Constants.Action.Disabled),
}

private const val PAGE_INDICATOR_POSITION = "page_indicator_position"
private const val SHOW_NOTIFICATION_INDICATOR = "show_notification_indicator"
private const val SHOW_STATUS_BAR_NOTIFICATION_INDICATOR = "show_status_bar_notification_indicator"
private const val NOTIFICATION_INDICATOR_SECTION = "notification_indicator_section"
private const val NOTIFICATION_INDICATOR_ALIGNMENT = "notification_indicator_alignment"
private const val STATUS_BAR_ENABLED = "status_bar_enabled"
private const val STATUS_BAR_MODE = "status_bar_mode"
private const val TIME_ENABLED = "time_enabled"
private const val TIME_FORMAT = "time_format"
private const val SHOW_SECONDS = "show_seconds"
private const val LEADING_ZERO = "leading_zero"
private const val BATTERY_PERCENTAGE = "battery_percentage"
private const val BATTERY_ICON = "battery_icon"
private const val CELLULAR_ENABLED = "cellular_enabled"
private const val WIFI_ENABLED = "wifi_enabled"
private const val BLUETOOTH_ENABLED = "bluetooth_enabled"
private const val FONT_SIZE_OPTION = "font_size_option"
private const val HAPTICS_ENABLED = "haptics_enabled"
private const val HAPTICS_APP_TAP_ENABLED = "haptics_app_tap_enabled"
private const val HAPTICS_LONG_PRESS_ENABLED = "haptics_long_press_enabled"
private const val HAPTICS_GESTURE_ACTIONS_ENABLED = "haptics_gesture_actions_enabled"
private const val HAPTICS_STATUS_BAR_PRESS_ENABLED = "haptics_status_bar_press_enabled"
private const val AUTO_ROTATE_ENABLED = "auto_rotate_enabled"

class Prefs(
    val context: Context,
) {
    companion object {
        @Volatile private var instance: Prefs? = null

        fun getInstance(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }

    enum class TimeFormat { Standard, TwentyFourHour }

    enum class ThemeMode { Dark, Light, Automatic }

    enum class StatusBarMode { Enabled, None, AndroidStatusBar }

    enum class PageIndicatorPosition { Left, Right, Hidden }

    enum class NotificationIndicatorSection { Connectivity, Time, Battery }

    enum class NotificationIndicatorAlignment { Before, After }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    val mySerial: Long = userManager.getSerialNumberForUser(android.os.Process.myUserHandle())

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

    init {
        migrateHiddenApps()
    }

    private fun migrateHiddenApps() {
        val stored = prefs.getStringSet(HIDDEN_APPS, null) ?: return
        var changed = false
        val migrated =
            stored.mapTo(mutableSetOf()) { entry ->
                val parts = entry.split("|")
                if (parts.size == 2) {
                    val serial = parts[1].toLongOrNull()
                    if (serial == null || serial == mySerial) {
                        changed = true
                        parts[0]
                    } else {
                        entry
                    }
                } else {
                    entry
                }
            }
        if (changed) {
            prefs.edit().putStringSet(HIDDEN_APPS, migrated).apply()
        }
    }

    fun firstSettingsOpen(): Boolean = firstTrueFalseAfter(FIRST_SETTINGS_OPEN)

    var homePages: Int
        get() = prefs.getInt(HOME_PAGES, 1)
        set(value) = prefs.edit().putInt(HOME_PAGES, value.coerceIn(HomeLayout.MIN_PAGES, HomeLayout.MAX_PAGES)).apply()

    fun getAppsPerPage(page: Int): Int = prefs.getInt("${HOME_APPS_PER_PAGE}$page", 4)

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
        get() {
            if (prefs.contains(THEME_MODE)) {
                return enumPref(THEME_MODE, ThemeMode.Dark)
            }
            return if (prefs.getBoolean(INVERT_COLOURS, false)) ThemeMode.Light else ThemeMode.Dark
        }
        set(value) = prefs.edit().putString(THEME_MODE, value.name).apply()

    var invertColours: Boolean
        get() = themeMode == ThemeMode.Light
        set(value) {
            themeMode = if (value) ThemeMode.Light else ThemeMode.Dark
        }

    var hiddenApps: MutableSet<String>
        get() = prefs.getStringSet(HIDDEN_APPS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set(value) = prefs.edit().putStringSet(HIDDEN_APPS, value).apply()

    fun getHomeAppModel(i: Int): AppModel = loadApp("$i")

    fun setHomeAppModel(
        i: Int,
        appModel: AppModel,
    ) {
        storeApp("$i", appModel)
    }

    var pinnedShortcuts: Set<String>
        get() = prefs.getStringSet(PINNED_SHORTCUTS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(PINNED_SHORTCUTS, value).apply()

    fun addPinnedShortcut(
        packageName: String,
        shortcutId: String,
        label: String,
    ) {
        val entry = ShortcutEntry(packageName, shortcutId, label)
        pinnedShortcuts = pinnedShortcuts + entry.serialize()
    }

    fun removePinnedShortcut(payload: String) {
        pinnedShortcuts =
            pinnedShortcuts
                .filterNot { entry ->
                    ShortcutEntry.parse(entry)?.payload == payload
                }.toSet()
    }

    private fun normalizePinnedEntry(entry: PinnedAppEntry): PinnedAppEntry =
        if (entry.userSerial < 0L) {
            entry.copy(userSerial = mySerial)
        } else {
            entry
        }

    private fun isValidPinnedEntry(entry: PinnedAppEntry): Boolean = entry.packageName.isNotEmpty() && entry.activityName.isNotEmpty()

    var pinnedApps: List<PinnedAppEntry>
        get() {
            val raw = prefs.getString(PINNED_APPS, null) ?: return emptyList()
            return try {
                val array = JSONArray(raw)
                val list = mutableListOf<PinnedAppEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val packageName = obj.optString("p", "")
                    val activityName = obj.optString("a", "")
                    val serialRaw = if (obj.has("u")) obj.optLong("u", -1L) else -1L
                    val serial = if (serialRaw < 0L) mySerial else serialRaw
                    if (packageName.isEmpty() || activityName.isEmpty()) continue
                    list.add(PinnedAppEntry(packageName, activityName, serial))
                }
                list.distinct()
            } catch (_: Exception) {
                emptyList()
            }
        }
        set(value) {
            val normalized =
                value
                    .map(::normalizePinnedEntry)
                    .filter(::isValidPinnedEntry)
                    .distinct()

            val array = JSONArray()
            normalized.forEach { entry ->
                val obj = JSONObject()
                obj.put("p", entry.packageName)
                obj.put("a", entry.activityName)
                obj.put("u", entry.userSerial)
                array.put(obj)
            }

            prefs.edit().putString(PINNED_APPS, array.toString()).apply()
        }

    fun isPinned(entry: PinnedAppEntry): Boolean {
        val normalizedEntry = normalizePinnedEntry(entry)
        if (!isValidPinnedEntry(normalizedEntry)) return false
        return pinnedApps.contains(normalizedEntry)
    }

    fun pin(entry: PinnedAppEntry) {
        val normalizedEntry = normalizePinnedEntry(entry)
        if (!isValidPinnedEntry(normalizedEntry)) return
        val current = pinnedApps.toMutableList()
        if (current.contains(normalizedEntry)) return
        current.add(normalizedEntry)
        pinnedApps = current
    }

    fun unpin(entry: PinnedAppEntry) {
        val normalizedEntry = normalizePinnedEntry(entry)
        if (!isValidPinnedEntry(normalizedEntry)) return
        val current = pinnedApps
        val filtered = current.filterNot { it == normalizedEntry }
        if (filtered.size == current.size) return
        pinnedApps = filtered
    }

    fun movePinnedUp(entry: PinnedAppEntry) {
        val normalizedEntry = normalizePinnedEntry(entry)
        if (!isValidPinnedEntry(normalizedEntry)) return
        val current = pinnedApps.toMutableList()
        val index = current.indexOf(normalizedEntry)
        if (index <= 0) return
        val above = current[index - 1]
        current[index - 1] = normalizedEntry
        current[index] = above
        pinnedApps = current
    }

    fun movePinnedDown(entry: PinnedAppEntry) {
        val normalizedEntry = normalizePinnedEntry(entry)
        if (!isValidPinnedEntry(normalizedEntry)) return
        val current = pinnedApps.toMutableList()
        val index = current.indexOf(normalizedEntry)
        if (index < 0 || index >= current.lastIndex) return
        val below = current[index + 1]
        current[index + 1] = normalizedEntry
        current[index] = below
        pinnedApps = current
    }

    fun removePinnedForPackage(
        packageName: String,
        userSerial: Long,
    ) {
        val serial = if (userSerial < 0L) mySerial else userSerial
        val current = pinnedApps
        val filtered = current.filterNot { it.packageName == packageName && it.userSerial == serial }
        if (filtered.size == current.size) return
        pinnedApps = filtered
    }

    var hiddenShortcutIds: Set<String>
        get() = prefs.getStringSet(HIDDEN_SHORTCUT_IDS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(HIDDEN_SHORTCUT_IDS, value).apply()

    fun hideShortcut(id: String) {
        hiddenShortcutIds = hiddenShortcutIds + id
    }

    fun unhideShortcut(id: String) {
        hiddenShortcutIds = hiddenShortcutIds - id
    }

    fun getGestureApp(type: GestureType): AppModel = loadApp(type.appKey)

    fun setGestureApp(
        type: GestureType,
        appModel: AppModel,
    ) {
        storeApp(type.appKey, appModel)
    }

    fun getGestureAction(type: GestureType): Constants.Action = loadAction(type.actionKey, type.defaultAction)

    fun setGestureAction(
        type: GestureType,
        action: Constants.Action,
    ) {
        storeAction(type.actionKey, action)
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

    private fun loadApp(id: String): AppModel {
        val name = prefs.getString("${APP_NAME}_$id", "") ?: ""
        val pack = prefs.getString("${APP_PACKAGE}_$id", "") ?: ""
        val alias = prefs.getString("${APP_ALIAS}_$id", "") ?: ""
        val activity = prefs.getString("${APP_ACTIVITY}_$id", "") ?: ""
        val serial = prefs.getLong("${APP_USER_SERIAL}_$id", -1L)
        val myHandle = android.os.Process.myUserHandle()
        val userHandle =
            if (serial >= 0) {
                userManager.getUserForSerialNumber(serial) ?: myHandle
            } else {
                myHandle
            }

        return AppModel(
            appLabel = name,
            appPackage = pack,
            appAlias = alias,
            appActivityName = activity,
            user = userHandle,
            key = null,
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
            .putString("${APP_ALIAS}_$id", appModel.appAlias)
            .putLong("${APP_USER_SERIAL}_$id", serial)
            .apply()
    }

    var fontSizeOption: FontSizeOption
        get() {
            val key = prefs.getString(FONT_SIZE_OPTION, FontSizeOption.Medium.name)
            return FontSizeOption.fromKey(key)
        }
        set(value) = prefs.edit().putString(FONT_SIZE_OPTION, value.name).apply()

    var pageIndicatorPosition: PageIndicatorPosition
        get() = enumPref(PAGE_INDICATOR_POSITION, PageIndicatorPosition.Left)
        set(value) = prefs.edit().putString(PAGE_INDICATOR_POSITION, value.name).apply()

    var showNotificationIndicator: Boolean
        get() = prefs.getBoolean(SHOW_NOTIFICATION_INDICATOR, true)
        set(value) = prefs.edit().putBoolean(SHOW_NOTIFICATION_INDICATOR, value).apply()

    var showStatusBarNotificationIndicator: Boolean
        get() = prefs.getBoolean(SHOW_STATUS_BAR_NOTIFICATION_INDICATOR, true)
        set(value) = prefs.edit().putBoolean(SHOW_STATUS_BAR_NOTIFICATION_INDICATOR, value).apply()

    var notificationIndicatorSection: NotificationIndicatorSection
        get() = enumPref(NOTIFICATION_INDICATOR_SECTION, NotificationIndicatorSection.Time)
        set(value) = prefs.edit().putString(NOTIFICATION_INDICATOR_SECTION, value.name).apply()

    var notificationIndicatorAlignment: NotificationIndicatorAlignment
        get() = enumPref(NOTIFICATION_INDICATOR_ALIGNMENT, NotificationIndicatorAlignment.After)
        set(value) = prefs.edit().putString(NOTIFICATION_INDICATOR_ALIGNMENT, value.name).apply()

    var statusBarEnabled: Boolean
        get() = prefs.getBoolean(STATUS_BAR_ENABLED, true)
        set(value) = prefs.edit().putBoolean(STATUS_BAR_ENABLED, value).apply()

    var statusBarMode: StatusBarMode
        get() {
            if (prefs.contains(STATUS_BAR_MODE)) {
                return enumPref(STATUS_BAR_MODE, StatusBarMode.Enabled)
            }
            return if (prefs.getBoolean(STATUS_BAR_ENABLED, true)) StatusBarMode.Enabled else StatusBarMode.None
        }
        set(value) = prefs.edit().putString(STATUS_BAR_MODE, value.name).apply()

    var timeEnabled: Boolean
        get() = prefs.getBoolean(TIME_ENABLED, true)
        set(value) = prefs.edit().putBoolean(TIME_ENABLED, value).apply()

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
        get() = prefs.getBoolean(BATTERY_PERCENTAGE, true)
        set(value) = prefs.edit().putBoolean(BATTERY_PERCENTAGE, value).apply()

    var batteryIcon: Boolean
        get() = prefs.getBoolean(BATTERY_ICON, true)
        set(value) = prefs.edit().putBoolean(BATTERY_ICON, value).apply()

    var cellularEnabled: Boolean
        get() = prefs.getBoolean(CELLULAR_ENABLED, false)
        set(value) = prefs.edit().putBoolean(CELLULAR_ENABLED, value).apply()

    var wifiEnabled: Boolean
        get() = prefs.getBoolean(WIFI_ENABLED, false)
        set(value) = prefs.edit().putBoolean(WIFI_ENABLED, value).apply()

    var bluetoothEnabled: Boolean
        get() = prefs.getBoolean(BLUETOOTH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(BLUETOOTH_ENABLED, value).apply()

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

    var autoRotateEnabled: Boolean
        get() = prefs.getBoolean(AUTO_ROTATE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(AUTO_ROTATE_ENABLED, value).apply()

    fun getHiddenAppKey(
        packageName: String,
        userSerial: Long,
    ): String = if (userSerial == mySerial) packageName else "$packageName|$userSerial"

    fun isAppHidden(
        packageName: String,
        userSerial: Long,
    ): Boolean {
        val hidden = hiddenApps
        return if (userSerial == mySerial) {
            hidden.contains(packageName)
        } else {
            hidden.contains("$packageName|$userSerial")
        }
    }

    fun getAppAlias(appName: String): String = prefs.getString(appName, "") ?: ""

    fun setAppAlias(
        appPackage: String,
        appAlias: String,
    ) {
        prefs.edit().putString(appPackage, appAlias).apply()
    }

    private fun firstTrueFalseAfter(key: String): Boolean {
        val first = prefs.getBoolean(key, true)
        if (first) {
            prefs.edit().putBoolean(key, false).apply()
        }
        return first
    }
}
