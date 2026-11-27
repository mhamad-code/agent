package com.agent.ai.chat

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.ai.R
import com.agent.ai.data.chat.SlashCommandEntity
import com.agent.ai.markdown.LocalLinkHandler
import com.agent.ai.motion.LocalHapticsEnabled
import com.agent.ai.motion.LocalReduceMotion
import com.agent.ai.motion.rememberHaptics
import com.agent.ai.ui.Routes
import com.agent.ai.ui.components.AgentDrawer
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentSheet
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.FloatingLogo
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.PrimaryButton
import com.agent.ai.ui.components.SquareButton
import com.agent.ai.ui.components.SectionLabel
import com.agent.ai.ui.screens.ContextSheet
import com.agent.ai.ui.screens.ModelsSheet
import com.agent.ai.ui.screens.ToolsSheet
import com.agent.ai.ui.theme.AgentBackdrop
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.Dimens
import com.agent.ai.ui.theme.InterFamily
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private enum class ChatSheet { Models, Tools, Context }

/**
 * The home screen: the message tree of the open conversation, the composer, and every
 * action the chat supports.
 */
@Composable
fun ChatScreen(
    chat: ChatViewModel,
    onNavigate: (String) -> Unit,
) {
    val state by chat.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    var drawerOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ChatSheet?>(null) }
    var pendingLink by remember { mutableStateOf<String?>(null) }
    var menuMessage by remember { mutableStateOf<ChatMessageUi?>(null) }
    var selectText by remember { mutableStateOf<String?>(null) }
    var editMessage by remember { mutableStateOf<ChatMessageUi?>(null) }
    var exportFor by remember { mutableStateOf<com.agent.ai.data.chat.ConversationEntity?>(null) }
    var promptDialog by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorText by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var fillCommand by remember { mutableStateOf<SlashCommandEntity?>(null) }
    var voiceDenied by remember { mutableStateOf(false) }

    val haptics = rememberHaptics()
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    val follow = state.settings.autoScroll

    // Follow the end of the reply, but only while the user is at the bottom.
    LaunchedEffect(state.streamingText.length, state.messages.size, state.streamingReasoning.length) {
        if (follow && atBottom && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex, 1_000_000)
        }
    }

    // A new message always scrolls to the bottom.
    LaunchedEffect(state.messages.firstOrNull()?.id, state.messages.size) {
        if (state.messages.isNotEmpty() && state.streamingMessageId != null) {
            listState.scrollToItem(state.messages.lastIndex, 1_000_000)
        }
    }

    // A search result opens the conversation at that message.
    LaunchedEffect(state.scrollToMessageId, state.messages.size) {
        val id = state.scrollToMessageId ?: return@LaunchedEffect
        val index = state.messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            listState.scrollToItem(index, 0)
            chat.consumeScrollTo()
        }
    }

    // The Undo snackbar lives exactly as long as the six second delete window.
    LaunchedEffect(state.undo?.token) {
        val undo = state.undo ?: return@LaunchedEffect
        val result = withTimeoutOrNull(ChatViewModel.UNDO_MS) {
            snackbar.showSnackbar(
                message = context.getString(
                    if (undo.conversationDelete) R.string.chat_deleted else R.string.message_deleted
                ),
                actionLabel = context.getString(R.string.message_undo),
                duration = SnackbarDuration.Indefinite,
            )
        }
        if (result == SnackbarResult.ActionPerformed) chat.undoDelete() else chat.dismissUndo()
    }

    LaunchedEffect(state.noticeRes) {
        val res = state.noticeRes ?: return@LaunchedEffect
        val text = state.noticeArg?.let { context.getString(res, it) } ?: context.getString(res)
        snackbar.showSnackbar(text)
        chat.consumeNotice()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(chat::addAttachment)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(chat::addAttachment)
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        listening = false
        val heard = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!heard.isNullOrBlank()) chat.dictate(heard)
    }

    fun startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.voice_unavailable)) }
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, state.settings.voiceLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure { scope.launch { snackbar.showSnackbar(context.getString(R.string.voice_unavailable)) } }
        listening = true
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoice()
        } else {
            voiceDenied = true
        }
    }

    CompositionLocalProvider(
        LocalLinkHandler provides { url -> pendingLink = url },
        LocalHapticsEnabled provides state.settings.haptics,
    ) {
        Box(Modifier.fillMaxSize()) {
            AgentBackdrop()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .padding(horizontal = Dimens.screenPaddingWide, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.gapWide),
            ) {
                ChatHeader(
                    title = state.title,
                    modelName = state.models.firstOrNull { it.id == state.modelId }?.name,
                    onMenu = { drawerOpen = true },
                    onOpenModels = { sheet = ChatSheet.Models },
                    onNewChat = chat::newChat,
                )

                Box(Modifier.weight(1f)) {
                    if (state.messages.isEmpty()) {
                        EmptyChat(
                            thinking = state.retrying,
                            lastConversation = state.conversations.firstOrNull(),
                            onContinue = { conversation ->
                                chat.openConversation(conversation.id)
                            },
                            onNavigate = onNavigate,
                        )
                    } else {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalDensity provides
                                androidx.compose.ui.unit.Density(
                                    density.density,
                                    density.fontScale * state.settings.textScale,
                                )
                        ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = 4.dp,
                                bottom = 12.dp,
                            ),
                        ) {
                            items(state.messages, key = { it.id }) { message ->
                                MessageItem(
                                    message = message,
                                    streamingText = state.streamingText,
                                    streamingReasoning = state.streamingReasoning,
                                    awaitingFirstToken = state.awaitingFirstToken,
                                    reasoningStartedAt = state.reasoningStartedAt,
                                    settings = state.settings,
                                    onLongPress = {
                                        haptics()
                                        menuMessage = message
                                    },
                                    onCopy = { text ->
                                        clipboard.setText(AnnotatedString(text))
                                        scope.launch {
                                            snackbar.showSnackbar(context.getString(R.string.action_copied))
                                        }
                                    },
                                    onRegenerate = { chat.regenerate(message.id) },
                                    onMore = { menuMessage = message },
                                    onSwitchBranch = { offset -> chat.switchBranch(message.id, offset) },
                                    onContinue = { chat.continueReply(message.id) },
                                    onRetry = { chat.retry(message.id) },
                                    onOpenModels = { sheet = ChatSheet.Models },
                                )
                            }
                        }
                        }

                        if (!atBottom && state.messages.isNotEmpty()) {
                            JumpToLatest(
                                dot = state.streaming,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 4.dp, bottom = 4.dp),
                                onClick = {
                                    scope.launch {
                                        listState.scrollToItem(state.messages.lastIndex, 1_000_000)
                                    }
                                },
                            )
                        }
                    }
                }

                Composer(
                    state = state,
                    listening = listening,
                    onDraftChange = chat::onDraftChange,
                    onSend = chat::send,
                    onStop = chat::stop,
                    onAttachImage = { imagePicker.launch("image/*") },
                    onAttachFile = { filePicker.launch("*/*") },
                    onRemoveAttachment = chat::removeAttachment,
                    onVoice = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (granted) startVoice() else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    onOpenModels = { sheet = ChatSheet.Models },
                    onOpenTools = { sheet = ChatSheet.Tools },
                    onOpenContext = { sheet = ChatSheet.Context },
                    onExpand = {
                        editorText = state.draft
                        editorOpen = true
                    },
                    onSlashCommand = { entry ->
                        when (entry.name) {
                            "/compact" -> {
                                chat.onDraftChange("")
                                chat.compact()
                            }

                            "/new" -> {
                                chat.onDraftChange("")
                                chat.newChat()
                            }

                            else -> Unit
                        }
                    },
                    onFillCommand = { command ->
                        chat.onDraftChange("")
                        fillCommand = command
                    },
                    onDeepThink = chat::setDeepThink,
                    onSendNow = { chat.sendPending(automatic = false) },
                    onCancelPending = chat::cancelPending,
                )
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 84.dp),
            )

            AgentDrawer(
                visible = drawerOpen,
                current = Routes.Main,
                conversations = state.conversations,
                activeConversationId = state.conversationId,
                onDismiss = { drawerOpen = false },
                onNavigate = { route ->
                    drawerOpen = false
                    onNavigate(route)
                },
                onNewChat = {
                    drawerOpen = false
                    chat.newChat()
                },
                onOpenChat = { id ->
                    drawerOpen = false
                    chat.openConversation(id)
                },
                onRenameChat = chat::rename,
                onDeleteChat = chat::deleteConversation,
                onPinChat = chat::setPinned,
                onExportChat = { conversation ->
                    drawerOpen = false
                    exportFor = conversation
                },
                onConversationPrompt = {
                    drawerOpen = false
                    promptDialog = true
                },
                onSearchChats = {
                    drawerOpen = false
                    onNavigate(Routes.Search)
                },
            )
        }
    }

    when (sheet) {
        ChatSheet.Models -> ModelsSheet(
            models = state.models,
            selectedId = state.modelId,
            loading = state.modelsLoading,
            error = state.modelsError,
            offline = state.modelsOffline,
            onSelect = {
                chat.selectModel(it)
                sheet = null
            },
            onRefresh = chat::refreshModels,
            onDismiss = { sheet = null },
        )

        ChatSheet.Tools -> ToolsSheet(
            capabilities = state.capabilities,
            deepThink = state.deepThink,
            onDeepThink = chat::setDeepThink,
            onDismiss = { sheet = null },
        )

        ChatSheet.Context -> ContextSheet(
            state = state,
            onCompact = {
                sheet = null
                chat.compact()
            },
            onRestore = {
                sheet = null
                chat.restoreCompaction()
            },
            onDismiss = { sheet = null },
        )

        null -> Unit
    }

    if (state.showPrivacyNotice) {
        PrivacyNoticeSheet(onAccept = chat::acceptPrivacyNotice)
    }

    pendingLink?.let { url ->
        LinkConfirmDialog(
            url = url,
            onDismiss = { pendingLink = null },
            onOpen = {
                pendingLink = null
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            },
        )
    }

    menuMessage?.let { message ->
        MessageMenuSheet(
            message = message,
            onCopy = {
                clipboard.setText(AnnotatedString(message.text))
                menuMessage = null
                scope.launch { snackbar.showSnackbar(context.getString(R.string.action_copied)) }
            },
            onSelectText = {
                selectText = message.text
                menuMessage = null
            },
            onShare = {
                menuMessage = null
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message.text)
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(send, context.getString(R.string.message_share_title))
                    )
                }
            },
            onRegenerate = {
                menuMessage = null
                chat.regenerate(message.id)
            },
            onSaveToMemory = {
                menuMessage = null
                chat.saveToMemory(message.id)
            },
            onEdit = {
                editMessage = message
                menuMessage = null
            },
            onDelete = {
                menuMessage = null
                chat.deleteMessage(message.id)
            },
            onRestoreCompaction = {
                menuMessage = null
                chat.restoreCompaction()
            },
            onDismiss = { menuMessage = null },
        )
    }

    selectText?.let { text ->
        SelectTextDialog(text = text, onDismiss = { selectText = null })
    }

    editMessage?.let { message ->
        EditMessageDialog(
            initial = message.text,
            onSave = { text ->
                editMessage = null
                if (text.isNotBlank()) chat.editAndResend(message.id, text)
            },
            onDismiss = { editMessage = null },
        )
    }

    exportFor?.let { conversation ->
        ExportDialog(
            conversation = conversation,
            onExport = { allBranches, asJson ->
                chat.exportConversation(conversation.id, allBranches, asJson)
            },
            onDismiss = { exportFor = null },
        )
    }

    if (promptDialog) {
        PromptDialog(
            initial = state.conversationPrompt.orEmpty(),
            onSave = {
                chat.setConversationPrompt(it)
                promptDialog = false
            },
            onDismiss = { promptDialog = false },
        )
    }

    if (editorOpen) {
        FullEditorDialog(
            text = editorText,
            onTextChange = { editorText = it },
            onDone = {
                chat.onDraftChange(editorText)
                editorOpen = false
            },
            onDismiss = { editorOpen = false },
        )
    }

    fillCommand?.let { command ->
        CommandFillDialog(
            command = command,
            onInsert = { text ->
                chat.onDraftChange(text)
                fillCommand = null
            },
            onDismiss = { fillCommand = null },
        )
    }

    if (voiceDenied) {
        AlertDialog(
            onDismissRequest = { voiceDenied = false },
            containerColor = AgentTheme.palette.c2,
            title = {
                Text(
                    text = stringResource(R.string.voice_permission_denied),
                    fontSize = 15.sp,
                    color = AgentTheme.palette.c5,
                )
            },
            confirmButton = {
                TextButton(onClick = { voiceDenied = false }) {
                    Text(stringResource(R.string.cancel), color = AgentTheme.palette.c5)
                }
            },
        )
    }

    BackHandler(enabled = sheet != null) { sheet = null }

    DisposableEffect(Unit) {
        onDispose { chat.onScreenLeft() }
    }
}

