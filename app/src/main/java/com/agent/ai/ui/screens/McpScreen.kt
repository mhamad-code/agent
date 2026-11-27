package com.agent.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.CenterMessage
import com.agent.ai.ui.components.Chip
import com.agent.ai.ui.components.DashedButton
import com.agent.ai.ui.components.MonoText
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.SmallButton
import com.agent.ai.ui.components.StatusDot
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun McpScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette

    AgentScreen {
        AppHeader(
            title = "MCP Servers",
            subtitle = "2 servers",
            onBack = onBack,
            trailing = { SmallButton("Add") },
        )

        Panel(padding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    StatusDot(palette.c5)
                    Text(
                        "github",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                        modifier = Modifier.weight(1f),
                    )
                    Chip("Remote")
                }
                Text(
                    "Connected · reads run automatically, writes ask first",
                    fontSize = 12.sp,
                    color = palette.c4,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                ToolPermission("list_issues", "Auto")
                ToolPermission("create_issue", "Ask")
                ToolPermission("delete_repository", "Ask")
            }
        }

        Panel(padding = PaddingValues(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusDot(palette.c3)
                Column(Modifier.weight(1f)) {
                    Text("filesystem-local", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                    Text("Stopped · runs in the terminal", fontSize = 13.sp, color = palette.c4)
                }
                Chip("Local")
            }
        }

        DashedButton(
            title = "Import a config file (JSON)",
            icon = AgentIcons.Link,
            height = 56.dp,
        )

        Box(Modifier.weight(1f))

        CenterMessage("Content from servers is treated as data, never as instructions.")
    }
}

@Composable
private fun ToolPermission(name: String, permission: String) {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            MonoText(name)
        }
        if (permission == "Auto") Chip("Auto", filled = true) else Chip("Ask")
    }
}
