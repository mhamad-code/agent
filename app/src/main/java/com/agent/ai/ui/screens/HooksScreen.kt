package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.CenterMessage
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.SmallButton
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.MonoStyle

@Composable
fun HooksScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette
    var editHook by remember { mutableStateOf(true) }
    var pushHook by remember { mutableStateOf(true) }
    var startHook by remember { mutableStateOf(false) }

    AgentScreen {
        AppHeader(
            title = "Hooks",
            subtitle = "Scripts that run around the agent",
            onBack = onBack,
            trailing = { SmallButton("Add") },
        )

        HookCard(
            title = "After editing a file",
            script = "ktlint --format",
            note = "Runs in the terminal after every file edit.",
            checked = editHook,
            onChange = { editHook = it },
        )
        HookCard(
            title = "Before git push",
            script = "./check.sh",
            note = "Blocks the push if the script fails.",
            checked = pushHook,
            onChange = { pushHook = it },
        )
        HookCard(
            title = "Session start",
            script = "./setup.sh",
            note = null,
            checked = startHook,
            onChange = { startHook = it },
        )

        Box(Modifier.weight(1f))

        CenterMessage("Each hook runs code, so you confirm it when you add it.")
    }
}

@Composable
private fun HookCard(
    title: String,
    script: String,
    note: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val palette = AgentTheme.palette
    Panel(padding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(checked = checked, onCheckedChange = onChange)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.c0)
                    .border(1.dp, palette.c3, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(script, style = MonoStyle, color = palette.c5)
            }
            if (note != null) {
                Text(note, fontSize = 12.sp, color = palette.c4)
            }
        }
    }
}
