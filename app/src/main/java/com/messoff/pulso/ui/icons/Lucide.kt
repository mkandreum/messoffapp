package com.messoff.pulso.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val cache = HashMap<Pair<List<String>, Float>, ImageVector>()

/** Construye un icono Lucide (24×24, trazo redondeado) con el grosor pedido. */
fun lucideVector(paths: List<String>, stroke: Float = 1.8f): ImageVector =
    cache.getOrPut(paths to stroke) {
        val builder = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (d in paths) {
            builder.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        builder.build()
    }

@Composable
fun LucideIcon(
    paths: List<String>,
    tint: Color,
    size: Dp = 20.dp,
    stroke: Float = 1.8f,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val vector = remember(paths, stroke) { lucideVector(paths, stroke) }
    Icon(vector, contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}
