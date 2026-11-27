package com.agent.ai.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.R
import com.agent.ai.data.chat.ConversationEntity
import com.agent.ai.motion.LocalReduceMotion
import com.agent.ai.motion.pressFx
import com.agent.ai.motion.rememberHaptics
import com.agent.ai.ui.Routes
import com.agent.ai.ui.theme.AgentTheme
import java.util.Calendar
import kotlinx.coroutines.delay

/** Conversations under Pinned, Today, Yesterday, Previous 7 days and Older. */
private data class ChatGroup(val label: Int, val chats: List<ConversationEntity>)

private fun groupConversations(conversations: List<ConversationEntity>): List<ChatGroup> {
    val pinned = conversations.filter { it.pinned }
    val rest = conversations.filterNot { it.pinned }
    val today = startOfDay(System.currentTimeMillis())
    val yesterday = today - DAY
    val week = today - 6 * DAY

    val buckets = linkedMapOf<Int, MutableList<ConversationEntity>>()
    fun add(label: Int, chat: ConversationEntity) {
        buckets.getOrPut(label) { mutableListOf() }.add(chat)
    }
    rest.forEach { chat ->
        when {
            chat.updatedAt >= today -> add(R.string.group_today, chat)
            chat.updatedAt >= yesterday -> add(R.string.group_yesterday, chat)
            chat.updatedAt >= week -> add(R.string.group_previous_7, chat)
            else -> add(R.string.group_older, chat)
        }
    }
    val out = mutableListOf<ChatGroup>()
    if (pinned.isNotEmpty()) out.add(ChatGroup(R.string.group_pinned, pinned))
    buckets.forEach { (label, chats) -> out.add(ChatGroup(label, chats)) }
    return out
}

private const val DAY = 24L * 60L * 60L * 1000L

private fun startOfDay(time: Long): Long = Calendar.getInstance().run {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    timeInMillis
}

/**
 * The navigation drawer shared by the chat and code screens.
 *
 * The panel slides in over the screen in 300ms with a decelerating curve while the
 * scrim fades from 0 to 65 percent. The logo pops in with a slight overshoot, then the
 * app name flips in letter by letter, and the menu items slide in from 14dp.
 */
