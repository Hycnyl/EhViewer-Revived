package com.hippo.ehviewer.ui.theme

import android.app.WallpaperManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.scrollbar.LocalScrollbarStyle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.ehviewer.core.ui.component.scrollbarStyle
import com.ehviewer.core.util.isAtLeastOMR1
import com.ehviewer.core.util.isAtLeastS
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.collectAsState
import com.materialkolor.dynamicColorScheme

fun ColorScheme.amoled(amoled: Boolean) = if (amoled) {
    copy(
        surface = Color.Black,
        onSurface = Color.White,
        background = Color.Black,
        onBackground = Color.White,
    )
} else {
    this
}

@Composable
fun EhTheme(useDarkTheme: Boolean, content: @Composable () -> Unit) {
    val amoled by Settings.blackDarkTheme.collectAsState()
    val source by Settings.themeColorSource.collectAsState()
    val preset by Settings.themePresetColor.collectAsState()
    val custom by Settings.themeCustomColor.collectAsState()
    val style by Settings.themePaletteStyle.collectAsState()
    val wallpaper = if (source !in 1..2) wallpaperColorScheme(useDarkTheme, amoled) else null
    val seed = when (source) {
        1 -> preset
        2 -> custom
        else -> wallpaper!!.primary.toArgb()
    }
    val colors = if (wallpaper != null && style !in 1 until themePaletteStyles.size) {
        wallpaper
    } else {
        remember(seed, useDarkTheme, style, amoled) { seedColorScheme(seed, useDarkTheme, style, amoled) }
    }

    MaterialTheme(colorScheme = colors, motionScheme = CustomMotionScheme) {
        val scrollbarStyle = scrollbarStyle(color = MaterialTheme.colorScheme.primary)
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            LocalScrollbarStyle provides scrollbarStyle,
            content = content,
        )
    }
}

@Composable
internal fun wallpaperColorScheme(useDarkTheme: Boolean, amoled: Boolean): ColorScheme {
    val context = LocalContext.current
    return if (isAtLeastS) {
        if (useDarkTheme) {
            dynamicDarkColorScheme(context).amoled(amoled)
        } else {
            dynamicLightColorScheme(context)
        }
    } else {
        val color = if (isAtLeastOMR1) extractWallPaperPalette() else null
        if (color != null) {
            dynamicColorScheme(
                primary = color.first,
                isDark = useDarkTheme,
                isAmoled = amoled,
                secondary = color.second,
                tertiary = color.third,
            )
        } else {
            if (useDarkTheme) {
                darkColorScheme().amoled(amoled)
            } else {
                expressiveLightColorScheme()
            }
        }
    }
}

@Composable
fun Color.scrim() = copy(alpha = if (isSystemInDarkTheme()) 0.5f else 0.9f)

typealias WallPaperPalette = Triple<Color, Color?, Color?>

@Composable
@RequiresApi(Build.VERSION_CODES.O_MR1)
fun extractWallPaperPalette(): WallPaperPalette? {
    val colors = WallpaperManager.getInstance(LocalContext.current)?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
    val primary = colors.primaryColor.toArgb().let { Color(it) }
    val secondary = colors.secondaryColor?.toArgb()?.let { Color(it) }
    val tertiary = colors.tertiaryColor?.toArgb()?.let { Color(it) }
    return WallPaperPalette(primary, secondary, tertiary)
}

// Spatial motion retains a physical settling curve; colour/opacity use Material's effect specs.
object CustomMotionScheme : MotionScheme by MotionScheme.expressive() {
    override fun <T> defaultSpatialSpec() = spring<T>(dampingRatio = 1f, stiffness = 400f)
    override fun <T> fastSpatialSpec() = spring<T>(dampingRatio = 1f, stiffness = 550f)
    override fun <T> slowSpatialSpec() = spring<T>(dampingRatio = 1f, stiffness = 250f)
}
