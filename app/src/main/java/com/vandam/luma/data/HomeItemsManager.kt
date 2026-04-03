package com.vandam.luma.data

import android.content.Context
import java.text.Collator
import kotlin.math.max

object HomeItemsManager {
    fun orderedEnabledItems(
        context: Context,
        prefs: Prefs,
    ): List<AppModel> {
        val collator = Collator.getInstance()
        val defaultItems = buildDefaultItems(context, prefs, collator)
        val remainingByKey = LinkedHashMap<String, AppModel>()
        defaultItems.forEach { item ->
            remainingByKey[prefs.homeItemKey(item)] = item
        }

        val orderedItems = mutableListOf<AppModel>()
        val preferredKeys =
            prefs.homeItemOrderKeys.ifEmpty {
                buildList {
                    for (index in 0 until HomeLayout.TOTAL_SLOTS) {
                        val item = prefs.getHomeAppModel(index)
                        if (item.appPackage.isBlank() || item.appActivityName.isBlank()) continue
                        add(prefs.homeItemKey(item))
                    }
                }
            }

        preferredKeys.forEach { key ->
            remainingByKey.remove(key)?.let { orderedItems.add(it) }
        }

        orderedItems.addAll(remainingByKey.values)
        return orderedItems
    }

    fun visibleHomeItems(
        context: Context,
        prefs: Prefs,
    ): List<AppModel> {
        val hiddenKeys = prefs.hiddenHomeItemKeys
        return orderedEnabledItems(context, prefs).filterNot { prefs.homeItemKey(it) in hiddenKeys }
    }

    fun applyCurrentHomeLayout(
        context: Context,
        prefs: Prefs,
    ) {
        val orderedItems = orderedEnabledItems(context, prefs)
        val currentKeys = orderedItems.map(prefs::homeItemKey)
        prefs.homeItemOrderKeys = currentKeys

        val hiddenKeys = prefs.hiddenHomeItemKeys.intersect(currentKeys.toSet())
        if (hiddenKeys != prefs.hiddenHomeItemKeys) {
            prefs.hiddenHomeItemKeys = hiddenKeys
        }

        val visibleItems = orderedItems.filterNot { prefs.homeItemKey(it) in hiddenKeys }
        val truncatedItems = visibleItems.take(HomeLayout.TOTAL_SLOTS)

        truncatedItems.forEachIndexed { index, appModel ->
            prefs.setHomeAppModel(index, appModel)
        }

        for (index in truncatedItems.size until HomeLayout.TOTAL_SLOTS) {
            prefs.setHomeAppModel(index, emptyAppModel())
        }

        val pageCount = max(1, (truncatedItems.size + HomeLayout.APPS_PER_PAGE - 1) / HomeLayout.APPS_PER_PAGE)
        prefs.homePages = pageCount

        for (page in 1..HomeLayout.MAX_PAGES) {
            val startIndex = (page - 1) * HomeLayout.APPS_PER_PAGE
            val remaining = (truncatedItems.size - startIndex).coerceAtLeast(0)
            val appCount =
                when {
                    page > pageCount -> 0
                    remaining >= HomeLayout.APPS_PER_PAGE -> HomeLayout.APPS_PER_PAGE
                    else -> remaining
                }
            prefs.setAppsPerPage(page, appCount)
        }
    }

    private fun buildDefaultItems(
        context: Context,
        prefs: Prefs,
        collator: Collator,
    ): List<AppModel> =
        buildList {
            if (prefs.isToolEnabled(Tool.Phone)) {
                add(
                    Tool.Phone.toAppModel(
                        context = context,
                        collator = collator,
                        alias = prefs.getAppAlias(Tool.Phone.packageName),
                    ),
                )
            }

            val middleItems = mutableListOf<AppModel>()

            Tool.entries
                .filterNot { it == Tool.Phone || it == Tool.Settings }
                .filter(prefs::isToolEnabled)
                .forEach { tool ->
                    middleItems.add(
                        tool.toAppModel(
                            context = context,
                            collator = collator,
                            alias = prefs.getAppAlias(tool.packageName),
                        ),
                    )
                }

            prefs.enabledManagedAppIds
                .mapNotNull(ManagedAppCatalog::fromId)
                .forEach { app ->
                    middleItems.add(
                        app.toAppModel(
                            collator = collator,
                            alias = prefs.getAppAlias(app.packageName),
                        ),
                    )
                }

            prefs.enabledAndroidApps.forEach { app ->
                middleItems.add(
                    app.toAppModel(
                        collator = collator,
                        alias = prefs.getAppAlias(app.packageName),
                    ),
                )
            }

            middleItems.sortWith { left, right ->
                collator.compare(left.displayName, right.displayName)
            }

            addAll(middleItems)

            if (prefs.isToolEnabled(Tool.Settings)) {
                add(
                    Tool.Settings.toAppModel(
                        context = context,
                        collator = collator,
                        alias = prefs.getAppAlias(Tool.Settings.packageName),
                    ),
                )
            }
        }

    private fun emptyAppModel(): AppModel =
        AppModel(
            appLabel = "",
            key = null,
            appPackage = "",
            appActivityName = "",
            user = android.os.Process.myUserHandle(),
            appAlias = "",
            hasNotification = false,
            entryType = AppEntryType.Tool,
        )
}
