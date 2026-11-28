package com.agent.ai.chat

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.FileProvider
import com.agent.ai.R
import com.agent.ai.agent.AgentTools
import com.agent.ai.agent.ApprovalPolicy
import com.agent.ai.agent.ProjectSandbox
import com.agent.ai.agent.ToolExecutor
import com.agent.ai.data.chat.AttachmentDraft
import com.agent.ai.data.chat.AttachmentStore
import com.agent.ai.data.chat.ChatDatabase
import com.agent.ai.data.chat.ChatExporter
import com.agent.ai.data.chat.ChatPreferences
import com.agent.ai.data.chat.ChatRepository
import com.agent.ai.data.chat.ChatSettings
import com.agent.ai.data.chat.ConnectivityMonitor
import com.agent.ai.data.chat.DraftPolicy
import com.agent.ai.data.chat.OfflinePolicy
import com.agent.ai.data.chat.ContextBreakdown
import com.agent.ai.data.chat.ContextBudget
import com.agent.ai.data.chat.ConversationEntity
import com.agent.ai.data.chat.MemoryEntity
import com.agent.ai.data.chat.MessageEntity
import com.agent.ai.data.chat.MessagePart
import com.agent.ai.data.chat.MessageParts
import com.agent.ai.data.chat.MessageStatus
import com.agent.ai.data.chat.MessageTree
import com.agent.ai.data.chat.SlashCommandCatalog
import com.agent.ai.data.chat.SlashCommandEntity
import com.agent.ai.data.chat.SlashCommandEntry
import com.agent.ai.data.chat.ToolActivityEntity
import com.agent.ai.data.chat.ToolStatus
import com.agent.ai.data.chat.partsOrText
import com.agent.ai.terminal.LinuxRuntime
import com.agent.ai.providers.ChatRequest
import com.agent.ai.providers.KiloGatewayProvider
import com.agent.ai.providers.ModelCapabilities
import com.agent.ai.providers.ModelInfo
import com.agent.ai.providers.Provider
import com.agent.ai.providers.ProviderError
import com.agent.ai.providers.ProviderErrorKind
import com.agent.ai.providers.ProviderEvent
import com.agent.ai.providers.Role
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** One message as the chat renders it. */
data class ChatMessageUi(
    val id: String,
    val role: Role,
    val parts: List<MessagePart>,
    val text: String,
    val reasoning: String,
    val reasoningSeconds: Int,
    val status: String,
    val modelId: String,
    val errorKind: ProviderErrorKind? = null,
    val errorDetail: String? = null,
    val branch: Pair<Int, Int>? = null,
    val truncated: Boolean = false,
    val summary: Boolean = false,
    val streaming: Boolean = false,
    /** Tool calls the model made in this message, in order. */
    val tools: List<MessagePart.ToolCall> = emptyList(),
) {
    val hasReasoning: Boolean get() = reasoning.isNotBlank()
    val isUser: Boolean get() = role == Role.User
}

/** A delete that can still be taken back, with the snackbar's six second window. */
data class UndoState(
    val conversationId: String?,
    val ids: Set<String>,
    val token: Long,
    val conversationDelete: Boolean = false,
    /** The branch that was open before the delete, so Undo puts it back. */
    val previousLeafId: String? = null,
)

data class ChatUiState(
    val conversationId: String? = null,
    val title: String = "",
    val conversationPrompt: String? = null,
    val messages: List<ChatMessageUi> = emptyList(),
    val conversations: List<ConversationEntity> = emptyList(),
    val draft: String = "",
    val attachments: List<AttachmentDraft> = emptyList(),
    val streaming: Boolean = false,
    val awaitingFirstToken: Boolean = false,
    val retrying: Boolean = false,
    val streamingMessageId: String? = null,
    val streamingText: String = "",
    val streamingReasoning: String = "",
    val reasoningStartedAt: Long = 0L,
    val error: ProviderErrorKind? = null,
    val modelId: String = ChatRepository.defaultModelId(),
    val models: List<ModelInfo> = emptyList(),
    val modelsLoading: Boolean = false,
    val modelsError: ProviderErrorKind? = null,
    val modelsOffline: Boolean = false,
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val context: ContextBreakdown = ContextBreakdown(),
    val contextPercent: Int = 0,
    val contextWindow: Int = 0,
    val reportedUsage: Int? = null,
    val showPrivacyNotice: Boolean = false,
    val online: Boolean = true,
    /** A message saved while offline, waiting to be sent. */
    val pendingMessageId: String? = null,
    val pendingText: String = "",
    val settings: ChatSettings = ChatSettings(),
    val memories: List<MemoryEntity> = emptyList(),
    val commands: List<SlashCommandEntity> = emptyList(),
    val undo: UndoState? = null,
    @StringRes val noticeRes: Int? = null,
    val noticeArg: String? = null,
    val deepThink: Boolean = false,
    val scrollToMessageId: String? = null,
    val titleAuto: Boolean = true,
    val hasCompaction: Boolean = false,
    /** Messages on the visible branch that are older than the loaded page. */
    val hiddenCount: Int = 0,
    /** The clipboard holds an image this provider can read. */
    val clipboardHasImage: Boolean = false,
    /** Set while the one-time attachment notice waits for an answer. */
    val attachmentNoticeProvider: String? = null,
    /** False when the model cannot call tools; [toolsReason] says so in one line. */
    val toolsEnabled: Boolean = false,
    val toolsReason: String? = null,
    /** How many agent steps this request has used. */
    val agentStep: Int = 0,
    /** A tool call is waiting for the user's answer. */
    val agentWaiting: Boolean = false,
    /** The 25 step limit was reached; the user chooses whether to continue. */
    val awaitingContinue: Boolean = false,
) {
    val visionEnabled: Boolean get() = capabilities.vision
    val reasoningEnabled: Boolean get() = capabilities.reasoning
    val almostFull: Boolean get() = ContextBudget.almostFull(contextPercent)

    val slash: List<SlashCommandEntry>
        get() = SlashCommandCatalog.matching(draft, commands)
}

/**
 * Owns the chat: the message tree, streaming, retries, drafts, the offline queue,
 * compaction, memory and export.
 */
