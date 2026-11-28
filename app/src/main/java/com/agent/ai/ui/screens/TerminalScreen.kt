package com.agent.ai.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.ai.R
import com.agent.ai.terminal.AnsiScreen
import com.agent.ai.terminal.LinuxRuntime
import com.agent.ai.terminal.TerminalSession
import com.agent.ai.ui.components.AccentBar
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PrimaryButton
import com.agent.ai.ui.components.SquareButton
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.MonoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A real shell session over [TerminalSession]: scrollback, copy and paste, pinch to change
 * the font size, an extra keys row, and the exact reason when the device cannot run it.
 */
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val state by TerminalSession.state.collectAsStateWithLifecycle()
    val snapshot by TerminalSession.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf(12f) }
    var installing by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf(0f) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!state.running) TerminalSession.inspect(context)
    }

    AgentScreen(gap = 8.dp) {
        AppHeader(
            title = stringResource(R.string.terminal_title),
            subtitle = state.workspace.ifBlank { null },
            onBack = onBack,
        ) {
            SquareButton(
                icon = AgentIcons.Info,
                label = stringResource(R.string.diagnostics_title),
                onClick = onOpenDiagnostics,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state.running) state.backendLabel else stringResource(R.string.terminal_stopped),
                fontSize = 11.sp,
                color = AgentTheme.palette.c4,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.running) {
                SmallAction(stringResource(R.string.terminal_stop)) {
                    TerminalSession.stop(context)
                }
            } else {
                SmallAction(stringResource(R.string.terminal_start)) {
                    scope.launch { TerminalSession.start(context) }
                }
            }
        }

        val support = state.support
        if (!state.running && support != null && !support.supported) {
            Panel(padding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        support.summary,
                        fontSize = 13.sp,
                        color = AgentTheme.palette.c5,
                    )
                    if (LinuxRuntime.arch() != null) {
                        if (installing) {
                            AccentBar(installProgress)
                            Text(
                                stringResource(R.string.terminal_installing),
                                fontSize = 11.sp,
                                color = AgentTheme.palette.c4,
                            )
                        } else {
                            PrimaryButton(
                                text = stringResource(R.string.terminal_install),
                                onClick = {
                                    installing = true
                                    scope.launch {
                                        val result = LinuxRuntime.installRootfs(context) { done, total ->
                                            if (total > 0) installProgress = done.toFloat() / total
                                        }
                                        installing = false
                                        result.onFailure { notice = it.message }
                                        if (result.isSuccess) TerminalSession.inspect(context)
                                    }
                                },
                            )
                        }
                    }
                    OutlineButton(
                        text = stringResource(R.string.terminal_use_device_shell),
                        onClick = { scope.launch { TerminalSession.start(context, forceDeviceShell = true) } },
                    )
                }
            }
        }

        state.lastError?.let { error ->
            Panel(padding = PaddingValues(12.dp)) {
                Text(error, fontSize = 11.sp, color = AgentTheme.palette.c5)
            }
        }
        notice?.let { text ->
            Panel(padding = PaddingValues(12.dp)) {
                Text(text, fontSize = 11.sp, color = AgentTheme.palette.c5)
            }
        }

        // The screen itself: monospace, themed colours, one selectable text.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(AgentTheme.palette.c0)
                .border(1.dp, AgentTheme.palette.c3, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        fontSize = (fontSize * zoom).coerceIn(8f, 24f)
                    }
                },
        ) {
            val scroll = rememberScrollState()
            LaunchedEffect(snapshot.lines.size, snapshot.cursorColumn) {
                scroll.scrollTo(scroll.maxValue)
            }
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(10.dp),
                ) {
                    val lines = snapshot.lines.takeLast(500)
                    lines.forEach { runs ->
                        Text(
                            text = buildAnnotatedString {
                                runs.forEach { run ->
                                    val fg = run.fg?.let { androidx.compose.ui.graphics.Color(AnsiScreen.palette[it]) }
                                        ?: AgentTheme.palette.c5
                                    val bg = run.bg?.let { androidx.compose.ui.graphics.Color(AnsiScreen.palette[it]) }
                                        ?: androidx.compose.ui.graphics.Color.Transparent
                                    withStyle(
                                        SpanStyle(
                                            color = fg,
                                            background = bg,
                                            fontWeight = if (run.bold) FontWeight.Medium else FontWeight.Normal,
                                        )
                                    ) { append(run.text) }
                                }
                            },
                            style = MonoStyle.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.45f).sp),
                        )
                    }
                }
            }
        }

        // Extra keys, sent straight to the process.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerminalSession.keys.forEach { (label, sequence) ->
                SmallAction(label) {
                    if (label == "|" || label == "~" || label == "/" || label == "-") {
                        command += sequence
                    } else {
                        TerminalSession.write(sequence)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = command,
                onValueChange = { command = it },
                textStyle = MonoStyle.copy(fontSize = 13.sp, color = AgentTheme.palette.c5),
                cursorBrush = SolidColor(AgentTheme.palette.c5),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AgentTheme.palette.c1)
                    .border(1.dp, AgentTheme.palette.c3, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (command.isEmpty()) {
                            Text(
                                stringResource(R.string.terminal_input_hint),
                                fontSize = 13.sp,
                                color = AgentTheme.palette.c4,
                                style = MonoStyle,
                            )
                        }
                        inner()
                    }
                },
            )
            SquareButton(
                icon = AgentIcons.ArrowUp,
                label = stringResource(R.string.composer_send),
                onClick = {
                    TerminalSession.write(command + "\n")
                    command = ""
                },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(stringResource(R.string.terminal_copy)) {
                val text = snapshot.lines.joinToString("\n") { line ->
                    line.joinToString("") { it.text }
                }
                copyToClipboard(context, text)
                notice = context.getString(R.string.terminal_copied)
            }
            SmallAction(stringResource(R.string.terminal_paste)) {
                val clip = context.getSystemService(ClipboardManager::class.java)
                    ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!clip.isNullOrEmpty()) {
                    command += clip
                    notice = null
                }
            }
            SmallAction(stringResource(R.string.terminal_clear)) { TerminalSession.clear() }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.sp, color = palette.c5, maxLines = 1)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(ClipData.newPlainText("terminal", text))
}
