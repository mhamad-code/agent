package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.R
import com.agent.ai.providers.ModelInfo
import com.agent.ai.providers.ProviderErrorKind
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentSheet
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.SectionLabel
import com.agent.ai.ui.theme.AgentTheme

/**
 * The model picker, filled from the live model list (cached in Room, so it still works
 * offline). Only models that anonymous requests can use are shown.
 */
@Composable
fun ModelsSheet(
    models: List<ModelInfo>,
    selectedId: String,
    loading: Boolean,
    error: ProviderErrorKind?,
    offline: Boolean,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AgentTheme.palette
    val ordered = models.sortedWith(
        compareBy({ it.id != selectedId }, { !it.id.startsWith("kilo-auto/") }, { it.name })
    )

    AgentSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.models_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.models_refresh),
                fontSize = 12.sp,
                color = if (loading) palette.c4 else palette.c5,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, palette.c3, RoundedCornerShape(8.dp))
                    .clickable(enabled = !loading, onClick = onRefresh)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        if (loading) {
            Note(stringResource(R.string.models_loading), palette.c4)
        }
        if (offline && !loading) {
            Note(stringResource(R.string.models_offline), palette.c4)
        }
        if (error != null && models.isEmpty() && !loading) {
            Note(stringResource(R.string.models_empty), palette.c4)
        }

        SectionLabel(stringResource(R.string.models_gateway))
        ordered.forEach { model ->
            val contextText = if (model.contextLength > 0) {
                " · " + stringResource(R.string.models_context, compactTokens(model.contextLength))
            } else {
                ""
            }
            ModelOption(
                title = model.name,
                subtitle = model.id + contextText,
                badge = model.free,
                selected = model.id == selectedId,
                onClick = { onSelect(model.id) },
            )
        }
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = color,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

private fun compactTokens(value: Int): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}

@Composable
private fun ModelOption(
    title: String,
    subtitle: String,
    badge: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.c2 else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) palette.c4 else palette.c3,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (badge) {
                    Text(
                        text = stringResource(R.string.models_free_badge),
                        fontSize = 10.sp,
                        color = palette.c4,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.c1)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = palette.c4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            LineIcon(AgentIcons.Check, size = 20.dp, tint = palette.c5)
        }
    }
}