class ChatViewModel internal constructor(
    application: Application,
    private val provider: Provider,
) : AndroidViewModel(application) {

    /** The constructor the default view model factory uses. */
    constructor(application: Application) : this(application, KiloGatewayProvider())

    private val app = application
    private val dao = ChatDatabase.get(application).chatDao()
    private val repository = ChatRepository(dao, provider)
    private val preferences = ChatPreferences(application)
    private val connectivity = ConnectivityMonitor(application)
    private val attachments = AttachmentStore(application)
    private val sandbox = ProjectSandbox(LinuxRuntime.workspace(application))
    private val tools = ToolExecutor(sandbox, File(application.filesDir, "backups"))

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var draftJob: Job? = null
    private var undoJob: Job? = null
    private var pendingAutoSent = false
    /** Providers whose attachment notice the user already accepted. */
    private var acknowledgedProviders: Set<String> = emptySet()
    /** Tools the user chose Always allow for. */
    private var alwaysAllowed: Set<String> = emptySet()
    /** Tool calls waiting for an approve or reject tap. */
    private val approvalWaiters = mutableMapOf<String, kotlinx.coroutines.CompletableDeferred<Pair<Boolean, Boolean>>>()
    private var agentStep = 0
    /** How many messages of the open branch are loaded; grows when the user asks. */
    private var pageLimit = PAGE_SIZE

    private val activeConversation = MutableStateFlow<String?>(null)
    // A deleted chat disappears at once and is removed for good when the undo window ends.
    private val hiddenConversations = MutableStateFlow<Set<String>>(emptySet())
    private var allRows: List<MessageEntity> = emptyList()
    private var activeLeafId: String? = null
    private var currentConversationId: String? = null

    private var lastPersist = 0L
    private var reasoningStartedAt = 0L
    private var reportedUsage: Int? = null

    init {
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
                recompute()
            }
        }

        viewModelScope.launch {
            preferences.privacyAccepted.collect { accepted ->
                _state.update { it.copy(showPrivacyNotice = !accepted) }
            }
        }

        viewModelScope.launch {
            preferences.attachmentNotices.collect { providers ->
                acknowledgedProviders = providers
            }
        }

        viewModelScope.launch {
            preferences.alwaysAllowTools.collect { allowed ->
                alwaysAllowed = allowed
            }
        }

        viewModelScope.launch {
            preferences.selectedModel.collect { model ->
                _state.update {
                    if (it.modelId == model) it else it.copy(modelId = model)
                }
                updateCapabilities()
            }
        }

        viewModelScope.launch {
            combine(repository.conversations(), hiddenConversations) { conversations, hidden ->
                conversations.filterNot { it.id in hidden }
            }.collect { conversations ->
                _state.update { it.copy(conversations = conversations) }
            }
        }

        viewModelScope.launch {
            repository.models().collect { cached ->
                _state.update {
                    it.copy(
                        models = cached.map { model ->
                            ModelInfo(
                                id = model.id,
                                name = model.name,
                                free = model.free,
                                contextLength = model.contextLength,
                                description = model.description,
                                capabilities = ModelCapabilities(
                                    vision = model.vision,
                                    reasoning = model.reasoning,
                                    toolCalling = model.toolCalling,
                                ),
                            )
                        },
                    )
                }
                updateCapabilities()
                recompute()
            }
        }

        viewModelScope.launch {
            repository.memories().collect { memories ->
                _state.update { it.copy(memories = memories) }
                recompute()
            }
        }

        viewModelScope.launch {
            repository.commands().collect { commands ->
                _state.update { it.copy(commands = commands) }
            }
        }

        viewModelScope.launch {
            connectivity.online.collect { online ->
                val wasOffline = !_state.value.online
                _state.update { it.copy(online = online) }
                // One automatic send when the connection comes back, never more.
                if (OfflinePolicy.shouldAutoSend(
                        nowOnline = online,
                        wasOnline = !wasOffline,
                        hasPending = _state.value.pendingMessageId != null,
                        alreadyAutoSent = pendingAutoSent,
                    )
                ) {
                    sendPending(automatic = true)
                }
            }
        }

        refreshModels()
    }

    // ----- Composer -------------------------------------------------------------------

    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = text) }
        scheduleDraftSave(text)
    }

    private fun scheduleDraftSave(text: String) {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(DraftPolicy.DelayMs)
            repository.saveDraft(draftKey(), text)
        }
    }

    private fun flushDraft() {
        draftJob?.cancel()
        val text = _state.value.draft
        viewModelScope.launch { repository.saveDraft(draftKey(), text) }
    }

    private fun draftKey(): String = currentConversationId ?: NEW_CHAT_KEY

    fun dictate(text: String) {
        if (text.isBlank()) return
        val current = _state.value.draft
        val joined = if (current.isBlank()) text else "$current $text"
        onDraftChange(joined)
    }

    fun send() {
        val snapshot = _state.value
        if (snapshot.streaming) return
        val text = snapshot.draft.trim()
        val pending = snapshot.attachments
        if (text.isEmpty() && pending.isEmpty()) return
        if (pending.any { it.preparing }) {
            _state.update { it.copy(noticeRes = R.string.notice_attachment_preparing) }
            return
        }
        if (pending.any { it.part != null } && provider.id !in acknowledgedProviders) {
            _state.update { it.copy(attachmentNoticeProvider = provider.id) }
            return
        }
        performSend(text, pending.filter { it.part != null })
    }

    /** Runs after the one-time attachment notice is accepted. */
    fun confirmAttachmentNotice() {
        val id = _state.value.attachmentNoticeProvider ?: return
        acknowledgedProviders = acknowledgedProviders + id
        _state.update { it.copy(attachmentNoticeProvider = null) }
        viewModelScope.launch { preferences.acknowledgeAttachmentNotice(id) }
        val snapshot = _state.value
        performSend(snapshot.draft.trim(), snapshot.attachments.filter { it.part != null })
    }

    fun cancelAttachmentNotice() {
        _state.update { it.copy(attachmentNoticeProvider = null) }
    }

    private fun performSend(text: String, pending: List<AttachmentDraft>) {
        agentStep = 0
        _state.update {
            it.copy(
                draft = "",
                attachments = emptyList(),
                error = null,
                agentStep = 0,
                awaitingContinue = false,
            )
        }
        draftJob?.cancel()
        viewModelScope.launch {
            repository.saveDraft(draftKey(), "")
            val firstAttachment = pending.firstNotNullOfOrNull { it.part }
            val fallbackName = when (firstAttachment) {
                is MessagePart.Image -> firstAttachment.name
                is MessagePart.File -> firstAttachment.name
                else -> "Attachment"
            }
            val conversationId = ensureConversation(text.ifBlank { fallbackName })
            val parts = buildList {
                if (text.isNotEmpty()) add(MessagePart.Text(text))
                pending.forEach { draft -> draft.part?.let { add(it) } }
            }
            if (OfflinePolicy.shouldQueue(_state.value.online)) {
                val id = repository.appendMessage(
                    conversationId,
                    activeLeafId,
                    Role.User,
                    parts,
                    MessageStatus.WAITING,
                    _state.value.modelId,
                )
                activeLeafId = id
                pendingAutoSent = false
                _state.update {
                    it.copy(pendingMessageId = id, pendingText = text.ifBlank { fallbackName })
                }
                recompute()
                return@launch
            }
            val userId = repository.appendMessage(
                conversationId,
                activeLeafId,
                Role.User,
                parts,
                MessageStatus.DONE,
                _state.value.modelId,
            )
            activeLeafId = userId
            recompute()
            startReply(conversationId, userId)
        }
    }

    private suspend fun ensureConversation(firstText: String): String {
        currentConversationId?.let { return it }
        val model = _state.value.modelId
        val id = repository.createConversation(
            modelId = model,
            title = ChatRepository.fallbackTitle(firstText),
            autoTitle = _state.value.settings.autoTitles,
        )
        currentConversationId = id
        activeConversation.value = id
        activeLeafId = null
        _state.update {
            it.copy(
                conversationId = id,
                title = ChatRepository.fallbackTitle(firstText),
                titleAuto = _state.value.settings.autoTitles,
                conversationPrompt = null,
            )
        }
        repository.saveDraft(NEW_CHAT_KEY, "")
        return id
    }

    fun stop() {
        if (!_state.value.streaming) return
        streamJob?.cancel()
        streamJob = null
        provider.cancel()
        approvalWaiters.values.forEach { it.complete(false to false) }
        approvalWaiters.clear()
        _state.update { it.copy(agentWaiting = false) }
        val messageId = _state.value.streamingMessageId
        val text = _state.value.streamingText
        val reasoning = _state.value.streamingReasoning
        _state.update {
            it.copy(
                streaming = false,
                awaitingFirstToken = false,
                retrying = false,
                streamingMessageId = null,
                streamingText = "",
                streamingReasoning = "",
            )
        }
        val conversation = currentConversationId
        viewModelScope.launch {
            if (messageId != null) {
                repository.finish(
                    messageId,
                    partsOf(text, reasoning, reasoningStartedAt),
                    MessageStatus.STOPPED,
                )
                recompute()
            }
            // A call that was waiting for a tap can never finish now.
            if (conversation != null) markInterrupted(conversation)
        }
    }

    // ----- Reply lifecycle ------------------------------------------------------------

    private fun startReply(
        conversationId: String,
        parentId: String?,
        assistantId: String? = null,
        historyLeaf: String? = null,
        continueInstruction: String? = null,
        initialText: String = "",
        initialReasoning: String = "",
    ) {
        val model = _state.value.modelId
        viewModelScope.launch {
            val targetId = assistantId ?: repository.appendMessage(
                conversationId,
                parentId,
                Role.Assistant,
                emptyList(),
                MessageStatus.STREAMING,
                model,
            )
            activeLeafId = targetId
            _state.update {
                it.copy(
                    streaming = true,
                    awaitingFirstToken = true,
                    retrying = false,
                    streamingMessageId = targetId,
                    streamingText = initialText,
                    streamingReasoning = initialReasoning,
                    reasoningStartedAt = 0L,
                    error = null,
                )
            }
            recompute()
            runStream(
                conversationId = conversationId,
                messageId = targetId,
                modelId = model,
                historyLeaf = historyLeaf ?: parentId ?: conversationId,
                initialText = initialText,
                initialReasoning = initialReasoning,
                continueInstruction = continueInstruction,
            )
        }
    }

    fun retry(messageId: String) {
        val conversationId = currentConversationId ?: return
        if (_state.value.streaming) return
        val message = allRows.firstOrNull { it.id == messageId } ?: return
        startReply(
            conversationId = conversationId,
            parentId = message.parentId ?: message.id,
            assistantId = messageId,
            historyLeaf = message.parentId ?: conversationId,
        )
    }

    fun regenerate(messageId: String) {
        if (_state.value.streaming) return
        val conversationId = currentConversationId ?: return
        val message = allRows.firstOrNull { it.id == messageId } ?: return
        startReply(conversationId, message.parentId)
    }

    fun editAndResend(messageId: String, newText: String) {
        if (_state.value.streaming) return
        val conversationId = currentConversationId ?: return
        val message = allRows.firstOrNull { it.id == messageId } ?: return
        val kept = message.partsOrText().filterNot { it is MessagePart.Text }
        viewModelScope.launch {
            val userId = repository.appendMessage(
                conversationId,
                message.parentId,
                Role.User,
                listOf(MessagePart.Text(newText.trim())) + kept,
                MessageStatus.DONE,
                _state.value.modelId,
            )
            activeLeafId = userId
            recompute()
            startReply(conversationId, userId)
        }
    }

    fun continueReply(messageId: String) {
        if (_state.value.streaming) return
        val conversationId = currentConversationId ?: return
        val message = allRows.firstOrNull { it.id == messageId } ?: return
        val parts = message.partsOrText()
        startReply(
            conversationId = conversationId,
            parentId = message.parentId ?: conversationId,
            assistantId = messageId,
            historyLeaf = messageId,
            continueInstruction = CONTINUE_INSTRUCTION,
            initialText = MessageParts.text(parts),
            initialReasoning = MessageParts.reasoning(parts),
        )
    }

    private data class Attempt(
        val text: String,
        val reasoning: String,
        val produced: Boolean,
        val error: ProviderError?,
        val truncated: Boolean,
        val usage: Int?,
        val toolCalls: List<ProviderEvent.ToolCall> = emptyList(),
    )

    private suspend fun runStream(
        conversationId: String,
        messageId: String,
        modelId: String,
        historyLeaf: String,
        initialText: String,
        initialReasoning: String,
        continueInstruction: String?,
    ) {
        var attempt = 0
        var text = initialText
        var reasoning = initialReasoning
        reasoningStartedAt = if (initialReasoning.isEmpty()) 0L else System.currentTimeMillis()

        while (true) {
            val result = attemptStream(
                conversationId, messageId, modelId, historyLeaf,
                text, reasoning, continueInstruction,
            )
            text = result.text
            reasoning = result.reasoning
            result.usage?.let { reportedUsage = it }

            val error = result.error
            if (error == null) {
                if (result.toolCalls.isNotEmpty() && _state.value.toolsEnabled) {
                    finishWithTools(conversationId, messageId, text, reasoning, result.toolCalls)
                    return
                }
                finishReply(messageId, text, reasoning, MessageStatus.DONE, null, result.truncated)
                return
            }

            // Retry only when nothing was shown yet: a partial reply is never restarted.
            if (!result.produced && error.retryable && attempt < MAX_RETRIES) {
                attempt++
                _state.update { it.copy(retrying = true) }
                delay(retryDelayMillis(attempt, error.retryAfterSeconds))
                _state.update { it.copy(retrying = false) }
                continue
            }

            if (text.isBlank() && reasoning.isBlank()) {
                finishReply(messageId, text, reasoning, MessageStatus.FAILED, error, false)
            } else {
                // Some of the reply arrived: keep it and offer Continue.
                finishReply(messageId, text, reasoning, MessageStatus.DONE, null, true)
            }
            return
        }
    }

    private suspend fun attemptStream(
        conversationId: String,
        messageId: String,
        modelId: String,
        historyLeaf: String,
        initialText: String,
        initialReasoning: String,
        continueInstruction: String?,
    ): Attempt {
        var text = initialText
        var reasoning = initialReasoning
        var produced = false
        var failure: ProviderError? = null
        var truncated = false
        var usage: Int? = null
        val toolArguments = sortedMapOf<Int, StringBuilder>()
        val toolMeta = mutableMapOf<Int, Pair<String?, String>>()

        try {
            val capabilities = capabilitiesFor(modelId)
            val window = capabilitiesWindow(modelId)
            val history = repository.history(
                conversationId = conversationId,
                leafId = historyLeaf,
                systemPrompt = systemPrompt(),
                memory = repository.memoryForPrompt(_state.value.settings.memoryEnabled),
                allowImages = capabilities.vision,
                budget = window,
            ).toMutableList()

            if (continueInstruction != null) {
                history += com.agent.ai.providers.WireMessage(Role.User, continueInstruction)
            }

            val toolsOn = capabilities.toolCalling
            val request = ChatRequest(
                model = modelId,
                messages = history,
                maxTokens = 2048,
                temperature = 0.7,
                reasoning = capabilities.reasoning && _state.value.deepThink,
                tools = if (toolsOn && _state.value.toolsEnabled) AgentTools.specs else emptyList(),
            )

            provider.chat(request).collect { event ->
                when (event) {
                    is ProviderEvent.TextDelta -> {
                        produced = true
                        text += event.text
                        _state.update {
                            it.copy(streamingText = text, awaitingFirstToken = false)
                        }
                        maybePersist(messageId, text, reasoning)
                    }

                    is ProviderEvent.ReasoningDelta -> {
                        produced = true
                        if (reasoningStartedAt == 0L) reasoningStartedAt = System.currentTimeMillis()
                        reasoning += event.text
                        _state.update {
                            it.copy(
                                streamingReasoning = reasoning,
                                awaitingFirstToken = false,
                                reasoningStartedAt = reasoningStartedAt,
                            )
                        }
                    }

                    is ProviderEvent.Failed -> failure = event.error
                    is ProviderEvent.Finished -> {
                        truncated = event.truncated
                        event.usage?.let { usage = it.promptTokens }
                    }

                    is ProviderEvent.ToolCall -> {
                        produced = true
                        val buffer = toolArguments.getOrPut(event.index) { StringBuilder() }
                        buffer.append(event.arguments)
                        val previous = toolMeta[event.index]
                        toolMeta[event.index] = Pair(
                            event.id ?: previous?.first,
                            event.name.ifBlank { previous?.second.orEmpty() },
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            failure = failure ?: ProviderError(ProviderErrorKind.Unknown, t.message.orEmpty())
        }

        val calls = toolArguments.map { (index, buffer) ->
            val meta = toolMeta[index]
            ProviderEvent.ToolCall(
                id = meta?.first,
                name = meta?.second.orEmpty(),
                arguments = buffer.toString(),
                index = index,
            )
        }.filter { it.name.isNotBlank() }

        return Attempt(text, reasoning, produced, failure, truncated, usage, calls)
    }

    // ----- Agent loop -----------------------------------------------------------------

    /**
     * Saves the calls as message parts, then runs them one by one. Writing, editing and
     * commands wait for an approve tap; the result goes back to the model as a tool
     * message, and the reply continues until the model stops asking for tools.
     */
    private suspend fun finishWithTools(
        conversationId: String,
        messageId: String,
        text: String,
        reasoning: String,
        calls: List<ProviderEvent.ToolCall>,
    ) {
        streamJob = null
        val seconds = reasoningSeconds()
        val parts = buildList {
            addAll(partsOf(text, reasoning, reasoningStartedAt, seconds))
            calls.forEach { call ->
                add(
                    MessagePart.ToolCall(
                        id = call.id ?: java.util.UUID.randomUUID().toString(),
                        name = call.name,
                        arguments = call.arguments,
                        status = ToolStatus.WAITING,
                    )
                )
            }
        }
        _state.update {
            it.copy(
                streaming = false,
                awaitingFirstToken = false,
                retrying = false,
                streamingMessageId = null,
                streamingText = "",
                streamingReasoning = "",
                error = null,
            )
        }
        reasoningStartedAt = 0L
        repository.finish(messageId, parts, MessageStatus.DONE, null, null, false)
        recompute()
        runToolCalls(conversationId, messageId, calls)
    }

    private fun runToolCalls(
        conversationId: String,
        assistantId: String,
        calls: List<ProviderEvent.ToolCall>,
    ) {
        if (agentStep >= AgentTools.MAX_STEPS) {
            _state.update { it.copy(awaitingContinue = true) }
            return
        }
        agentStep++
        streamJob = viewModelScope.launch {
            _state.update {
                it.copy(streaming = true, awaitingFirstToken = false, agentStep = agentStep)
            }
            val results = mutableListOf<MessagePart.ToolCall>()
            for (call in calls) {
                val id = call.id ?: java.util.UUID.randomUUID().toString()
                val decision = ApprovalPolicy.decide(call.name, call.arguments, alwaysAllowed)
                var approvedBy = "automatic"
                if (decision != com.agent.ai.agent.ApprovalDecision.Automatic) {
                    updateToolPart(assistantId, id) {
                        it.copy(status = ToolStatus.WAITING, output = "")
                    }
                    _state.update { it.copy(agentWaiting = true) }
                    val (approved, always) = awaitApproval(id)
                    _state.update { it.copy(agentWaiting = false) }
                    if (!approved) {
                        val rejected = MessagePart.ToolCall(
                            id = id,
                            name = call.name,
                            arguments = call.arguments,
                            status = ToolStatus.REJECTED,
                            output = app.getString(R.string.tool_rejected_result),
                            approvedBy = "rejected",
                        )
                        updateToolPart(assistantId, id) { rejected }
                        logActivity(rejected)
                        results += rejected
                        continue
                    }
                    approvedBy = when {
                        decision == com.agent.ai.agent.ApprovalDecision.AlwaysAsk -> "once"
                        always -> "always"
                        else -> "once"
                    }
                    if (always && decision != com.agent.ai.agent.ApprovalDecision.AlwaysAsk) {
                        alwaysAllowed = alwaysAllowed + call.name
                        preferences.setAlwaysAllowTool(call.name, true)
                    }
                }
                updateToolPart(assistantId, id) { it.copy(status = ToolStatus.RUNNING, approvedBy = approvedBy) }
                // A running command writes into its card as the lines arrive.
                var lastLive = 0L
                val outcome = tools.run(call.name, call.arguments) { text ->
                    val now = System.currentTimeMillis()
                    if (now - lastLive > 700L) {
                        lastLive = now
                        viewModelScope.launch {
                            updateToolPart(assistantId, id) { it.copy(output = text) }
                        }
                    }
                }
                val finished = MessagePart.ToolCall(
                    id = id,
                    name = call.name,
                    arguments = call.arguments,
                    status = if (outcome.ok) ToolStatus.DONE else ToolStatus.FAILED,
                    output = outcome.output,
                    diff = outcome.diff,
                    backupPath = outcome.backupPath,
                    approvedBy = approvedBy,
                )
                updateToolPart(assistantId, id) { finished }
                logActivity(finished)
                lastLive = 0L
                results += finished
            }
            _state.update { it.copy(streaming = false, agentWaiting = false) }
            val toolMessageId = repository.appendMessage(
                conversationId = conversationId,
                parentId = assistantId,
                role = Role.Tool,
                parts = results,
                status = MessageStatus.DONE,
                modelId = _state.value.modelId,
            )
            activeLeafId = toolMessageId
            recompute()
            startReply(conversationId, toolMessageId)
        }
    }

    private suspend fun awaitApproval(callId: String): Pair<Boolean, Boolean> {
        val deferred = kotlinx.coroutines.CompletableDeferred<Pair<Boolean, Boolean>>()
        approvalWaiters[callId] = deferred
        return try {
            deferred.await()
        } finally {
            approvalWaiters.remove(callId)
        }
    }

    fun approveTool(callId: String, always: Boolean = false) {
        approvalWaiters[callId]?.complete(true to always)
    }

    fun rejectTool(callId: String) {
        approvalWaiters[callId]?.complete(false to false)
    }

    fun continueAgent() {
        if (!_state.value.awaitingContinue) return
        agentStep = 0
        _state.update { it.copy(awaitingContinue = false) }
        val conversationId = currentConversationId ?: return
        startReply(
            conversationId = conversationId,
            parentId = activeLeafId,
            continueInstruction = "Continue the task where you stopped.",
        )
    }

    /** Puts the file back the way it was before an edit. */
    fun revertTool(callId: String) {
        val row = allRows.lastOrNull { message ->
            message.partsOrText().any { it is MessagePart.ToolCall && it.id == callId }
        } ?: return
        val part = row.partsOrText().filterIsInstance<MessagePart.ToolCall>()
            .firstOrNull { it.id == callId } ?: return
        val backup = part.backupPath?.let(::File) ?: return
        val path = (AgentTools.parseObject(part.arguments)["path"] as? kotlinx.serialization.json.JsonPrimitive)
            ?.content
            ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val target = sandbox.resolve(path)
                backup.copyTo(target, overwrite = true)
            }.onSuccess {
                updateToolPart(row.id, callId) {
                    it.copy(status = ToolStatus.DONE, diff = "", output = app.getString(R.string.tool_reverted))
                }
                _state.update { it.copy(noticeRes = R.string.tool_reverted) }
            }.onFailure { error ->
                _state.update { it.copy(noticeRes = R.string.notice_export_failed, noticeArg = error.message) }
            }
        }
    }

    private suspend fun updateToolPart(
        messageId: String,
        callId: String,
        transform: (MessagePart.ToolCall) -> MessagePart.ToolCall,
    ) {
        val row = dao.message(messageId) ?: return
        val parts = row.partsOrText().map { part ->
            if (part is MessagePart.ToolCall && part.id == callId) transform(part) else part
            }
        val encoded = MessageParts.encode(parts)
        dao.updateParts(messageId, encoded, MessageParts.text(parts), row.status)
        allRows = allRows.map { if (it.id == messageId) it.copy(parts = encoded) else it }
        recompute()
    }

    private fun logActivity(part: MessagePart.ToolCall) {
        viewModelScope.launch {
            dao.insertActivity(
                ToolActivityEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    at = System.currentTimeMillis(),
                    tool = part.name,
                    input = part.arguments.take(2_000),
                    result = part.output.take(2_000),
                    approvedBy = part.approvedBy.ifBlank { "automatic" },
                    ok = part.status == ToolStatus.DONE,
                )
            )
        }
    }

    /** A call left waiting by a closed app can never finish, so it is marked failed. */
    private suspend fun markInterrupted(conversationId: String) {
        var changed = false
        for (row in allRows) {
            val parts = row.partsOrText()
            val stale = parts.any {
                it is MessagePart.ToolCall &&
                    (it.status == ToolStatus.WAITING || it.status == ToolStatus.RUNNING)
            }
            if (!stale) continue
            val next = parts.map { part ->
                if (part is MessagePart.ToolCall &&
                    (part.status == ToolStatus.WAITING || part.status == ToolStatus.RUNNING)
                ) {
                    part.copy(status = ToolStatus.FAILED, output = app.getString(R.string.tool_interrupted))
                } else {
                    part
                }
            }
            dao.updateParts(row.id, MessageParts.encode(next), MessageParts.text(next), row.status)
            changed = true
        }
        if (changed) {
            allRows = repository.allMessages(conversationId)
            recompute()
        }
    }

    private suspend fun finishReply(
        messageId: String,
        text: String,
        reasoning: String,
        status: String,
        error: ProviderError?,
        truncated: Boolean,
    ) {
        streamJob = null
        val seconds = reasoningSeconds()
        _state.update {
            it.copy(
                streaming = false,
                awaitingFirstToken = false,
                retrying = false,
                streamingMessageId = null,
                streamingText = "",
                streamingReasoning = "",
                error = if (text.isBlank() && reasoning.isBlank()) error?.kind else null,
            )
        }
        val parts = partsOf(text, reasoning, reasoningStartedAt, seconds)
        repository.finish(
            messageId = messageId,
            parts = parts,
            status = status,
            errorKind = if (status == MessageStatus.FAILED) error?.kind?.name else null,
            detail = error?.detail,
            truncated = truncated,
        )
        reasoningStartedAt = 0L
        recompute()

        val conversationId = currentConversationId
        if (conversationId != null && status != MessageStatus.FAILED) {
            maybeAutoTitle(conversationId)
        }
    }

    private fun partsOf(
        text: String,
        reasoning: String,
        startedAt: Long,
        seconds: Int = 0,
    ): List<MessagePart> = buildList {
        if (reasoning.isNotBlank()) {
            val measured = if (seconds > 0) seconds else if (startedAt > 0L) {
                ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(1)
            } else {
                0
            }
            add(MessagePart.Reasoning(reasoning, measured))
        }
        if (text.isNotBlank()) add(MessagePart.Text(text))
    }

    private fun reasoningSeconds(): Int =
        if (reasoningStartedAt == 0L) 0
        else ((System.currentTimeMillis() - reasoningStartedAt) / 1000L).toInt().coerceAtLeast(1)

    private fun maybeAutoTitle(conversationId: String) {
        val snapshot = _state.value
        if (!snapshot.settings.autoTitles) return
        val conversation = snapshot.conversations.firstOrNull { it.id == conversationId }
        if (conversation != null && !conversation.titleAuto) return
        val firstUser = allRows.firstOrNull {
            it.conversationId == conversationId && it.role == Role.User.wire
        } ?: return
        viewModelScope.launch {
            val title = repository.autoTitle(
                conversationId = conversationId,
                modelId = _state.value.modelId,
                firstUserText = MessageParts.text(firstUser.partsOrText()),
                allowAuto = _state.value.settings.autoTitles,
            )
            if (title != null && conversationId == currentConversationId) {
                _state.update { it.copy(title = title) }
            }
        }
    }

    private fun maybePersist(messageId: String, text: String, reasoning: String) {
        val now = System.currentTimeMillis()
        if (now - lastPersist < 250L) return
        lastPersist = now
        viewModelScope.launch {
            dao.updateParts(
                messageId,
                MessageParts.encode(partsOf(text, reasoning, reasoningStartedAt)),
                MessageParts.text(partsOf(text, reasoning, reasoningStartedAt)),
                MessageStatus.STREAMING,
            )
        }
    }

    // ----- Offline queue --------------------------------------------------------------

    fun sendPending(automatic: Boolean = false) {
        val snapshot = _state.value
        val messageId = snapshot.pendingMessageId ?: return
        val conversationId = currentConversationId ?: return
        if (snapshot.streaming) return
        if (automatic && pendingAutoSent) return
        if (automatic) pendingAutoSent = true
        viewModelScope.launch {
            val message = repository.message(messageId) ?: return@launch
            repository.updateParts(messageId, message.partsOrText(), MessageStatus.DONE)
            _state.update {
                it.copy(pendingMessageId = null, pendingText = "", noticeRes = R.string.notice_sent_after_connect)
            }
            activeLeafId = messageId
            recompute()
            startReply(conversationId, messageId)
        }
    }

    fun cancelPending() {
        val messageId = _state.value.pendingMessageId ?: return
        viewModelScope.launch {
            repository.purge(listOf(messageId))
            _state.update { it.copy(pendingMessageId = null, pendingText = "") }
            activeLeafId = _state.value.messages.lastOrNull()?.id
            recompute()
        }
    }

    // ----- Message actions ------------------------------------------------------------

    fun deleteMessage(messageId: String) {
        val conversationId = currentConversationId ?: return
        val previousLeaf = activeLeafId
        viewModelScope.launch {
            val removed = repository.softDelete(conversationId, messageId)
            val leaf = repository.activeLeafAfterDelete(conversationId, removed)
            activeLeafId = leaf
            repository.setActiveLeaf(conversationId, leaf)
            val token = System.currentTimeMillis()
            _state.update {
                it.copy(undo = UndoState(conversationId, removed, token, previousLeafId = previousLeaf))
            }
            recompute()
            undoJob?.cancel()
            undoJob = viewModelScope.launch {
                delay(UNDO_MS)
                if (_state.value.undo?.token == token) {
                    repository.purge(removed)
                    _state.update { it.copy(undo = null) }
                    recompute()
                }
            }
        }
    }

    fun undoDelete() {
        val undo = _state.value.undo ?: return
        undoJob?.cancel()
        if (undo.conversationDelete) {
            undo.conversationId?.let { hiddenConversations.value = hiddenConversations.value - it }
            _state.update { it.copy(undo = null) }
            return
        }
        viewModelScope.launch {
            repository.restoreMessages(undo.ids)
            val restoredLeaf = undo.previousLeafId ?: activeLeafId
            activeLeafId = restoredLeaf
            undo.conversationId?.let { repository.setActiveLeaf(it, restoredLeaf) }
            _state.update { it.copy(undo = null) }
            recompute()
        }
    }

    fun dismissUndo() {
        val undo = _state.value.undo ?: return
        undoJob?.cancel()
        if (undo.conversationDelete) {
            val id = undo.conversationId
            _state.update { it.copy(undo = null) }
            if (id != null) viewModelScope.launch { purgeConversation(id) }
            return
        }
        viewModelScope.launch {
            repository.purge(undo.ids)
            _state.update { it.copy(undo = null) }
            recompute()
        }
    }

    fun switchBranch(messageId: String, offset: Int) {
        val conversationId = currentConversationId ?: return
        val message = allRows.firstOrNull { it.id == messageId } ?: return
        val versions = MessageTree.siblings(allRows, message)
        if (versions.size < 2) return
        val index = versions.indexOfFirst { it.id == message.id }
        val target = versions[(index + offset).coerceIn(0, versions.size - 1)]
        viewModelScope.launch {
            val leaf = repository.switchToSibling(conversationId, target.id)
            activeLeafId = leaf
            recompute()
        }
    }

    fun saveToMemory(messageId: String) {
        val message = allRows.firstOrNull { it.id == messageId } ?: return
        val text = MessageParts.text(message.partsOrText()).trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            repository.addMemory(text.take(600))
            _state.update { it.copy(noticeRes = R.string.notice_saved_to_memory) }
        }
    }

    // ----- Conversations --------------------------------------------------------------

    fun newChat() {
        if (_state.value.streaming) stop()
        flushDraft()
        currentConversationId = null
        activeConversation.value = null
        activeLeafId = null
        reportedUsage = null
        pageLimit = PAGE_SIZE
        _state.update {
            it.copy(
                conversationId = null,
                title = "",
                conversationPrompt = null,
                messages = emptyList(),
                draft = "",
                attachments = emptyList(),
                error = null,
                context = ContextBreakdown(),
                contextPercent = 0,
                pendingMessageId = null,
                pendingText = "",
                hasCompaction = false,
            )
        }
        loadDraft(NEW_CHAT_KEY)
    }

    fun openConversation(id: String) {
        if (id == currentConversationId) return
        if (_state.value.streaming) stop()
        flushDraft()
        currentConversationId = id
        activeConversation.value = id
        reportedUsage = null
        pageLimit = PAGE_SIZE
        _state.update {
            it.copy(
                conversationId = id,
                title = "",
                conversationPrompt = null,
                messages = emptyList(),
                draft = "",
                attachments = emptyList(),
                error = null,
                pendingMessageId = null,
                pendingText = "",
                scrollToMessageId = null,
            )
        }
        loadDraft(id)
        viewModelScope.launch {
            val conversation = repository.conversation(id) ?: return@launch
            activeLeafId = conversation.activeLeafId
            _state.update {
                it.copy(
                    title = conversation.title,
                    titleAuto = conversation.titleAuto,
                    conversationPrompt = conversation.systemPrompt,
                    hasCompaction = conversation.compaction != null,
                )
            }
            allRows = repository.allMessages(id)
            markInterrupted(id)
            recompute()
            val model = allRows.lastOrNull { it.modelId.isNotBlank() }?.modelId
            if (model != null && model != _state.value.modelId) selectModel(model, persist = false)
        }
    }

    fun openConversationAt(conversationId: String, messageId: String) {
        openConversation(conversationId)
        _state.update { it.copy(scrollToMessageId = messageId) }
    }

    fun consumeScrollTo() {
        _state.update { it.copy(scrollToMessageId = null) }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch {
            repository.renameByUser(id, title)
            if (id == currentConversationId) {
                _state.update {
                    it.copy(title = title.ifBlank { ChatRepository.Untitled }, titleAuto = false)
                }
            }
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }

    /** The chat vanishes at once; the rows and files go when the undo window ends. */
    fun deleteConversation(id: String) {
        hiddenConversations.value = hiddenConversations.value + id
        if (id == currentConversationId) newChat()
        val token = System.currentTimeMillis()
        _state.update { it.copy(undo = UndoState(id, emptySet(), token, conversationDelete = true)) }
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            delay(UNDO_MS)
            if (_state.value.undo?.token == token) {
                purgeConversation(id)
                _state.update { it.copy(undo = null) }
            }
        }
    }

    private suspend fun purgeConversation(id: String) {
        val parts = repository.deleteConversation(id)
        withContext(Dispatchers.IO) { attachments.deleteFilesFor(parts) }
        hiddenConversations.value = hiddenConversations.value - id
    }

    fun setConversationPrompt(prompt: String?) {
        val id = currentConversationId ?: return
        viewModelScope.launch {
            repository.setConversationPrompt(id, prompt)
            _state.update { it.copy(conversationPrompt = prompt?.takeIf { p -> p.isNotBlank() }) }
            recompute()
        }
    }

    // ----- Models and notices ---------------------------------------------------------

    fun selectModel(modelId: String, persist: Boolean = true) {
        _state.update { it.copy(modelId = modelId, modelsError = null) }
        updateCapabilities()
        if (persist) {
            viewModelScope.launch {
                preferences.setSelectedModel(modelId)
                currentConversationId?.let { repository.setModel(it, modelId) }
            }
        }
    }

    /** The model switcher applies from the next message; nothing is re-sent. */
    fun setDeepThink(enabled: Boolean) {
        if (enabled && !_state.value.reasoningEnabled) return
        _state.update { it.copy(deepThink = enabled) }
    }

    fun refreshModels() {
        if (_state.value.modelsLoading) return
        _state.update { it.copy(modelsLoading = true, modelsError = null) }
        viewModelScope.launch {
            val result = repository.refreshModels()
            val cached = repository.cachedModels()
            _state.update {
                it.copy(
                    modelsLoading = false,
                    modelsError = (result.exceptionOrNull() as? com.agent.ai.providers.ProviderException)
                        ?.error?.kind
                        ?: if (result.isFailure && cached.isEmpty()) ProviderErrorKind.NoInternet else null,
                    modelsOffline = result.isFailure && cached.isNotEmpty(),
                )
            }
        }
    }

    fun acceptPrivacyNotice() {
        _state.update { it.copy(showPrivacyNotice = false) }
        viewModelScope.launch { preferences.setPrivacyAccepted(true) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun consumeNotice() {
        _state.update { it.copy(noticeRes = null, noticeArg = null) }
    }

    fun updateSettings(transform: (ChatSettings) -> ChatSettings) {
        viewModelScope.launch {
            preferences.update(transform)
            recompute()
        }
    }

    // ----- Attachments ----------------------------------------------------------------

    fun addAttachment(uri: Uri) {
        val snapshot = _state.value
        if (snapshot.attachments.size >= AttachmentStore.MAX_ATTACHMENTS) {
            _state.update { it.copy(noticeRes = R.string.notice_attachment_limit) }
            return
        }
        val mime = app.contentResolver.getType(uri).orEmpty()
        if (mime.startsWith("image/") && !snapshot.visionEnabled) {
            _state.update { it.copy(noticeRes = R.string.notice_no_vision) }
            return
        }
        val name = runCatching { uri.lastPathSegment.orEmpty() }.getOrDefault("") 
            .substringAfterLast('/').ifBlank { app.getString(R.string.attachment_default_name) }
        val id = java.util.UUID.randomUUID().toString()
        _state.update {
            it.copy(
                attachments = it.attachments + AttachmentDraft(
                    id = id,
                    name = name,
                    mime = mime,
                    preparing = true,
                ),
            )
        }
        viewModelScope.launch {
            val result = when {
                mime.startsWith("image/") -> attachments.importImage(uri)
                mime == "application/pdf" -> attachments.importPdf(uri)
                else -> attachments.importText(uri)
            }
            result.onSuccess { part ->
                if (part is MessagePart.File && part.size > AttachmentStore.MAX_ATTACHMENT_BYTES) {
                    attachments.deleteFilesFor(listOf(part))
                    replaceDraft(id) {
                        it.copy(preparing = false, error = app.getString(R.string.notice_attachment_too_large))
                    }
                    _state.update { it.copy(noticeRes = R.string.notice_attachment_too_large) }
                    return@onSuccess
                }
                val preparedName = when (part) {
                    is MessagePart.Image -> part.name
                    is MessagePart.File -> part.name
                    else -> name
                }
                replaceDraft(id) {
                    it.copy(
                        part = part,
                        preparing = false,
                        name = preparedName,
                        mime = when (part) {
                            is MessagePart.Image -> part.mime
                            is MessagePart.File -> part.mime
                            else -> it.mime
                        },
                    )
                }
            }.onFailure { error ->
                replaceDraft(id) {
                    it.copy(
                        preparing = false,
                        error = error.message ?: app.getString(R.string.attachment_prepare_failed),
                    )
                }
                _state.update {
                    it.copy(noticeRes = R.string.notice_attachment_failed, noticeArg = error.message)
                }
            }
        }
    }

    private fun replaceDraft(id: String, transform: (AttachmentDraft) -> AttachmentDraft) {
        _state.update { current ->
            current.copy(
                attachments = current.attachments.map { if (it.id == id) transform(it) else it },
            )
        }
    }

    fun removeAttachment(id: String) {
        val draft = _state.value.attachments.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(attachments = it.attachments.filterNot { it.id == id }) }
        draft.part?.let { part ->
            viewModelScope.launch(Dispatchers.IO) { attachments.deleteFilesFor(listOf(part)) }
        }
    }

    /** The reason a failed attachment shows when it is tapped. */
    fun showAttachmentError(id: String) {
        val draft = _state.value.attachments.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                noticeRes = R.string.notice_attachment_failed,
                noticeArg = draft.error,
            )
        }
    }

    // ----- Clipboard and pages -------------------------------------------------------

    /** Checks the clipboard for an image the current model can read. */
    fun refreshClipboard() {
        val hasImage = runCatching { clipboardImageUri() != null }.getOrDefault(false) &&
            _state.value.visionEnabled &&
            _state.value.attachments.size < AttachmentStore.MAX_ATTACHMENTS
        _state.update { if (it.clipboardHasImage == hasImage) it else it.copy(clipboardHasImage = hasImage) }
    }

    /** Pastes an image from the clipboard. A text clipboard is left to the text field. */
    fun pasteImage(): Boolean {
        val uri = clipboardImageUri() ?: return false
        if (!_state.value.visionEnabled) {
            _state.update { it.copy(noticeRes = R.string.notice_no_vision) }
            return false
        }
        addAttachment(uri)
        return true
    }

    private fun clipboardImageUri(): Uri? {
        val manager = app.getSystemService(android.content.ClipboardManager::class.java) ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val item = clip.getItemAt(0)
        val uri = item.uri ?: return null
        val mime = clip.description?.getMimeType(0)
            ?: app.contentResolver.getType(uri)
            ?: return null
        return if (mime.startsWith("image/")) uri else null
    }

    /** Loads the next older page of the conversation. */
    fun loadEarlier() {
        pageLimit += PAGE_SIZE
        recompute()
    }

    // ----- Context and compaction -----------------------------------------------------

    fun compact() {
        val conversationId = currentConversationId ?: return
        if (_state.value.streaming) return
        viewModelScope.launch {
            _state.update { it.copy(noticeRes = R.string.notice_compacting) }
            val done = repository.compact(
                conversationId = conversationId,
                modelId = _state.value.modelId,
                keepRecentTokens = COMPACT_KEEP_TOKENS,
            )
            _state.update {
                it.copy(
                    noticeRes = if (done) R.string.notice_compacted else R.string.notice_compact_failed,
                    hasCompaction = it.hasCompaction || done,
                )
            }
            val conversation = repository.conversation(conversationId)
            activeLeafId = conversation?.activeLeafId
            allRows = repository.allMessages(conversationId)
            recompute()
        }
    }

    fun restoreCompaction() {
        val conversationId = currentConversationId ?: return
        viewModelScope.launch {
            repository.restoreCompaction(conversationId)
            val conversation = repository.conversation(conversationId)
            activeLeafId = conversation?.activeLeafId
            allRows = repository.allMessages(conversationId)
            _state.update {
                it.copy(noticeRes = R.string.notice_compaction_restored, hasCompaction = false)
            }
            recompute()
        }
    }

    // ----- Memory and commands --------------------------------------------------------

    fun addMemory(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repository.addMemory(text) }
    }

    fun updateMemory(id: String, text: String) {
        viewModelScope.launch {
            if (text.isBlank()) repository.deleteMemory(id) else repository.updateMemory(id, text)
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { repository.deleteMemory(id) }
    }

    fun clearMemories() {
        viewModelScope.launch { repository.clearMemories() }
    }

    fun saveCommand(id: String?, name: String, prompt: String, description: String) {
        if (name.isBlank() || prompt.isBlank()) return
        viewModelScope.launch { repository.saveCommand(id, name, prompt, description) }
    }

    fun deleteCommand(id: String) {
        viewModelScope.launch { repository.deleteCommand(id) }
    }

    // ----- Search and export ----------------------------------------------------------

    suspend fun search(query: String) = repository.search(query)

    fun exportConversation(conversationId: String, allBranches: Boolean, asJson: Boolean) {
        viewModelScope.launch {
            runCatching {
                val conversation = repository.conversation(conversationId)
                    ?: error("Conversation not found")
                val all = repository.allMessages(conversationId)
                val body = if (asJson) {
                    ChatExporter.json(conversation, all)
                } else {
                    ChatExporter.markdown(conversation, all, allBranches)
                }
                val extension = if (asJson) "json" else "md"
                val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                val safe = conversation.title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").take(40)
                    .ifBlank { "conversation" }
                val file = File(dir, "$safe.$extension")
                withContext(Dispatchers.IO) { file.writeText(body) }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (asJson) "application/json" else "text/markdown"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                app.startActivity(
                    Intent.createChooser(intent, app.getString(R.string.export_share_title))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                _state.update { it.copy(noticeRes = R.string.notice_exported) }
            }.onFailure { error ->
                _state.update {
                    it.copy(noticeRes = R.string.notice_export_failed, noticeArg = error.message)
                }
            }
        }
    }

    /** Leaving the chat screen cancels the request but keeps the text received so far. */
    fun onScreenLeft() {
        flushDraft()
        if (_state.value.streaming) stop()
    }

    override fun onCleared() {
        provider.cancel()
        super.onCleared()
    }

    // ----- Internals ------------------------------------------------------------------

    private fun loadDraft(key: String) {
        viewModelScope.launch {
            val text = repository.draftOnce(key).orEmpty()
            if (_state.value.draft.isEmpty()) _state.update { it.copy(draft = text) }
        }
    }

    private fun systemPrompt(): String {
        val settings = _state.value
        val global = settings.settings.systemPrompt
        val own = settings.conversationPrompt
        return listOfNotNull(global, own)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .ifBlank { ChatRepository.DEFAULT_SYSTEM_PROMPT }
    }

    private fun capabilitiesFor(modelId: String): ModelCapabilities =
        _state.value.models.firstOrNull { it.id == modelId }?.capabilities ?: ModelCapabilities()

    private fun capabilitiesWindow(modelId: String): Int {
        val reported = _state.value.models.firstOrNull { it.id == modelId }?.contextLength ?: 0
        return if (reported > 0) reported else ContextBudget.FALLBACK_WINDOW
    }

    private fun updateCapabilities() {
        val modelId = _state.value.modelId
        val capabilities = capabilitiesFor(modelId)
        _state.update {
            it.copy(
                capabilities = capabilities,
                deepThink = it.deepThink && capabilities.reasoning,
                contextWindow = capabilitiesWindow(modelId),
                toolsEnabled = capabilities.toolCalling,
                toolsReason = if (capabilities.toolCalling) {
                    null
                } else {
                    app.getString(R.string.tools_model_unsupported)
                },
            )
        }
    }

    /** Recomputes the visible branch, the branch counters and the context numbers. */
    private fun recompute() {
        val snapshot = _state.value
        if (currentConversationId == null) {
            _state.update {
                it.copy(
                    messages = emptyList(),
                    context = ContextBreakdown(),
                    contextPercent = 0,
                )
            }
            return
        }
        val visible = MessageTree.path(allRows, activeLeafId)
        val memoryTexts = if (snapshot.settings.memoryEnabled) snapshot.memories.map { it.text } else emptyList()
        val breakdown = ContextBudget.breakdown(systemPrompt(), memoryTexts, visible)
        val window = capabilitiesWindow(snapshot.modelId)
        val used = maxOf(breakdown.total, snapshot.reportedUsage ?: 0)
        // Long conversations load a page at a time; the model still gets the whole branch.
        val shown = if (visible.size > pageLimit) visible.takeLast(pageLimit) else visible
        val hidden = visible.size - shown.size
        // A tool result row exists for the model, not for the screen: the cards above
        // already show it, so the result is never printed twice.
        val ui = shown.mapNotNull { message ->
            if (message.role == Role.Tool.wire) return@mapNotNull null
            val parts = message.partsOrText()
            val branch = MessageTree.branchPosition(allRows, message)
            ChatMessageUi(
                id = message.id,
                role = when (message.role) {
                    Role.Assistant.wire -> Role.Assistant
                    Role.System.wire -> Role.System
                    else -> Role.User
                },
                parts = parts,
                text = MessageParts.text(parts),
                reasoning = MessageParts.reasoning(parts),
                reasoningSeconds = parts.filterIsInstance<MessagePart.Reasoning>()
                    .firstOrNull()?.seconds ?: 0,
                status = message.status,
                modelId = message.modelId,
                tools = MessageParts.tools(parts),
                errorKind = ChatRepository.kindOf(message.errorKind),
                errorDetail = message.errorDetail,
                branch = branch,
                truncated = message.truncated,
                summary = message.summary,
                streaming = message.id == snapshot.streamingMessageId,
            )
        }
        _state.update {
            it.copy(
                messages = ui,
                hiddenCount = hidden,
                context = breakdown,
                contextPercent = ContextBudget.percent(used, window),
                contextWindow = window,
                reportedUsage = snapshot.reportedUsage,
                conversations = it.conversations.map { conversation ->
                    conversation
                },
            )
        }
    }

    private fun retryDelayMillis(attempt: Int, retryAfterSeconds: Int?): Long {
        val fromServer = (retryAfterSeconds ?: 0).toLong() * 1000L
        val backoff = 1_000L shl attempt // 2s, then 4s
        return maxOf(fromServer, backoff).coerceAtMost(8_000L)
    }

    private fun updateConversationRows(rows: List<MessageEntity>) {
        allRows = rows
        recompute()
    }

    init {
        // The message flow is rebound whenever the open conversation changes.
        viewModelScope.launch {
            activeConversation.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else repository.messages(id)
            }.collect { rows -> updateConversationRows(rows) }
        }
    }

    companion object {
        const val NEW_CHAT_KEY = "#new"
        const val UNDO_MS = 6_000L
        const val PAGE_SIZE = 40
        const val DRAFT_DELAY_MS = 500L
        const val MAX_RETRIES = 2
        const val COMPACT_KEEP_TOKENS = 2_500

        const val CONTINUE_INSTRUCTION =
            "Continue the reply from exactly where it stopped. Do not repeat anything already written."
    }
}
