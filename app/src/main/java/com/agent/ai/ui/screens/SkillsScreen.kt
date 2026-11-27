package com.agent.ai.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.AppSwitch
import com.agent.ai.ui.components.Chip
import com.agent.ai.ui.components.InputPlaceholder
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.SegmentedControl
import com.agent.ai.ui.components.SmallButton
import com.agent.ai.ui.theme.AgentTheme
import androidx.compose.foundation.background

private data class Skill(
    val name: String,
    val description: String,
    val tags: List<String>,
    val enabled: Boolean,
)

@Composable
fun SkillsScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette
    var tab by remember { mutableIntStateOf(0) }
    var skills by remember {
        mutableStateOf(
            listOf(
                Skill("ui-ux-pro-max", "Design guidance for building polished interfaces.", listOf("zip", "Runs scripts"), true),
                Skill("git-helper", "Commit messages and branch workflows.", listOf("link"), true),
                Skill("web-testing", "Check pages in the built-in browser.", listOf("zip", "Runs scripts"), false),
                Skill("docs-writer", "Write and update project docs.", listOf("manual"), true),
            )
        )
    }

    AgentScreen {
        AppHeader(
            title = "Skills",
            subtitle = "${skills.size} installed",
            onBack = onBack,
            trailing = { SmallButton("Install") },
        )

        SegmentedControl(
            options = listOf("Installed", "This project"),
            selectedIndex = tab,
            onSelect = { tab = it },
        )

        if (tab == 0) {
            skills.forEachIndexed { index, skill ->
                Panel(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                skill.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.c5,
                            )
                            Text(skill.description, fontSize = 12.sp, lineHeight = 17.sp, color = palette.c4)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                skill.tags.forEach { Chip(it) }
                            }
                        }
                        AppSwitch(
                            checked = skill.enabled,
                            onCheckedChange = { value ->
                                skills = skills.toMutableList().also {
                                    it[index] = skill.copy(enabled = value)
                                }
                            },
                        )
                    }
                }
            }
        } else {
            Panel(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Text(
                    "No skills are scoped to this project yet.",
                    fontSize = 13.sp,
                    color = palette.c4,
                )
            }
        }

        Box(Modifier.weight(1f))

        Panel(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Install from a link", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.c1)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("https://github.com/…", fontSize = 13.sp, color = palette.c4)
                    }
                    SmallButtonInstall()
                }
                Text(
                    "You will see what the skill contains and approve it first.",
                    fontSize = 12.sp,
                    color = palette.c4,
                )
            }
        }
    }
}

@Composable
private fun SmallButtonInstall() {
    val palette = AgentTheme.palette
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.c5)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Install", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.c0)
    }
}
