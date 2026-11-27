package com.agent.ai.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon is a static vector asset. When the optional "Match theme" setting
 * is on, the matching activity-alias is enabled so the icon follows the active theme.
 * Switching aliases can briefly close the app on some launchers.
 */
object LauncherIcons {

    const val DefaultTheme = "forest"

    private val aliases = mapOf(
        "forest" to "com.agent.ai.LauncherForest",
        "ocean" to "com.agent.ai.LauncherOcean",
        "amoled" to "com.agent.ai.LauncherAmoled",
        "dracula" to "com.agent.ai.LauncherDracula",
        "nord" to "com.agent.ai.LauncherNord",
        "solarized" to "com.agent.ai.LauncherSolarized",
        "blossom" to "com.agent.ai.LauncherBlossom",
        "carnival" to "com.agent.ai.LauncherCarnival",
        "lilac" to "com.agent.ai.LauncherLilac",
        "cobalt" to "com.agent.ai.LauncherCobalt",
        "ember" to "com.agent.ai.LauncherEmber",
    )

    fun aliasFor(themeId: String?): String = aliases[themeId] ?: aliases.getValue(DefaultTheme)

    fun apply(context: Context, themeId: String?) {
        val target = aliasFor(themeId)
        val pm = context.packageManager
        aliases.values.forEach { name ->
            val state = if (name == target) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(context.packageName, name),
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
