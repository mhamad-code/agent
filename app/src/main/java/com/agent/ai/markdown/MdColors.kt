package com.agent.ai.markdown

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.agent.ai.ui.theme.AgentPalette
import com.agent.ai.ui.theme.contrastRatio
import com.agent.ai.ui.theme.mix
import com.agent.ai.ui.theme.relativeLuminance

/**
 * Every colour used by the renderer, derived from the active palette only. Syntax colours
 * are pushed toward the palette's ink until they reach 4.5:1 on the code background, so a
 * highlighted block stays readable on every built-in theme and on imported wallpapers
 * instead of relying on fixed editor colours.
 */
@Immutable
data class MdColors(
    val text: Color,
    val muted: Color,
    val accent: Color,
    val link: Color,
    val rule: Color,
    val quoteBar: Color,
    val tableHeader: Color,
    val tableBorder: Color,
    val codeBackground: Color,
    val codeHeader: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val type: Color,
    val function: Color,
    val operator: Color,
    val property: Color,
    val added: Color,
    val removed: Color,
    val addedBackground: Color,
    val removedBackground: Color,
)

private const val MinRatio = 4.5f

fun readableOn(color: Color, background: Color, palette: AgentPalette): Color {
    if (contrastRatio(color, background) >= MinRatio) return color
    val target = if (relativeLuminance(background) < 0.5f) palette.c5 else palette.c0
    var current = color
    repeat(24) {
        current = mix(current, target, 0.12f)
        if (contrastRatio(current, background) >= MinRatio) return current
    }
    return target
}

fun mdColors(palette: AgentPalette): MdColors {
    val code = palette.c1
    val addedTint = mix(code, palette.accent, 0.18f)
    val removedTint = mix(code, palette.c5, 0.10f)

    return MdColors(
        text = palette.c5,
        muted = readableOn(palette.c4, palette.c0, palette),
        accent = readableOn(palette.accent, palette.c0, palette),
        link = readableOn(mix(palette.c5, palette.accent, 0.35f), palette.c0, palette),
        rule = palette.c3,
        quoteBar = palette.c4,
        tableHeader = palette.c2,
        tableBorder = palette.c3,
        codeBackground = code,
        codeHeader = palette.c2,
        keyword = readableOn(mix(palette.c5, palette.accent, 0.25f), code, palette),
        string = readableOn(mix(palette.accent, palette.c5, 0.30f), code, palette),
        comment = readableOn(mix(palette.c4, code, 0.15f), code, palette),
        number = readableOn(mix(palette.c5, palette.accent, 0.55f), code, palette),
        type = readableOn(mix(palette.c4, palette.c5, 0.45f), code, palette),
        function = readableOn(palette.c5, code, palette),
        operator = readableOn(palette.c4, code, palette),
        property = readableOn(mix(palette.accent, palette.c5, 0.55f), code, palette),
        added = readableOn(mix(palette.accent, palette.c5, 0.35f), addedTint, palette),
        removed = readableOn(mix(palette.c5, palette.c4, 0.30f), removedTint, palette),
        addedBackground = addedTint,
        removedBackground = removedTint,
    )
}

fun MdColors.colorFor(kind: TokenKind): Color = when (kind) {
    TokenKind.Plain -> text
    TokenKind.Keyword -> keyword
    TokenKind.String -> string
    TokenKind.Comment -> comment
    TokenKind.Number -> number
    TokenKind.Type -> type
    TokenKind.Function -> function
    TokenKind.Operator -> operator
    TokenKind.Property -> property
    TokenKind.Added -> added
    TokenKind.Removed -> removed
}
