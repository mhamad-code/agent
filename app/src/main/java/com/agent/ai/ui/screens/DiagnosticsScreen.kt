package com.agent.ai.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.ai.R
import com.agent.ai.terminal.LinuxRuntime
import com.agent.ai.terminal.TerminalSession
import com.agent.ai.terminal.TerminalSupport
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.MonoStyle
import kotlinx.coroutines.launch

/**
 * The report a bug needs: the device, each startup check and its result, and the last
 * error. No account, file name or message text is included.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by TerminalSession.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var support by remember { mutableStateOf<TerminalSupport?>(state.support) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch { support = TerminalSession.inspect(context) }
    }

    val lines = buildList {
        add("App ${com.agent.ai.BuildConfig.VERSION_NAME} (${com.agent.ai.BuildConfig.VERSION_CODE})")
        add("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        add("Device ${Build.MANUFACTURER} ${Build.MODEL}")
        add("CPU ${Build.SUPPORTED_ABIS.joinToString()}")
        add("Alpine name ${LinuxRuntime.arch() ?: "unsupported"}")
        add("Workspace ${LinuxRuntime.workspace(context).absolutePath}")
        add("proot installed ${LinuxRuntime.isProotInstalled(context)}")
        add("Root file system ${LinuxRuntime.sizeOf(LinuxRuntime.rootfs(context)) / (1024 * 1024)} MB")
        add("Shell running ${state.running} (${state.backendLabel})")
        add("Last error ${state.lastError ?: "none"}")
        support?.checks?.forEach { check ->
            add("${if (check.passed) "ok" else "failed"} · ${check.name} · ${check.detail}")
        }
    }

    AgentScreen(scrollable = true) {
        AppHeader(title = stringResource(R.string.diagnostics_title), onBack = onBack)

        Panel(padding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.diagnostics_subtitle),
                    fontSize = 13.sp,
                    color = AgentTheme.palette.c4,
                )
                lines.forEach { line ->
                    Text(line, style = MonoStyle.copy(fontSize = 11.sp), color = AgentTheme.palette.c5)
                }
            }
        }

        support?.let { result ->
            Panel {
                result.checks.forEachIndexed { index, check ->
                    if (index > 0) PanelDivider()
                    PanelRow {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                if (check.passed) "OK" else "—",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AgentTheme.palette.c4,
                                modifier = Modifier.size(width = 22.dp, height = 16.dp),
                            )
                            Column {
                                Text(check.name, fontSize = 13.sp, color = AgentTheme.palette.c5)
                                Text(check.detail, fontSize = 11.sp, color = AgentTheme.palette.c4)
                            }
                        }
                    }
                }
            }
        }

        OutlineButton(
            text = if (copied) {
                stringResource(R.string.diagnostics_copied)
            } else {
                stringResource(R.string.diagnostics_copy)
            },
            onClick = {
                val report = lines.joinToString("\n")
                val manager = context.getSystemService(ClipboardManager::class.java)
                manager?.setPrimaryClip(ClipData.newPlainText("diagnostics", report))
                copied = true
            },
        )
    }
}
