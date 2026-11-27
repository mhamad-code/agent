package com.agent.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.CheckSquare
import com.agent.ai.ui.components.InputPlaceholder
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.PrimaryButton
import com.agent.ai.ui.components.SectionLabel
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette
    var settingsIncluded by remember { mutableStateOf(true) }
    var skillsIncluded by remember { mutableStateOf(true) }
    var projectsIncluded by remember { mutableStateOf(true) }
    var memoryIncluded by remember { mutableStateOf(true) }
    var keysIncluded by remember { mutableStateOf(false) }

    AgentScreen {
        AppHeader(title = "Backup", subtitle = "One encrypted file, no cloud", onBack = onBack)

        SectionLabel("Include")

        Panel {
            CheckRow("Settings and themes", settingsIncluded) { settingsIncluded = it }
            PanelDivider()
            CheckRow("Skills and plugins", skillsIncluded) { skillsIncluded = it }
            PanelDivider()
            CheckRow("Projects", projectsIncluded) { projectsIncluded = it }
            PanelDivider()
            CheckRow("Memory", memoryIncluded) { memoryIncluded = it }
            PanelDivider()
            CheckRow("API keys and tokens", keysIncluded) { keysIncluded = it }
        }

        InputPlaceholder("Password for the backup file", height = 52.dp)

        Box(Modifier.weight(1f))

        PrimaryButton("Create backup")
        OutlineButton("Restore from a file")
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = AgentTheme.palette
    PanelRow {
        CheckSquare(checked = checked, onClick = { onChange(!checked) })
        Text(title, fontSize = 14.sp, color = palette.c5, modifier = Modifier.weight(1f))
    }
}
