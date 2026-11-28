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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AccentBar
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.TrailingChevron
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun WorkspaceScreen(
    onBack: () -> Unit,
    onOpenTerminal: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
) {
    val palette = AgentTheme.palette
    var keepRunning by remember { mutableStateOf(true) }

    AgentScreen {
        AppHeader(title = "Workspace and terminal", subtitle = null, onBack = onBack)

        Panel(padding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "Storage",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                        modifier = Modifier.weight(1f),
                    )
                    Text("1.2 GB used", fontSize = 14.sp, color = palette.c4)
                }
                AccentBar(0.28f)
                Text("Projects, packages and checkpoints", fontSize = 12.sp, color = palette.c4)
            }
        }

        Panel {
            ChevronRow("Shell", "Open the terminal", onOpenTerminal)
            PanelDivider()
            ChevronRow("Diagnostics", "Device checks and the last error", onOpenDiagnostics)
        }

        Panel {
            PanelRow {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Keep running in background",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                    )
                    Text(
                        "Shows a notification while the agent works",
                        fontSize = 12.sp,
                        color = palette.c4,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                AppSwitch(checked = keepRunning, onCheckedChange = { keepRunning = it })
            }
        }

        Box(Modifier.weight(1f))

        OutlineButton("Reset the environment")
    }
}

@Composable
private fun ChevronRow(title: String, subtitle: String, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    PanelRow(onClick = onClick) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
            Text(subtitle, fontSize = 12.sp, color = palette.c4, modifier = Modifier.padding(top = 2.dp))
        }
        TrailingChevron()
    }
}
