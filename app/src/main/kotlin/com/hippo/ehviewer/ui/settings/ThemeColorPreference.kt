package com.hippo.ehviewer.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.IconTooltip
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.theme.seedColorScheme
import com.hippo.ehviewer.ui.theme.wallpaperColorScheme
import com.materialkolor.hct.Hct

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
private val themeStyleLabels = listOf(R.string.theme_style_tonal, R.string.theme_style_vibrant, R.string.theme_style_expressive, R.string.theme_style_neutral, R.string.theme_style_monochrome)

@Composable
internal fun ThemeColorPreference(dark: Boolean) {
    var source by Settings.themeColorSource.asMutableState()
    var preset by Settings.themePresetColor.asMutableState()
    var custom by Settings.themeCustomColor.asMutableState()
    var style by Settings.themePaletteStyle.asMutableState()
    val amoled by Settings.blackDarkTheme.collectAsState()
    var editing by rememberSaveable { mutableStateOf(false) }
    val wallpaper = wallpaperColorScheme(dark, amoled)
    ThemeColorControls(
        source = source,
        preset = preset,
        custom = custom,
        style = style,
        dark = dark,
        wallpaper = wallpaper,
        onSource = { source = it },
        onPreset = { preset = it },
        onStyle = { style = it },
        onEdit = { editing = true },
        amoled = amoled,
    )
    if (editing) {
        CustomThemeColorDialog(custom, style, dark, amoled = amoled, onDismiss = { editing = false }) {
            custom = it
            source = 2
            editing = false
        }
    }
}

@Composable
internal fun ThemeColorControls(
    source: Int,
    preset: Int,
    custom: Int,
    style: Int,
    dark: Boolean,
    wallpaper: ColorScheme,
    onSource: (Int) -> Unit,
    onPreset: (Int) -> Unit,
    onStyle: (Int) -> Unit,
    onEdit: () -> Unit,
    amoled: Boolean = false,
) {
    val selectedSource = source.takeIf { it in 0..2 } ?: 0
    val selectedStyle = style.takeIf { it in themeStyleLabels.indices } ?: 0
    val seed = if (selectedSource == 1) preset else custom
    val colors = if (selectedSource == 0 && selectedStyle == 0) {
        wallpaper
    } else if (selectedSource == 0) {
        // The system primary carries the wallpaper hue without requesting wallpaper access.
        seedColorScheme(wallpaper.primary.toArgb(), dark, selectedStyle, amoled)
    } else {
        remember(seed, dark, selectedStyle, amoled) { seedColorScheme(seed, dark, selectedStyle, amoled) }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.theme_colors), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.theme_colors_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val sources = listOf(R.string.theme_color_dynamic, R.string.theme_color_presets, R.string.theme_color_custom)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.forEachIndexed { index, title ->
                FilterChip(selected = selectedSource == index, onClick = { onSource(index) }, label = { Text(stringResource(title)) })
            }
        }
        when (selectedSource) {
            0 -> Text(stringResource(R.string.theme_color_wallpaper), style = MaterialTheme.typography.bodySmall)
            1 -> FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                themePresetColors.forEach { (color, title) ->
                    val palette = remember(color, dark, selectedStyle) { seedColorScheme(color, dark, selectedStyle) }
                    val label = stringResource(title)
                    IconTooltip(label) {
                        Surface(
                            onClick = { onPreset(color) },
                            modifier = Modifier.size(52.dp).semantics {
                                contentDescription = label
                                selected = preset == color
                            },
                            shape = CircleShape,
                            border = BorderStroke(if (preset == color) 2.dp else 1.dp, if (preset == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Box(Modifier.padding(5.dp), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
                                    drawRect(palette.primary, size = Size(size.width / 2, size.height))
                                    drawRect(palette.secondary, topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height / 2))
                                    drawRect(palette.tertiary, topLeft = Offset(size.width / 2, size.height / 2), size = Size(size.width / 2, size.height / 2))
                                }
                                if (preset == color) Icon(Icons.Default.Check, contentDescription = null, tint = palette.onPrimary)
                            }
                        }
                    }
                }
            }
            2 -> OutlinedButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.theme_color_edit) + " · " + colorHex(custom))
            }
        }
        var stylesExpanded by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.theme_color_style), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Box {
                OutlinedButton(onClick = { stylesExpanded = true }) {
                    Text(stringResource(themeStyleLabels[selectedStyle]))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = stylesExpanded, onDismissRequest = { stylesExpanded = false }) {
                    themeStyleLabels.forEachIndexed { index, title ->
                        DropdownMenuItem(text = { Text(stringResource(title)) }, onClick = {
                            onStyle(index)
                            stylesExpanded = false
                        })
                    }
                }
            }
        }
        ThemeColorPreview(colors)
    }
}

