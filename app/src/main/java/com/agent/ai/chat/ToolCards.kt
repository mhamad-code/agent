package com.agent.ai.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.R
import com.agent.ai.agent.AgentToolNames
import com.agent.ai.agent.AgentTools
import com.agent.ai.data.chat.MessagePart
import com.agent.ai.data.chat.ToolStatus
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.MonoStyle

/**
 * One tool call, the way the conversation should read: the tool, the exact path or
 * command, a status, and on a tap the real input, output and diff.
 */
@Composable
fun ToolCallCard(
    call: MessagePart.ToolCall,
    onApprove: () -> Unit,
    onAlwaysAllow: (() -> Unit)?,
    onReject: () -> Unit,
    onRevert: () -> Unit,
) {
    val palette = AgentTheme.palette
    var expanded by rememberSaveable(call.id) { mutableStateOf(false) }
    val summary = AgentTools.summary(call.name, call.arguments)
    val waiting = call.status == ToolStatus.WAITING
    val running = call.status == ToolStatus.RUNNING

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(palette.c2),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(toolIcon(call.name), size = 14.dp, tint = palette.c4)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = call.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                    maxLines = 1,
                )
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        fontSize = 11.sp,
                        color = palette.c4,
                        style = MonoStyle.copy(fontSize = 11.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = palette.c4,
                )
            } else {
                StatusPill(call.status)
            }
        }

        if (waiting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(stringResource(R.string.tool_approve), primary = true, onClick = onApprove)
                if (onAlwaysAllow != null) {
                    ActionButton(stringResource(R.string.tool_always_allow), onClick = onAlwaysAllow)
                }
                ActionButton(stringResource(R.string.tool_reject), onClick = onReject)
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (call.arguments.isNotBlank()) {
                    CardSection(stringResource(R.string.tool_input), call.arguments)
                }
                if (call.diff.isNotBlank()) {
                    DiffView(call.diff)
                }
                if (call.output.isNotBlank()) {
                    CardSection(stringResource(R.string.tool_output), call.output)
                }
                if (call.backupPath != null && call.diff.isNotBlank()) {
                    ActionButton(stringResource(R.string.tool_revert), onClick = onRevert)
                }
            }
        }
    }
}

@Composable
private fun CardSection(title: String, body: String) {
    val palette = AgentTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title.uppercase(), fontSize = 10.sp, letterSpacing = 1.2.sp, color = palette.c4)
        Text(
            text = body.take(4_000),
            style = MonoStyle.copy(fontSize = 11.sp),
            color = palette.c5,
        )
    }
}

/** Added lines in one colour, removed lines in another, like a diff should read. */
@Composable
private fun DiffView(diff: String) {
    val palette = AgentTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.c0)
            .padding(8.dp),
    ) {
        diff.lines().take(60).forEach { line ->
            Text(
                text = line.ifEmpty { " " },
                style = MonoStyle.copy(fontSize = 11.sp),
                color = when {
                    line.startsWith("+") -> palette.c5
                    line.startsWith("-") -> palette.c4
                    else -> palette.c4
                },
            )
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val palette = AgentTheme.palette
    val label = when (status) {
        ToolStatus.WAITING -> stringResource(R.string.tool_status_waiting)
        ToolStatus.RUNNING -> stringResource(R.string.tool_status_running)
        ToolStatus.DONE -> stringResource(R.string.tool_status_done)
        ToolStatus.FAILED -> stringResource(R.string.tool_status_failed)
        ToolStatus.REJECTED -> stringResource(R.string.tool_status_rejected)
        else -> status
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.c2)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, fontSize = 10.sp, color = palette.c4, maxLines = 1)
    }
}

@Composable
private fun ActionButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) palette.c5 else palette.c2)
            .border(1.dp, if (primary) palette.c5 else palette.c3, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) palette.c0 else palette.c5,
        )
    }
}

private fun toolIcon(name: String): List<String> = when (name) {
    AgentToolNames.ReadFile, AgentToolNames.WriteFile, AgentToolNames.EditFile -> AgentIcons.FileText
    AgentToolNames.ListDir -> AgentIcons.Folder
    AgentToolNames.Search -> AgentIcons.Search
    AgentToolNames.RunCommand -> AgentIcons.Terminal
    else -> AgentIcons.Tool
}
