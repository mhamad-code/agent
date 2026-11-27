package com.agent.ai.motion

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch

/**
 * Set to true by Settings > Reduce motion, or when the system animation scale is off.
 * Every motion helper in the app reads it and jumps straight to the final state.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Chat settings > Haptics. Off means every haptic helper does nothing. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Light, single-tick haptic used by send, approve and switch toggles. */
@Composable
fun rememberHaptics(): () -> Unit {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) {
        {
            if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}

/**
 * Touch and pointer feedback for cards and rows.
 *
 * Touch: scale to [pressedScale] and tilt up to [maxTilt] degrees toward the finger,
 * with a short camera distance so the tilt reads as depth. Release springs back
 * (damping 0.6). Hover, only on devices with a hoverable pointer: lift 3dp, grow to
 * [hoverScale] and a 14dp shadow.
 */
fun Modifier.pressFx(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    enabled: Boolean = true,
    restElevation: Dp = 0.dp,
    pressedScale: Float = 0.97f,
    hoverScale: Float = 1.02f,
    maxTilt: Float = 10f,
): Modifier = composed {
    val reduce = LocalReduceMotion.current
    if (!enabled || reduce) return@composed this

    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    val scale = remember { Animatable(1f) }
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val elevation = remember { Animatable(restElevation.value) }

    val spec = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(pressed, hovered, touch, size) {
        if (pressed) {
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val dx = if (touch == Offset.Unspecified || w <= 0f) 0f else ((touch.x - w / 2f) / (w / 2f)).coerceIn(-1f, 1f)
            val dy = if (touch == Offset.Unspecified || h <= 0f) 0f else ((touch.y - h / 2f) / (h / 2f)).coerceIn(-1f, 1f)
            launch { scale.animateTo(pressedScale, spec) }
            launch { rotY.animateTo(dx * maxTilt, spec) }
            launch { rotX.animateTo(-dy * maxTilt, spec) }
            launch { elevation.animateTo(2f, spec) }
        } else {
            launch { scale.animateTo(if (hovered) hoverScale else 1f, spec) }
            launch { rotX.animateTo(0f, spec) }
            launch { rotY.animateTo(0f, spec) }
            launch { lift.animateTo(if (hovered) -3f else 0f, spec) }
            launch { elevation.animateTo(if (hovered) 14f else restElevation.value, spec) }
        }
    }

    this
        .onSizeChanged { size = it }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        touch = event.changes.firstOrNull()?.position ?: Offset.Unspecified
                    }
                }
            }
        }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationX = rotX.value
            rotationY = rotY.value
            translationY = lift.value.dp.toPx()
            cameraDistance = 10f * density
            transformOrigin = TransformOrigin(0.5f, 0.5f)
        }
        .shadow(
            elevation = elevation.value.dp,
            shape = shape,
            clip = false,
        )
}

/**
 * Lists enter one row at a time: 10dp lower and transparent, 400ms each, 70ms apart.
 */
fun Modifier.staggeredEntry(
    index: Int,
    distance: Dp = 10.dp,
    stepMs: Int = 70,
    durationMs: Int = 400,
): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) {
        if (reduce) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = durationMs,
                    delayMillis = (index.coerceAtLeast(0)) * stepMs,
                    easing = FastOutLinearInEasing,
                ),
            )
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * distance.toPx()
    }
}
