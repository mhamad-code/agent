package com.agent.ai.chat

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.R
import com.agent.ai.data.chat.ChatSettings
import com.agent.ai.data.chat.MessagePart
import com.agent.ai.data.chat.MessageStatus
import com.agent.ai.markdown.MarkdownText
import com.agent.ai.motion.LocalReduceMotion
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.InterFamily
import kotlinx.coroutines.delay

/**
 * One message in the transcript: the user's bubble, or an assistant reply with its
 * reasoning line and, once finished, the quiet action row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: ChatMessageUi,
    streamingText: String,
    streamingReasoning: String,
    awaitingFirstToken: Boolean,
    reasoningStartedAt: Long,
    settings: ChatSettings,
    onLongPress: () -> Unit,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onMore: () -> Unit,
    onSwitchBranch: (Int) -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenAttachment: (MessagePart) -> Unit = {},
    onApproveTool: (String) -> Unit = {},
    onAlwaysAllowTool: (String) -> Unit = {},
    onRejectTool: (String) -> Unit = {},
    onRevertTool: (String) -> Unit = {},
) {
    val palette = AgentTheme.palette
    val isStreaming = message.streaming
    val text = if (isStreaming) streamingText else message.text
    val reasoning = if (isStreaming) streamingReasoning else message.reasoning
    val haptic = com.agent.ai.motion.rememberHaptics()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (message.isUser) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 310.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.c2)
                        .border(1.dp, palette.c3, RoundedCornerShape(16.dp))
                        .combinedClickable(onClick = {}, onLongClick = onLongPress)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Attachments come above the text, in the same square style as the input.
                    MessageAttachmentStrip(message.parts, onOpenAttachment)
                    if (text.isNotBlank()) {
                        Text(
                            text = text,
                            style = TextStyle(
                                fontFamily = InterFamily,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = palette.c5,
                            ),
                        )
                    }
                    if (message.status == MessageStatus.WAITING) {
                        Text(
                            text = stringResource(R.string.offline_waiting),
                            fontSize = 11.sp,
                            color = palette.c4,
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = onLongPress),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (message.summary) {
                    Text(
                        text = stringResource(R.string.chat_summary_label).uppercase(),
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                        color = palette.c4,
                    )
                }

                if (reasoning.isNotBlank() || (isStreaming && reasoningStartedAt > 0L)) {
                    ReasoningLine(
                        reasoning = reasoning,
                        streaming = isStreaming,
                        startedAt = if (isStreaming) reasoningStartedAt else 0L,
                        seconds = message.reasoningSeconds,
                        defaultExpanded = settings.reasoningExpanded,
                    )
                }

                when {
                    awaitingFirstToken && text.isBlank() && reasoning.isBlank() -> ThinkingRow()
                    text.isNotBlank() -> MarkdownText(text = text, streaming = isStreaming)
                    isStreaming -> ThinkingRow()
                }

                // Each call reads as a card, in the order the model made it.
                message.tools.forEach { call ->
                    ToolCallCard(
                        call = call,
                        onApprove = { onApproveTool(call.id) },
                        onAlwaysAllow = if (
                            com.agent.ai.agent.ApprovalPolicy.isAlwaysAsk(call.name, call.arguments)
                        ) {
                            null
                        } else {
                            { onAlwaysAllowTool(call.id) }
                        },
                        onReject = { onRejectTool(call.id) },
                        onRevert = { onRevertTool(call.id) },
                    )
                }

                if (message.status == MessageStatus.FAILED) {
                    FailedBox(
                        kind = message.errorKind,
                        detail = message.errorDetail,
                        onRetry = onRetry,
                        onOpenModels = onOpenModels,
                    )
                }

                if (message.status == MessageStatus.STOPPED) {
                    Text(
                        text = stringResource(R.string.chat_stopped),
                        fontSize = 11.sp,
                        color = palette.c4,
                    )
                }

                if (!isStreaming && (text.isNotBlank() || message.tools.isNotEmpty())) {
                    ReplyActions(
                        onCopy = {
                            haptic()
                            onCopy(text)
                        },
                        onRegenerate = onRegenerate,
                        onMore = onMore,
                    )
                }

                if (message.truncated && !isStreaming) {
                    PillAction(stringResource(R.string.chat_continue), onContinue)
                }

                if (!isStreaming && message.modelId.isNotBlank()) {
                    Text(
                        text = message.modelId,
                        fontSize = 10.sp,
                        color = palette.c4.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        message.branch?.let { (index, count) ->
            BranchSwitcher(index, count, onSwitchBranch)
        }
    }
}

@Composable
private fun BranchSwitcher(index: Int, count: Int, onSwitch: (Int) -> Unit) {
    val palette = AgentTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    enabled = index > 1,
                    onClickLabel = stringResource(R.string.action_prev_branch),
                ) { onSwitch(-1) },
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(
                AgentIcons.ChevronLeft,
                size = 14.dp,
                tint = if (index > 1) palette.c5 else palette.c4.copy(alpha = 0.4f),
            )
        }
        Text(
            text = "$index/$count",
            fontSize = 11.sp,
            color = palette.c4,
        )
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    enabled = index < count,
                    onClickLabel = stringResource(R.string.action_next_branch),
                ) { onSwitch(1) },
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(
                AgentIcons.ChevronRight,
                size = 14.dp,
                tint = if (index < count) palette.c5 else palette.c4.copy(alpha = 0.4f),
            )
        }
    }
}

/** One line above the reply: a live shimmer while it thinks, then the measured time. */
@Composable
private fun ReasoningLine(
    reasoning: String,
    streaming: Boolean,
    startedAt: Long,
    seconds: Int,
    defaultExpanded: Boolean,
) {
    val palette = AgentTheme.palette
    val reduce = LocalReduceMotion.current
    var expanded by remember { mutableStateOf(defaultExpanded) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(streaming) {
        while (streaming) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val liveSeconds = if (streaming && startedAt > 0L) {
        ((now - startedAt) / 1000L).toInt().coerceAtLeast(0)
    } else {
        seconds
    }

    val alpha = if (reduce || !streaming) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "reasoning")
        val value by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "reasoningAlpha",
        )
        value
    }

    val label = if (streaming) {
        stringResource(R.string.reasoning_thinking) + " " + liveSeconds + " s"
    } else {
        stringResource(R.string.reasoning_thought_for, liveSeconds)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LineIcon(AgentIcons.Brain, size = 14.dp, tint = palette.c4.copy(alpha = alpha))
            Text(
                text = label,
                fontSize = 12.sp,
                color = palette.c4.copy(alpha = alpha),
            )
            LineIcon(
                if (expanded) AgentIcons.ChevronDown else AgentIcons.ChevronRight,
                size = 12.dp,
                tint = palette.c4,
            )
        }
        AnimatedVisibility(visible = expanded && reasoning.isNotBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.c1)
                    .border(1.dp, palette.c3, RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Text(
                    text = reasoning,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = palette.c4,
                )
            }
        }
    }
}

