package com.agent.ai.ui.theme

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.agent.ai.R
import com.agent.ai.motion.LocalReduceMotion

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
)

private val AgentTypography = Typography(
    displayLarge = TextStyle(fontFamily = InterFamily, fontSize = 32.sp),
    headlineSmall = TextStyle(fontFamily = InterFamily, fontSize = 25.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontFamily = InterFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = InterFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = InterFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = InterFamily, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = InterFamily, fontSize = 11.sp),
)

/** Monospace style used by the code editor, terminal and inline commands. */
val MonoStyle = TextStyle(
    fontFamily = MonoFamily,
    fontSize = 12.sp,
    lineHeight = 20.sp,
)

val LocalAgentPalette = staticCompositionLocalOf { Palettes.default }
val LocalAgentBackground = staticCompositionLocalOf { Backgrounds.default }

object AgentTheme {
    val palette: AgentPalette
        @Composable get() = LocalAgentPalette.current

    val background: AgentBackground
        @Composable get() = LocalAgentBackground.current
}

/**
 * Every colour token moves to the new theme over 450ms instead of jumping, so the logo
 * and the surfaces recolour in one motion. With Reduce motion on, the swap is instant.
 */
@Composable
private fun animatedPalette(target: AgentPalette): AgentPalette {
    val reduce = LocalReduceMotion.current
    val spec: AnimationSpec<Color> = if (reduce) snap() else tween(450)

    val c0 by animateColorAsState(target.c0, spec, label = "c0")
    val c1 by animateColorAsState(target.c1, spec, label = "c1")
    val c2 by animateColorAsState(target.c2, spec, label = "c2")
    val c3 by animateColorAsState(target.c3, spec, label = "c3")
    val c4 by animateColorAsState(target.c4, spec, label = "c4")
    val c5 by animateColorAsState(target.c5, spec, label = "c5")
    val accent by animateColorAsState(target.accent, spec, label = "accent")

    return target.copy(
        c0 = c0,
        c1 = c1,
        c2 = c2,
        c3 = c3,
        c4 = c4,
        c5 = c5,
        accent = accent,
    )
}

@Composable
fun AgentTheme(
    palette: AgentPalette,
    background: AgentBackground,
    content: @Composable () -> Unit,
) {
    val animated = animatedPalette(palette)

    val scheme = if (animated.isLight) {
        lightColorScheme(
            primary = animated.c5,
            onPrimary = animated.c0,
            secondary = animated.accent,
            onSecondary = animated.c0,
            background = animated.c0,
            onBackground = animated.c5,
            surface = animated.c2,
            onSurface = animated.c5,
            surfaceVariant = animated.c1,
            onSurfaceVariant = animated.c4,
            outline = animated.c3,
            error = Color(0xFFB3261E),
        )
    } else {
        darkColorScheme(
            primary = animated.c5,
            onPrimary = animated.c0,
            secondary = animated.accent,
            onSecondary = animated.c0,
            background = animated.c0,
            onBackground = animated.c5,
            surface = animated.c2,
            onSurface = animated.c5,
            surfaceVariant = animated.c1,
            onSurfaceVariant = animated.c4,
            outline = animated.c3,
            error = Color(0xFFFFB4AB),
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = animated.isLight
                isAppearanceLightNavigationBars = animated.isLight
            }
        }
    }

    CompositionLocalProvider(
        LocalAgentPalette provides animated,
        LocalAgentBackground provides background,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AgentTypography,
            content = content,
        )
    }
}

/**
 * The screen backdrop: the theme gradient, or the selected wallpaper under the
 * adaptive scrim. A wallpaper change cross-fades over 400ms.
 */
@Composable
fun AgentBackdrop(modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val background = AgentTheme.background
    val spec: FiniteAnimationSpec<Float> = if (reduce) snap() else tween(400)

    Crossfade(targetState = background, animationSpec = spec, label = "wallpaper") { bg ->
        BackdropLayer(bg, modifier)
    }
}

@Composable
private fun BackdropLayer(background: AgentBackground, modifier: Modifier) {
    val palette = AgentTheme.palette
    val fileBitmap = remember(background.imagePath) {
        background.imagePath?.let { path ->
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            fileBitmap != null -> {
                Image(
                    bitmap = fileBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            background.imageRes != null -> {
                Image(
                    painter = painterResource(background.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0f to palette.c3,
                                        0.42f to palette.c1,
                                        0.80f to palette.c0,
                                    ),
                                    center = Offset(size.width * 0.5f, 0f),
                                    radius = size.width * 1.3f,
                                )
                            )
                        }
                )
            }
        }

        if (!background.isDefault) {
            val a = background.scrimAlpha
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to palette.c0.copy(alpha = (a + 0.04f).coerceAtMost(0.72f)),
                            0.45f to palette.c0.copy(alpha = a),
                            1f to palette.c0.copy(alpha = (a + 0.06f).coerceAtMost(0.74f)),
                        )
                    )
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Transparent,
                            1f to palette.c0.copy(alpha = 0.35f),
                        )
                    )
            )
        }
    }
}

/** Shared inset constants taken from the 390 x 844 reference screens. */
object Dimens {
    val screenPadding = 12.dp
    val screenPaddingWide = 16.dp
    val gap = 8.dp
    val gapWide = 12.dp
    val headerHeight = 44.dp
    val cardRadius = 16.dp
    val buttonRadius = 14.dp

    /** Settings list metrics from the update spec. */
    val rowMinHeight = 68.dp
    val rowPadding = 16.dp
    val iconTile = 40.dp
    val iconGap = 16.dp
    val groupRadius = 20.dp
    val groupLabelTop = 24.dp
    val listTop = 16.dp
    val listBottom = 24.dp
    val dividerInset = rowPadding + iconTile + iconGap
}
