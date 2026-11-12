package com.hippo.ehviewer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.IconTooltip
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.theme.seedColorScheme
import com.hippo.ehviewer.ui.theme.wallpaperColorScheme
import com.materialkolor.hct.Hct

private val themeStyleLabels = listOf(R.string.theme_style_tonal, R.string.theme_style_vibrant, R.string.theme_style_expressive, R.string.theme_style_neutral, R.string.theme_style_monochrome)

@Composable
internal fun ThemeColorPreference(dark: Boolean) {
    var source by Settings.themeColorSource.asMutableState()
    var preset by Settings.themePresetColor.asMutableState()
    var custom by Settings.themeCustomColor.asMutableState()
    var style by Settings.themePaletteStyle.asMutableState()
    var savedColors by Settings.themeCustomPalette.asMutableState()
    var hiddenPresets by Settings.themeHiddenPresets.asMutableState()
    val amoled by Settings.blackDarkTheme.collectAsState()
    val palette = remember(source, preset, custom, savedColors, hiddenPresets) { loadThemePalette(source, preset, custom, savedColors, hiddenPresets) }
    fun apply(next: ThemePalette) {
        if (savedColors == null || next.customColors != palette.customColors) savedColors = encodeThemeColors(next.customColors)
        if (next.hiddenPresets != palette.hiddenPresets) hiddenPresets = encodeThemeColors(next.hiddenPresets)
        if (next.preset != preset) preset = next.preset
        if (next.custom != custom) custom = next.custom
        if (next.source != source) source = next.source
    }
    var adding by rememberSaveable { mutableStateOf(false) }
    val wallpaper = wallpaperColorScheme(dark, amoled)
    ThemeColorControls(palette, style, dark, wallpaper, ::apply, { style = it }, { adding = true }, amoled)
    if (adding) {
        CustomThemeColorDialog(palette.selectedColor ?: wallpaper.primary.toArgb(), style, dark, amoled, onDismiss = { adding = false }) {
            apply(palette.add(it))
            adding = false
        }
    }
}

@Composable
internal fun ThemeColorControls(
    palette: ThemePalette,
    style: Int,
    dark: Boolean,
    wallpaper: ColorScheme,
    onChange: (ThemePalette) -> Unit,
    onStyle: (Int) -> Unit,
    onAdd: () -> Unit,
    amoled: Boolean = false,
) {
    val selectedStyle = style.takeIf { it in themeStyleLabels.indices } ?: 0
    val dynamic = if (selectedStyle == 0) wallpaper else remember(wallpaper.primary, dark, selectedStyle, amoled) { seedColorScheme(wallpaper.primary.toArgb(), dark, selectedStyle, amoled) }
    val colors = palette.selectedColor?.let { seed -> remember(seed, dark, selectedStyle, amoled) { seedColorScheme(seed, dark, selectedStyle, amoled) } } ?: dynamic
    var removable by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(removable != null) { removable = null }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.theme_colors), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (removable != null) {
                    TextButton(onClick = { removable = null }) { Text(stringResource(android.R.string.cancel)) }
                } else {
                    StylePicker(selectedStyle, onStyle)
                    if (palette.hiddenPresets.isNotEmpty()) {
                        val restore = stringResource(R.string.theme_color_restore)
                        IconTooltip(restore) {
                            IconButton(onClick = { onChange(palette.restorePresets()) }) { Icon(Icons.Default.Restore, contentDescription = restore) }
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val dynamicLabel = stringResource(R.string.theme_color_dynamic)
            IconTooltip(dynamicLabel) {
                PaletteColor(dynamic, dynamicLabel, palette.selectedColor == null, dynamic = true, onClick = {
                    removable = null
                    onChange(palette.select(null))
                })
            }
            val removeLabel = stringResource(R.string.theme_color_remove)
            palette.visiblePresets.forEach { (color, title) ->
                val label = stringResource(title)
                val deleting = removable == color
                PaletteColor(
                    remember(color, dark, selectedStyle) { seedColorScheme(color, dark, selectedStyle) },
                    if (deleting) stringResource(R.string.theme_color_delete, label) else label,
                    selected = palette.selectedColor == color,
                    deleting = deleting,
                    onClick = {
                        if (deleting) onChange(palette.remove(color)) else onChange(palette.select(color))
                        removable = null
                    },
                    onLongClick = { removable = color },
                    longClickLabel = removeLabel,
                )
            }
            palette.visibleCustomColors.forEach { color ->
                val label = stringResource(R.string.theme_color_custom_value, colorHex(color))
                val deleting = removable == color
                PaletteColor(
                    remember(color, dark, selectedStyle) { seedColorScheme(color, dark, selectedStyle) },
                    if (deleting) stringResource(R.string.theme_color_delete, label) else label,
                    selected = palette.selectedColor == color,
                    deleting = deleting,
                    onClick = {
                        if (deleting) onChange(palette.remove(color)) else onChange(palette.select(color))
                        removable = null
                    },
                    onLongClick = { removable = color },
                    longClickLabel = removeLabel,
                )
            }
            val add = stringResource(R.string.theme_color_add)
            IconTooltip(add) {
                FilledTonalIconButton(onClick = {
                    removable = null
                    onAdd()
                }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Add, contentDescription = add)
                }
            }
        }
        ThemeColorPreview(colors)
    }
}

