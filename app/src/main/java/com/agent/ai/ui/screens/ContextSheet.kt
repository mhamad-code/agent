package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.R
import com.agent.ai.chat.ChatUiState
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentSheet
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.PrimaryButton
import com.agent.ai.ui.theme.AgentTheme

/** The numbers behind the context ring, split by what takes the space. */
@Composable
fun ContextSheet(
    state: ChatUiState,
    onCompact: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AgentTheme.palette
    val breakdown = state.context
    val window = state.contextWindow

    AgentSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.context_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                )
                Text(
                    text = stringResource(
                        R.string.context_used,
                        breakdown.total,
                        compact(window),
                    ) + " · " + state.contextPercent + "%",
                    fontSize = 12.sp,
                    color = palette.c4,
                )
            }
            Text(
                text = "${state.contextPercent}%",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.c1)
                .border(1.dp, palette.c3, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PartRow(stringResource(R.string.context_system), breakdown.system, breakdown.total)
            PartRow(stringResource(R.string.context_memory), breakdown.memory, breakdown.total)
            PartRow(stringResource(R.string.context_messages), breakdown.messages, breakdown.total)
            PartRow(stringResource(R.string.context_attachments), breakdown.attachments, breakdown.total)
        }

        Text(
            text = stringResource(R.string.context_estimated),
            fontSize = 11.sp,
            color = palette.c4,
        )

        if (state.almostFull) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.c2)
                    .border(1.dp, palette.c4, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LineIcon(AgentIcons.Info, size = 18.dp, tint = palette.c5)
                Column {
                    Text(
                        text = stringResource(R.string.context_almost_full),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                    )
                    Text(
                        text = stringResource(R.string.context_almost_full_body),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = palette.c4,
                    )
                }
            }
        }

        PrimaryButton(
            text = stringResource(R.string.context_compact),
            onClick = {
                onCompact()
                onDismiss()
            },
        )

        if (state.hasCompaction) {
            OutlineButton(
                text = stringResource(R.string.context_restore),
                onClick = {
                    onRestore()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun PartRow(label: String, value: Int, total: Int) {
    val palette = AgentTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = palette.c5,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$value",
            fontSize = 12.sp,
            color = palette.c4,
        )
        Box(
            Modifier
                .padding(start = 10.dp)
                .height(6.dp)
                .weight(0.7f)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.c3)
        ) {
            val fraction = if (total <= 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.c4)
            )
        }
    }
}

private fun compact(value: Int): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}
