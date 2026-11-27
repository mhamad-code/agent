package com.agent.ai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.agent.ai.motion.pressFx
import com.agent.ai.motion.rememberHaptics
import com.agent.ai.motion.staggeredEntry
import com.agent.ai.ui.theme.AgentBackdrop
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.Dimens
import com.agent.ai.ui.theme.MonoStyle

/**
 * Root of every screen: the themed backdrop plus the 390 x 844 content frame,
 * scaled up to the current window and capped on tablets.
 */
@Composable
fun AgentScreen(
    padding: Dp = Dimens.screenPadding,
    gap: Dp = Dimens.gap,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AgentBackdrop()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val base = Modifier
                .fillMaxHeight()
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(padding)
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val needsScroll = maxHeight < 620.dp
                if (scrollable || needsScroll) {
                    Column(
                        modifier = base
                            .widthIn(max = 560.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        content = content,
                    )
                } else {
                    Column(
                        modifier = base,
                        verticalArrangement = Arrangement.spacedBy(gap),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
fun AppHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.headerHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            SquareButton(icon = AgentIcons.ChevronLeft, label = "Back", onClick = onBack)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AgentTheme.palette.c5,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = AgentTheme.palette.c4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
fun SquareButton(
    icon: List<String>,
    label: String,
    onClick: () -> Unit = {},
    size: Dp = 44.dp,
    tint: Color? = null,
) {
    val palette = AgentTheme.palette
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.c2)
            .border(1.dp, palette.c3, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LineIcon(icon, size = 20.dp, tint = tint ?: palette.c5)
    }
}

@Composable
fun SmallButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val palette = AgentTheme.palette
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.c5)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.c0)
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp),
    radius: Dp = Dimens.cardRadius,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = AgentTheme.palette
    val shape = RoundedCornerShape(radius)
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressFx(source, shape, enabled = onClick != null)
            .clip(shape)
            .background(palette.c2)
            .border(1.dp, palette.c3, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = source,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(padding),
        content = content,
    )
}

@Composable
fun PanelDivider(insetStart: Dp = 0.dp, insetEnd: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = insetStart, end = insetEnd)
            .height(1.dp)
            .background(AgentTheme.palette.c3)
    )
}

@Composable
fun PanelRow(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = Dimens.rowPadding, vertical = 12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressFx(source, RoundedCornerShape(0.dp), enabled = onClick != null)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = source, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .heightIn(min = Dimens.rowMinHeight)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.iconGap),
        content = content,
    )
}

@Composable
fun IconTile(
    size: Dp = 40.dp,
    radius: Dp = 12.dp,
    background: Color? = null,
    tint: Color? = null,
    content: @Composable () -> Unit,
) {
    val palette = AgentTheme.palette
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(background ?: palette.c1),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides (tint ?: palette.c4)
        ) { content() }
    }
}

/**
 * Settings-style row: 68dp minimum height, 16dp horizontal padding, a 40dp icon tile,
 * 16dp between tile and text, a 16sp semibold title and a 13sp subtitle.
 */
@Composable
fun SettingRow(
    icon: List<String>,
    title: String,
    subtitle: String? = null,
    index: Int = -1,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = AgentTheme.palette
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (index >= 0) Modifier.staggeredEntry(index) else Modifier)
            .pressFx(source, RoundedCornerShape(0.dp), enabled = onClick != null)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = source, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .heightIn(min = Dimens.rowMinHeight)
            .padding(horizontal = Dimens.rowPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.iconGap),
    ) {
        Box(
            Modifier
                .size(Dimens.iconTile)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.c1)
                .border(1.dp, palette.c3, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(icon, size = 20.dp, tint = palette.c4)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = palette.c4,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        } else {
            LineIcon(AgentIcons.ChevronRight, size = 18.dp, tint = palette.c4)
        }
    }
}

/** Group label plus the rounded, bordered list container used by every list screen. */
@Composable
fun ListGroup(
    title: String? = null,
    topPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = AgentTheme.palette
    Column(Modifier.fillMaxWidth().padding(top = topPadding)) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = palette.c4,
                modifier = Modifier.padding(
                    start = Dimens.rowPadding,
                    top = Dimens.groupLabelTop,
                    bottom = 8.dp,
                ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.groupRadius))
                .background(palette.c2)
                .border(1.dp, palette.c3, RoundedCornerShape(Dimens.groupRadius)),
            content = content,
        )
    }
}

/** 1dp divider between rows, inset to the row text. */
@Composable
fun ListDivider() {
    PanelDivider(insetStart = Dimens.dividerInset)
}

