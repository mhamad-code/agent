package com.agent.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.CenterMessage
import com.agent.ai.ui.components.Chip
import com.agent.ai.ui.components.DashedButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.SmallButton
import com.agent.ai.ui.theme.AgentTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubagentsScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette

    AgentScreen {
        AppHeader(
            title = "Subagents",
            subtitle = "Separate context, focused tools",
            onBack = onBack,
            trailing = { SmallButton("Add") },
        )

        SubagentCard("researcher", "Low-cost model", listOf("Read", "Search", "Web"))
        SubagentCard("reviewer", "Strong model", listOf("Read", "Git"))
        SubagentCard("tester", "Default model", listOf("Read", "Terminal"))

        DashedButton(title = "Define one in a markdown file", icon = AgentIcons.Plus, height = 52.dp)

        Box(Modifier.weight(1f))

        CenterMessage("A subagent works alone and returns only a short summary to the main agent.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubagentCard(name: String, model: String, tools: List<String>) {
    val palette = AgentTheme.palette
    Panel(padding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                    modifier = Modifier.weight(1f),
                )
                Chip(model)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tools.forEach { Chip(it) }
            }
        }
    }
}
