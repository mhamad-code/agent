package com.agent.ai.ui.screens

import androidx.compose.foundation.layout.Column
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
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.InputPlaceholder
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.RadioDot
import com.agent.ai.ui.components.SectionLabel
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun SearchScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette
    var agentSearch by remember { mutableStateOf(true) }
    var readPages by remember { mutableStateOf(true) }
    var provider by remember { mutableStateOf("searxng") }

    AgentScreen {
        AppHeader(
            title = "Web search",
            subtitle = "What the agent uses to look things up",
            onBack = onBack,
        )

        Panel {
            PanelRow {
                Text(
                    "Let the agent search the web",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(checked = agentSearch, onCheckedChange = { agentSearch = it })
            }
            PanelDivider()
            PanelRow {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Read pages as text",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                    )
                    Text(
                        "Fetch a page and strip it down",
                        fontSize = 12.sp,
                        color = palette.c4,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                AppSwitch(checked = readPages, onCheckedChange = { readPages = it })
            }
        }

        SectionLabel("Provider")

        Panel {
            ProviderRow("SearXNG", "Your own server", provider == "searxng") { provider = "searxng" }
            PanelDivider()
            ProviderRow("Brave Search", "API key", provider == "brave") { provider = "brave" }
            PanelDivider()
            ProviderRow("Tavily", "API key", provider == "tavily") { provider = "tavily" }
        }

        InputPlaceholder("Server address", height = 52.dp)
    }
}

@Composable
private fun ProviderRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    PanelRow {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
            Text(subtitle, fontSize = 12.sp, color = palette.c4, modifier = Modifier.padding(top = 2.dp))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RadioDot(selected = selected)
        }
    }
}