@Composable
private fun ChatHeader(
    title: String,
    modelName: String?,
    onMenu: () -> Unit,
    onOpenModels: () -> Unit,
    onNewChat: () -> Unit,
) {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.headerHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SquareButton(
            icon = AgentIcons.Menu,
            label = stringResource(R.string.drawer_open),
            onClick = onMenu,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.c2)
                .border(1.dp, palette.c3, RoundedCornerShape(14.dp))
                .clickable(onClickLabel = stringResource(R.string.models_title), onClick = onOpenModels)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = title.ifBlank { stringResource(R.string.drawer_new_chat) },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = palette.c5,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = modelName ?: stringResource(R.string.models_auto_free),
                    fontSize = 11.sp,
                    color = palette.c4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LineIcon(AgentIcons.ChevronDown, size = 11.dp, tint = palette.c4)
            }
        }
        SquareButton(
            icon = AgentIcons.Plus,
            label = stringResource(R.string.drawer_new_chat),
            onClick = onNewChat,
        )
    }
}

@Composable
private fun EmptyChat(
    thinking: Boolean,
    lastConversation: com.agent.ai.data.chat.ConversationEntity?,
    onContinue: (com.agent.ai.data.chat.ConversationEntity) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val palette = AgentTheme.palette
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FloatingLogo(size = 84.dp, thinking = thinking)
        Text(
            text = stringResource(R.string.home_welcome_back),
            fontSize = 12.sp,
            letterSpacing = 3.6.sp,
            color = palette.c4,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = stringResource(R.string.home_tagline),
            fontSize = 25.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 31.sp,
            color = palette.c5,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 14.dp, bottom = 22.dp)
                .widthIn(max = 280.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (lastConversation != null) {
                QuickAction(
                    icon = AgentIcons.Message,
                    title = stringResource(R.string.empty_continue_chat, lastConversation.title),
                    subtitle = stringResource(R.string.empty_continue_sub),
                ) { onContinue(lastConversation) }
            }
            QuickAction(
                icon = AgentIcons.Folder,
                title = stringResource(R.string.home_new_project),
                subtitle = stringResource(R.string.home_new_project_sub),
            ) { onNavigate(Routes.Projects) }
            QuickAction(
                icon = AgentIcons.Code,
                title = stringResource(R.string.home_code_mode),
                subtitle = stringResource(R.string.home_code_mode_sub),
            ) { onNavigate(Routes.Code) }
            QuickAction(
                icon = AgentIcons.Bolt,
                title = stringResource(R.string.home_install_skill),
                subtitle = stringResource(R.string.home_install_skill_sub),
            ) { onNavigate(Routes.Skills) }
        }
    }
}

