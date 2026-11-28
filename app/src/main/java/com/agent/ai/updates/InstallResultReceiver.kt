package com.agent.ai.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/** Records the PackageInstaller result so the Updates page can show what happened. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val text = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "Waiting for you to confirm the install"
            PackageInstaller.STATUS_SUCCESS -> "Installed"
            PackageInstaller.STATUS_FAILURE_ABORTED -> "The install was cancelled"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "The install was blocked: ${message.orEmpty()}"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "The install conflicts with the installed app"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This build is not compatible with the device"
            PackageInstaller.STATUS_FAILURE_INVALID -> "The APK was rejected as invalid"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "There is not enough storage"
            else -> "Install result: ${message.orEmpty()}"
        }
        UpdatePreferences(context).rememberInstallResult(text)

        // The system asks for one more confirmation tap after the session commits.
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(confirm) }
        }
    }

    companion object {
        const val ACTION_RESULT = "com.agent.ai.updates.INSTALL_RESULT"
    }
}
