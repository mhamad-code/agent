package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.SectionLabel
import com.agent.ai.ui.components.TrailingChevron
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    onOpenLock: () -> Unit,
) {
    val palette = AgentTheme.palette
    var appLock by remember { mutableStateOf(true) }
    var blockScreenshots by remember { mutableStateOf(false) }

    AgentScreen {
        AppHeader(title = "Security and approvals", subtitle = null, onBack = onBack)

        Panel {
            SettingToggle(
                title = "App lock",
                subtitle = "Biometrics or PIN",
                checked = appLock,
                onChange = {
                    appLock = it
                    if (it) onOpenLock()
                },
            )
            PanelDivider()
            SettingToggle(
                title = "Block screenshots",
                subtitle = null,
                checked = blockScreenshots,
                onChange = { blockScreenshots = it },
            )
        }

        SectionLabel("Always allowed")

        Panel {
            PanelRow {
                Column(Modifier.weight(1f)) {
                    Text("read_file", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                    Text("Reads, run automatically", fontSize = 13.sp, color = palette.c4)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.c1)
                        .border(1.dp, palette.c3, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(AgentIcons.Trash, size = 14.dp, tint = palette.c4)
                }
            }
            PanelDivider()
            PanelRow {
                Column(Modifier.weight(1f)) {
                    Text("git status", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                    Text("Terminal command", fontSize = 13.sp, color = palette.c4)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.c1)
                        .border(1.dp, palette.c3, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(AgentIcons.Trash, size = 14.dp, tint = palette.c4)
                }
            }
        }

        SectionLabel("Always asks (cannot be changed)")

        Panel {
            LockedRow("git push", "Needs your approval every time")
            PanelDivider()
            LockedRow("Delete files or repositories", null)
            PanelDivider()
            LockedRow("Send messages as you", null)
        }

        Box(Modifier.weight(1f))

        Panel {
            PanelRow(
                modifier = Modifier.clickable { },
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Activity log", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                    Text("Every tool the agent ran", fontSize = 13.sp, color = palette.c4)
                }
                TrailingChevron()
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val palette = AgentTheme.palette
    PanelRow {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = palette.c4, modifier = Modifier.padding(top = 2.dp))
            }
        }
        AppSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LockedRow(title: String, subtitle: String?) {
    val palette = AgentTheme.palette
    PanelRow {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = palette.c4, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