@Composable
private fun QuickAction(
    icon: List<String>,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.c2)
            .border(1.dp, palette.c3, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.c1),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(icon, size = 18.dp, tint = palette.c4)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = palette.c4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LineIcon(AgentIcons.ArrowUpRight, size = 16.dp, tint = palette.c4)
    }
}

@Composable
private fun JumpToLatest(dot: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.c2)
            .border(1.dp, palette.c3, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineIcon(AgentIcons.ChevronDown, size = 13.dp, tint = palette.c5)
        Text(
            text = stringResource(R.string.chat_jump_latest),
            fontSize = 11.sp,
            color = palette.c5,
        )
        if (dot) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(palette.c5)
            )
        }
    }
}

@Composable
private fun MessageMenuSheet(
    message: ChatMessageUi,
    onCopy: () -> Unit,
    onSelectText: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit,
    onSaveToMemory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestoreCompaction: () -> Unit,
    onDismiss: () -> Unit,
) {
    AgentSheet(onDismiss = onDismiss) {
        if (message.isUser) {
            SheetAction(stringResource(R.string.action_copy), onCopy)
            SheetAction(stringResource(R.string.action_select_text), onSelectText)
            SheetAction(stringResource(R.string.action_edit_resend), onEdit)
            SheetAction(stringResource(R.string.action_delete), onDelete, danger = true)
        } else {
            SheetAction(stringResource(R.string.action_copy), onCopy)
            SheetAction(stringResource(R.string.action_select_text), onSelectText)
            SheetAction(stringResource(R.string.action_share), onShare)
            SheetAction(stringResource(R.string.action_regenerate), onRegenerate)
            SheetAction(stringResource(R.string.action_save_memory), onSaveToMemory)
            if (message.summary) {
                SheetAction(stringResource(R.string.context_restore), onRestoreCompaction)
            }
            SheetAction(stringResource(R.string.action_delete), onDelete, danger = true)
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit, danger: Boolean = false) {
    val palette = AgentTheme.palette
    Text(
        text = label,
        fontSize = 14.sp,
        color = if (danger) palette.c4 else palette.c5,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
}

@Composable
private fun SelectTextDialog(text: String, onDismiss: () -> Unit) {
    val palette = AgentTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.c2,
        title = {
            Text(
                text = stringResource(R.string.message_select_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
            )
        },
        text = {
            Box(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = palette.c5,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = palette.c5)
            }
        },
    )
}

@Composable
private fun EditMessageDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AgentTheme.palette
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.c2,
        title = {
            Text(
                text = stringResource(R.string.message_edit_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
            )
        },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = palette.c5),
                cursorBrush = SolidColor(palette.c5),
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.c1)
                    .border(1.dp, palette.c3, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(R.string.composer_send), color = palette.c5)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = palette.c4)
            }
        },
    )
}

