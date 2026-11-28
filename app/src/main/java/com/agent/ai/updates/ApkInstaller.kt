package com.agent.ai.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What happened when the downloaded APK was checked against the installed app. */
sealed interface SignatureCheck {
    data object Matches : SignatureCheck

    /** A debug build signed with another key, or a tampered file. */
    data class Mismatch(val installed: String, val downloaded: String) : SignatureCheck

    data class Unreadable(val reason: String) : SignatureCheck
}

/**
 * Compares the certificate of the downloaded APK with the installed app, then hands the
 * file to the PackageInstaller. Android also refuses a mismatch at install time; this check
 * makes the refusal visible before the download is used.
 */
class ApkInstaller(private val context: Context) {

    fun signatureCheck(file: File): SignatureCheck {
        val installed = installedSignature() ?: return SignatureCheck.Unreadable(
            "The installed certificate could not be read",
        )
        val downloaded = runCatching {
            val info = context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_SIGNATURES,
            ) ?: return SignatureCheck.Unreadable("The downloaded file is not an APK")
            info.signatures?.firstOrNull()?.toByteArray()?.let { sha256(it) }
        }.getOrNull() ?: return SignatureCheck.Unreadable("The downloaded certificate could not be read")
        return if (installed == downloaded) {
            SignatureCheck.Matches
        } else {
            SignatureCheck.Mismatch(installed, downloaded)
        }
    }

    /** True when Android will let this app start an install from its own files. */
    fun canInstall(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** The system screen where the user allows installs from this app. */
    fun unknownSourcesIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }

    /**
     * Writes the APK through a PackageInstaller session. The system shows its own
     * confirmation dialog, and [InstallResultReceiver] records the outcome.
     */
    suspend fun install(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!canInstall()) {
                error("Installing from this app is not allowed yet.")
            }
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("agent-update", 0, file.length()).use { output ->
                    file.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
                    session.fsync(output)
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(InstallResultReceiver.ACTION_RESULT)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        }
    }

    private fun installedSignature(): String? = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES,
        )
        @Suppress("DEPRECATION")
        info.signatures?.firstOrNull()?.toByteArray()?.let { sha256(it) }
    }.getOrNull()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
