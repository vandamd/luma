package com.vandam.luma

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.vandam.luma.data.AppModel
import com.vandam.luma.helper.launchAppModel

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private var currentHomePage = 0

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
}
