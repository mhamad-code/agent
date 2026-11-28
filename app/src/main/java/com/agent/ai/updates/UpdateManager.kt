package com.agent.ai.updates

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A download in flight, so the page can show progress and the source in use. */
data class DownloadProgress(
    val downloaded: Long,
    val total: Long,
    val source: String,
) {
    val fraction: Float
        get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
}

data class UpdateUiState(
    val currentVersionName: String = "",
    val currentVersionCode: Int = 0,
    val checking: Boolean = false,
    val check: UpdateCheck? = null,
    val failures: List<SourceFailure> = emptyList(),
    /** The releases the repository lists, newest first, for the past releases section. */
    val releases: List<ReleaseSummary> = emptyList(),
    /** The release the dialog and the page show, when it is newer than this build. */
    val offered: UpdateManifest? = null,
    val skippedVersion: Int = 0,
    val dialogVisible: Boolean = false,
    val download: DownloadProgress? = null,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0,
    val hashConfirmed: Boolean = true,
    val signature: SignatureCheck? = null,
    val needsInstallPermission: Boolean = false,
    val installMessage: String? = null,
    val message: String? = null,
    val mirror: String = "",
    val mirrorError: String? = null,
    val releasing: Boolean = false,
) {
    val hasUpdate: Boolean get() = offered != null
    val lastCheckedAt: Long? get() = check?.checkedAt
    val answeredBy: UpdateSource? get() = when (val c = check) {
        is UpdateCheck.UpToDate -> c.source
        is UpdateCheck.Available -> c.source
        else -> null
    }
}

/**
 * Owns the update session: the six-hour automatic check, the manual check, the download
 * with its resume and hash check, and the hand-off to the installer.
 */
class UpdatesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val preferences = UpdatePreferences(application)
    private val installer = ApkInstaller(application)
    private val downloader = UpdateDownloader(
        directory = File(application.getExternalFilesDir(null) ?: application.filesDir, "updates"),
        userMirrorUrl = { preferences.mirror.value.ifBlank { null } },
    )
    private val checker = UpdateChecker(preferences)

    private val _state = MutableStateFlow(
        UpdateUiState(
            currentVersionName = com.agent.ai.BuildConfig.VERSION_NAME,
            currentVersionCode = com.agent.ai.BuildConfig.VERSION_CODE,
            skippedVersion = preferences.skippedVersion.value,
            mirror = preferences.mirror.value,
            installMessage = preferences.installResult(),
        ),
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null
    private var started = false
    private var readyFile: File? = null

    init {
        UpdateChecker.versionCodeProvider = { com.agent.ai.BuildConfig.VERSION_CODE }
        startOnce()
    }

    /** Called once when the app process starts: check only if six hours have passed. */
    fun startOnce() {
        if (started) return
        started = true
        val elapsed = System.currentTimeMillis() - preferences.lastCheck()
        if (elapsed >= UpdateConfig.AUTO_CHECK_INTERVAL_MS) {
            check(force = false, showDialog = true)
        } else {
            _state.update {
                it.copy(
                    skippedVersion = preferences.skippedVersion.value,
                    mirror = preferences.mirror.value,
                )
            }
        }
    }

    fun check(force: Boolean = true, showDialog: Boolean = false) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, message = null) }
        viewModelScope.launch {
            val result = checker.check(force)
            val failures = (result as? UpdateCheck.Failed)?.failures.orEmpty()
            val offered = (result as? UpdateCheck.Available)?.manifest
            val skipped = preferences.skippedVersion.value
            preferences.rememberResult(
                source = (result as? UpdateCheck.Available)?.source?.label
                    ?: (result as? UpdateCheck.UpToDate)?.source?.label,
                status = when (result) {
                    is UpdateCheck.Available -> "New version ${result.manifest.versionName}"
                    is UpdateCheck.UpToDate -> "Up to date"
                    is UpdateCheck.Failed -> "Check failed"
                },
            )
            _state.update {
                it.copy(
                    checking = false,
                    check = result,
                    failures = failures,
                    offered = offered,
                    skippedVersion = skipped,
                    dialogVisible = showDialog && offered != null && offered.versionCode > skipped,
                )
            }
        }
    }

    /** Fetches the release list for the page. A failure is not an error: the page still works. */
    fun loadReleases() {
        if (_state.value.releases.isNotEmpty()) return
        viewModelScope.launch {
            val client = OkHttpClient.Builder()
                .callTimeout(UpdateConfig.SOURCE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder()
                .url(UpdateConfig.releasesApiUrl)
                .header("User-Agent", "Agent-Android")
                .build()
            val body = runCatching {
                client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull() ?: return@launch
            val releases = ReleaseListParser.parse(body)
            _state.update { it.copy(releases = releases) }
        }
    }

    fun showDialog() {
        val offered = _state.value.offered ?: return
        if (offered.versionCode <= _state.value.skippedVersion) return
        _state.update { it.copy(dialogVisible = true) }
    }

    fun later() {
        _state.update { it.copy(dialogVisible = false) }
    }

    fun skipVersion() {
        val offered = _state.value.offered ?: return
        preferences.skip(offered.versionCode)
        _state.update { it.copy(dialogVisible = false, skippedVersion = offered.versionCode) }
    }

    fun clearSkip() {
        preferences.clearSkip()
        _state.update { it.copy(skippedVersion = 0) }
    }

    fun setMirror(value: String) {
        preferences.setMirror(value)
        val error = if (value.isNotBlank() && UpdateConfig.hostOf(value) == null) {
            "That is not a valid address. Use https://host/path."
        } else {
            null
        }
        _state.update { it.copy(mirror = value, mirrorError = error) }
    }

    fun resetMirror() {
        preferences.resetMirror()
        _state.update { it.copy(mirror = "", mirrorError = null) }
    }

    /** Starts (or resumes) the download, then verifies the hash and the signature. */
    fun startUpdate() {
        val manifest = _state.value.offered ?: return
        if (_state.value.downloading) return
        _state.update {
            it.copy(
                downloading = true,
                download = DownloadProgress(0, manifest.apk.sizeBytes, ""),
                dialogVisible = false,
                message = null,
                signature = null,
                installMessage = null,
            )
        }
        downloadJob = viewModelScope.launch {
            val hash = githubHash(manifest)
            val outcome = downloader.download(
                manifest = manifest,
                hashFromGitHub = hash,
                onProgress = { done, total, source ->
                    _state.update {
                        it.copy(download = DownloadProgress(done, total, source), downloadedBytes = done)
                    }
                },
            )
            when (outcome) {
                is DownloadOutcome.Ready -> {
                    readyFile = outcome.file
                    val signature = withContext(Dispatchers.IO) { installer.signatureCheck(outcome.file) }
                    val needsPermission = !installer.canInstall()
                    _state.update {
                        it.copy(
                            downloading = false,
                            download = null,
                            hashConfirmed = outcome.hashConfirmed,
                            signature = signature,
                            needsInstallPermission = needsPermission,
                            message = when (signature) {
                                is SignatureCheck.Mismatch ->
                                    "The downloaded file is signed with a different key. It was refused."

                                is SignatureCheck.Unreadable ->
                                    "The signature could not be read: ${signature.reason}"

                                SignatureCheck.Matches -> "Downloaded and verified"
                            },
                        )
                    }
                }

                is DownloadOutcome.Failed -> {
                    _state.update {
                        it.copy(
                            downloading = false,
                            download = null,
                            failures = outcome.attempts.map {
                                SourceFailure(UpdateSource.JsDelivr, "${it.url}: ${it.reason}")
                            },
                            message = "Every address failed. ${outcome.attempts.size} were tried.",
                        )
                    }
                }

                DownloadOutcome.Cancelled -> {
                    _state.update { it.copy(downloading = false, download = null, message = "Download cancelled") }
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update { it.copy(downloading = false, download = null, message = "Download cancelled") }
    }

    fun installDownloaded() {
        val file = readyFile ?: return
        viewModelScope.launch {
            if (!installer.canInstall()) {
                _state.update { it.copy(needsInstallPermission = true) }
                return@launch
            }
            installer.install(file).onFailure { error ->
                _state.update {
                    it.copy(
                        installMessage = "The install could not start: ${error.message}",
                        needsInstallPermission = error.message?.contains("not allowed") == true,
                    )
                }
            }
        }
    }

    fun openInstallPermission() {
        val intent = installer.unknownSourcesIntent() ?: return
        runCatching { app.startActivity(intent) }
        _state.update { it.copy(needsInstallPermission = false, message = "Allow installs, then come back") }
    }

    fun openReleases() {
        runCatching {
            app.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(UpdateConfig.releasesPage))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null, installMessage = null) }
    }

    fun dismissInstallMessage() {
        preferences.rememberInstallResult(null)
        _state.update { it.copy(installMessage = null) }
    }

    /**
     * The hash must come from GitHub, never from a mirror. When jsDelivr answered the
     * check, the manifest is read again from the raw GitHub address for the hash only.
     */
    private suspend fun githubHash(manifest: UpdateManifest): String? {
        if (manifest.apk.sha256.length != 64) return null
        val client = OkHttpClient.Builder()
            .callTimeout(UpdateConfig.SOURCE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        val urls = listOf(UpdateConfig.rawUrl, UpdateConfig.contentsApiUrl)
        for (url in urls) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Agent-Android")
                .apply {
                    if (url == UpdateConfig.contentsApiUrl) {
                        header("Accept", "application/vnd.github.raw+json")
                    }
                }
                .build()
            val body = runCatching {
                client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull() ?: continue
            val parsed = UpdateManifestParser.parse(body) ?: continue
            if (parsed.versionCode == manifest.versionCode) return parsed.apk.sha256
        }
        return null
    }
}
