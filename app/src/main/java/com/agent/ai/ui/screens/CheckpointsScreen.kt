package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.theme.AgentTheme

private data class Checkpoint(val title: String, val meta: String, val last: Boolean)

@Composable
fun CheckpointsScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette
    val checkpoints = listOf(
        Checkpoint("Before: update the route", "2 min ago · 2 files", false),
        Checkpoint("Before: add the template", "12 min ago · 1 file", false),
        Checkpoint("Before: set up project", "40 min ago · 4 files", true),
    )

    AgentScreen {
        AppHeader(title = "Checkpoints", subtitle = "flask-app", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 4.dp),
        ) {
            checkpoints.forEach { checkpoint ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(palette.c4)
                        )
                        if (!checkpoint.last) {
                            Box(
                                Modifier
                                    .padding(top = 4.dp)
                                    .width(2.dp)
                                    .height(96.dp)
                                    .background(palette.c3)
                            )
                        }
                    }
                    Panel(
                        modifier = Modifier.weight(1f),
                        padding = PaddingValues(12.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 0.dp),
                        ) {
                            Column {
                                Text(
                                    checkpoint.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.c5,
                                )
                                Text(checkpoint.meta, fontSize = 12.sp, color = palette.c4)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlineButton("Restore files", modifier = Modifier.weight(1f), height = 36.dp)
                                OutlineButton("Restore chat", modifier = Modifier.weight(1f), height = 36.dp)
                            }
                        }
                    }
                }
                Box(Modifier.height(8.dp))
            }
        }

        Panel(padding = PaddingValues(12.dp)) {
            Text(
                "Using 38 MB of 500 MB. Older checkpoints are cleaned up automatically. They are kept apart from your project history.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = palette.c4,
            )
        }
    }
}