@Composable
fun AgentDrawer(
    visible: Boolean,
    current: String,
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onRenameChat: (String, String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onPinChat: (String, Boolean) -> Unit = { _, _ -> },
    onExportChat: (ConversationEntity) -> Unit = {},
    onConversationPrompt: () -> Unit = {},
    onSearchChats: () -> Unit = {},
) {
    val palette = AgentTheme.palette
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()

    var menuFor by remember { mutableStateOf<ConversationEntity?>(null) }
    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    val scrim by animateFloatAsState(
        targetValue = if (visible) 0.65f else 0f,
        animationSpec = tween(if (reduce) 0 else 300, easing = LinearOutSlowInEasing),
        label = "drawerScrim",
    )
    val panelX by animateDpAsState(
        targetValue = if (visible) 0.dp else -320.dp,
        animationSpec = tween(if (reduce) 0 else 300, easing = LinearOutSlowInEasing),
        label = "drawerPanel",
    )

    val scrimSource = remember { MutableInteractionSource() }

    BackHandler(enabled = visible, onBack = onDismiss)

    // Nothing of the drawer is composed while it is closed: a full screen scrim would
    // otherwise swallow every touch meant for the screen underneath.
    if (visible || panelX > -320.dp) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim }
                    .background(palette.c0)
                    .then(
                        if (visible) {
                            Modifier.clickable(
                                interactionSource = scrimSource,
                                indication = null,
                                onClick = onDismiss,
                            )
                        } else {
                            Modifier
                        }
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .offset { IntOffset(x = panelX.roundToPx(), y = 0) }
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .background(palette.c0.copy(alpha = 0.94f))
                    .border(1.dp, palette.c3),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    DrawerHeader(play = visible)

                    Spacer(Modifier.height(10.dp))
                    NewChatRow {
                        haptics()
                        onNewChat()
                    }
                    Spacer(Modifier.height(4.dp))
                    SearchRow {
                        haptics()
                        onSearchChats()
                    }

                    DrawerLabel(stringResource(R.string.drawer_features), top = 14.dp)
                    DrawerLabel(stringResource(R.string.drawer_main), top = 2.dp)

                    var index = 0
                    DrawerItem(stringResource(R.string.drawer_chat), AgentIcons.Message, current == Routes.Main, index++, visible) { onNavigate(Routes.Main) }
                    DrawerItem(stringResource(R.string.drawer_code), AgentIcons.Code, current == Routes.Code, index++, visible) { onNavigate(Routes.Code) }
                    DrawerItem(stringResource(R.string.drawer_projects), AgentIcons.Folder, current == Routes.Projects, index++, visible) { onNavigate(Routes.Projects) }
                    DrawerItem(stringResource(R.string.drawer_skills), AgentIcons.Bolt, current == Routes.Skills, index++, visible) { onNavigate(Routes.Skills) }
                    DrawerItem(stringResource(R.string.drawer_mcp), AgentIcons.Sliders, current == Routes.Mcp, index++, visible) { onNavigate(Routes.Mcp) }
                    DrawerItem(stringResource(R.string.drawer_checkpoints), AgentIcons.Clock, current == Routes.Checkpoints, index++, visible) { onNavigate(Routes.Checkpoints) }

                    DrawerLabel(stringResource(R.string.drawer_more), top = 16.dp)
                    DrawerItem(stringResource(R.string.drawer_commands), AgentIcons.Terminal, current == Routes.Commands, index++, visible) { onNavigate(Routes.Commands) }
                    DrawerItem(stringResource(R.string.drawer_git), AgentIcons.Branch, current == Routes.Git, index++, visible) { onNavigate(Routes.Git) }
                    DrawerItem(stringResource(R.string.drawer_hooks), AgentIcons.Link, current == Routes.Hooks, index++, visible) { onNavigate(Routes.Hooks) }
                    DrawerItem(stringResource(R.string.drawer_subagents), AgentIcons.Users, current == Routes.Subagents, index++, visible) { onNavigate(Routes.Subagents) }
                    DrawerItem(stringResource(R.string.drawer_usage), AgentIcons.Clock, current == Routes.Usage, index++, visible) { onNavigate(Routes.Usage) }

                    DrawerLabel(stringResource(R.string.drawer_your_chats), top = 16.dp)
                    if (conversations.isEmpty()) {
                        Text(
                            text = stringResource(R.string.drawer_no_chats),
                            fontSize = 12.sp,
                            color = palette.c4,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    } else {
                        groupConversations(conversations).forEach { group ->
                            Text(
                                text = stringResource(group.label).uppercase(),
                                fontSize = 9.sp,
                                letterSpacing = 1.2.sp,
                                color = palette.c4.copy(alpha = 0.8f),
                                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                            )
                            group.chats.forEach { conversation ->
                                ChatRow(
                                    title = conversation.title,
                                    active = conversation.id == activeConversationId,
                                    pinned = conversation.pinned,
                                    index = index++,
                                    play = visible,
                                    onClick = {
                                        haptics()
                                        onOpenChat(conversation.id)
                                    },
                                    onLongClick = {
                                        haptics()
                                        menuFor = conversation
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    ProviderCard()
                    Spacer(Modifier.height(8.dp))
                    DrawerItem(stringResource(R.string.drawer_settings), AgentIcons.Settings, current == Routes.Settings, index++, visible) {
                        onNavigate(Routes.Settings)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    menuFor?.let { chat ->
        ChatMenuDialog(
            chat = chat,
            onRename = {
                renameText = chat.title
                renaming = chat
                menuFor = null
            },
            onPin = {
                onPinChat(chat.id, !chat.pinned)
                menuFor = null
            },
            onExport = {
                onExportChat(chat)
                menuFor = null
            },
            onPrompt = {
                onConversationPrompt()
                menuFor = null
            },
            onDelete = {
                onDeleteChat(chat.id)
                menuFor = null
            },
            onDismiss = { menuFor = null },
        )
    }

    renaming?.let { chat ->
        RenameChatDialog(
            text = renameText,
            onTextChange = { renameText = it },
            onSave = {
                onRenameChat(chat.id, renameText)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun ChatMenuDialog(
    chat: ConversationEntity,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onExport: () -> Unit,
    onPrompt: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AgentTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.c2,
        title = {
            Text(
                text = chat.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                DialogRow(if (chat.pinned) {
                    stringResource(R.string.chat_unpin)
                } else {
                    stringResource(R.string.chat_pin)
                }, onPin)
                DialogRow(stringResource(R.string.chat_rename), onRename)
                DialogRow(stringResource(R.string.chat_export), onExport)
                DialogRow(stringResource(R.string.chat_conversation_prompt), onPrompt)
                DialogRow(stringResource(R.string.chat_delete), onDelete)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = palette.c4)
            }
        },
    )
}

@Composable
private fun DialogRow(label: String, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    Text(
        text = label,
        fontSize = 14.sp,
        color = palette.c5,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    )
}

@Composable
private fun RenameChatDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AgentTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.c2,
        title = {
            Text(
                text = stringResource(R.string.chat_rename_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
            )
        },
        text = {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    color = palette.c5,
                ),
                cursorBrush = SolidColor(palette.c5),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.c1)
                    .border(1.dp, palette.c3, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.chat_rename_save), color = palette.c5)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = palette.c4)
            }
        },
    )
}

/** Logo pops in, then the name flips in letter by letter. */
@Composable
private fun DrawerHeader(play: Boolean) {
    val palette = AgentTheme.palette
    val reduce = LocalReduceMotion.current
    val pop = remember { Animatable(0f) }

    LaunchedEffect(play, reduce) {
        if (!play) {
            pop.snapTo(0f)
            return@LaunchedEffect
        }
        if (reduce) {
            pop.snapTo(1f)
            return@LaunchedEffect
        }
        pop.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.graphicsLayer {
                val v = pop.value
                scaleX = 0.6f + 0.4f * v
                scaleY = 0.6f + 0.4f * v
                rotationZ = -12f * (1f - v)
                alpha = v.coerceIn(0f, 1f)
            }
        ) {
            AppLogo(size = 34.dp, shadow = false)
        }
        // The name is laid out letter by letter, so the Row is pinned to left to right
        // no matter what the surrounding layout direction is. Arabic letters join inside
        // a word, so an Arabic name must be animated as one word instead.
        val name = stringResource(R.string.app_name)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(horizontalArrangement = Arrangement.spacedBy(0.5.dp)) {
                name.forEachIndexed { i, char ->
                    FlipLetter(char = char, index = i, play = play, tint = palette.c5, depth = palette.c3)
                }
            }
        }
    }
}

@Composable
private fun FlipLetter(
    char: Char,
    index: Int,
    play: Boolean,
    tint: Color,
    depth: Color,
) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(play, reduce) {
        if (!play) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        if (reduce) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        delay(180L + 45L * index)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        )
    }

    val letter: @Composable (Color, Int) -> Unit = { color, offsetDp ->
        Text(
            text = char.toString(),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.offset(y = offsetDp.dp),
        )
    }

    Box(
        Modifier.graphicsLayer {
            val v = progress.value
            alpha = v.coerceIn(0f, 1f)
            rotationX = -85f * (1f - v.coerceAtMost(1.15f))
            val s = 0.9f + 0.1f * v
            scaleX = s
            scaleY = s
            translationY = (-10).dp.toPx() * (1f - v.coerceAtMost(1f))
            transformOrigin = TransformOrigin(0.5f, 1f)
            cameraDistance = 12f * density
        }
    ) {
        letter(depth.copy(alpha = 0.55f), 3)
        letter(depth.copy(alpha = 0.40f), 2)
        letter(depth.copy(alpha = 0.28f), 1)
        letter(tint, 0)
    }
}

