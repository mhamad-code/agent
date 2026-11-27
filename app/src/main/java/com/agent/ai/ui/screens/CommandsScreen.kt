package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.agent.ai.data.chat.SlashCommandCatalog
import com.agent.ai.data.chat.SlashCommandEntity
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.Chip
import com.agent.ai.ui.components.DashedButton
import com.agent.ai.ui.components.MonoText
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.SectionLabel
import com.agent.ai.ui.components.SmallButton
import com.agent.ai.ui.theme.AgentTheme

/** The built-in commands, plus the ones the user writes. */
@Composable
fun CommandsScreen(
    chat: ChatViewModel,
    onBack: () -> Unit,
) {
    val palette = AgentTheme.palette
    val state by chat.state.collectAsState()
    var editing by remember { mutableStateOf<SlashCommandEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    AgentScreen(scrollable = true, padding = 16.dp, gap = 10.dp) {
        AppHeader(
            title = stringResource(R.string.commands_title),
            subtitle = stringResource(R.string.commands_subtitle),
            onBack = onBack,
            trailing = {
                SmallButton(stringResource(R.string.command_add)) {
                    editing = null
                    adding = true
                }
            },
        )

        SectionLabel(stringResource(R.string.commands_built_in))
        Panel {
            SlashCommandCatalog.commands.forEachIndexed { index, command ->
                if (index > 0) PanelDivider()
                PanelRow {
                    Column(Modifier.weight(1f)) {
                        MonoText(command.name, color = palette.c5)
                        Text(
                            text = stringResource(command.description),
                            fontSize = 12.sp,
                            color = palette.c4,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (command.enabled) {
                        Chip(stringResource(R.string.commands_built_in))
                    } else {
                        Chip(stringResource(R.string.command_needs_tools))
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.commands_yours))
        if (state.commands.isEmpty()) {
            Text(
                text = stringResource(R.string.commands_empty),
                fontSize = 12.sp,
                color = palette.c4,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        } else {
            Panel {
                state.commands.forEachIndexed { index, command ->
                    if (index > 0) PanelDivider()
                    PanelRow(
                        onClick = {
                            editing = command
                            adding = false
                        },
                    ) {
                        Column(Modifier.weight(1f)) {
                            MonoText("/" + command.name, color = palette.c5)
                            Text(
                                text = command.description.ifBlank {
                                    command.prompt.take(60)
                                },
                                fontSize = 12.sp,
                                color = palette.c4,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Chip(stringResource(R.string.commands_yours))
                    }
                }
            }
        }

        DashedButton(
            title = stringResource(R.string.command_add),
            icon = AgentIcons.Plus,
            height = 48.dp,
            onClick = {
                editing = null
                adding = true
            },
        )
    }

    if (adding || editing != null) {
        CommandDialog(
            command = editing,
            onSave = { name, prompt, description ->
                chat.saveCommand(editing?.id, name, prompt, description)
                adding = false
                editing = null
            },
            onDelete = editing?.let { command ->
                {
                    chat.deleteCommand(command.id)
                    editing = null
                }
            },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
}

@Composable
private fun CommandDialog(
    command: SlashCommandEntity?,
    onSave: (String, String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val palette = AgentTheme.palette
    var name by remember(command) { mutableStateOf(command?.name.orEmpty()) }
    var prompt by remember(command) { mutableStateOf(command?.prompt.orEmpty()) }
    var description by remember(command) { mutableStateOf(command?.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.c2,
        title = {
            Text(
                text = if (command == null) {
                    stringResource(R.string.command_add)
                } else {
                    "/" + command.name
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Field(
                    label = stringResource(R.string.command_name),
                    value = name,
                    placeholder = stringResource(R.string.command_name_hint),
                    onChange = { name = it },
                )
                Field(
                    label = stringResource(R.string.command_description),
                    value = description,
                    placeholder = "",
                    onChange = { description = it },
                )
                Field(
                    label = stringResource(R.string.command_prompt),
                    value = prompt,
                    placeholder = stringResource(R.string.command_prompt_hint),
                    onChange = { prompt = it },
                    multiline = true,
                )
                Text(
                    text = stringResource(R.string.command_prompt_hint),
                    fontSize = 11.sp,
                    color = palette.c4,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, prompt, description) },
                enabled = name.isNotBlank() && prompt.isNotBlank(),
            ) {
                Text(stringResource(R.string.command_save), color = palette.c5)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = palette.c4)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = palette.c4)
                }
            }
        },
    )
}

/** A labelled text field inside a dialog, with the app's own styling. */
@Composable
fun Field(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    multiline: Boolean = false,
) {
    val palette = AgentTheme.palette
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(label, fontSize = 11.sp, color = palette.c4)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = palette.c5),
            cursorBrush = SolidColor(palette.c5),
            singleLine = !multiline,
            maxLines = if (multiline) 6 else 1,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(top = 4.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(palette.c1)
                .border(1.dp, palette.c3, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .padding(10.dp),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontSize = 13.sp, color = palette.c4.copy(alpha = 0.7f))
                }
                inner()
            },
        )
    }
}
