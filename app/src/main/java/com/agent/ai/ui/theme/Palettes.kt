package com.agent.ai.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * The six colour slots used by every screen in the reference design.
 *
 * c0 background, c1 sunken surface, c2 card, c3 border, c4 muted text/icon, c5 primary text.
 */
@Immutable
data class AgentPalette(
    val id: String,
    val name: String,
    val c0: Color,
    val c1: Color,
    val c2: Color,
    val c3: Color,
    val c4: Color,
    val c5: Color,
    val accent: Color,
    val isLight: Boolean,
    val swatches: List<Color>,
    val catalog: String? = null,
) {
    val background: Color get() = c0
    val sunken: Color get() = c1
    val card: Color get() = c2
    val border: Color get() = c3
    val muted: Color get() = c4
    val ink: Color get() = c5
    val onAccent: Color get() = c0
    val onInk: Color get() = c0
    val glow: Color get() = c5.copy(alpha = 0.35f)
    val highlight: Color get() = c4.copy(alpha = 0.16f)
    val scrim: Color get() = c0
}

object Palettes {

    /** The Forest palette from the reference HTML screens. This is the default. */
    val Forest = AgentPalette(
        id = "forest",
        name = "Forest",
        c0 = Color(0xFF051F20),
        c1 = Color(0xFF0B2B26),
        c2 = Color(0xFF163832),
        c3 = Color(0xFF235347),
        c4 = Color(0xFF8EB69B),
        c5 = Color(0xFFDAF1DE),
        accent = Color(0xFF8EB69B),
        isLight = false,
        swatches = listOf(
            Color(0xFF051F20), Color(0xFF0B2B26), Color(0xFF163832),
            Color(0xFF235347), Color(0xFF8EB69B), Color(0xFFDAF1DE),
        ),
    )

    val Ocean = AgentPalette(
        id = "ocean",
        name = "Ocean",
        c0 = Color(0xFF0B132B),
        c1 = Color(0xFF1C2541),
        c2 = Color(0xFF253A52),
        c3 = Color(0xFF3A506B),
        c4 = Color(0xFF5BC0DE),
        c5 = Color(0xFFF4D6B0),
        accent = Color(0xFFD97706),
        isLight = false,
        swatches = listOf(
            Color(0xFF0B132B), Color(0xFF1C2541), Color(0xFF3A506B),
            Color(0xFF5BC0DE), Color(0xFFF4D6B0), Color(0xFFD97706),
        ),
        catalog = "palette_5",
    )

    val Amoled = AgentPalette(
        id = "amoled",
        name = "AMOLED",
        c0 = Color(0xFF000000),
        c1 = Color(0xFF0D0D0D),
        c2 = Color(0xFF1F1F1F),
        c3 = Color(0xFF2E2E2E),
        c4 = Color(0xFF8EB69B),
        c5 = Color(0xFFDAF1DE),
        accent = Color(0xFF8EB69B),
        isLight = false,
        swatches = listOf(
            Color(0xFF000000), Color(0xFF0D0D0D), Color(0xFF1F1F1F),
            Color(0xFF8EB69B), Color(0xFFDAF1DE),
        ),
    )

    val Dracula = AgentPalette(
        id = "dracula",
        name = "Dracula",
        c0 = Color(0xFF1E1F29),
        c1 = Color(0xFF282A36),
        c2 = Color(0xFF343746),
        c3 = Color(0xFF44475A),
        c4 = Color(0xFFBD93F9),
        c5 = Color(0xFFF8F8F2),
        accent = Color(0xFFFF79C6),
        isLight = false,
        swatches = listOf(
            Color(0xFF282A36), Color(0xFF44475A), Color(0xFFBD93F9),
            Color(0xFFFF79C6), Color(0xFFF8F8F2),
        ),
    )

