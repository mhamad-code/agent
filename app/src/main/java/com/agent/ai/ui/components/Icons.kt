package com.agent.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Line-art icons, drawn from the exact 24 x 24 paths used by the reference screens. */
object AgentIcons {
    private fun circle(cx: Float, cy: Float, r: Float): String {
        val d = r * 2
        return "M${cx - r} ${cy}a$r $r 0 1 0 $d 0a$r $r 0 1 0 ${-d} 0"
    }

    val Menu = listOf("M4 7h16M4 12h16M4 17h10")
    val ChevronDown = listOf("M6 9l6 6 6-6")
    val ChevronLeft = listOf("M15 5l-7 7 7 7")
    val ChevronRight = listOf("M9 5l7 7-7 7")
    val Plus = listOf("M12 5v14M5 12h14")
    val Folder = listOf("M3 7h6l2 2h10v10H3z")
    val Code = listOf("M8 8l-4 4 4 4M16 8l4 4-4 4")
    val Bolt = listOf("M13 3L5 13h6l-1 8 8-10h-6z")
    val ArrowUpRight = listOf("M7 17L17 7M9 7h8v8")
    val Check = listOf("M5 12l5 5 9-10")
    val Trash = listOf("M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13")
    val Shield = listOf("M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z")
    val Settings = listOf("M12 3v3M12 18v3M3 12h3M18 12h3", circle(12f, 12f, 3f))
    val Clock = listOf("M12 8v4l3 2", circle(12f, 12f, 8f))
    val Terminal = listOf("M4 6h16v12H4zM8 10l3 2-3 2M13 15h4")
    val Mic = listOf("M12 4a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V7a3 3 0 0 1 3-3zM6 11a6 6 0 0 0 12 0M12 17v3")
    val ArrowUp = listOf("M12 19V5M6 11l6-6 6 6")
    val Stop = listOf("M6 6h12v12H6z")
    val Link = listOf("M10 14a4 4 0 0 0 6 0l3-3a4 4 0 0 0-6-6l-1 1M14 10a4 4 0 0 0-6 0l-3 3a4 4 0 0 0 6 6l1-1")
    val Smartphone = listOf("M8 3h8v18H8zM11 18h2")
    val Play = listOf("M7 5l12 7-12 7z")
    val Sliders = listOf("M4 8h16M4 16h16M7 4v16M17 4v16")
    val Users = listOf("M4 21c1-4 4-6 8-6s7 2 8 6", circle(12f, 8f, 4f))
    val Globe = listOf("M12 3v18", circle(12f, 12f, 9f))
    val FileText = listOf("M5 4h14v16H5zM9 8h6M9 12h6M9 16h4")
    val Download = listOf("M12 4v10M8 10l4 4 4-4M5 19h14")
    val Info = listOf("M12 11v5M12 8h.01", circle(12f, 12f, 9f))
    val Sparkles = listOf("M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z")
    val Message = listOf("M4 5h16v11H9l-5 4z")
    val Fingerprint = listOf("M12 4a7 7 0 0 1 7 7v2M5 13v-2a7 7 0 0 1 3-5.7M9 20c1-2 1-4 1-6a2 2 0 0 1 4 0c0 3-1 5-2 7M6 17c.5-2 .5-4 .5-6")
    val Branch = listOf("M6 4v10a4 4 0 0 0 4 4h6", circle(6f, 4f, 2f), circle(18f, 18f, 2f))
    val Grid = listOf("M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z")
    val Tool = listOf("M14 4a4 4 0 0 0-5 5l-5 5 3 3 5-5a4 4 0 0 0 5-5l-3 3-3-3z")
    val Brain = listOf("M12 4a4 4 0 0 0-4 4 4 4 0 0 0-1 7 4 4 0 0 0 5 4M12 4a4 4 0 0 1 4 4 4 4 0 0 1 1 7 4 4 0 0 1-5 4M12 4v15")
    val Search = listOf("M10 4a6 6 0 1 0 0 12 6 6 0 0 0 0-12M15 15l5 5")
    val X = listOf("M6 6l12 12M18 6L6 18")
    val Copy = listOf("M9 9h10v12H9zM5 15V3h10")
    val Refresh = listOf("M20 12a8 8 0 1 1-2.4-5.7M20 4v4h-4")
    val More = listOf("M6 12h.01M12 12h.01M18 12h.01")
    val Paperclip = listOf("M9 12l6-6a3 3 0 0 1 4 4l-8 8a5 5 0 0 1-7-7l8-8")
    val Expand = listOf("M4 9V4h5M20 15v5h-5M4 4l6 6M20 20l-6-6")
    val Pin = listOf("M9 3h6l-1 6 4 4H6l4-4zM12 13v8")
    val Send = listOf("M4 12l16-8-6 16-2-6z")
    val Share = listOf("M12 15V3M8 7l4-4 4 4M5 13v8h14v-8")
    val Image = listOf(
        "M4 5h16v14H4z",
        "M8 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3",
        "M4 17l5-5 4 4 3-3 4 4",
    )
}

/**
 * Draws a line icon scaled from the 24 x 24 design grid.
 */
@Composable
fun LineIcon(
    paths: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color.Unspecified,
    strokeWidth: Float = 1.8f,
) {
    val color = if (tint == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint
    val parsed = remember(paths) {
        paths.map { data -> PathParser().parsePathString(data).toPath(Path()) }
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            parsed.forEach { path ->
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}
