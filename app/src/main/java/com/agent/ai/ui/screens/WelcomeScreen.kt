package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.components.AppLogo
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PrimaryButton
import com.agent.ai.ui.theme.AgentTheme

@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    onChooseProvider: () -> Unit,
) {
    val palette = AgentTheme.palette

    AgentScreen {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo(size = 96.dp, glow = true)
            Text(
                text = "WELCOME",
                fontSize = 12.sp,
                letterSpacing = 3.6.sp,
                color = palette.c4,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "Your AI workspace, on your phone",
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 31.sp,
                color = palette.c5,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(280.dp),
            )
        }

        Panel(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Bullet("Files, chats and memory stay on this device")
                Bullet("A real terminal, editor and browser")
                Bullet("Free model ready, no key needed")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.c1)
                .border(1.dp, palette.c4, RoundedCornerShape(16.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LineIcon(AgentIcons.Shield, size = 20.dp, tint = palette.c4)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) {
                        append("About the free model\n")
                    }
                    append("Messages and code you send to the free cloud model leave your device and may be logged by the provider. For sensitive projects, use an on-device model.")
                },
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = palette.c5,
            )
        }

        PrimaryButton("Continue", onClick = onContinue)
        OutlineButton("Choose another provider", onClick = onChooseProvider)
    }
}

@Composable
private fun Bullet(text: String) {
    val palette = AgentTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LineIcon(AgentIcons.Check, size = 18.dp, tint = palette.c4)
        Text(text, fontSize = 14.sp, color = palette.c5)
    }
}