    val Nord = AgentPalette(
        id = "nord",
        name = "Nord",
        c0 = Color(0xFF2E3440),
        c1 = Color(0xFF3B4252),
        c2 = Color(0xFF434C5E),
        c3 = Color(0xFF4C566A),
        c4 = Color(0xFF88C0D0),
        c5 = Color(0xFFECEFF4),
        accent = Color(0xFF81A1C1),
        isLight = false,
        swatches = listOf(
            Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFF81A1C1),
            Color(0xFF88C0D0), Color(0xFFECEFF4),
        ),
    )

    val Solarized = AgentPalette(
        id = "solarized",
        name = "Solarized",
        c0 = Color(0xFF002B36),
        c1 = Color(0xFF073642),
        c2 = Color(0xFF0B3D4A),
        c3 = Color(0xFF14505F),
        c4 = Color(0xFFB58900),
        c5 = Color(0xFFFDF6E3),
        accent = Color(0xFF268BD2),
        isLight = false,
        swatches = listOf(
            Color(0xFF002B36), Color(0xFF073642), Color(0xFF268BD2),
            Color(0xFFB58900), Color(0xFFFDF6E3),
        ),
    )

    /** Light palette lifted from the soft pink/sage swatch catalogue. */
    val Blossom = AgentPalette(
        id = "blossom",
        name = "Blossom",
        c0 = Color(0xFFEAF2F2),
        c1 = Color(0xFFF1F7F7),
        c2 = Color(0xFFFFFFFF),
        c3 = Color(0xFFD5E5E5),
        c4 = Color(0xFF738585),
        c5 = Color(0xFF24302F),
        accent = Color(0xFFF7CBCA),
        isLight = true,
        swatches = listOf(
            Color(0xFFF7CBCA), Color(0xFFDDD3D3), Color(0xFFF1F7F7),
            Color(0xFFD5E5E5), Color(0xFFBDD7D8), Color(0xFF5D6B6B),
        ),
        catalog = "palette_1",
    )

    /** Light palette lifted from the cyan/indigo/pink swatch catalogue. */
    val Carnival = AgentPalette(
        id = "carnival",
        name = "Carnival",
        c0 = Color(0xFFE3EEFB),
        c1 = Color(0xFFEEF5FE),
        c2 = Color(0xFFFFFFFF),
        c3 = Color(0xFFB9C9E6),
        c4 = Color(0xFF6C6EA1),
        c5 = Color(0xFF232752),
        accent = Color(0xFFFF1053),
        isLight = true,
        swatches = listOf(
            Color(0xFFD5E5FC), Color(0xFF66C7F4), Color(0xFF6C6EA1),
            Color(0xFFDAD2C5), Color(0xFFFF1053), Color(0xFF9EA0D3),
        ),
        catalog = "palette_2",
    )

    /** Light palette lifted from the pastel lavender swatch catalogue. */
    val Lilac = AgentPalette(
        id = "lilac",
        name = "Lilac",
        c0 = Color(0xFFF4F1FC),
        c1 = Color(0xFFFAF8FE),
        c2 = Color(0xFFFFFFFF),
        c3 = Color(0xFFDCD3EE),
        c4 = Color(0xFF6F6AA8),
        c5 = Color(0xFF2C2A52),
        accent = Color(0xFFB8AEE3),
        isLight = true,
        swatches = listOf(
            Color(0xFFFADCFE), Color(0xFFC7F3F6), Color(0xFFFEFDF9),
            Color(0xFFA4C6E9), Color(0xFF838BCC), Color(0xFFB8AEE3),
        ),
        catalog = "palette_3",
    )

    /** Dark palette lifted from the cobalt gradient catalogue. */
    val Cobalt = AgentPalette(
        id = "cobalt",
        name = "Cobalt",
        c0 = Color(0xFF03195B),
        c1 = Color(0xFF0A2382),
        c2 = Color(0xFF12309C),
        c3 = Color(0xFF1951FC),
        c4 = Color(0xFF5E9BFC),
        c5 = Color(0xFFCBE9FD),
        accent = Color(0xFF3781FC),
        isLight = false,
        swatches = listOf(
            Color(0xFF03195B), Color(0xFF0B2CB1), Color(0xFF1951FC),
            Color(0xFF3781FC), Color(0xFF5E9BFC), Color(0xFFCBE9FD),
        ),
        catalog = "palette_4",
    )

    /** Dark palette lifted from the gold gradient catalogue. */
    val Ember = AgentPalette(
        id = "ember",
        name = "Ember",
        c0 = Color(0xFF2B1A08),
        c1 = Color(0xFF583714),
        c2 = Color(0xFF6B4520),
        c3 = Color(0xFF8A5E2C),
        c4 = Color(0xFFDEC67E),
        c5 = Color(0xFFFFEB97),
        accent = Color(0xFFFFEB97),
        isLight = false,
        swatches = listOf(
            Color(0xFFFFEB97), Color(0xFFBFA366), Color(0xFFA58752),
            Color(0xFF836438), Color(0xFF583714),
        ),
        catalog = "palette_6",
    )

    val all: List<AgentPalette> = listOf(
        Forest, Ocean, Amoled, Dracula, Nord, Solarized,
        Blossom, Carnival, Lilac, Cobalt, Ember,
    )

    val default: AgentPalette = Forest

    fun byId(id: String?): AgentPalette = all.firstOrNull { it.id == id } ?: default

    /**
     * Builds the six tokens from colours sampled from a gallery image.
     *
     * The colours are sorted by luminance, the darkest becomes the background, the
     * lightest the primary text, and the middle ones fill the card, border and muted
     * slots. The background is then pushed dark enough and the text lightened until
     * the contrast passes 4.5 to 1.
     */
    fun fromSwatches(id: String, name: String, raw: List<Color>): AgentPalette {
        val sorted = raw.distinct().sortedBy { relativeLuminance(it) }
        if (sorted.size < 2) return default.copy(id = id, name = name, catalog = null)

        fun pick(fraction: Float): Color =
            sorted[((sorted.size - 1) * fraction).roundToInt().coerceIn(0, sorted.size - 1)]

        var c0 = pick(0f)
        if (relativeLuminance(c0) > 0.05f) c0 = mix(c0, Color.Black, 0.55f)
        val c1 = mix(pick(0.20f), c0, 0.55f)
        val c2 = mix(pick(0.42f), c0, 0.28f)
        val c3 = mix(pick(0.62f), c0, 0.08f)
        var c4 = pick(0.82f)
        var c5 = pick(1f)

        var guard = 0
        while (contrastRatio(c5, c0) < 4.5f && guard < 16) {
            c5 = mix(c5, Color.White, 0.14f)
            c4 = mix(c4, c5, 0.18f)
            guard++
        }

        return AgentPalette(
            id = id,
            name = name,
            c0 = c0,
            c1 = c1,
            c2 = c2,
            c3 = c3,
            c4 = c4,
            c5 = c5,
            accent = c4,
            isLight = false,
            swatches = listOf(c0, c1, c2, c3, c4, c5),
            catalog = null,
        )
    }
}
