package app.luma.style

enum class FontSizeOption(
    val fontScale: Float,
) {
    System(0.86f),
    Small(0.72f),
    Medium(0.86f),
    Large(1.0f),
    ExtraLarge(1.15f),
    ;

    companion object {
        fun fromKey(key: String?): FontSizeOption = entries.firstOrNull { it.name == key } ?: System
    }
}
