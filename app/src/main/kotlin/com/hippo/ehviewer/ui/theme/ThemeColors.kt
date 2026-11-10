package com.hippo.ehviewer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec.SpecVersion

internal val themePaletteStyles = listOf(PaletteStyle.TonalSpot, PaletteStyle.Vibrant, PaletteStyle.Expressive, PaletteStyle.Neutral, PaletteStyle.Monochrome)

internal fun seedColorScheme(seed: Int, dark: Boolean, style: Int, amoled: Boolean = false): ColorScheme = dynamicColorScheme(
    primary = Color(seed or 0xFF000000.toInt()),
    isDark = dark,
    isAmoled = dark && amoled,
    style = themePaletteStyles.getOrElse(style) { PaletteStyle.TonalSpot },
    specVersion = SpecVersion.SPEC_2021,
)