@Composable
internal fun ThemeColorPreview(colors: ColorScheme) {
    MaterialTheme(colorScheme = colors) {
        Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.theme_color_preview), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.medium, color = colors.primaryContainer) {
                        Text("Aa", Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
                    }
                    listOf(colors.primary, colors.secondary, colors.tertiary).forEach { color ->
                        Surface(Modifier.size(32.dp), shape = CircleShape, color = color) {}
                    }
                }
                LinearWavyProgressIndicator(progress = { 0.72f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun colorHex(color: Int) = "#%06X".format(color and 0xFFFFFF)
private fun parseColorHex(text: String): Int? {
    val value = text.trim().removePrefix("#")
    return value.takeIf { it.length == 6 && it.all { c -> c.digitToIntOrNull(16) != null } }?.toIntOrNull(16)?.or(0xFF000000.toInt())
}

@Composable
internal fun CustomThemeColorDialog(initial: Int, style: Int, dark: Boolean, amoled: Boolean = false, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val initialHct = remember(initial) { Hct.fromInt(initial) }
    var hue by rememberSaveable { mutableFloatStateOf(initialHct.hue.toFloat()) }
    var chroma by rememberSaveable { mutableFloatStateOf(initialHct.chroma.toFloat()) }
    var tone by rememberSaveable { mutableFloatStateOf(initialHct.tone.toFloat()) }
    var hex by rememberSaveable { mutableStateOf(colorHex(initial)) }
    val parsed = parseColorHex(hex)
    val seed = parsed ?: Hct.from(hue.toDouble(), chroma.toDouble(), tone.toDouble()).toInt()
    fun update(h: Float = hue, c: Float = chroma, t: Float = tone) {
        hue = h
        chroma = c
        tone = t
        hex = colorHex(Hct.from(h.toDouble(), c.toDouble(), t.toDouble()).toInt())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_color_edit)) },
        confirmButton = { TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeColorPreview(remember(seed, dark, style, amoled) { seedColorScheme(seed, dark, style, amoled) })
                OutlinedTextField(
                    value = hex,
                    onValueChange = {
                        hex = it
                        parseColorHex(it)?.let { color ->
                            val hct = Hct.fromInt(color)
                            hue = hct.hue.toFloat()
                            chroma = hct.chroma.toFloat()
                            tone = hct.tone.toFloat()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.theme_color_hex)) },
                    supportingText = { Text(stringResource(R.string.theme_color_hex_hint)) },
                    singleLine = true,
                    isError = parsed == null,
                )
                ColorSlider(stringResource(R.string.theme_color_hue), hue, 0f..360f) { update(h = it) }
                ColorSlider(stringResource(R.string.theme_color_chroma), chroma, 0f..120f) { update(c = it) }
                ColorSlider(stringResource(R.string.theme_color_tone), tone, 0f..100f) { update(t = it) }
            }
        },
    )
}

@Composable
private fun ColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label · ${value.toInt()}", style = MaterialTheme.typography.labelMedium)
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
    }
}