@Composable
private fun ExportDialog(
    conversation: com.agent.ai.data.chat.ConversationEntity,
    onExport: (Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AgentTheme.palette
    var allBranches by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.c2,
        title = {
            Text(
                text = stringResource(R.string.chat_export_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = conversation.title,
                    fontSize = 13.sp,
                    color = palette.c4,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.chat_export_all_branches),
                        fontSize = 13.sp,
                        color = palette.c5,
                        modifier = Modifier.weight(1f),
                    )
                    AppSwitch(checked = allBranches, onCheckedChange = { allBranches = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportButton(stringResource(R.string.chat_export_markdown)) {
                        onExport(allBranches, false)
                    }
                    ExportButton(stringResource(R.string.chat_export_json)) {
                        onExport(allBranches, true)
                    }
                }
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
private fun ExportButton(label: String, onClick: () -> Unit) {
    val palette = AgentTheme.palette
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.c5)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.c0)
    }
}

@Composable
private fun PromptDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AgentTheme.palette
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.c2,
        title = {
            Text(
                text = stringResource(R.string.chat_conversation_prompt),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.chat_conversation_prompt_hint),
                    fontSize = 12.sp,
                    color = palette.c4,
                )
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = palette.c5),
                    cursorBrush = SolidColor(palette.c5),
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.c1)
                        .border(1.dp, palette.c3, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(R.string.chat_conversation_prompt_save), color = palette.c5)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = palette.c4)
            }
        },
    )
}

