package com.hippo.ehviewer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.ehviewer.core.i18n.R
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec.SpecVersion
import com.materialkolor.hct.Hct

internal enum class ThemePaletteStyle(val id: Int, val label: Int, val scheme: PaletteStyle) {
    Vivid(1, R.string.theme_style_vivid, PaletteStyle.Content),
    Tinted(2, R.string.theme_style_tinted, PaletteStyle.Content),
    Soft(0, R.string.theme_style_soft, PaletteStyle.TonalSpot),
    Monochrome(4, R.string.theme_style_monochrome, PaletteStyle.Monochrome),
    ;

    companion object {
        // IDs are stored in theme_palette_style. Old Neutral (3) and Tonal Spot (0) both become Soft.
        fun fromPreference(id: Int) = entries.firstOrNull { it.id == id } ?: Soft
    }
}

internal fun seedColorScheme(seed: Int, dark: Boolean, style: Int, amoled: Boolean = false): ColorScheme {
    val selected = ThemePaletteStyle.fromPreference(style)
    val seedColor = Color(seed or 0xFF000000.toInt())
    val base = dynamicColorScheme(
        primary = seedColor,
        isDark = dark,
        isAmoled = dark && amoled,
        style = selected.scheme,
        specVersion = SpecVersion.SPEC_2021,
    )
    if (selected.scheme != PaletteStyle.Content) return base

    val hct = Hct.fromInt(seedColor.toArgb())
    val accentTone = if (dark) (hct.tone + 3).coerceIn(50.0, 75.0) else hct.tone.coerceIn(35.0, 60.0)
    val accent = if (accentTone == hct.tone) seedColor else Color(Hct.from(hct.hue, hct.chroma, accentTone).toInt())
    // Keep the same accent in Vivid and Tinted. Surfaces distinguish the two styles.
    val tint = if (selected == ThemePaletteStyle.Tinted) (hct.chroma / 6).coerceAtMost(10.0) else null
    fun surface(color: Color, maxChroma: Double = 3.0): Color {
        val neutral = Hct.fromInt(color.toArgb())
        return Color(Hct.from(hct.hue, tint ?: neutral.chroma.coerceAtMost(maxChroma), neutral.tone).toInt())
    }
    fun primaryContainer(tone: Double) = Color(Hct.from(hct.hue, hct.chroma.coerceAtMost(48.0), tone).toInt())
    val paperChroma = if (dark) 3.0 else 1.5
    return base.copy(
        primary = accent,
        onPrimary = if (accent.luminance() <= 0.18f) Color.White else Color.Black,
        primaryContainer = primaryContainer(if (dark) 30.0 else 90.0),
        onPrimaryContainer = primaryContainer(if (dark) 90.0 else 30.0),
        surfaceTint = accent,
        background = surface(base.background, paperChroma),
        surface = surface(base.surface, paperChroma),
        surfaceBright = surface(base.surfaceBright, paperChroma),
        surfaceDim = surface(base.surfaceDim),
        surfaceVariant = surface(base.surfaceVariant),
        surfaceContainerLowest = surface(base.surfaceContainerLowest),
        surfaceContainerLow = surface(base.surfaceContainerLow),
        surfaceContainer = surface(base.surfaceContainer),
        surfaceContainerHigh = surface(base.surfaceContainerHigh),
        surfaceContainerHighest = surface(base.surfaceContainerHighest),
        outlineVariant = surface(base.outlineVariant, 4.0),
    )
}
