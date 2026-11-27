package com.agent.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import com.agent.ai.ui.components.Chip
import com.agent.ai.ui.components.IconTile
import com.agent.ai.ui.components.InputPlaceholder
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.MonoText
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.SquareButton
import com.agent.ai.ui.theme.AgentTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette

    AgentScreen {
        AppHeader(
            title = "Projects",
            subtitle = "Stored in the workspace",
            onBack = onBack,
            trailing = { SquareButton(icon = AgentIcons.Plus, label = "New project") },
        )

        InputPlaceholder("Search projects", height = 48.dp, radius = 24.dp)

        ProjectRow("flask-app", "~/projects/flask-app", "Edited 5 min ago", "main")
        ProjectRow("portfolio-site", "~/projects/portfolio-site", "Edited yesterday", "main")
        ProjectRow("notes-api", "~/projects/notes-api", "Edited 3 days ago", "dev")

        Box(Modifier.weight(1f))

        Panel(padding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Start from a template", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Flask", "React", "Android", "Static site", "Your own").forEach { Chip(it) }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(title: String, path: String, edited: String, branch: String) {
    val palette = AgentTheme.palette
    Panel(padding = PaddingValues(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(size = 40.dp, radius = 12.dp, background = palette.c1, tint = palette.c4) {
                LineIcon(AgentIcons.Folder, size = 20.dp, tint = palette.c4)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                MonoText(path, color = palette.c4)
                Text(
                    edited,
                    fontSize = 12.sp,
                    color = palette.c4,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Chip(branch)
        }
    }
}
