package app.luma

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.luma.R
import app.luma.data.AppModel
import app.luma.data.Constants
import app.luma.data.Constants.AppDrawerFlag
import app.luma.data.GestureType
import app.luma.data.Prefs
import app.luma.data.StatusBarSectionType
import app.luma.helper.getAppsList
import app.luma.helper.getHiddenAppsList
import app.luma.helper.showToast
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs.getInstance(appContext)

    val appList = MutableLiveData<List<AppModel>?>()
    val hiddenApps = MutableLiveData<List<AppModel>?>()

    fun selectedApp(
        appModel: AppModel,
        flag: AppDrawerFlag,
        n: Int = 0,
    ) {
        when (flag) {
            AppDrawerFlag.LaunchApp, AppDrawerFlag.HiddenApps -> {
                launchApp(appModel)
            }

            AppDrawerFlag.SetHomeApp -> {
                prefs.setHomeAppModel(n, appModel)
            }

            AppDrawerFlag.SetSwipeLeft -> {
                prefs.setGestureAction(GestureType.SWIPE_LEFT, Constants.Action.OpenApp)
                prefs.setGestureApp(GestureType.SWIPE_LEFT, appModel)
            }

            AppDrawerFlag.SetSwipeRight -> {
                prefs.setGestureAction(GestureType.SWIPE_RIGHT, Constants.Action.OpenApp)
                prefs.setGestureApp(GestureType.SWIPE_RIGHT, appModel)
            }

            AppDrawerFlag.SetSwipeUp -> {
                prefs.setGestureAction(GestureType.SWIPE_UP, Constants.Action.OpenApp)
                prefs.setGestureApp(GestureType.SWIPE_UP, appModel)
            }

            AppDrawerFlag.SetSwipeDown -> {
                prefs.setGestureAction(GestureType.SWIPE_DOWN, Constants.Action.OpenApp)
                prefs.setGestureApp(GestureType.SWIPE_DOWN, appModel)
            }

            AppDrawerFlag.SetDoubleTap -> {
                prefs.setGestureAction(GestureType.DOUBLE_TAP, Constants.Action.OpenApp)
                prefs.setGestureApp(GestureType.DOUBLE_TAP, appModel)
            }

            AppDrawerFlag.SetStatusBarCellular -> {
                prefs.setSectionAction(StatusBarSectionType.CELLULAR, Constants.Action.OpenApp)
                prefs.setSectionApp(StatusBarSectionType.CELLULAR, appModel)
            }

            AppDrawerFlag.SetStatusBarTime -> {
                prefs.setSectionAction(StatusBarSectionType.TIME, Constants.Action.OpenApp)
                prefs.setSectionApp(StatusBarSectionType.TIME, appModel)
            }

            AppDrawerFlag.SetStatusBarBattery -> {
                prefs.setSectionAction(StatusBarSectionType.BATTERY, Constants.Action.OpenApp)
                prefs.setSectionApp(StatusBarSectionType.BATTERY, appModel)
            }
        }
    }

    private fun launchApp(appModel: AppModel) {
        val packageName = appModel.appPackage
        val appActivityName = appModel.appActivityName
        val userHandle = appModel.user

        if (packageName == Constants.PINNED_SHORTCUT_PACKAGE) {
            launchPinnedShortcut(appActivityName)
            return
        }

        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, userHandle)

        val component =
            when (activityInfo.size) {
                0 -> {
                    showToast(appContext, appContext.getString(R.string.toast_app_not_found))
                    return
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
    }

    private fun launchPinnedShortcut(payload: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            showToast(appContext, appContext.getString(R.string.toast_shortcuts_require_android))
            return
        }

        val parts = payload.split("|", limit = 2)
        val shortcutPackage = parts.getOrNull(0) ?: return
        val shortcutId = parts.getOrNull(1) ?: return

        try {
            val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcher.startShortcut(shortcutPackage, shortcutId, null, null, android.os.Process.myUserHandle())
        } catch (_: Exception) {
            showToast(appContext, appContext.getString(R.string.toast_unable_to_launch_shortcut))
        }
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
