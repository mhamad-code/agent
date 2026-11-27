package com.agent.ai.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.R
import com.agent.ai.data.chat.SearchHit
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.InterFamily
import kotlinx.coroutines.delay

/** Search across every conversation, using the Room full-text index. */
@Composable
fun ChatSearchScreen(
    chat: ChatViewModel,
    onBack: () -> Unit,
) {
    val palette = AgentTheme.palette
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            searched = false
            return@LaunchedEffect
        }
        loading = true
        delay(250)
        results = chat.search(query)
        searched = true
        loading = false
    }

    AgentScreen(padding = 16.dp, gap = 12.dp) {
        AppHeader(
            title = stringResource(R.string.search_title),
            subtitle = if (searched) {
                stringResource(R.string.search_results, results.size)
            } else {
                stringResource(R.string.search_hint)
            },
            onBack = onBack,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.c2)
                .border(1.dp, palette.c3, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LineIcon(AgentIcons.Search, size = 16.dp, tint = palette.c4)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = InterFamily,
                    fontSize = 14.sp,
                    color = palette.c5,
                ),
                cursorBrush = SolidColor(palette.c5),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            fontSize = 14.sp,
                            color = palette.c4,
                        )
                    }
                    inner()
                },
            )
        }

        if (searched && results.isEmpty() && !loading) {
            Text(
                text = stringResource(R.string.search_no_results),
                fontSize = 13.sp,
                color = palette.c4,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.messageId }) { hit ->
                Panel(
                    onClick = {
                        chat.openConversationAt(hit.conversationId, hit.messageId)
                        onBack()
                    },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = hit.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.c4,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = highlight(hit.text, query, palette.c3),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = palette.c5,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Marks every occurrence of a search term on a soft highlight. */
private fun highlight(text: String, query: String, color: androidx.compose.ui.graphics.Color): AnnotatedString {
    val terms = query.split(Regex("\\s+")).filter { it.length > 1 }
    if (terms.isEmpty()) return AnnotatedString(text)
    val builder = AnnotatedString.Builder(text)
    terms.forEach { term ->
        var index = text.indexOf(term, ignoreCase = true)
        while (index >= 0) {
            builder.addStyle(
                SpanStyle(background = color),
                index,
                (index + term.length).coerceAtMost(text.length),
            )
            index = text.indexOf(term, index + term.length, ignoreCase = true)
        }
    }
    return builder.toAnnotatedString()
}
