package com.agent.ai.data.chat

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.chatDataStore by preferencesDataStore(name = "agent_chat")

/** Everything on the Chat settings page, plus the bits of chat state that are not rows. */
data class ChatSettings(
    val enterToSend: Boolean = true,
    val autoScroll: Boolean = true,
    val reasoningExpanded: Boolean = false,
    val textScale: Float = 1f,
    val autoTitles: Boolean = true,
    val haptics: Boolean = true,
    val memoryEnabled: Boolean = true,
    val systemPrompt: String = ChatRepository.DEFAULT_SYSTEM_PROMPT,
    val voiceLanguage: String = "en-US",
)

/** Small chat settings that are not rows in the database. */
class ChatPreferences(private val context: Context) {

    private object Keys {
        val PrivacyAccepted = booleanPreferencesKey("privacy_accepted")
        val SelectedModel = stringPreferencesKey("selected_model")
        val EnterToSend = booleanPreferencesKey("enter_to_send")
        val AutoScroll = booleanPreferencesKey("auto_scroll")
        val ReasoningExpanded = booleanPreferencesKey("reasoning_expanded")
        val TextScale = floatPreferencesKey("text_scale")
        val AutoTitles = booleanPreferencesKey("auto_titles")
        val Haptics = booleanPreferencesKey("haptics")
        val MemoryEnabled = booleanPreferencesKey("memory_enabled")
        val SystemPrompt = stringPreferencesKey("system_prompt")
        val VoiceLanguage = stringPreferencesKey("voice_language")
        val AttachmentNotices = stringSetPreferencesKey("attachment_notices")
        val AlwaysAllowTools = stringSetPreferencesKey("always_allow_tools")
    }

    /** Tools the user chose Always allow for. The always-ask list is not affected. */
    val alwaysAllowTools: Flow<Set<String>> =
        context.chatDataStore.data.map { it[Keys.AlwaysAllowTools] ?: emptySet() }

    /** Providers the user already agreed to send attachments to. */
    val attachmentNotices: Flow<Set<String>> =
        context.chatDataStore.data.map { it[Keys.AttachmentNotices] ?: emptySet() }

    suspend fun acknowledgeAttachmentNotice(providerId: String) {
        context.chatDataStore.edit { preferences ->
            val current = preferences[Keys.AttachmentNotices] ?: emptySet()
            preferences[Keys.AttachmentNotices] = current + providerId
        }
    }

    val privacyAccepted: Flow<Boolean> =
        context.chatDataStore.data.map { it[Keys.PrivacyAccepted] ?: false }

    val selectedModel: Flow<String> = context.chatDataStore.data.map { preferences ->
        preferences[Keys.SelectedModel] ?: ChatRepository.defaultModelId()
    }

    val settings: Flow<ChatSettings> = context.chatDataStore.data.map { preferences ->
        ChatSettings(
            enterToSend = preferences[Keys.EnterToSend] ?: true,
            autoScroll = preferences[Keys.AutoScroll] ?: true,
            reasoningExpanded = preferences[Keys.ReasoningExpanded] ?: false,
            textScale = preferences[Keys.TextScale] ?: 1f,
            autoTitles = preferences[Keys.AutoTitles] ?: true,
            haptics = preferences[Keys.Haptics] ?: true,
            memoryEnabled = preferences[Keys.MemoryEnabled] ?: true,
            systemPrompt = preferences[Keys.SystemPrompt] ?: ChatRepository.DEFAULT_SYSTEM_PROMPT,
            voiceLanguage = preferences[Keys.VoiceLanguage] ?: "en-US",
        )
    }

    suspend fun setPrivacyAccepted(accepted: Boolean) {
        context.chatDataStore.edit { it[Keys.PrivacyAccepted] = accepted }
    }

    suspend fun setAlwaysAllowTool(tool: String, allowed: Boolean) {
        context.chatDataStore.edit { preferences ->
            val current = preferences[Keys.AlwaysAllowTools] ?: emptySet()
            preferences[Keys.AlwaysAllowTools] = if (allowed) current + tool else current - tool
        }
    }

    suspend fun setSelectedModel(modelId: String) {
        context.chatDataStore.edit { it[Keys.SelectedModel] = modelId }
    }

    suspend fun update(transform: (ChatSettings) -> ChatSettings) {
        context.chatDataStore.edit { preferences ->
            val current = ChatSettings(
                enterToSend = preferences[Keys.EnterToSend] ?: true,
                autoScroll = preferences[Keys.AutoScroll] ?: true,
                reasoningExpanded = preferences[Keys.ReasoningExpanded] ?: false,
                textScale = preferences[Keys.TextScale] ?: 1f,
                autoTitles = preferences[Keys.AutoTitles] ?: true,
                haptics = preferences[Keys.Haptics] ?: true,
                memoryEnabled = preferences[Keys.MemoryEnabled] ?: true,
                systemPrompt = preferences[Keys.SystemPrompt] ?: ChatRepository.DEFAULT_SYSTEM_PROMPT,
                voiceLanguage = preferences[Keys.VoiceLanguage] ?: "en-US",
            )
            val next = transform(current)
            preferences[Keys.EnterToSend] = next.enterToSend
            preferences[Keys.AutoScroll] = next.autoScroll
            preferences[Keys.ReasoningExpanded] = next.reasoningExpanded
            preferences[Keys.TextScale] = next.textScale
            preferences[Keys.AutoTitles] = next.autoTitles
            preferences[Keys.Haptics] = next.haptics
            preferences[Keys.MemoryEnabled] = next.memoryEnabled
            preferences[Keys.SystemPrompt] = next.systemPrompt
            preferences[Keys.VoiceLanguage] = next.voiceLanguage
        }
    }
}
