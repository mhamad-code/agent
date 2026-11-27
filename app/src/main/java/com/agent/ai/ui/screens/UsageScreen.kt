package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.Chip
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun UsageScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette
    var spendingLimit by remember { mutableStateOf(false) }
    val bars = listOf(0.30f, 0.55f, 0.40f, 0.80f, 0.65f, 0.90f, 0.45f)
    val days = listOf("M", "T", "W", "T", "F", "S", "S")

    AgentScreen {
        AppHeader(title = "Usage and cost", subtitle = "This month", onBack = onBack)

        Panel(padding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tokens", fontSize = 12.sp, color = palette.c4)
                Text("1.2M", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    bars.forEach { value ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(value)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(palette.c4)
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { day ->
                        Text(
                            day,
                            fontSize = 11.sp,
                            color = palette.c4,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Panel {
            PanelRow {
                Column(Modifier.weight(1f)) {
                    Text("Kilo Gateway", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                    Text("kilo-auto/free", fontSize = 12.sp, color = palette.c4)
                }
                Chip("Free")
            }
            PanelDivider()
            PanelRow {
                Column(Modifier.weight(1f)) {
                    Text("On-device", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                    Text("No cost", fontSize = 12.sp, color = palette.c4)
                }
                Chip("Free")
            }
        }

        Panel {
            PanelRow {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Monthly spending limit",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                    )
                    Text(
                        "Alert me before the limit",
                        fontSize = 12.sp,
                        color = palette.c4,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                AppSwitch(checked = spendingLimit, onCheckedChange = { spendingLimit = it })
            }
        }
    }
}