@Composable
private fun DrawerLabel(text: String, top: androidx.compose.ui.unit.Dp) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        color = AgentTheme.palette.c4,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = top, bottom = 4.dp),
    )
}

@Composable
private fun NewChatRow(onClick: () -> Unit) {
    val palette = AgentTheme.palette
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .pressFx(source, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(palette.c4)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineIcon(AgentIcons.Plus, size = 18.dp, tint = palette.c0, strokeWidth = 2.4f)
        Text(
            text = stringResource(R.string.drawer_new_chat),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.c0,
        )
    }
}

@Composable
private fun SearchRow(onClick: () -> Unit) {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineIcon(AgentIcons.Search, size = 15.dp, tint = palette.c4)
        Text(
            text = stringResource(R.string.drawer_search_chats),
            fontSize = 13.sp,
            color = palette.c4,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    title: String,
    active: Boolean,
    pinned: Boolean,
    index: Int,
    play: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = AgentTheme.palette
    val reduce = LocalReduceMotion.current
    val p = remember { Animatable(0f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val background by animateColorAsState(
        targetValue = if (active) palette.c2 else Color.Transparent,
        animationSpec = tween(200),
        label = "chatRowBg",
    )

    LaunchedEffect(play, reduce) {
        if (!play) {
            p.snapTo(0f)
            return@LaunchedEffect
        }
        if (reduce) {
            p.snapTo(1f)
            return@LaunchedEffect
        }
        delay(600L + 60L * index)
        p.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = p.value
                translationX = (if (rtl) 14f else -14f).dp.toPx() * (1f - p.value)
            }
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIcon(
            if (pinned) AgentIcons.Pin else AgentIcons.Message,
            size = 14.dp,
            tint = palette.c4,
        )
        Text(
            text = title,
            fontSize = 13.sp,
            color = palette.c5,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProviderCard() {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.c2)
            .border(1.dp, palette.c3, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(palette.c5)
        )
        Column {
            Text(
                text = stringResource(R.string.models_gateway),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
            )
            Text(
                text = stringResource(R.string.models_auto_free),
                fontSize = 12.sp,
                color = palette.c4,
            )
        }
    }
}

@Composable
private fun DrawerItem(
    title: String,
    icon: List<String>,
    active: Boolean,
    index: Int,
    play: Boolean,
    onClick: () -> Unit,
) {
    val palette = AgentTheme.palette
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val source = remember { MutableInteractionSource() }
    val p = remember { Animatable(0f) }
    val background by animateColorAsState(
        targetValue = if (active) palette.c2 else Color.Transparent,
        animationSpec = tween(200),
        label = "drawerItemBg",
    )

    LaunchedEffect(play, reduce) {
        if (!play) {
            p.snapTo(0f)
            return@LaunchedEffect
        }
        if (reduce) {
            p.snapTo(1f)
            return@LaunchedEffect
        }
        delay(600L + 60L * index)
        p.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = p.value
                translationX = (if (rtl) 14f else -14f).dp.toPx() * (1f - p.value)
            }
            .height(44.dp)
            .pressFx(source, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(interactionSource = source, indication = null) {
                haptics()
                onClick()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LineIcon(icon, size = 20.dp, tint = if (active) palette.c5 else palette.c4)
        Text(title, fontSize = 14.sp, color = palette.c5)
    }
}
