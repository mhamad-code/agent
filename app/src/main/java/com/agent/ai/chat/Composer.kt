package com.agent.ai.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.R
import com.agent.ai.data.chat.SlashCommandEntity
import com.agent.ai.data.chat.SlashCommandEntry
import com.agent.ai.motion.pressFx
import com.agent.ai.motion.rememberHaptics
import com.agent.ai.providers.ModelCapabilities
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.InterFamily

/**
 * The input: a field that grows from one to six lines, attachments, the slash menu, the
 * context ring and the capability aware Deep Think pill.
 */
@Composable
fun Composer(
    state: ChatUiState,
    listening: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttachImage: () -> Unit,
    onAttachFile: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onVoice: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenContext: () -> Unit,
    onExpand: () -> Unit,
    onSlashCommand: (SlashCommandEntry) -> Unit,
    onFillCommand: (SlashCommandEntity) -> Unit,
    onDeepThink: (Boolean) -> Unit,
    onSendNow: () -> Unit,
    onCancelPending: () -> Unit,
) {
    val palette = AgentTheme.palette
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val haptics = rememberHaptics()
    val enterToSend = state.settings.enterToSend
    val showSlash = state.slash.isNotEmpty() && state.draft.startsWith("/")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.c2)
            .border(1.dp, palette.c3, RoundedCornerShape(20.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.pendingMessageId != null) {
            OfflineBanner(onSendNow = onSendNow, onCancel = onCancelPending)
        }

        if (state.error != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = errorText(state.error),
                    fontSize = 12.sp,
                    color = palette.c5,
                    modifier = Modifier.weight(1f),
                )
                if (state.error == com.agent.ai.providers.ProviderErrorKind.ModelGone) {
                    SmallPill(stringResource(R.string.error_choose_model), onOpenModels)
                }
            }
        }

        if (state.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.attachments.forEach { draft ->
                    AttachmentChip(draft, onRemove = { onRemoveAttachment(draft.id) })
                }
            }
        }

        if (showSlash) {
            SlashMenu(
                entries = state.slash.take(6),
                onPick = { entry ->
                    if (entry.custom != null) {
                        onFillCommand(entry.custom)
                    } else {
                        onSlashCommand(entry)
                    }
                },
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 24.dp)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            if (!enterToSend || event.isShiftPressed) {
                                false
                            } else {
                                haptics()
                                onSend()
                                true
                            }
                        } else {
                            false
                        }
                    },
                textStyle = TextStyle(
                    fontFamily = InterFamily,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = palette.c5,
                ),
                cursorBrush = SolidColor(palette.c5),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onSend = {
                    haptics()
                    onSend()
                    focus.clearFocus()
                }),
                decorationBox = { innerTextField ->
                    Box {
                        if (state.draft.isEmpty()) {
                            Text(
                                text = stringResource(R.string.composer_hint),
                                fontSize = 15.sp,
                                color = palette.c4,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = stringResource(R.string.composer_expand), onClick = onExpand),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(AgentIcons.Expand, size = 15.dp, tint = palette.c4)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CircleIcon(
                icon = AgentIcons.Paperclip,
                label = stringResource(R.string.composer_attach),
                onClick = onAttachFile,
            )
            CircleIcon(
                icon = AgentIcons.Image,
                label = stringResource(R.string.attach_image),
                onClick = onAttachImage,
            )
            Pill(stringResource(R.string.composer_tools), onOpenTools)
            Pill(
                if (state.deepThink) {
                    stringResource(R.string.composer_deep_think_on)
                } else {
                    stringResource(R.string.composer_deep_think)
                },
                onClick = { if (state.reasoningEnabled) onDeepThink(!state.deepThink) },
                enabled = state.reasoningEnabled,
                filled = state.deepThink && state.reasoningEnabled,
                contentDescription = if (state.reasoningEnabled) {
                    stringResource(R.string.composer_deep_think)
                } else {
                    stringResource(R.string.model_no_reasoning)
                },
            )
            Spacer(Modifier.weight(1f))
            ContextRing(
                percent = state.contextPercent,
                onClick = onOpenContext,
            )
            CircleIcon(
                AgentIcons.Mic,
                stringResource(R.string.composer_voice),
                onClick = onVoice,
                active = listening,
            )
            if (state.streaming) {
                StopButton { haptics(); onStop() }
            } else {
                SendButton(
                    enabled = state.draft.isNotBlank() || state.attachments.isNotEmpty(),
                ) {
                    haptics()
                    onSend()
                }
            }
        }

        if (!state.reasoningEnabled) {
            Text(
                text = stringResource(R.string.model_no_reasoning),
                fontSize = 10.sp,
                color = palette.c4,
                modifier = Modifier.padding(start = 2.dp),
            )
        }

        if (state.almostFull) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenContext)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LineIcon(AgentIcons.Info, size = 14.dp, tint = palette.c5)
                Text(
                    text = stringResource(R.string.context_almost_full) + " · " + state.contextPercent + "%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.c5,
                )
                Text(
                    text = "/compact",
                    fontSize = 11.sp,
                    color = palette.c4,
                )
            }
        }
    }
}

