package app.luma

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.luma.data.AppModel
import app.luma.data.Constants
import app.luma.data.Constants.AppDrawerFlag
import app.luma.data.GestureScope
import app.luma.data.GestureType
import app.luma.data.Prefs
import app.luma.data.StatusBarSectionType
import app.luma.helper.getAppsList
import app.luma.helper.getHiddenAppsList
import app.luma.helper.launchAppModel
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs.getInstance(appContext)
    private var currentHomePage = 0

    val appList = MutableLiveData<List<AppModel>?>()
    val hiddenApps = MutableLiveData<List<AppModel>?>()

    fun resetHomePageNow() {
        currentHomePage = 0
    }

    fun getCurrentHomePage(): Int = currentHomePage

    fun setCurrentHomePage(page: Int) {
        currentHomePage = page
    }

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

            AppDrawerFlag.SetCameraKey -> {
                prefs.setCameraKeyAction(Constants.Action.OpenApp)
                prefs.setCameraKeyApp(appModel)
                true
            }

            AppDrawerFlag.SetScrollwheelButton -> {
                prefs.setScrollwheelButtonAction(Constants.Action.OpenApp)
                prefs.setScrollwheelButtonApp(appModel)
                true
            }

            AppDrawerFlag.SetSwipeLeft -> setGestureApp(GestureType.SWIPE_LEFT, appModel, GestureScope.Homescreen)

            AppDrawerFlag.SetSwipeRight -> setGestureApp(GestureType.SWIPE_RIGHT, appModel, GestureScope.Homescreen)

            AppDrawerFlag.SetSwipeUp -> setGestureApp(GestureType.SWIPE_UP, appModel, GestureScope.Homescreen)

            AppDrawerFlag.SetSwipeDown -> setGestureApp(GestureType.SWIPE_DOWN, appModel, GestureScope.Homescreen)

            AppDrawerFlag.SetDoubleTap -> setGestureApp(GestureType.DOUBLE_TAP, appModel, GestureScope.Homescreen)

            AppDrawerFlag.SetLockscreenSwipeLeft -> setGestureApp(GestureType.SWIPE_LEFT, appModel, GestureScope.Lockscreen)

            AppDrawerFlag.SetLockscreenSwipeRight -> setGestureApp(GestureType.SWIPE_RIGHT, appModel, GestureScope.Lockscreen)

            AppDrawerFlag.SetLockscreenSwipeUp -> setGestureApp(GestureType.SWIPE_UP, appModel, GestureScope.Lockscreen)

            AppDrawerFlag.SetLockscreenSwipeDown -> setGestureApp(GestureType.SWIPE_DOWN, appModel, GestureScope.Lockscreen)

            AppDrawerFlag.SetLockscreenDoubleTap -> setGestureApp(GestureType.DOUBLE_TAP, appModel, GestureScope.Lockscreen)

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

            AppDrawerFlag.SetLockscreenShortcut -> {
                prefs.setLockscreenShortcutAction(Constants.Action.OpenApp)
                prefs.setLockscreenShortcutApp(appModel)
                true
            }

            AppDrawerFlag.SetLockscreenDateTap -> {
                prefs.setLockscreenDateTapAction(Constants.Action.OpenApp)
                prefs.setLockscreenDateTapApp(appModel)
                true
            }
        }

    private fun launchApp(
        appModel: AppModel,
        launchContext: Context? = null,
    ): Boolean = launchAppModel(launchContext ?: appContext, appModel)

    private fun setGestureApp(
        gestureType: GestureType,
        appModel: AppModel,
        scope: GestureScope,
    ): Boolean {
        prefs.setGestureAction(gestureType, Constants.Action.OpenApp, scope)
        prefs.setGestureApp(gestureType, appModel, scope)
        return true
    }

    fun getAppList(includeHidden: Boolean = false) {
        viewModelScope.launch {
            appList.value = getAppsList(appContext, includeHidden)
        }
    }

    fun getHiddenApps() {
        viewModelScope.launch {
            hiddenApps.value = getHiddenAppsList(appContext)
        }
    }
}
