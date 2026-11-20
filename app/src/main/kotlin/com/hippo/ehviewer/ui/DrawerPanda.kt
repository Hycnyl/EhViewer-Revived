package com.hippo.ehviewer.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.VectorConfig
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import com.hippo.ehviewer.R
import com.materialkolor.hct.Hct

@Composable
internal fun DrawerPanda(followTheme: Boolean, modifier: Modifier = Modifier) {
    val art = ImageVector.vectorResource(R.drawable.sadpanda_low_poly)
    val painter = if (followTheme) {
        val primary = MaterialTheme.colorScheme.primary
        val fills = remember(art, primary) { terrainColors(art.root, primary) }
        rememberVectorPainter(
            defaultWidth = art.defaultWidth,
            defaultHeight = art.defaultHeight,
            viewportWidth = art.viewportWidth,
            viewportHeight = art.viewportHeight,
            autoMirror = false,
        ) { _, _ -> RenderVectorGroup(art.root, fills) }
    } else {
        rememberVectorPainter(art)
    }
    Image(painter = painter, contentDescription = null, modifier = modifier, contentScale = ContentScale.FillWidth)
}

private fun terrainColors(root: VectorGroup, primary: Color): Map<String, VectorConfig> = buildMap {
    val accent = Hct.fromInt(primary.toArgb())
    fun visit(group: VectorGroup) {
        for (node in group) {
            when (node) {
                is VectorGroup -> visit(node)
                is VectorPath -> if (node.name.startsWith("ground_")) {
                    val original = (node.fill as SolidColor).value
                    val tone = Hct.fromInt(original.toArgb()).tone
                    val color = Hct.from(accent.hue, accent.chroma.coerceAtMost(38.0), tone)
                    put(node.name, FacetColor(Color(color.toInt())))
                }
            }
        }
    }
    visit(root)
}

private class FacetColor(color: Color) : VectorConfig {
    private val brush = SolidColor(color)

    @Suppress("UNCHECKED_CAST")
    override fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T =
        if ((property == VectorProperty.Fill || property == VectorProperty.Stroke) && defaultValue is SolidColor) brush as T else defaultValue
}
