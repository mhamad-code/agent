package com.agent.ai.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.data.AppearanceSettings
import com.agent.ai.motion.pressFx
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.DashedButton
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.ListDivider
import com.agent.ai.ui.components.ListGroup
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.SegmentedControl
import com.agent.ai.ui.theme.AgentBackground
import com.agent.ai.ui.theme.AgentPalette
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.Backgrounds
import com.agent.ai.ui.theme.Dimens
import com.agent.ai.ui.theme.Palettes

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    settings: AppearanceSettings,
    onSelectTheme: (String) -> Unit,
    onSelectBackground: (String) -> Unit,
    onAddWallpaper: (Uri) -> Unit,
    onMatchIconTheme: (Boolean) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var confirmIconSwitch by remember { mutableStateOf(false) }
    val palette = AgentTheme.palette

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) onAddWallpaper(uri)
    }

    AgentScreen(scrollable = true, gap = 12.dp) {
        AppHeader(
            title = "Appearance",
            subtitle = "The logo follows the theme. Wallpapers stay on this device.",
            onBack = onBack,
        )

        SegmentedControl(
            options = listOf("Themes", "Backgrounds"),
            selectedIndex = tab,
            onSelect = { tab = it },
        )

        if (tab == 0) {
            ThemeGrid(selectedId = settings.themeId, onSelect = onSelectTheme)

            ListGroup(title = "Launcher icon", topPadding = 0.dp) {
                PanelRow {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Match theme",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.c5,
                        )
                        Text(
                            "Uses the ${settings.theme.name} icon",
                            fontSize = 13.sp,
                            color = palette.c4,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    AppSwitch(
                        checked = settings.matchIconTheme,
                        onCheckedChange = { value ->
                            if (value) confirmIconSwitch = true else onMatchIconTheme(false)
                        },
                    )
                }
            }

            Panel {
                PanelRow {
                    Text("Editor and terminal", fontSize = 16.sp, color = palette.c5, modifier = Modifier.weight(1f))
                    Text("Monokai", fontSize = 14.sp, color = palette.c4)
                }
                ListDivider()
                PanelRow {
                    Text("Code font", fontSize = 16.sp, color = palette.c5, modifier = Modifier.weight(1f))
                    Text("JetBrains Mono", fontSize = 14.sp, color = palette.c4)
                }
                ListDivider()
                PanelRow {
                    Text("Import theme (JSON)", fontSize = 16.sp, color = palette.c5, modifier = Modifier.weight(1f))
                    LineIcon(AgentIcons.ChevronRight, size = 16.dp, tint = palette.c4)
                }
            }

            Text(
                text = "${Palettes.all.size} themes available. Selection is saved on this device.",
                fontSize = 12.sp,
                color = palette.c4,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        } else {
            Text(
                text = "Every wallpaper carries its own palette, so text, cards and icons keep their contrast.",
                fontSize = 12.sp,
                color = palette.c4,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )

            settings.backgrounds.forEach { background ->
                BackgroundCard(
                    background = background,
                    theme = settings.theme,
                    selected = background.id == settings.backgroundId,
                    onClick = { onSelectBackground(background.id) },
                )
            }

            DashedButton(
                title = "Add from gallery",
                subtitle = "The palette is sampled from the photo",
                icon = AgentIcons.Plus,
                height = 64.dp,
                onClick = { pickImage.launch("image/*") },
            )
        }
    }

    if (confirmIconSwitch) {
        AlertDialog(
            onDismissRequest = { confirmIconSwitch = false },
            containerColor = palette.c2,
            titleContentColor = palette.c5,
            textContentColor = palette.c4,
            title = { Text("Match the launcher icon?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text("The icon changes to your active theme. Switching may briefly close the app while the launcher refreshes.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmIconSwitch = false
                    onMatchIconTheme(true)
                }) { Text("Turn on", color = palette.c5) }
            },
            dismissButton = {
                TextButton(onClick = { confirmIconSwitch = false }) {
                    Text("Cancel", color = palette.c4)
                }
            },
        )
    }
}

@Composable
private fun ThemeGrid(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Palettes.all.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { theme ->
                    ThemeCard(
                        theme = theme,
                        selected = theme.id == selectedId,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(theme.id) },
                    )
                }
                if (row.size == 1) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: AgentPalette,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = AgentTheme.palette
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .pressFx(source, shape)
            .clip(shape)
            .background(palette.c2)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) palette.c5 else palette.c3,
                shape = shape,
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            theme.swatches.forEach { swatch ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(swatch)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = theme.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                LineIcon(AgentIcons.Check, size = 14.dp, tint = palette.c5)
            }
        }
    }
}

/**
 * A wallpaper card: a wide preview, its name, a one line description and its six
 * palette swatches. The selected card gets a strong outline and a check mark.
 */
@Composable
private fun BackgroundCard(
    background: AgentBackground,
    theme: AgentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = AgentTheme.palette
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    val fileBitmap = remember(background.imagePath) {
        background.imagePath?.let { path ->
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(path, options)?.asImageBitmap()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressFx(source, shape)
            .clip(shape)
            .background(palette.c2)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) palette.c5 else palette.c3,
                shape = shape,
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            when {
                fileBitmap != null -> Image(
                    bitmap = fileBitmap,
                    contentDescription = background.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                )

                background.imageRes != null -> Image(
                    painter = painterResource(background.imageRes),
                    contentDescription = background.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                )

                else -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(theme.c3, theme.c1, theme.c0),
                            )
                        )
                )
            }

            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(palette.c5),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(AgentIcons.Check, size = 13.dp, tint = palette.c0, strokeWidth = 3f)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = background.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = background.description,
                    fontSize = 12.sp,
                    color = palette.c4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val swatches = background.palette?.swatches
                    ?: listOf(theme.c0, theme.c1, theme.c2, theme.c3, theme.c4, theme.c5)
                swatches.take(6).forEach { swatch ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(swatch)
                            .border(1.dp, Color.Black.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}
