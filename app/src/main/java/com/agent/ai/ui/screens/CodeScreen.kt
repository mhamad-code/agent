package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.Routes
import com.agent.ai.ui.components.AgentDrawer
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.MonoText
import com.agent.ai.ui.components.SquareButton
import com.agent.ai.ui.components.StatusDot
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun CodeScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenReview: () -> Unit,
    onOpenGit: () -> Unit,
) {
    var drawerOpen by remember { mutableStateOf(false) }
    val palette = AgentTheme.palette

    Box(Modifier.fillMaxSize()) {
        AgentScreen {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SquareButton(
                    icon = AgentIcons.Menu,
                    label = "Menu",
                    onClick = { drawerOpen = true },
                )
                Column(Modifier.weight(1f)) {
                    Text("flask-app", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                    Text("main · 2 changes", fontSize = 12.sp, color = palette.c4)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.c4),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(AgentIcons.Play, size = 20.dp, tint = palette.c0)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.c2)
                    .border(1.dp, palette.c3, RoundedCornerShape(16.dp)),
            ) {
                EditorTabs()
                CodeView()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.c2)
                    .border(1.dp, palette.c3, RoundedCornerShape(16.dp)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.c2)
                        .padding(8.dp)
                        .drawBottomDivider(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TabLabel("Terminal", selected = true)
                    TabLabel("Browser")
                    TabLabel("Problems 0")
                }
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    TerminalLine(listOf("\$" to palette.c4, " python app.py" to palette.c5))
                    TerminalLine(listOf(" * Running on http://127.0.0.1:5000" to palette.c4))
                    TerminalLine(listOf(" * Debug mode: on" to palette.c4))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.c2)
                    .border(1.dp, palette.c3, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusDot(palette.c5)
                    Text(
                        text = "Agent is editing app.py",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.c1)
                            .border(1.dp, palette.c3, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(AgentIcons.Stop, size = 16.dp, tint = palette.c5)
                    }
                }
                StepRow("Create the route", palette.c4, 0.6f)
                StepRow("Update the template", palette.c5, 1f)
                StepRow("Run the tests", palette.c3, 1f)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.c2)
                        .border(1.dp, palette.c3, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("Message the agent…", fontSize = 14.sp, color = palette.c4)
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(palette.c5),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(AgentIcons.ArrowUp, size = 20.dp, tint = palette.c0)
                }
            }
        }

        AgentDrawer(
            visible = drawerOpen,
            current = Routes.Code,
            // Code mode has no chat list of its own; the home screen owns the chats.
            conversations = emptyList(),
            activeConversationId = null,
            onNewChat = {},
            onOpenChat = {},
            onRenameChat = { _, _ -> },
            onDeleteChat = {},
            onPinChat = { _, _ -> },
            onExportChat = {},
            onConversationPrompt = {},
            onSearchChats = { onNavigate(Routes.Search) },
            onDismiss = { drawerOpen = false },
            onNavigate = { route ->
                drawerOpen = false
                if (route == Routes.Main) onBack() else onNavigate(route)
            },
        )
    }
}

@Composable
private fun EditorTabs() {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .drawBottomDivider(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabLabel("app.py", selected = true)
        TabLabel("index.html")
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean = false) {
    val palette = AgentTheme.palette
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) palette.c3 else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.sp, color = if (selected) palette.c5 else palette.c4)
    }
}

@Composable
private fun CodeView() {
    val palette = AgentTheme.palette
    val stringColor = Color(0xFFF4E3C2)
    Column(Modifier.padding(vertical = 10.dp)) {
        CodeLine(1, false) {
            CodeSpan("from", palette.c4)
            CodeSpan(" flask ", palette.c5)
            CodeSpan("import", palette.c4)
            CodeSpan(" Flask", palette.c5)
        }
        CodeLine(2, false) {
            CodeSpan("app = Flask(__name__)", palette.c5)
        }
        CodeLine(3, false) {}
        CodeLine(4, false) {
            CodeSpan("@app.route(", palette.c5)
            CodeSpan("\"/\"", stringColor)
            CodeSpan(")", palette.c5)
        }
        CodeLine(5, true) {
            CodeSpan("def", palette.c4)
            CodeSpan(" home():", palette.c5)
        }
        CodeLine(6, true) {
            CodeSpan("    return ", palette.c4)
            CodeSpan("\"Hello\"", stringColor)
        }
    }
}

@Composable
private fun CodeLine(number: Int, highlighted: Boolean, content: @Composable RowScope.() -> Unit) {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) palette.c4.copy(alpha = 0.16f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString(),
            fontSize = 12.sp,
            color = palette.c3,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier
                .width(28.dp)
                .padding(end = 10.dp),
            style = com.agent.ai.ui.theme.MonoStyle.copy(fontSize = 12.sp),
        )
        Row(content = content)
    }
}

@Composable
private fun CodeSpan(text: String, color: Color) {
    Text(text = text, style = com.agent.ai.ui.theme.MonoStyle, color = color)
}

@Composable
private fun TerminalLine(spans: List<Pair<String, Color>>) {
    Row {
        spans.forEach { (text, color) ->
            Text(text = text, style = com.agent.ai.ui.theme.MonoStyle, color = color)
        }
    }
}

@Composable
private fun StepRow(text: String, dot: Color, alpha: Float) {
    val palette = AgentTheme.palette
    Row(
        modifier = Modifier.alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(dot)
        Text(text, fontSize = 12.sp, color = palette.c5)
    }
}

/** 1dp hairline under a row, matching the design's tab strips. */
@Composable
fun Modifier.drawBottomDivider(): Modifier {
    val color = AgentTheme.palette.c3
    val stroke = 1.dp
    return this.drawBehind {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, size.height - stroke.toPx() / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height - stroke.toPx() / 2f),
            strokeWidth = stroke.toPx(),
        )
    }
}
