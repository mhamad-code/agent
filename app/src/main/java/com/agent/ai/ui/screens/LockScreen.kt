package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.AppLogo
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val palette = AgentTheme.palette

    AgentScreen {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo(size = 88.dp, glow = true)
            Text(
                "Agent is locked",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "Unlock to open your projects",
                fontSize = 13.sp,
                color = palette.c4,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(palette.c2)
                    .border(1.dp, palette.c4, CircleShape)
                    .clickable(onClick = onUnlock),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(AgentIcons.Fingerprint, size = 40.dp, tint = palette.c5)
            }
        }

        OutlineButton("Use PIN instead", onClick = onUnlock)
    }
}
