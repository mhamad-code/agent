package com.agent.ai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.agent.ai.motion.LocalReduceMotion
import com.agent.ai.ui.theme.AgentTheme

/**
 * The home logo: it floats up and down by 6dp over 4 seconds while the glow behind it
 * breathes between 45 and 80 percent opacity and scale 1.0 to 1.08 over 3 seconds.
 *
 * While [thinking] the glow also rotates a full turn every 1.3 seconds at 90 percent
 * opacity. Infinite motion stops when Reduce motion is on, when the app is not in the
 * foreground, or while the screen is off.
 */
@Composable
fun FloatingLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    thinking: Boolean = false,
) {
    val palette = AgentTheme.palette
    val reduce = LocalReduceMotion.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = !reduce && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val bob = remember { Animatable(0.5f) }
    val breath = remember { Animatable(0.5f) }
    val spin = remember { Animatable(0f) }

    LaunchedEffect(active) {
        if (!active) {
            bob.snapTo(0.5f)
            breath.snapTo(0.5f)
            spin.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            bob.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
            bob.animateTo(0f, tween(2000, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            breath.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
            breath.animateTo(0f, tween(1500, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(active, thinking) {
        if (!active || !thinking) {
            spin.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            spin.animateTo(360f, tween(1300, easing = androidx.compose.animation.core.LinearEasing))
            spin.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = (-6).dp.toPx() * bob.value },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(size * 1.9f)
                .graphicsLayer {
                    val scale = 1f + 0.08f * breath.value
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.45f + 0.35f * breath.value
                    rotationZ = spin.value
                }
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.c4.copy(alpha = if (thinking) 0.90f else 0.55f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = this.size.minDimension / 2f,
                )
            )
        }
        AppLogo(size = size, shadow = false)
    }
}
