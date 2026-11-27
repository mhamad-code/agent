package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.CenterMessage
import com.agent.ai.ui.components.Chip
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.SectionLabel
import com.agent.ai.ui.components.StatusDot
import com.agent.ai.ui.theme.AgentTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette

    AgentScreen {
        AppHeader(
            title = "Accounts",
            subtitle = "Let the agent act on your behalf",
            onBack = onBack,
        )

        Panel(padding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatusDot(palette.c5)
                    Column(Modifier.weight(1f)) {
                        Text("GitHub", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                        Text("Connected with device sign-in", fontSize = 13.sp, color = palette.c4)
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Read repositories", "Issues: write", "Push: asks first").forEach { Chip(it) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlineButton("Permissions", modifier = Modifier.weight(1f), height = 40.dp)
                    OutlineButton("Sign out", modifier = Modifier.weight(1f), height = 40.dp)
                }
            }
        }

        SectionLabel("Add an account")

        Panel {
            AccountRow("GitLab", "G")
            PanelDivider()
            AccountRow("Google Drive", "G")
            PanelDivider()
            AccountRow("Notion", "N")
            PanelDivider()
            AccountRow("Slack", "S")
        }

        Box(Modifier.weight(1f))

        CenterMessage("Sign in with OAuth, a device code or a personal token. Tokens stay encrypted on this device.")
    }
}

@Composable
private fun AccountRow(name: String, letter: String) {
    val palette = AgentTheme.palette
    PanelRow {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(palette.c1),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.c4)
        }
        Text(
            name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.c5,
            modifier = Modifier.weight(1f),
        )
        Chip("Connect")
    }
}
