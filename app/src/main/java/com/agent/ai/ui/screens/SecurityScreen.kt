package com.agent.ai.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agent.ai.R
import com.agent.ai.agent.AgentToolNames
import com.agent.ai.data.chat.ChatDatabase
import com.agent.ai.data.chat.ChatPreferences
import com.agent.ai.data.chat.ToolActivityEntity
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.SectionLabel
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.MonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SecurityViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ChatDatabase.get(application).chatDao()
    private val preferences = ChatPreferences(application)

    val activity = dao.observeActivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val alwaysAllow = preferences.alwaysAllowTools
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun removeRule(tool: String) {
        viewModelScope.launch { preferences.setAlwaysAllowTool(tool, false) }
    }

    fun clearLog() {
        viewModelScope.launch { dao.clearActivity() }
    }
}

/** The always-allow rules the user can remove, the fixed always-ask list, and the log. */
@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    onOpenLock: () -> Unit,
    viewModel: SecurityViewModel = viewModel(),
) {
    val palette = AgentTheme.palette
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val alwaysAllow by viewModel.alwaysAllow.collectAsStateWithLifecycle()
    var appLock by remember { mutableStateOf(true) }
    var blockScreenshots by remember { mutableStateOf(false) }

    AgentScreen(scrollable = true) {
        AppHeader(title = "Security and approvals", subtitle = null, onBack = onBack)

        Panel {
            SettingToggle(
                title = "App lock",
                subtitle = "Biometrics or PIN",
                checked = appLock,
                onChange = {
                    appLock = it
                    if (it) onOpenLock()
                },
            )
            PanelDivider()
            SettingToggle(
                title = "Block screenshots",
                subtitle = null,
                checked = blockScreenshots,
                onChange = { blockScreenshots = it },
            )
        }

        SectionLabel(stringResource(R.string.security_always_allowed))

        Panel {
            // Reading and searching always run without a tap; they are not a user rule.
            AgentToolNames.automatic.forEach { tool ->
                RuleRow(title = tool, subtitle = "Runs automatically inside the project", onRemove = null)
                PanelDivider()
            }
            alwaysAllow.forEach { tool ->
                RuleRow(
                    title = tool,
                    subtitle = "Runs without asking",
                    onRemove = { viewModel.removeRule(tool) },
                )
                PanelDivider()
            }
            RuleRow(
                title = stringResource(R.string.security_always_ask),
                subtitle = "git push, deletes and commands that write outside the project",
                onRemove = null,
            )
        }

        SectionLabel(stringResource(R.string.security_activity_log))

        Panel {
            if (activity.isEmpty()) {
                PanelRow {
                    Text(
                        stringResource(R.string.security_activity_empty),
                        fontSize = 13.sp,
                        color = palette.c4,
                    )
                }
            } else {
                activity.take(30).forEachIndexed { index, entry ->
                    if (index > 0) PanelDivider()
                    ActivityRow(entry)
                }
            }
        }

        if (activity.isNotEmpty()) {
            OutlineButton(
                text = stringResource(R.string.security_clear_log),
                onClick = viewModel::clearLog,
            )
        }

        Box(Modifier.heightIn(min = 24.dp))
    }
}

@Composable
private fun RuleRow(title: String, subtitle: String, onRemove: (() -> Unit)?) {
    val palette = AgentTheme.palette
    PanelRow {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
            Text(subtitle, fontSize = 12.sp, color = palette.c4, modifier = Modifier.padding(top = 2.dp))
        }
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.security_remove_rule),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c4,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ToolActivityEntity) {
    val palette = AgentTheme.palette
    PanelRow(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    entry.tool,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                )
                Text(
                    entry.approvedBy,
                    fontSize = 11.sp,
                    color = palette.c4,
                )
                Text(
                    timeOf(entry.at),
                    fontSize = 11.sp,
                    color = palette.c4,
                )
            }
            Text(
                text = entry.input.lineSequence().firstOrNull().orEmpty().take(120),
                style = MonoStyle.copy(fontSize = 11.sp),
                color = palette.c4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.result.isNotBlank()) {
                Text(
                    text = entry.result.lineSequence().firstOrNull().orEmpty().take(120),
                    fontSize = 11.sp,
                    color = palette.c4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val palette = AgentTheme.palette
    PanelRow {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = palette.c4, modifier = Modifier.padding(top = 2.dp))
            }
        }
        AppSwitch(checked = checked, onCheckedChange = onChange)
    }
}

private fun timeOf(at: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(at))
