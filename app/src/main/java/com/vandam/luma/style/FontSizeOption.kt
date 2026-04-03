package com.vandam.luma.style

enum class FontSizeOption(
    val title: String,
    val fontScale: Float,
) {
    Small(
        title = "Small",
        fontScale = 0.72f,
    ),
    Medium(
        title = "Medium (Default)",
        fontScale = 0.86f,
    ),
    ;

    companion object {
        fun fromKey(key: String?): FontSizeOption = values().firstOrNull { it.name == key } ?: Medium
    }
}
