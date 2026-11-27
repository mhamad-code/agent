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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.IconTile
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.PrimaryButton
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.MonoStyle

private data class DiffLine(
    val marker: String,
    val number: String,
    val code: String,
    val kind: Kind,
) {
    enum class Kind { Context, Removed, Added }
}

@Composable
fun ReviewScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette

    AgentScreen {
        AppHeader(
            title = "Review changes",
            subtitle = "2 files · by agent",
            onBack = onBack,
            trailing = {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.c4)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Accept all", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.c0)
                }
            },
        )

        Panel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "app.py",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                    modifier = Modifier.weight(1f),
                )
                Text("+3 −2", fontSize = 12.sp, color = palette.c4)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.c3))
            Column(Modifier.padding(vertical = 6.dp)) {
                DiffRow(DiffLine(" ", "4", "@app.route(\"/\")", DiffLine.Kind.Context))
                DiffRow(DiffLine("-", "5", "def home():", DiffLine.Kind.Removed))
                DiffRow(DiffLine("-", "6", "    return \"Hello\"", DiffLine.Kind.Removed))
                DiffRow(DiffLine("+", "5", "def home():", DiffLine.Kind.Added))
                DiffRow(DiffLine("+", "6", "    name = request.args.get(\"name\")", DiffLine.Kind.Added))
                DiffRow(DiffLine("+", "7", "    return f\"Hello {name}\"", DiffLine.Kind.Added))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.c3))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, palette.c4, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Reject", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                }
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.c5)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Accept", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.c0)
                }
            }
        }

        Panel {
            PanelRow(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                Text(
                    "templates/index.html",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                    modifier = Modifier.weight(1f),
                )
                Text("+12 −0", fontSize = 12.sp, color = palette.c4)
            }
        }

        Box(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.c1)
                .border(1.dp, palette.c4, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconTile(size = 36.dp, radius = 12.dp, background = palette.c2, tint = palette.c4) {
                    LineIcon(AgentIcons.Shield, size = 20.dp, tint = palette.c4)
                }
                Column {
                    Text(
                        "Approval needed",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                    )
                    Text("The agent wants to run a command", fontSize = 12.sp, color = palette.c4)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.c0)
                    .border(1.dp, palette.c3, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("$", style = MonoStyle, color = palette.c4)
                Text(" git push origin main", style = MonoStyle, color = palette.c5)
            }
            Text("Pushing always needs your approval.", fontSize = 12.sp, color = palette.c4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("Reject", modifier = Modifier.weight(1f), height = 44.dp)
                PrimaryButton("Approve once", modifier = Modifier.weight(1f), height = 44.dp)
            }
        }
    }
}

@Composable
private fun DiffRow(line: DiffLine) {
    val palette = AgentTheme.palette
    val background = when (line.kind) {
        DiffLine.Kind.Removed -> palette.c0.copy(alpha = 0.7f)
        DiffLine.Kind.Added -> palette.c4.copy(alpha = 0.2f)
        DiffLine.Kind.Context -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .alpha(if (line.kind == DiffLine.Kind.Removed) 0.75f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line.marker,
            style = MonoStyle,
            color = palette.c4,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(20.dp),
        )
        Text(
            text = line.number,
            style = MonoStyle,
            color = palette.c3,
            modifier = Modifier.width(22.dp),
        )
        Text(text = line.code, style = MonoStyle, color = palette.c5)
    }
}
