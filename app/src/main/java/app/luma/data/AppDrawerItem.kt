package app.luma.data

sealed class AppDrawerItem {
    data class App(val appModel: AppModel) : AppDrawerItem()
    data class Folder(val folderModel: FolderModel) : AppDrawerItem()

    val label: String
        get() = when (this) {
            is App -> appModel.appAlias.ifEmpty { appModel.appLabel }
            is Folder -> folderModel.name
        }
}