@Composable
private fun OfflineBanner(onSendNow: () -> Unit, onCancel: () -> Unit) {
    val palette = AgentTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.offline_banner),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = palette.c5,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallPill(stringResource(R.string.offline_send_now), onSendNow)
            SmallPill(stringResource(R.string.cancel), onCancel)
        }
    }
}

@Composable
private fun AttachmentChip(
    draft: com.agent.ai.data.chat.AttachmentDraft,
    onRemove: () -> Unit,
) {
    val palette = AgentTheme.palette
    val name = when (val part = draft.part) {
        is com.agent.ai.data.chat.MessagePart.Image -> part.name
        is com.agent.ai.data.chat.MessagePart.File -> part.name
        else -> ""
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(10.dp))
            .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LineIcon(AgentIcons.Paperclip, size = 12.dp, tint = palette.c4)
        Text(
            text = name,
            fontSize = 11.sp,
            color = palette.c5,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.attachment_remove), onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(AgentIcons.X, size = 11.dp, tint = palette.c4)
        }
    }
}

@Composable
private fun SlashMenu(entries: List<SlashCommandEntry>, onPick: (SlashCommandEntry) -> Unit) {
    val palette = AgentTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp),
    ) {
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = entry.enabled) { onPick(entry) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = entry.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (entry.enabled) palette.c5 else palette.c4,
                )
                Text(
                    text = when {
                        entry.custom != null -> entry.custom.description.ifBlank {
                            stringResource(R.string.commands_yours)
                        }

                        entry.enabled -> stringResource(entry.description)
                        else -> stringResource(entry.reason ?: R.string.slash_disabled)
                    },
                    fontSize = 11.sp,
                    color = palette.c4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!entry.enabled) {
                    Text(
                        text = stringResource(R.string.slash_disabled),
                        fontSize = 10.sp,
                        color = palette.c4.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** The small ring beside the input: used context as a percentage of the window. */
@Composable
fun ContextRing(percent: Int, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    val full = percent >= 80
    val track = palette.c3
    val color = palette.c5
    Box(
        modifier = Modifier
            .size(34.dp)
            .clickable(onClickLabel = stringResource(R.string.context_open), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(28.dp)) {
            val stroke = if (full) 3.5.dp.toPx() else 2.5.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * (percent.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "$percent",
            fontSize = 9.sp,
            color = palette.c4,
        )
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .pressFx(source, CircleShape, pressedScale = 0.9f, maxTilt = 6f)
            .clip(CircleShape)
            .background(if (enabled) palette.c5 else palette.c3)
            .clickable(
                interactionSource = source,
                indication = null,
                onClickLabel = stringResource(R.string.composer_send),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        LineIcon(AgentIcons.ArrowUp, size = 18.dp, tint = palette.c0, strokeWidth = 2.4f)
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    val palette = AgentTheme.palette
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .pressFx(source, CircleShape, pressedScale = 0.9f, maxTilt = 6f)
            .clip(CircleShape)
            .background(palette.c5)
            .clickable(
                interactionSource = source,
                indication = null,
                onClickLabel = stringResource(R.string.composer_stop),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(11.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.c0)
        )
    }
}

@Composable
private fun CircleIcon(
    icon: List<String>,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val palette = AgentTheme.palette
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) palette.c4 else palette.c1)
            .border(1.dp, palette.c3, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LineIcon(icon, size = 16.dp, tint = if (active) palette.c0 else palette.c5)
    }
}

@Composable
private fun Pill(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    filled: Boolean = false,
    contentDescription: String? = null,
) {
    val palette = AgentTheme.palette
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .height(36.dp)
            .pressFx(source, RoundedCornerShape(18.dp), enabled = enabled, pressedScale = 0.94f, maxTilt = 6f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (filled) palette.c4 else palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onClickLabel = contentDescription ?: text,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = when {
                !enabled -> palette.c4.copy(alpha = 0.5f)
                filled -> palette.c0
                else -> palette.c5
            },
        )
    }
}

@Composable
fun SmallPill(label: String, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.c5)
    }
}

/** Shown on the empty chat when the model cannot do something the composer offers. */
@Composable
fun CapabilityNote(capabilities: ModelCapabilities) {
    val palette = AgentTheme.palette
    if (capabilities.vision && capabilities.reasoning) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!capabilities.vision) {
            Text(
                text = stringResource(R.string.model_no_images),
                fontSize = 10.sp,
                color = palette.c4,
            )
        }
        if (!capabilities.reasoning) {
            Text(
                text = stringResource(R.string.model_no_reasoning),
                fontSize = 10.sp,
                color = palette.c4,
            )
        }
    }
}
