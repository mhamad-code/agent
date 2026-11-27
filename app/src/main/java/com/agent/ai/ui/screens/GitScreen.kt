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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.Badge
import com.agent.ai.ui.components.CenterMessage
import com.agent.ai.ui.components.MonoText
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.PrimaryButton
import com.agent.ai.ui.components.SectionLabel
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun GitScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette

    AgentScreen {
        AppHeader(
            title = "Git",
            subtitle = "flask-app",
            onBack = onBack,
            trailing = { Badge("main") },
        )

        SectionLabel("Changes")
        Panel {
            PanelRow {
                MonoText("app.py", modifier = Modifier.weight(1f))
                Text("+3 −2", fontSize = 12.sp, color = palette.c4)
            }
            PanelDivider()
            PanelRow {
                MonoText("templates/index.html", modifier = Modifier.weight(1f))
                Text("+12 −0", fontSize = 12.sp, color = palette.c4)
            }
        }

        Panel(padding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Suggested commit message", fontSize = 12.sp, color = palette.c4)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.c1)
                        .border(1.dp, palette.c3, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        "Add name parameter to the home route",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = palette.c5,
                    )
                }
                PrimaryButton("Commit", height = 44.dp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlineButton("Pull", modifier = Modifier.weight(1f), height = 44.dp)
            OutlineButton("Branch", modifier = Modifier.weight(1f), height = 44.dp)
            OutlineButton("Push", modifier = Modifier.weight(1f), height = 44.dp)
        }

        Box(Modifier.weight(1f))

        CenterMessage("Push always asks for your approval.")
    }
}
