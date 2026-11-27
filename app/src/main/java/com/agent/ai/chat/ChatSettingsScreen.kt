package com.agent.ai.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.R
import com.agent.ai.data.chat.ChatSettings
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.ListDivider
import com.agent.ai.ui.components.ListGroup
import com.agent.ai.ui.components.SegmentedControl
import com.agent.ai.ui.components.SettingRow
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.Dimens

/** Chat settings, in the same list style and spacing as the rest of Settings. */
@Composable
fun ChatSettingsScreen(
    settings: ChatSettings,
    onUpdate: ((ChatSettings) -> ChatSettings) -> Unit,
    onBack: () -> Unit,
) {
    val palette = AgentTheme.palette
    var editingPrompt by remember { mutableStateOf(false) }
    var prompt by remember(settings.systemPrompt) { mutableStateOf(settings.systemPrompt) }
    val sizes = listOf(
        stringResource(R.string.text_size_small),
        stringResource(R.string.text_size_normal),
        stringResource(R.string.text_size_large),
    )
    val sizeIndex = when {
        settings.textScale < 0.97f -> 0
        settings.textScale > 1.07f -> 2
        else -> 1
    }

    AgentScreen(scrollable = true, padding = Dimens.screenPaddingWide, gap = Dimens.listTop) {
        AppHeader(
            title = stringResource(R.string.chat_settings),
            subtitle = stringResource(R.string.chat_settings_subtitle),
            onBack = onBack,
        )

        ListGroup(title = stringResource(R.string.chat_settings)) {
            SettingRow(
                icon = AgentIcons.ArrowUp,
                title = stringResource(R.string.setting_enter_send),
                subtitle = stringResource(R.string.setting_enter_send_sub),
                index = 0,
                trailing = {
                    AppSwitch(
                        checked = settings.enterToSend,
                        onCheckedChange = { value ->
                            onUpdate { it.copy(enterToSend = value) }
                        },
                    )
                },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Download,
                title = stringResource(R.string.setting_auto_scroll),
                subtitle = stringResource(R.string.setting_auto_scroll_sub),
                index = 1,
                trailing = {
                    AppSwitch(
                        checked = settings.autoScroll,
                        onCheckedChange = { value ->
                            onUpdate { it.copy(autoScroll = value) }
                        },
                    )
                },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Brain,
                title = stringResource(R.string.setting_reasoning_expanded),
                subtitle = stringResource(R.string.setting_reasoning_expanded_sub),
                index = 2,
                trailing = {
                    AppSwitch(
                        checked = settings.reasoningExpanded,
                        onCheckedChange = { value ->
                            onUpdate { it.copy(reasoningExpanded = value) }
                        },
                    )
                },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Message,
                title = stringResource(R.string.setting_auto_titles),
                subtitle = stringResource(R.string.setting_auto_titles_sub),
                index = 3,
                trailing = {
                    AppSwitch(
                        checked = settings.autoTitles,
                        onCheckedChange = { value ->
                            onUpdate { it.copy(autoTitles = value) }
                        },
                    )
                },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Smartphone,
                title = stringResource(R.string.setting_haptics),
                subtitle = stringResource(R.string.setting_haptics_sub),
                index = 4,
                trailing = {
                    AppSwitch(
                        checked = settings.haptics,
                        onCheckedChange = { value ->
                            onUpdate { it.copy(haptics = value) }
                        },
                    )
                },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Brain,
                title = stringResource(R.string.setting_memory),
                subtitle = stringResource(R.string.setting_memory_sub),
                index = 5,
                trailing = {
                    AppSwitch(
                        checked = settings.memoryEnabled,
                        onCheckedChange = { value ->
                            onUpdate { it.copy(memoryEnabled = value) }
                        },
                    )
                },
            )
        }

        Text(
            text = stringResource(R.string.setting_text_size).uppercase(),
            fontSize = 11.sp,
            letterSpacing = 1.6.sp,
            color = palette.c4,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
        )
        SegmentedControl(
            options = sizes,
            selectedIndex = sizeIndex,
            onSelect = { index ->
                onUpdate { it.copy(textScale = listOf(0.9f, 1f, 1.15f)[index]) }
            },
        )

        ListGroup(title = stringResource(R.string.setting_system_prompt)) {
            SettingRow(
                icon = AgentIcons.FileText,
                title = stringResource(R.string.setting_system_prompt),
                subtitle = if (settings.systemPrompt.length > 70) {
                    settings.systemPrompt.take(70) + "…"
                } else {
                    settings.systemPrompt
                },
                index = 6,
                onClick = { editingPrompt = true },
            )
        }

        ListGroup(title = stringResource(R.string.setting_voice_language)) {
            SettingRow(
                icon = AgentIcons.Globe,
                title = stringResource(R.string.voice_language_en),
                index = 7,
                onClick = { onUpdate { it.copy(voiceLanguage = "en-US") } },
                trailing = {
                    if (settings.voiceLanguage == "en-US") SelectedBadge()
                },
            )
            ListDivider()
            SettingRow(
                icon = AgentIcons.Globe,
                title = stringResource(R.string.voice_language_ar),
                index = 8,
                onClick = { onUpdate { it.copy(voiceLanguage = "ar-SA") } },
                trailing = {
                    if (settings.voiceLanguage == "ar-SA") SelectedBadge()
                },
            )
        }
    }

    if (editingPrompt) {
        AlertDialog(
            onDismissRequest = { editingPrompt = false },
            containerColor = palette.c2,
            title = {
                Text(
                    text = stringResource(R.string.setting_system_prompt),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                )
            },
            text = {
                BasicTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    textStyle = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = palette.c5),
                    cursorBrush = SolidColor(palette.c5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.c1)
                        .border(1.dp, palette.c3, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdate { it.copy(systemPrompt = prompt) }
                        editingPrompt = false
                    }
                ) {
                    Text(stringResource(R.string.chat_rename_save), color = palette.c5)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPrompt = false }) {
                    Text(stringResource(R.string.cancel), color = palette.c4)
                }
            },
        )
    }
}

/** A tiny check mark used by the selected language row. */
@Composable
internal fun SelectedBadge() {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("✓", fontSize = 11.sp, color = palette.c5)
    }
}
