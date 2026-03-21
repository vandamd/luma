package app.luma

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.luma.R
import app.luma.data.AppModel
import app.luma.data.AppEntryType
import app.luma.data.Constants
import app.luma.data.Constants.AppDrawerFlag
import app.luma.data.GestureType
import app.luma.data.Prefs
import app.luma.data.StatusBarSectionType
import app.luma.data.Tool
import app.luma.helper.ActionService
import app.luma.helper.getAppsList
import app.luma.helper.getHiddenAppsList
import app.luma.helper.showToast
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    companion object {
        private const val LIGHT_OS_PACKAGE = "com.lightos"
        private const val LIGHT_OS_MAIN_ACTIVITY = "com.lightos.MainActivity"
    }

    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs.getInstance(appContext)

    val appList = MutableLiveData<List<AppModel>?>()
    val hiddenApps = MutableLiveData<List<AppModel>?>()

    fun selectedApp(
        appModel: AppModel,
        flag: AppDrawerFlag,
        n: Int = 0,
        launchContext: Context? = null,
    ): Boolean =
        when (flag) {
            AppDrawerFlag.LaunchApp, AppDrawerFlag.HiddenApps -> launchApp(appModel, launchContext)

            AppDrawerFlag.SetHomeApp -> {
                prefs.setHomeAppModel(n, appModel)
                true
            }

            AppDrawerFlag.SetSwipeLeft -> {
                prefs.setGestureAction(GestureType.SWIPE_LEFT, Constants.Action.OpenApp)
                prefs.setGestureApp(GestureType.SWIPE_LEFT, appModel)
                true
            }

            AppDrawerFlag.SetSwipeRight -> {
                prefs.setGestureAction(GestureType.SWIPE_RIGHT, Constants.Action.OpenApp)
                prefs.setGestureApp(GestureType.SWIPE_RIGHT, appModel)
                true
            }

            AppDrawerFlag.SetSwipeUp -> {
                prefs.setGestureAction(GestureType.SWIPE_UP, Constants.Action.OpenApp)
                prefs.setGestureApp(GestureType.SWIPE_UP, appModel)
                true
            }

            AppDrawerFlag.SetSwipeDown -> {
                prefs.setGestureAction(GestureType.SWIPE_DOWN, Constants.Action.OpenApp)
                prefs.setGestureApp(GestureType.SWIPE_DOWN, appModel)
                true
            }

            AppDrawerFlag.SetDoubleTap -> {
                prefs.setGestureAction(GestureType.DOUBLE_TAP, Constants.Action.OpenApp)
                prefs.setGestureApp(GestureType.DOUBLE_TAP, appModel)
                true
            }

            AppDrawerFlag.SetStatusBarCellular -> {
                prefs.setSectionAction(StatusBarSectionType.CELLULAR, Constants.Action.OpenApp)
                prefs.setSectionApp(StatusBarSectionType.CELLULAR, appModel)
                true
            }

            AppDrawerFlag.SetStatusBarTime -> {
                prefs.setSectionAction(StatusBarSectionType.TIME, Constants.Action.OpenApp)
                prefs.setSectionApp(StatusBarSectionType.TIME, appModel)
                true
            }

            AppDrawerFlag.SetStatusBarBattery -> {
                prefs.setSectionAction(StatusBarSectionType.BATTERY, Constants.Action.OpenApp)
                prefs.setSectionApp(StatusBarSectionType.BATTERY, appModel)
                true
            }
        }

    private fun launchApp(
        appModel: AppModel,
        launchContext: Context? = null,
    ): Boolean {
        val packageName = appModel.appPackage
        val appActivityName = appModel.appActivityName
        val userHandle = appModel.user

        val tool = Tool.fromPackageName(packageName)
        if (appModel.entryType == AppEntryType.Tool || tool != null) {
            return launchTool(tool ?: Tool.fromId(appActivityName) ?: return true, launchContext)
        }

        if (packageName == Constants.PINNED_SHORTCUT_PACKAGE || appModel.entryType == AppEntryType.PinnedShortcut) {
            return launchPinnedShortcut(appActivityName)
        }

        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, userHandle)

        val component =
            when (activityInfo.size) {
                0 -> {
                    showToast(appContext, appContext.getString(R.string.toast_app_not_found))
                    return true
                }

                1 -> {
                    ComponentName(packageName, activityInfo[0].name)
                }

                else -> {
                    if (appActivityName.isNotEmpty()) {
                        ComponentName(packageName, appActivityName)
                    } else {
                        ComponentName(packageName, activityInfo.last().name)
                    }
                }
            }

        try {
            launcher.startMainActivity(component, userHandle, null, null)
        } catch (e: SecurityException) {
            try {
                launcher.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
            } catch (e: Exception) {
                showToast(appContext, appContext.getString(R.string.toast_unable_to_launch_app))
            }
        } catch (e: Exception) {
            showToast(appContext, appContext.getString(R.string.toast_unable_to_launch_app))
        }
        return true
    }

    private fun launchTool(
        tool: Tool,
        launchContext: Context? = null,
    ): Boolean {
        val launchContext = launchContext ?: appContext
        val actionService = ActionService.instance()
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("lightos://${tool.lightOsRoute}")).apply {
                component = ComponentName(LIGHT_OS_PACKAGE, LIGHT_OS_MAIN_ACTIVITY)
                // LightOS is a singleTask launcher activity. Reusing its existing task avoids
                // a cold React Native boot, which otherwise flashes the default app screen
                // before the deep link is applied.
                if (launchContext !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

        try {
            actionService?.showToolLaunchMask(prefs.isDarkTheme())
            launchContext.startActivity(intent)
        } catch (_: Exception) {
            actionService?.cancelToolLaunchMask()
            showToast(appContext, appContext.getString(R.string.toast_unable_to_launch_app))
        }
        return true
    }

    private fun launchPinnedShortcut(payload: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            showToast(appContext, appContext.getString(R.string.toast_shortcuts_require_android))
            return true
        }

        val parts = payload.split("|", limit = 2)
        val shortcutPackage = parts.getOrNull(0) ?: return true
        val shortcutId = parts.getOrNull(1) ?: return true

        try {
            val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcher.startShortcut(shortcutPackage, shortcutId, null, null, android.os.Process.myUserHandle())
        } catch (_: Exception) {
            showToast(appContext, appContext.getString(R.string.toast_unable_to_launch_shortcut))
        }
        return true
    }

    fun getAppList() {
        viewModelScope.launch {
            appList.value = getAppsList(appContext)
        }
    }

    fun getHiddenApps() {
        viewModelScope.launch {
            hiddenApps.value = getHiddenAppsList(appContext)
        }
    }
}