/** Row with a leading tile and a trailing label, used by list screens. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    subtitleMono: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val palette = AgentTheme.palette
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressFx(source, RoundedCornerShape(0.dp), enabled = onClick != null)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = source, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .heightIn(min = Dimens.rowMinHeight)
            .padding(horizontal = Dimens.rowPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.iconGap),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
                style = if (subtitleMono) MonoStyle.copy(fontWeight = FontWeight.Normal) else TextStyle.Default,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = palette.c4,
                    style = if (subtitleMono) MonoStyle else TextStyle.Default,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        color = AgentTheme.palette.c4,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
fun Chip(
    text: String,
    filled: Boolean = false,
    mono: Boolean = false,
) {
    val palette = AgentTheme.palette
    val bg = if (filled) palette.c4 else palette.c1
    val fg = if (filled) palette.c0 else palette.c4
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(1.dp, palette.c3, RoundedCornerShape(999.dp)))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = fg,
            style = if (mono) MonoStyle.copy(fontSize = 11.sp, lineHeight = 14.sp) else TextStyle.Default,
            maxLines = 1,
        )
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun TrailingChevron(tint: Color? = null) {
    LineIcon(AgentIcons.ChevronRight, size = 16.dp, tint = tint ?: AgentTheme.palette.c4)
}

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val palette = AgentTheme.palette
    val haptics = rememberHaptics()
    val track by animateColorAsState(
        targetValue = if (checked) palette.c4 else palette.c3,
        animationSpec = tween(200),
        label = "track",
    )
    // The knob overshoots slightly, like a physical switch. 350ms, damping 0.55.
    val offset by animateFloatAsState(
        targetValue = if (checked) 21f else 3f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.001f,
        ),
        label = "thumb",
    )
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(track)
            .clickable(enabled = enabled) {
                haptics()
                onCheckedChange(!checked)
            },
    ) {
        Box(
            Modifier
                .offset { IntOffset(x = offset.dp.roundToPx(), y = 3.dp.roundToPx()) }
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) palette.c0 else palette.c4)
        )
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AgentTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.c2)
            .border(1.dp, palette.c3, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) palette.c3 else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = if (active) palette.c5 else palette.c4,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    onClick: () -> Unit = {},
) {
    val palette = AgentTheme.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Dimens.buttonRadius))
            .background(palette.c5)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.c0)
    }
}

@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    onClick: () -> Unit = {},
) {
    val palette = AgentTheme.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Dimens.buttonRadius))
            .border(1.dp, palette.c4, RoundedCornerShape(Dimens.buttonRadius))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
    }
}

/** Filled placeholder field that mirrors the text inputs in the reference screens. */
@Composable
fun InputPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    radius: Dp = Dimens.cardRadius,
    mono: Boolean = false,
    leadingIcon: List<String>? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = AgentTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(palette.c2)
            .border(1.dp, palette.c3, RoundedCornerShape(radius))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leadingIcon != null) {
            LineIcon(leadingIcon, size = 16.dp, tint = palette.c4)
        }
        Text(
            text = text,
            fontSize = 14.sp,
            color = palette.c4,
            style = if (mono) MonoStyle else TextStyle.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CheckSquare(checked: Boolean, onClick: () -> Unit = {}) {
    val palette = AgentTheme.palette
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) palette.c4 else Color.Transparent)
            .border(1.5.dp, palette.c4, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            LineIcon(AgentIcons.Check, size = 14.dp, tint = palette.c0, strokeWidth = 3f)
        }
    }
}

@Composable
fun RadioDot(selected: Boolean) {
    val palette = AgentTheme.palette
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, palette.c4, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(palette.c4)
            )
        }
    }
}

@Composable
fun AccentBar(fraction: Float) {
    val palette = AgentTheme.palette
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(palette.c1)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(palette.c4)
        )
    }
}

/**
 * Bottom sheet frame used by the model picker and the tools panel. It grows and rises
 * into place over 300ms, so the switcher it opened from reads as its origin.
 */
@Composable
fun AgentSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = AgentTheme.palette
    val reduce = com.agent.ai.motion.LocalReduceMotion.current
    val reveal = androidx.compose.runtime.remember {
        androidx.compose.animation.core.Animatable(if (reduce) 1f else 0f)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (reduce) {
            reveal.snapTo(1f)
        } else {
            reveal.animateTo(
                1f,
                androidx.compose.animation.core.spring(
                    dampingRatio = 0.72f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.c0.copy(alpha = 0.72f))
                .clickable(onClick = onDismiss)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .graphicsLayer {
                    alpha = reveal.value.coerceIn(0f, 1f)
                    val s = 0.94f + 0.06f * reveal.value
                    scaleX = s
                    scaleY = s
                    translationY = (1f - reveal.value.coerceAtMost(1f)) * 24.dp.toPx()
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(palette.c0)
                .border(1.dp, palette.c3, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                // A long list (the model picker can hold dozens of entries) scrolls inside
                // the sheet instead of growing past the top of the screen.
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.c3)
            )
            content()
        }
    }
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 12,
    color: Color = AgentTheme.palette.c5,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MonoStyle.copy(fontSize = fontSize.sp, fontWeight = weight),
        color = color,
    )
}

@Composable
fun CenterMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        fontSize = 12.sp,
        color = AgentTheme.palette.c4,
        textAlign = TextAlign.Center,
    )
}

/** Outlined, dashed call-to-action used for "add" and "install" rows. */
@Composable
fun DashedButton(
    title: String,
    subtitle: String? = null,
    icon: List<String>? = null,
    height: Dp = 56.dp,
    onClick: () -> Unit = {},
) {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                drawRoundRect(
                    color = palette.c3,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                        ),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) LineIcon(icon, size = 20.dp, tint = palette.c4)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, fontSize = 12.sp, color = palette.c4, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Small solid badge, e.g. "Default" or "Read only". */
@Composable
fun Badge(text: String) {
    val palette = AgentTheme.palette
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.c4)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 11.sp, color = palette.c0, maxLines = 1)
    }
}

@Composable
fun VGap(height: Dp) {
    Spacer(Modifier.height(height))
}

@Composable
fun HGap(width: Dp) {
    Spacer(Modifier.width(width))
}
