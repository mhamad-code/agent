package com.agent.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agent.ai.ui.theme.AgentTheme

/**
 * The glass app logo, drawn as a vector from the active theme tokens so it recolours
 * whenever the theme or the wallpaper changes.
 *
 * Shape: a rounded square with a 45 degree gradient from c2 through c3 to c4, a pale
 * outer ring in c5 at 35 percent, a diagonal sheen in white at 25 percent, a soft
 * shadow tinted with c4, and a thick chevron made of two round-capped bars. The lower
 * bar is translucent so the fold under the upper bar shows.
 *
 * The launcher icon is a separate, static asset built from the same geometry.
 */
@Composable
fun AppLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    shadow: Boolean = true,
) {
    val palette = AgentTheme.palette
    val shape = RoundedCornerShape(size * 0.235f)

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (glow) {
            Canvas(
                Modifier
                    .size(size * 1.75f)
                    .fillMaxSize()
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.c4.copy(alpha = 0.40f), Color.Transparent),
                        center = center,
                        radius = this.size.minDimension / 2f,
                    )
                )
            }
        }
        Canvas(
            Modifier
                .size(size)
                .then(
                    if (shadow) {
                        Modifier.shadow(
                            elevation = size * 0.10f,
                            shape = shape,
                            clip = false,
                            ambientColor = palette.c4,
                            spotColor = palette.c4,
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            val w = this.size.width
            val r = w * 0.235f
            val radius = CornerRadius(r, r)

            // Glass body: 45 degree gradient from the card colour to the muted colour.
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(palette.c2, palette.c3, palette.c4),
                    start = Offset(0f, 0f),
                    end = Offset(w, w),
                ),
                size = Size(w, w),
                cornerRadius = radius,
            )

            // Diagonal sheen across the top left, clipped to the rounded square.
            val clip = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, w, w), radius)) }
            clipPath(clip) {
                val band = Path().apply {
                    moveTo(0f, w * 0.55f)
                    lineTo(w * 0.55f, 0f)
                    lineTo(w * 0.78f, 0f)
                    lineTo(0f, w * 0.78f)
                    close()
                }
                drawPath(
                    path = band,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.04f)),
                        start = Offset(0f, 0f),
                        end = Offset(w * 0.62f, w * 0.62f),
                    ),
                )
            }

            // Pale outer ring.
            val inset = w * 0.052f
            drawRoundRect(
                color = palette.c5.copy(alpha = 0.35f),
                topLeft = Offset(inset, inset),
                size = Size(w - inset * 2f, w - inset * 2f),
                cornerRadius = CornerRadius(r - inset, r - inset),
                style = Stroke(width = w * 0.012f),
            )

            // Chevron: two overlapping round-capped bars.
            val stroke = w * 0.145f
            val top = Offset(w * 0.315f, w * 0.255f)
            val tip = Offset(w * 0.662f, w * 0.500f)
            val bottom = Offset(w * 0.342f, w * 0.752f)

            // Soft shadow so the chevron sits above the glass.
            drawLine(
                color = Color.Black.copy(alpha = 0.16f),
                start = top + Offset(0f, w * 0.018f),
                end = tip + Offset(0f, w * 0.018f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.12f),
                start = bottom + Offset(0f, w * 0.018f),
                end = tip + Offset(0f, w * 0.018f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )

            drawLine(
                color = palette.c5.copy(alpha = 0.90f),
                start = top,
                end = tip,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = palette.c5.copy(alpha = 0.62f),
                start = bottom,
                end = tip,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