@Composable
private fun FullEditorDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AgentTheme.palette
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.c0)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.editor_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.editor_done), color = palette.c5)
                }
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(
                    fontFamily = InterFamily,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = palette.c5,
                ),
                cursorBrush = SolidColor(palette.c5),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.c1)
                    .border(1.dp, palette.c3, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.composer_hint),
                                fontSize = 16.sp,
                                color = palette.c4,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun CommandFillDialog(
    command: SlashCommandEntity,
    onInsert: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AgentTheme.palette
    val variables = remember(command.prompt) {
        com.agent.ai.data.chat.SlashCommandCatalog.variables(command.prompt)
    }
    var values by remember { mutableStateOf(variables.associateWith { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.c2,
        title = {
            Text(
                text = "/" + command.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                variables.forEach { variable ->
                    Text(variable, fontSize = 12.sp, color = palette.c4)
                    BasicTextField(
                        value = values[variable].orEmpty(),
                        onValueChange = { values = values + (variable to it) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = palette.c5),
                        cursorBrush = SolidColor(palette.c5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.c1)
                            .border(1.dp, palette.c3, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onInsert(
                        com.agent.ai.data.chat.SlashCommandCatalog.fill(command.prompt, values)
                    )
                }
            ) {
                Text(stringResource(R.string.command_insert), color = palette.c5)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = palette.c4)
            }
        },
    )
}

@Composable
private fun PrivacyNoticeSheet(onAccept: () -> Unit) {
    val palette = AgentTheme.palette
    AgentSheet(onDismiss = onAccept) {
        Text(
            text = stringResource(R.string.privacy_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.c5,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Text(
            text = stringResource(R.string.privacy_heading),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.c5,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.privacy_body),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = palette.c4,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = stringResource(R.string.privacy_attachments),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = palette.c4,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.privacy_accept),
            modifier = Modifier.padding(top = 14.dp),
            onClick = onAccept,
        )
    }
}

@Composable
private fun LinkConfirmDialog(url: String, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val palette = AgentTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.c2,
        title = {
            Text(
                text = stringResource(R.string.link_open_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.link_open_body, url),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = palette.c4,
            )
        },
        confirmButton = {
            TextButton(onClick = onOpen) {
                Text(stringResource(R.string.link_open), color = palette.c5)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = palette.c4)
            }
        },
    )
}
