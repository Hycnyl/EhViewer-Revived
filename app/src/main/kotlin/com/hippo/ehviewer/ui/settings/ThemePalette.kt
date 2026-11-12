package com.hippo.ehviewer.ui.settings

import com.ehviewer.core.i18n.R

internal val themePresetColors = listOf(
    0xFF6750A4.toInt() to R.string.theme_color_purple,
    0xFF0061A4.toInt() to R.string.theme_color_blue,
    0xFF006A60.toInt() to R.string.theme_color_teal,
    0xFF386A20.toInt() to R.string.theme_color_green,
    0xFF7E5700.toInt() to R.string.theme_color_amber,
    0xFF964900.toInt() to R.string.theme_color_orange,
    0xFF984061.toInt() to R.string.theme_color_rose,
    0xFF5C5F62.toInt() to R.string.theme_color_slate,
)

internal data class ThemePalette(
    val source: Int,
    val preset: Int,
    val custom: Int,
    val customColors: List<Int>,
    val hiddenPresets: Set<Int> = emptySet(),
) {
    val selectedColor get() = when (source) {
        1 -> preset
        2 -> custom
        else -> null
    }
    val visiblePresets get() = themePresetColors.filterNot { it.first in hiddenPresets }
    val visibleCustomColors get() = customColors.distinct().filterNot { color -> visiblePresets.any { it.first == color } }

    fun select(color: Int?): ThemePalette = when {
        color == null -> copy(source = 0)
        visiblePresets.any { it.first == color } -> copy(source = 1, preset = color)
        else -> copy(source = 2, custom = color)
    }

    fun add(color: Int): ThemePalette {
        val opaque = color or 0xFF000000.toInt()
        val next = if (visiblePresets.any { it.first == opaque }) this else copy(customColors = (customColors + opaque).distinct())
        return next.select(opaque)
    }

    fun remove(color: Int) = copy(
        source = if (selectedColor == color) 0 else source,
        customColors = customColors.filterNot { it == color },
        hiddenPresets = if (themePresetColors.any { it.first == color }) hiddenPresets + color else hiddenPresets,
    )

    fun restorePresets() = copy(
        hiddenPresets = emptySet(),
        customColors = customColors.filterNot { color -> themePresetColors.any { it.first == color } },
    )
}

internal fun decodeThemeColors(value: String?) = value.orEmpty().split(',').mapNotNull { part ->
    part.takeIf { it.length == 6 }?.toIntOrNull(16)?.or(0xFF000000.toInt())
}.distinct()

internal fun encodeThemeColors(colors: Collection<Int>) = colors.joinToString(",") { "%06X".format(it and 0xFFFFFF) }

internal fun loadThemePalette(source: Int, preset: Int, custom: Int, saved: String?, hidden: String): ThemePalette {
    val removed = decodeThemeColors(hidden).toSet()
    val visible = themePresetColors.map { it.first }.filterNot { it in removed }
    // A null list identifies the previous single-custom-color UI. Preserve that color once,
    // including when a preset is currently selected, without duplicating a visible preset.
    val colors = saved?.let(::decodeThemeColors) ?: listOf(custom).filterNot { it in visible }
    return ThemePalette(source, preset, custom, colors, removed)
}
