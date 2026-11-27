package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.BuildConfig
import com.agent.ai.R
import com.agent.ai.ui.Routes
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.ListDivider
import com.agent.ai.ui.components.ListGroup
import com.agent.ai.ui.components.SettingRow
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.Dimens

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
) {
    val palette = AgentTheme.palette
    val themeDots = listOf(palette.c0, palette.c3, palette.c4, palette.c5)

    AgentScreen(scrollable = true, padding = Dimens.screenPaddingWide, gap = Dimens.listTop) {
        AppHeader(
            title = "Settings",
            subtitle = "Everything stays on this device",
            onBack = onBack,
        )

        ListGroup(title = stringResource(R.string.chat_settings)) {
            SettingRow(
                icon = AgentIcons.Message,
                title = stringResource(R.string.chat_settings),
                subtitle = stringResource(R.string.chat_settings_subtitle),
                index = 0,
                onClick = { onNavigate(Routes.ChatSettings) },
            )
        }

        ListGroup(title = "Features") {
            SettingRow(
                icon = AgentIcons.Sliders,
                title = "Providers",
                subtitle = "Kilo Gateway (free) · 1 more",
                index = 0,
                onClick = { onNavigate(Routes.Providers) },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Bolt,
                title = "Skills",
                subtitle = "4 installed",
                index = 1,
                onClick = { onNavigate(Routes.Skills) },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Tool,
                title = "MCP Servers",
                subtitle = "2 connected",
                index = 2,
                onClick = { onNavigate(Routes.Mcp) },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Grid,
                title = "Plugins",
                subtitle = "1 installed",
                index = 3,
                onClick = { onNavigate(Routes.Plugins) },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Users,
                title = "Accounts",
                subtitle = "GitHub connected",
                index = 4,
                onClick = { onNavigate(Routes.Accounts) },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Shield,
                title = "Security and approvals",
                subtitle = "Always-allow rules, activity log",
                index = 5,
                onClick = { onNavigate(Routes.Security) },
            )
        }

        ListGroup(title = "Appearance") {
            SettingRow(
                icon = AgentIcons.Globe,
                title = "Theme and wallpaper",
                subtitle = "${palette.name} · ${AgentTheme.background.name}",
                index = 6,
                onClick = { onNavigate(Routes.Appearance) },
                trailing = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        themeDots.forEach { dot ->
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(dot)
                                    .border(1.dp, palette.c3, CircleShape)
                            )
                        }
                    }
                },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Play,
                title = "Reduce motion",
                subtitle = "No animations, no transitions",
                index = 7,
                trailing = {
                    AppSwitch(checked = reduceMotion, onCheckedChange = onReduceMotion)
                },
            )
        }

        ListGroup(title = "Device") {
            SettingRow(
                icon = AgentIcons.Terminal,
                title = "Workspace and terminal",
                subtitle = "Linux environment, storage",
                index = 8,
                onClick = { onNavigate(Routes.Workspace) },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.FileText,
                title = "Memory",
                subtitle = "On, stored on this device",
                index = 9,
                onClick = { onNavigate(Routes.Memory) },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Download,
                title = "Backup",
                subtitle = "Encrypted local file",
                index = 10,
                onClick = { onNavigate(Routes.Backup) },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Info,
                title = "About",
                subtitle = "Version, licenses",
                index = 11,
                onClick = { onNavigate(Routes.About) },
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_version_footer, BuildConfig.VERSION_NAME),
            fontSize = 12.sp,
            color = palette.c4,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
        )
        Spacer(
            Modifier.height(
                Dimens.listBottom + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        )
    }
}
