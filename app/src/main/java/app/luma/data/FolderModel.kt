package app.luma.data

import java.util.UUID

data class FolderModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val apps: List<PinnedAppEntry> = emptyList(),
)
