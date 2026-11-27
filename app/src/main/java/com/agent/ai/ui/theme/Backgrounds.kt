package com.agent.ai.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.agent.ai.R

/**
 * A wallpaper: the theme gradient, one of the shipped photos, or an image the user
 * added from the gallery. Every wallpaper carries the palette sampled from the image,
 * so text, cards, borders and icons stay readable on top of it.
 */
@Immutable
data class AgentBackground(
    val id: String,
    val name: String,
    val description: String,
    @DrawableRes val imageRes: Int? = null,
    val imagePath: String? = null,
    val palette: AgentPalette? = null,
    val scrimOverride: Float? = null,
) {
    val isDefault: Boolean get() = imageRes == null && imagePath == null

    /**
     * An adaptive scrim between 35 and 66 percent: the brightest colour in the image is
     * used as the worst case, and the scrim grows only as far as the 4.5 to 1 contrast
     * with the palette's text colour requires.
     */
    val scrimAlpha: Float
        get() {
            scrimOverride?.let { return it }
            val p = palette ?: return 0f
            if (isDefault) return 0f
            val worst = (p.swatches.ifEmpty { listOf(p.c3, p.c4) }).maxBy { relativeLuminance(it) }
            return requiredScrim(background = p.c0, text = p.c5, worstCase = worst)
        }
}

object Backgrounds {

    /** The gradient that ships with the reference design. */
    val Gradient = AgentBackground(
        id = "gradient",
        name = "Theme gradient",
        description = "Follows the active theme",
    )

    val VioletDusk = AgentBackground(
        id = "bg_1",
        name = "Violet dusk",
        description = "Indigo ridges under a violet sky",
        imageRes = R.drawable.bg_1,
        palette = AgentPalette(
            id = "bg_1",
            name = "Violet dusk",
            c0 = Color(0xFF10142E),
            c1 = Color(0xFF1B2145),
            c2 = Color(0xFF262C58),
            c3 = Color(0xFF46507F),
            c4 = Color(0xFFA38FC8),
            c5 = Color(0xFFEEEAF9),
            accent = Color(0xFFA38FC8),
            isLight = false,
            swatches = listOf(
                Color(0xFF10142E), Color(0xFF262C58), Color(0xFF46507F),
                Color(0xFF7B88C4), Color(0xFFA38FC8), Color(0xFFEEEAF9),
            ),
            catalog = "bg_1",
        ),
    )

    val Tidal = AgentBackground(
        id = "bg_2",
        name = "Tidal",
        description = "Cold water against a dark shoreline",
        imageRes = R.drawable.bg_2,
        palette = AgentPalette(
            id = "bg_2",
            name = "Tidal",
            c0 = Color(0xFF0A1417),
            c1 = Color(0xFF12222A),
            c2 = Color(0xFF1B3440),
            c3 = Color(0xFF2C5464),
            c4 = Color(0xFF8FB3C6),
            c5 = Color(0xFFE6F1F5),
            accent = Color(0xFF8FB3C6),
            isLight = false,
            swatches = listOf(
                Color(0xFF0A1417), Color(0xFF193D4D), Color(0xFF2D4231),
                Color(0xFF4E7086), Color(0xFF7DA8CA), Color(0xFFE6F1F5),
            ),
            catalog = "bg_2",
        ),
    )

    val Afterglow = AgentBackground(
        id = "bg_3",
        name = "Afterglow",
        description = "Warm dusk light over deep navy",
        imageRes = R.drawable.bg_3,
        palette = AgentPalette(
            id = "bg_3",
            name = "Afterglow",
            c0 = Color(0xFF12112A),
            c1 = Color(0xFF1E1B3A),
            c2 = Color(0xFF2B2748),
            c3 = Color(0xFF5A4358),
            c4 = Color(0xFFE2A183),
            c5 = Color(0xFFFBEEE3),
            accent = Color(0xFFF9B361),
            isLight = false,
            swatches = listOf(
                Color(0xFF12112A), Color(0xFF333752), Color(0xFF56435A),
                Color(0xFF8C5961), Color(0xFFDA8566), Color(0xFFF9B361),
            ),
            catalog = "bg_3",
        ),
    )

    val Skyline = AgentBackground(
        id = "bg_4",
        name = "Skyline",
        description = "Bright sky softened by a deep scrim",
        imageRes = R.drawable.bg_4,
        palette = AgentPalette(
            id = "bg_4",
            name = "Skyline",
            c0 = Color(0xFF06202F),
            c1 = Color(0xFF0C3044),
            c2 = Color(0xFF14425A),
            c3 = Color(0xFF22617C),
            c4 = Color(0xFFA9C6D0),
            c5 = Color(0xFFEDF7FC),
            accent = Color(0xFFA9C6D0),
            isLight = false,
            swatches = listOf(
                Color(0xFF06202F), Color(0xFF094367), Color(0xFF16252A),
                Color(0xFF3D5C6D), Color(0xFFA9C6D0), Color(0xFFB1DCF6),
            ),
            catalog = "bg_4",
        ),
    )

    val Brass = AgentBackground(
        id = "bg_5",
        name = "Brass",
        description = "Teal haze with a warm brass horizon",
        imageRes = R.drawable.bg_5,
        palette = AgentPalette(
            id = "bg_5",
            name = "Brass",
            c0 = Color(0xFF08191B),
            c1 = Color(0xFF10292B),
            c2 = Color(0xFF1A3A3A),
            c3 = Color(0xFF2F5B57),
            c4 = Color(0xFFC9B48D),
            c5 = Color(0xFFF6EAD4),
            accent = Color(0xFFC9B48D),
            isLight = false,
            swatches = listOf(
                Color(0xFF08191B), Color(0xFF0E2326), Color(0xFF20322C),
                Color(0xFF664735), Color(0xFF82878E), Color(0xFFF5DEB7),
            ),
            catalog = "bg_5",
        ),
    )

    /** Every wallpaper that ships with the app. */
    val all: List<AgentBackground> = listOf(Gradient, VioletDusk, Tidal, Afterglow, Skyline, Brass)

    val default: AgentBackground = Gradient

    fun byId(id: String?, extra: List<AgentBackground> = emptyList()): AgentBackground =
        (all + extra).firstOrNull { it.id == id } ?: default
}
