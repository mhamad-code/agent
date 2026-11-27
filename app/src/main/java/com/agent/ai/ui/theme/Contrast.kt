package com.agent.ai.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG relative luminance. */
fun relativeLuminance(color: Color): Float {
    fun channel(v: Float): Float =
        if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
}

/** WCAG contrast ratio between two opaque colours. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** Flattens [over] at [alpha] onto an opaque [base]. */
fun blend(base: Color, over: Color, alpha: Float): Color = Color(
    red = base.red * (1f - alpha) + over.red * alpha,
    green = base.green * (1f - alpha) + over.green * alpha,
    blue = base.blue * (1f - alpha) + over.blue * alpha,
    alpha = 1f,
)

/**
 * The smallest scrim between [min] and [max] that keeps [text] at [ratio] or better
 * on top of [worstCase], the brightest colour found in the wallpaper.
 */
fun requiredScrim(
    background: Color,
    text: Color,
    worstCase: Color,
    ratio: Float = 4.5f,
    min: Float = 0.35f,
    max: Float = 0.66f,
): Float {
    if (contrastRatio(text, blend(background, worstCase, min)) >= ratio) return min
    if (contrastRatio(text, blend(background, worstCase, max)) < ratio) return max
    var low = min
    var high = max
    repeat(18) {
        val mid = (low + high) / 2f
        if (contrastRatio(text, blend(background, worstCase, mid)) >= ratio) high = mid else low = mid
    }
    return high
}

/** Blends [color] toward [target] by [amount] (0 keeps the colour, 1 returns the target). */
fun mix(color: Color, target: Color, amount: Float): Color = Color(
    red = color.red + (target.red - color.red) * amount,
    green = color.green + (target.green - color.green) * amount,
    blue = color.blue + (target.blue - color.blue) * amount,
    alpha = 1f,
)
