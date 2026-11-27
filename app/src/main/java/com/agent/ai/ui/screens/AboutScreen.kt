package com.agent.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.agent.ai.BuildConfig
import com.agent.ai.R
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppLogo
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.TrailingChevron
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenWelcome: () -> Unit,
) {
    val palette = AgentTheme.palette

    AgentScreen {
        AppHeader(title = "About", subtitle = null, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo(size = 88.dp)
            Text(
                "Agent",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                fontSize = 12.sp,
                color = palette.c4,
            )
        }

        Panel {
            AboutRow("Open-source licenses", null) { }
            PanelDivider()
            AboutRow("Privacy", "What leaves your device", onOpenWelcome)
            PanelDivider()
            AboutRow("Free model terms", null) { }
            PanelDivider()
            AboutRow("Check for updates", null) { }
        }
    }
}

@Composable
private fun AboutRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    PanelRow(
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 12.sp, color = palette.c4, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Box(Modifier.padding(start = 8.dp)) {
                TrailingChevron()
            }
        }
    }
}
