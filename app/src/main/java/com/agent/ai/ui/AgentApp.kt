package com.agent.ai.ui

import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agent.ai.chat.ChatScreen
import com.agent.ai.chat.ChatSearchScreen
import com.agent.ai.chat.ChatSettingsScreen
import com.agent.ai.chat.ChatViewModel
import com.agent.ai.data.AppearanceViewModel
import com.agent.ai.motion.LocalReduceMotion
import com.agent.ai.ui.screens.AboutScreen
import com.agent.ai.ui.screens.AccountsScreen
import com.agent.ai.ui.screens.AppearanceScreen
import com.agent.ai.ui.screens.BackupScreen
import com.agent.ai.ui.screens.CheckpointsScreen
import com.agent.ai.ui.screens.CodeScreen
import com.agent.ai.ui.screens.CommandsScreen
import com.agent.ai.ui.screens.GitScreen
import com.agent.ai.ui.screens.HooksScreen
import com.agent.ai.ui.screens.LockScreen
import com.agent.ai.ui.screens.McpScreen
import com.agent.ai.ui.screens.MemoryScreen
import com.agent.ai.ui.screens.ModelsSheet
import com.agent.ai.ui.screens.PlanScreen
import com.agent.ai.ui.screens.PluginsScreen
import com.agent.ai.ui.screens.ProjectsScreen
import com.agent.ai.ui.screens.ProvidersScreen
import com.agent.ai.ui.screens.ReviewScreen
import com.agent.ai.ui.screens.SearchScreen
import com.agent.ai.ui.screens.SecurityScreen
import com.agent.ai.ui.screens.SettingsScreen
import com.agent.ai.ui.screens.SkillsScreen
import com.agent.ai.ui.screens.SubagentsScreen
import com.agent.ai.ui.screens.ToolsSheet
import com.agent.ai.ui.screens.UsageScreen
import com.agent.ai.ui.screens.WelcomeScreen
import com.agent.ai.ui.screens.WorkspaceScreen
import com.agent.ai.ui.theme.AgentTheme

/** Subpages slide horizontally by 30 percent of the width with a fade, over 300ms. */
private const val TransitionMs = 300

@Composable
fun AgentApp(
    appearance: AppearanceViewModel = viewModel(),
    chat: ChatViewModel = viewModel(),
) {
    val settings by appearance.settings.collectAsStateWithLifecycle()
    val chatState by chat.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Reduce motion also follows the system animation scale (0 means animations are off).
    val systemScale = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }
    val reduceMotion = settings.reduceMotion || systemScale == 0f

    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        AgentTheme(palette = settings.palette, background = settings.background) {
            val navController = rememberNavController()
            var sheet by remember { mutableStateOf<AgentSheetType?>(null) }

            val back: () -> Unit = { navController.popBackStack() }
            val navigate: (String) -> Unit = { route ->
                navController.navigate(route) { launchSingleTop = true }
            }

            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.Main,
                    enterTransition = {
                        slideInHorizontally(tween(TransitionMs)) { it * 3 / 10 } +
                            fadeIn(tween(TransitionMs))
                    },
                    exitTransition = { fadeOut(tween(TransitionMs)) },
                    popEnterTransition = { fadeIn(tween(TransitionMs)) },
                    popExitTransition = {
                        slideOutHorizontally(tween(TransitionMs)) { it * 3 / 10 } +
                            fadeOut(tween(TransitionMs))
                    },
                ) {
                    composable(Routes.Main) {
                        ChatScreen(chat = chat, onNavigate = navigate)
                    }
                    composable(Routes.Code) {
                        CodeScreen(
                            onBack = back,
                            onNavigate = navigate,
                            onOpenReview = { navigate(Routes.Review) },
                            onOpenGit = { navigate(Routes.Git) },
                        )
                    }
                    composable(Routes.Review) { ReviewScreen(onBack = back) }
                    composable(Routes.Settings) {
                        SettingsScreen(
                            onBack = back,
                            onNavigate = navigate,
                            reduceMotion = reduceMotion,
                            onReduceMotion = appearance::setReduceMotion,
                        )
                    }
                    composable(Routes.Welcome) {
                        WelcomeScreen(
                            onContinue = {
                                navController.popBackStack(Routes.Main, inclusive = false)
                            },
                            onChooseProvider = { navigate(Routes.Providers) },
                        )
                    }
                    composable(Routes.Providers) {
                        ProvidersScreen(onBack = back, onOpenModels = { sheet = AgentSheetType.Models })
                    }
                    composable(Routes.Skills) { SkillsScreen(onBack = back) }
                    composable(Routes.Appearance) {
                        AppearanceScreen(
                            onBack = back,
                            settings = settings,
                            onSelectTheme = appearance::selectTheme,
                            onSelectBackground = appearance::selectBackground,
                            onAddWallpaper = appearance::addWallpaper,
                            onMatchIconTheme = appearance::setMatchIconTheme,
                        )
                    }
                    composable(Routes.Mcp) { McpScreen(onBack = back) }
                    composable(Routes.Projects) { ProjectsScreen(onBack = back) }
                    composable(Routes.Checkpoints) { CheckpointsScreen(onBack = back) }
                    composable(Routes.Memory) { MemoryScreen(chat = chat, onBack = back) }
                    composable(Routes.Plan) { PlanScreen(onBack = back) }
                    composable(Routes.Plugins) { PluginsScreen(onBack = back) }
                    composable(Routes.Accounts) { AccountsScreen(onBack = back) }
                    composable(Routes.Security) {
                        SecurityScreen(onBack = back, onOpenLock = { navigate(Routes.Lock) })
                    }
                    composable(Routes.Workspace) { WorkspaceScreen(onBack = back) }
                    composable(Routes.Backup) { BackupScreen(onBack = back) }
                    composable(Routes.About) {
                        AboutScreen(onBack = back, onOpenWelcome = { navigate(Routes.Welcome) })
                    }
                    composable(Routes.Commands) { CommandsScreen(chat = chat, onBack = back) }
                    composable(Routes.Git) { GitScreen(onBack = back) }
                    composable(Routes.Hooks) { HooksScreen(onBack = back) }
                    composable(Routes.Lock) {
                        LockScreen(onUnlock = { navController.popBackStack(Routes.Main, inclusive = false) })
                    }
                    composable(Routes.Search) { ChatSearchScreen(chat = chat, onBack = back) }
                    composable(Routes.ChatSettings) {
                        ChatSettingsScreen(
                            settings = chatState.settings,
                            onUpdate = chat::updateSettings,
                            onBack = back,
                        )
                    }
                    composable(Routes.Subagents) { SubagentsScreen(onBack = back) }
                    composable(Routes.Usage) { UsageScreen(onBack = back) }
                }

                when (sheet) {
                    AgentSheetType.Models -> ModelsSheet(
                        models = chatState.models,
                        selectedId = chatState.modelId,
                        loading = chatState.modelsLoading,
                        error = chatState.modelsError,
                        offline = chatState.modelsOffline,
                        onSelect = {
                            chat.selectModel(it)
                            sheet = null
                        },
                        onRefresh = chat::refreshModels,
                        onDismiss = { sheet = null },
                    )

                    AgentSheetType.Tools -> ToolsSheet(
                        capabilities = chatState.capabilities,
                        deepThink = chatState.deepThink,
                        onDeepThink = chat::setDeepThink,
                        onDismiss = { sheet = null },
                    )
                    null -> Unit
                }
            }
        }
    }
}
