package com.agent.ai.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.R
import com.agent.ai.providers.ModelCapabilities
import com.agent.ai.ui.components.AgentSheet
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.Chip
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.theme.AgentTheme

/**
 * Only what works today is offered. Everything else is listed as unavailable, with the
 * reason, and Deep Think follows whether the model reports reasoning support.
 */
@Composable
fun ToolsSheet(
    capabilities: ModelCapabilities,
    deepThink: Boolean,
    onDeepThink: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AgentTheme.palette

    AgentSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.tools_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.c5,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        Panel {
            ToolRow(
                title = stringResource(R.string.tools_deep_think_sub),
                subtitle = if (capabilities.reasoning) {
                    stringResource(R.string.tools_deep_think_sub)
                } else {
                    stringResource(R.string.model_no_reasoning)
                },
                enabled = capabilities.reasoning,
                checked = deepThink && capabilities.reasoning,
                onChange = onDeepThink,
            )
        }

        Panel {
            UnavailableRow(stringResource(R.string.tools_web), stringResource(R.string.tools_web_sub))
            PanelDivider()
            UnavailableRow(stringResource(R.string.tools_browser), "Built-in")
            PanelDivider()
            UnavailableRow(stringResource(R.string.tools_terminal), "proot")
            PanelDivider()
            UnavailableRow(stringResource(R.string.tools_mcp), "12 tools")
            PanelDivider()
            UnavailableRow(stringResource(R.string.tools_skills), "4 enabled")
        }
    }
}

@Composable
private fun ToolRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val palette = AgentTheme.palette
    PanelRow {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) palette.c5 else palette.c4,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = palette.c4,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

@Composable
private fun UnavailableRow(title: String, subtitle: String) {
    val palette = AgentTheme.palette
    PanelRow {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.c4)
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = palette.c4.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Chip(stringResource(R.string.tools_not_available))
    }
}