@Composable
private fun ReplyActions(
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onMore: () -> Unit,
) {
    val palette = AgentTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconAction(AgentIcons.Copy, stringResource(R.string.action_copy), onCopy)
        IconAction(AgentIcons.Refresh, stringResource(R.string.action_regenerate), onRegenerate)
        IconAction(AgentIcons.More, stringResource(R.string.action_more), onMore)
    }
}

@Composable
private fun IconAction(
    icon: List<String>,
    label: String,
    onClick: () -> Unit,
) {
    val palette = AgentTheme.palette
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LineIcon(icon, size = 15.dp, tint = palette.c4)
    }
}

@Composable
private fun PillAction(label: String, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineIcon(AgentIcons.Play, size = 13.dp, tint = palette.c5)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.c5)
    }
}

@Composable
private fun FailedBox(
    kind: com.agent.ai.providers.ProviderErrorKind?,
    detail: String?,
    onRetry: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val palette = AgentTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LineIcon(AgentIcons.Shield, size = 15.dp, tint = palette.c4)
            Text(
                text = errorText(kind),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = palette.c5,
                modifier = Modifier.weight(1f),
            )
        }
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail.take(240),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = palette.c4,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (kind == com.agent.ai.providers.ProviderErrorKind.ModelGone) {
                PillAction(stringResource(R.string.error_choose_model), onOpenModels)
            } else {
                PillAction(stringResource(R.string.chat_try_again), onRetry)
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    val palette = AgentTheme.palette
    val reduce = LocalReduceMotion.current
    val alpha = if (reduce) {
        0.8f
    } else {
        val transition = rememberInfiniteTransition(label = "thinking")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
            label = "thinkingAlpha",
        )
        value
    }
    Text(
        text = stringResource(R.string.chat_thinking),
        fontSize = 14.sp,
        color = palette.c4.copy(alpha = alpha),
    )
}

fun humanSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
fun errorText(kind: com.agent.ai.providers.ProviderErrorKind?): String = stringResource(
    when (kind) {
        com.agent.ai.providers.ProviderErrorKind.NoInternet -> R.string.error_no_internet
        com.agent.ai.providers.ProviderErrorKind.Timeout -> R.string.error_timeout
        com.agent.ai.providers.ProviderErrorKind.RateLimited -> R.string.error_rate_limit
        com.agent.ai.providers.ProviderErrorKind.ServerError -> R.string.error_server
        com.agent.ai.providers.ProviderErrorKind.ModelGone -> R.string.error_model_gone
        else -> R.string.error_unknown
    }
)