@Composable
private fun PaletteColor(
    colors: ColorScheme,
    label: String,
    selected: Boolean,
    dynamic: Boolean = false,
    deleting: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    // Color cells have their own long-press edit action, so a touch Tooltip must not intercept it.
    Surface(
        modifier = Modifier.size(52.dp).clip(CircleShape).combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = longClickLabel).semantics {
            contentDescription = label
            this.selected = selected
            role = if (deleting) Role.Button else Role.RadioButton
        },
        shape = CircleShape,
        color = if (deleting) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.padding(5.dp), contentAlignment = Alignment.Center) {
            if (deleting) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            } else {
                Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
                    drawRect(colors.primary, size = Size(size.width / 2, size.height))
                    drawRect(colors.secondary, topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height / 2))
                    drawRect(colors.tertiary, topLeft = Offset(size.width / 2, size.height / 2), size = Size(size.width / 2, size.height / 2))
                }
                if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = colors.onPrimary)
                if (dynamic) {
                    Surface(Modifier.align(Alignment.BottomEnd).size(20.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)) {
                        Icon(Icons.Default.Colorize, contentDescription = null, modifier = Modifier.padding(3.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StylePicker(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { expanded = true }) {
            Text(stringResource(themeStyleLabels[selected]))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            themeStyleLabels.forEachIndexed { index, title ->
                DropdownMenuItem(text = { Text(stringResource(title)) }, onClick = {
                    onSelect(index)
                    expanded = false
                })
            }
        }
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
        title = { Text(stringResource(R.string.theme_color_add)) },
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
                    placeholder = { Text("#RRGGBB") },
                    supportingText = if (parsed == null) {
                        { Text(stringResource(R.string.theme_color_invalid_hex)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    isError = parsed == null,
                )
                val hueColors = remember { (0..36).map { Color(Hct.from(it * 10.0, 100.0, 60.0).toInt()) } }
                val chromaColors = remember(hue) { (0..24).map { Color(Hct.from(hue.toDouble(), it * 5.0, 60.0).toInt()) } }
                val toneColors = remember(hue, chroma) { (0..20).map { Color(Hct.from(hue.toDouble(), chroma.toDouble(), it * 5.0).toInt()) } }
                ColorSlider(stringResource(R.string.theme_color_hue), hue, 0f..360f, hueColors, Color(Hct.from(hue.toDouble(), 100.0, 60.0).toInt())) { update(h = it) }
                ColorSlider(stringResource(R.string.theme_color_chroma), chroma, 0f..120f, chromaColors, Color(Hct.from(hue.toDouble(), chroma.toDouble(), 60.0).toInt())) { update(c = it) }
                ColorSlider(stringResource(R.string.theme_color_tone), tone, 0f..100f, toneColors, Color(seed)) { update(t = it) }
            }
        },
    )
}

@Composable
private fun ColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, colors: List<Color>, thumbColor: Color, onChange: (Float) -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val stops = remember(colors, rtl) { if (rtl) colors.reversed() else colors }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("%.1f".format(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = 0,
            modifier = Modifier.semantics { contentDescription = label },
            thumb = {
                Box(Modifier.size(26.dp).shadow(2.dp, CircleShape).background(thumbColor, CircleShape).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
            },
            track = {
                Canvas(Modifier.fillMaxWidth().height(20.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)) {
                    drawRect(Brush.horizontalGradient(stops))
                }
            },
        )
    }
}
