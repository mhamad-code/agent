package com.agent.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.agent.ai.terminal.AgentAppContext
import com.agent.ai.ui.AgentApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgentAppContext.set(applicationContext)
        enableEdgeToEdge()
        setContent {
            // The app is English only for now, so the whole tree is forced to left to
            // right even on a phone set to Arabic. Sections that genuinely need a
            // different direction (code, tables) override it locally.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                AgentApp()
            }
        }
    }
}
