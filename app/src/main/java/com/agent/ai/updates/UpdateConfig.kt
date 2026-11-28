package com.agent.ai.updates

/**
 * The only place the repository is named. Every URL the update system builds comes from
 * here, so no screen or client repeats the owner or the repository name.
 */
object UpdateConfig {
    const val OWNER = "mhamad-code"
    const val REPO = "Agent"
    const val BRANCH = "main"
    const val MANIFEST_FILE = "update.json"

    /** Eight seconds per source, as the update design requires. */
    const val SOURCE_TIMEOUT_MS = 8_000L

    /** Automatic checks happen at most this often while the app starts. */
    const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    val releasesPage: String get() = "https://github.com/$OWNER/$REPO/releases"

    val contentsApiUrl: String
        get() = "https://api.github.com/repos/$OWNER/$REPO/contents/$MANIFEST_FILE?ref=$BRANCH"

    val rawUrl: String
        get() = "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/$MANIFEST_FILE"

    val releasesApiUrl: String
        get() = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=10"

    /** Always the last source: jsDelivr may serve an older cached copy. */
    val jsDelivrUrl: String
        get() = "https://cdn.jsdelivr.net/gh/$OWNER/$REPO@$BRANCH/$MANIFEST_FILE"

    /**
     * Downloads are only accepted from GitHub's own hosts or from the mirror the user typed
     * in Settings. A mirror listed in `update.json` must also pass this check.
     */
    private val githubHosts = setOf(
        "github.com",
        "www.github.com",
        "api.github.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "codeload.github.com",
        "github-releases.githubusercontent.com",
    )

    fun isAllowed(url: String, userMirrorHost: String?): Boolean {
        val host = hostOf(url) ?: return false
        if (host in githubHosts) return true
        return userMirrorHost != null && host.equals(userMirrorHost, ignoreCase = true)
    }

    fun hostOf(url: String): String? = runCatching {
        java.net.URI(url).host?.lowercase()
    }.getOrNull()
}

/** Where a manifest came from, in the order the checker tries them. */
enum class UpdateSource(val label: String) {
    ContentsApi("GitHub API"),
    Raw("raw.githubusercontent.com"),
    JsDelivr("jsDelivr");

    companion object {
        val order = listOf(ContentsApi, Raw, JsDelivr)
    }
}

/** Why one source did not answer, kept so the screen can list every attempt. */
data class SourceFailure(val source: UpdateSource, val reason: String)

/** What the app downloaded and from where. */
data class ApkAsset(
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
)

/** `update.json`, parsed. Unknown keys are ignored so the file can grow. */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val publishedAt: String,
    val notes: List<String>,
    val apk: ApkAsset,
    val mirrors: List<String>,
    val minVersionCode: Int?,
) {
    val hasHash: Boolean get() = apk.sha256.length == 64
}

/** The result of one check, including which source answered. */
sealed interface UpdateCheck {
    val checkedAt: Long

    data class UpToDate(
        val source: UpdateSource,
        override val checkedAt: Long,
        /** True when the source answered 304 from the remembered ETag. */
        val notModified: Boolean = false,
    ) : UpdateCheck

    data class Available(
        val manifest: UpdateManifest,
        val source: UpdateSource,
        override val checkedAt: Long,
    ) : UpdateCheck

    data class Failed(
        override val checkedAt: Long,
        val failures: List<SourceFailure>,
    ) : UpdateCheck
}
