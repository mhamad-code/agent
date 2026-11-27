package com.agent.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.Badge
import com.agent.ai.ui.components.CenterMessage
import com.agent.ai.ui.components.Chip
import com.agent.ai.ui.components.DashedButton
import com.agent.ai.ui.components.MonoText
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.StatusDot
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun ProvidersScreen(
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val palette = AgentTheme.palette

    AgentScreen {
        AppHeader(title = "Providers", subtitle = "Where your models run", onBack = onBack)

        Panel(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusDot(palette.c5)
                    Text(
                        "Kilo Gateway",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                        modifier = Modifier.weight(1f),
                    )
                    Badge("Default")
                }
                MonoText("kilo-auto/free", color = palette.c4)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Free")
                    Chip("Streaming")
                    Chip("Tools")
                }
                Text("Cloud provider. Your messages leave this device.", fontSize = 13.sp, color = palette.c4)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("Test connection", modifier = Modifier.weight(1f), height = 40.dp)
                    OutlineButton("Models", modifier = Modifier.weight(1f), height = 40.dp, onClick = onOpenModels)
                }
            }
        }

        DashedButton(
            title = "Add online provider",
            subtitle = "API key stored in the device keystore",
            icon = AgentIcons.Sliders,
            height = 64.dp,
        )
        DashedButton(
            title = "Add on-device model",
            subtitle = "Private, works offline",
            icon = AgentIcons.Smartphone,
            height = 64.dp,
        )

        Box(Modifier.weight(1f))

        CenterMessage("The model list is fetched on launch, because free models change over time.")
    }
}
