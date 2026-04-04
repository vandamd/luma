package com.vandam.luma

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.vandam.luma.data.AppModel
import com.vandam.luma.helper.getAppsList
import com.vandam.luma.helper.launchAppModel
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private var currentHomePage = 0

    val appList = MutableLiveData<List<AppModel>?>()

    fun resetHomePageNow() {
        currentHomePage = 0
    }

    fun getCurrentHomePage(): Int = currentHomePage

    fun setCurrentHomePage(page: Int) {
        currentHomePage = page
    }

    fun selectedApp(
        appModel: AppModel,
        launchContext: Context? = null,
    ): Boolean = launchApp(appModel, launchContext)

    private fun launchApp(
        appModel: AppModel,
        launchContext: Context? = null,
    ): Boolean = launchAppModel(launchContext ?: appContext, appModel)

    fun getAppList() {
        viewModelScope.launch {
            appList.value = getAppsList(appContext)
        }
    }
}
