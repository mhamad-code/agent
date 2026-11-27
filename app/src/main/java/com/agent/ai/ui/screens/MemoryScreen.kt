package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.agent.ai.chat.ChatViewModel
import com.agent.ai.data.chat.MemoryEntity
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.DashedButton
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.theme.AgentTheme

/**
 * Local memory: the user adds, edits and deletes items, and turns memory off. Nothing is
 * written here automatically at this stage.
 */
@Composable
fun MemoryScreen(
    chat: ChatViewModel,
    onBack: () -> Unit,
) {
    val palette = AgentTheme.palette
    val state by chat.state.collectAsState()
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    AgentScreen(scrollable = true, padding = 16.dp, gap = 10.dp) {
        AppHeader(
            title = stringResource(R.string.memory_title),
            subtitle = stringResource(R.string.memory_subtitle),
            onBack = onBack,
            trailing = {
                AppSwitch(
                    checked = state.settings.memoryEnabled,
                    onCheckedChange = { enabled ->
                        chat.updateSettings { it.copy(memoryEnabled = enabled) }
                    },
                )
            },
        )

        if (!state.settings.memoryEnabled) {
            Text(
                text = stringResource(R.string.memory_off),
                fontSize = 12.sp,
                color = palette.c4,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (state.memories.isEmpty()) {
            Text(
                text = stringResource(R.string.memory_empty),
                fontSize = 12.sp,
                color = palette.c4,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            )
        }

        state.memories.forEach { memory ->
            Panel(
                padding = PaddingValues(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                onClick = { editing = memory },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = memory.text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = palette.c5,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                onClickLabel = stringResource(R.string.memory_delete),
                                onClick = { chat.deleteMemory(memory.id) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(AgentIcons.Trash, size = 16.dp, tint = palette.c4)
                    }
                }
            }
        }

        DashedButton(
            title = stringResource(R.string.memory_add),
            icon = AgentIcons.Plus,
            height = 48.dp,
            onClick = { adding = true },
        )

        if (state.memories.isNotEmpty()) {
            OutlineButton(
                text = stringResource(R.string.memory_clear),
                onClick = { chat.clearMemories() },
            )
        }
    }

    if (adding || editing != null) {
        val target = editing
        val paletteColor = palette
        var text by remember(target) { mutableStateOf(target?.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = {
                adding = false
                editing = null
            },
            containerColor = paletteColor.c2,
            title = {
                Text(
                    text = stringResource(
                        if (target == null) R.string.memory_new_title else R.string.memory_edit_title
                    ),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = paletteColor.c5,
                )
            },
            text = {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = paletteColor.c5),
                    cursorBrush = SolidColor(paletteColor.c5),
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(paletteColor.c1)
                        .border(1.dp, paletteColor.c3, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (target == null) chat.addMemory(text) else chat.updateMemory(target.id, text)
                        adding = false
                        editing = null
                    },
                ) {
                    Text(stringResource(R.string.memory_save), color = paletteColor.c5)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        adding = false
                        editing = null
                    }
                ) {
                    Text(stringResource(R.string.cancel), color = paletteColor.c4)
                }
            },
        )
    }
}
