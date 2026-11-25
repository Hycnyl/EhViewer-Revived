package com.hippo.ehviewer.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.IconTooltip
import com.hippo.ehviewer.client.parser.Limits
import com.materialkolor.contrast.Contrast
import com.materialkolor.hct.Hct

internal enum class ImageQuotaLevel(
    val icon: ImageVector,
    val label: Int,
    private val hue: Double,
    private val chroma: Double,
) {
    Available(Icons.Default.Eco, R.string.image_limits_usage_available, 145.0, 50.0),
    NearLimit(Icons.Default.Info, R.string.image_limits_usage_near, 92.0, 56.0),
    Exhausted(Icons.Default.Warning, R.string.image_limits_usage_exhausted, 25.0, 66.0),
    ;

    fun tint(primary: Color, background: Color): Color {
        val accent = Hct.fromInt(primary.toArgb())
        val backgroundTone = Hct.fromInt(background.toArgb()).tone
        val hueDistance = (accent.hue - hue + 540.0) % 360.0 - 180.0
        val adjustedHue = hue + (hueDistance * 0.15).coerceIn(-8.0, 8.0)
        val adjustedChroma = chroma * 0.7 + accent.chroma * 0.3
        val tone = if (backgroundTone >= 50.0) {
            minOf(55.0, Contrast.darkerUnsafe(backgroundTone, 3.2))
        } else {
            maxOf(78.0, Contrast.lighterUnsafe(backgroundTone, 3.2))
        }
        return Color(Hct.from(adjustedHue, adjustedChroma, tone).toInt())
    }
}

internal fun Limits.quotaLevel(): ImageQuotaLevel? = when {
    maximum <= 0 -> null
    current >= maximum -> ImageQuotaLevel.Exhausted
    current * 4L > maximum * 3L -> ImageQuotaLevel.NearLimit
    else -> ImageQuotaLevel.Available
}

@Composable
internal fun ImageQuotaTitle(level: ImageQuotaLevel?, onDisableHint: () -> Unit) {
    var showHint by remember { mutableStateOf(false) }
    val fontSize = LocalTextStyle.current.fontSize
    val iconSize = with(LocalDensity.current) { fontSize.toDp() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(iconSize / 4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.image_limits), modifier = Modifier.weight(1f, fill = false))
        if (level != null) {
            val label = stringResource(level.label)
            val primary = MaterialTheme.colorScheme.primary
            val background = AlertDialogDefaults.containerColor
            val tint = remember(level, primary, background) { level.tint(primary, background) }
            IconTooltip(label) {
                Icon(
                    imageVector = level.icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(iconSize).clip(CircleShape).clickable(role = Role.Button) { showHint = true },
                )
            }
        }
    }

    if (showHint) {
        AlertDialog(
            onDismissRequest = { showHint = false },
            title = { Text(stringResource(R.string.image_limits_usage_hint)) },
            text = {
                Text(
                    text = stringResource(R.string.image_limits_usage_description),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showHint = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.image_limits_usage_acknowledge))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHint = false
                        onDisableHint()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.image_limits_usage_hide))
                }
            },
        )
    }
}
