package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.Badge
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.PrimaryButton
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun PlanScreen(onBack: () -> Unit) {
    val palette = AgentTheme.palette

    AgentScreen {
        AppHeader(
            title = "Plan mode",
            subtitle = "The agent only reads until you approve",
            onBack = onBack,
            trailing = { Badge("Read only") },
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "Add a name parameter to the home route.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = palette.c5,
                modifier = Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                    .background(palette.c3)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.c2)
                .border(1.dp, palette.c3, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Proposed plan", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.c5)
            PlanStep(1, "Read app.py and the tests")
            PlanStep(2, "Add the name parameter to home()")
            PlanStep(3, "Update the template")
            PlanStep(4, "Run the tests and fix failures")
        }

        Box(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton("Edit plan", modifier = Modifier.weight(1f), height = 48.dp)
            PrimaryButton("Approve and run", modifier = Modifier.weight(1.4f), height = 48.dp)
        }
    }
}

@Composable
private fun PlanStep(number: Int, text: String) {
    val palette = AgentTheme.palette
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(palette.c1)
                .border(1.dp, palette.c3, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), fontSize = 12.sp, color = palette.c5)
        }
        Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = palette.c5)
    }
}
