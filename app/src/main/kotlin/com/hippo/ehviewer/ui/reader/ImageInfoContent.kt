package com.hippo.ehviewer.ui.reader

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.hippo.ehviewer.image.ImageFileInfo
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

@Composable
internal fun ImageInfoContent(info: ImageFileInfo, pageNumber: Int, totalPages: Int) {
    val width = info.displayWidth
    val height = info.displayHeight
    val locale = LocalConfiguration.current.locales[0]
    val mimeType = info.dimensions.mimeType
    val format = when (mimeType) {
        "image/jpeg" -> "JPEG"
        "image/webp" -> "WebP"
        "image/heic", "image/heif" -> "HEIF"
        null -> "—"
        else -> mimeType.substringAfter('/').uppercase(Locale.ROOT)
    }
    val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
    val pixels = info.dimensions.width.toLong() * info.dimensions.height
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val divisor = gcd(width, height)
    val direction = stringResource(
        when {
            width < height -> R.string.image_info_portrait
            width > height -> R.string.image_info_landscape
            else -> R.string.image_info_square
        },
    )
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ImageDimensionDiagram(width, height, Modifier.fillMaxWidth().padding(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoValue(stringResource(R.string.image_info_format), format, Modifier.weight(1f))
            InfoValue(stringResource(R.string.image_info_file_size), Formatter.formatFileSize(LocalContext.current, info.byteCount), Modifier.weight(1f))
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoValue(stringResource(R.string.image_info_aspect_ratio), "${width / divisor}:${height / divisor}", Modifier.weight(1f))
            InfoValue(stringResource(R.string.image_info_pixels), "${number.format(pixels / 1_000_000.0)} MP", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoValue(stringResource(R.string.image_info_page), "$pageNumber / $totalPages", Modifier.weight(1f))
            InfoValue(stringResource(R.string.image_info_orientation), direction, Modifier.weight(1f))
        }
        if (info.swapsAxes) {
            InfoValue(
                stringResource(R.string.image_info_encoded_resolution),
                "${info.dimensions.width} × ${info.dimensions.height} px",
            )
        }
    }
}

@Composable
private fun InfoValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** Fit the unmodified aspect ratio; dimension leaders always originate at the measured edges. */
@Composable
internal fun ImageDimensionDiagram(width: Int, height: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
    val widthText = textMeasurer.measure("$width px", style)
    val heightText = textMeasurer.measure("$height px", style)
    val description = stringResource(R.string.image_resolution, width, height)
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    Canvas(modifier.height(208.dp * fontScale).semantics { contentDescription = description }) {
        val inset = 8.dp.toPx()
        val leader = 14.dp.toPx()
        val labelGap = 4.dp.toPx()
        val leftGutter = heightText.size.height + leader + labelGap + inset
        val topGutter = widthText.size.height + leader + labelGap + inset
        val availableWidth = (size.width - leftGutter - inset).coerceAtLeast(1f)
        val availableHeight = (size.height - topGutter - inset).coerceAtLeast(1f)
        val scale = min(availableWidth / width, availableHeight / height)
        val boxWidth = width * scale
        val boxHeight = height * scale
        val left = leftGutter + (availableWidth - boxWidth) / 2
        val top = topGutter + (availableHeight - boxHeight) / 2
        val right = left + boxWidth
        val bottom = top + boxHeight
        val lineY = top - leader
        val lineX = left - leader
        val stroke = 1.dp.toPx()
        val arrow = 5.dp.toPx()
        val color = scheme.primary
        fun line(start: Offset, end: Offset, extension: Boolean = false) {
            drawLine(if (extension) scheme.outline else color, start, end, strokeWidth = stroke)
        }
        fun arrowhead(tip: Offset, direction: Offset) {
            val base = tip + direction * arrow
            val side = Offset(-direction.y, direction.x) * (arrow * 0.55f)
            drawPath(
                Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo((base + side).x, (base + side).y)
                    lineTo((base - side).x, (base - side).y)
                    close()
                },
                color,
            )
        }
        drawRect(scheme.primaryContainer.copy(alpha = 0.45f), Offset(left, top), Size(boxWidth, boxHeight))
        drawRect(scheme.onSurfaceVariant, Offset(left, top), Size(boxWidth, boxHeight), style = Stroke(1.5.dp.toPx()))
        // Width: two vertical extension lines, a double-headed horizontal line, parallel label.
        line(Offset(left, top - labelGap), Offset(left, lineY - arrow), true)
        line(Offset(right, top - labelGap), Offset(right, lineY - arrow), true)
        val externalWidthArrows = boxWidth < arrow * 3
        val widthTail = if (externalWidthArrows) arrow * 2 else 0f
        line(Offset(left - widthTail, lineY), Offset(right + widthTail, lineY))
        arrowhead(Offset(left, lineY), Offset(if (externalWidthArrows) -1f else 1f, 0f))
        arrowhead(Offset(right, lineY), Offset(if (externalWidthArrows) 1f else -1f, 0f))
        drawText(widthText, color, Offset((left + right - widthText.size.width) / 2, lineY - labelGap - widthText.size.height))
        // Height: horizontal extension lines; the label follows the vertical measurement line.
        line(Offset(left - labelGap, top), Offset(lineX - arrow, top), true)
        line(Offset(left - labelGap, bottom), Offset(lineX - arrow, bottom), true)
        val externalHeightArrows = boxHeight < arrow * 3
        val heightTail = if (externalHeightArrows) arrow * 2 else 0f
        line(Offset(lineX, top - heightTail), Offset(lineX, bottom + heightTail))
        arrowhead(Offset(lineX, top), Offset(0f, if (externalHeightArrows) -1f else 1f))
        arrowhead(Offset(lineX, bottom), Offset(0f, if (externalHeightArrows) 1f else -1f))
        withTransform({
            translate(lineX - labelGap - heightText.size.height, (top + bottom + heightText.size.width) / 2)
            rotate(-90f, Offset.Zero)
        }) {
            drawText(heightText, color, Offset.Zero)
        }
    }
}
