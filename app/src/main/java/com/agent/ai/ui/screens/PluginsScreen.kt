package com.agent.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.CenterMessage
import com.agent.ai.ui.components.Chip
import com.agent.ai.ui.components.DashedButton
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PrimaryButton
import com.agent.ai.ui.components.SmallButton
import com.agent.ai.ui.theme.AgentTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PluginsScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette
    var enabled by remember { mutableStateOf(true) }

    AgentScreen {
        AppHeader(
            title = "Plugins",
            subtitle = "1 installed",
            onBack = onBack,
            trailing = { SmallButton("Install") },
        )

        Panel(padding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "web-dev-kit",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.c5,
                        )
                        Text("Version 1.2.0 · from a link", fontSize = 13.sp, color = palette.c4)
                    }
                    AppSwitch(checked = enabled, onCheckedChange = { enabled = it })
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("2 skills", "1 MCP server", "3 commands", "Runs scripts").forEach { Chip(it) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrimaryButton("Update available", modifier = Modifier.weight(1f), height = 40.dp)
                    OutlineButton("Remove", modifier = Modifier.weight(1f), height = 40.dp)
                }
            }
        }

        DashedButton(title = "Install from a link", icon = AgentIcons.Link, height = 52.dp)
        DashedButton(title = "Install from a zip file", icon = AgentIcons.Plus, height = 52.dp)

        Box(Modifier.weight(1f))

        CenterMessage("A plugin bundles skills, MCP servers and commands. You review what it contains before it installs.")
    }
}
