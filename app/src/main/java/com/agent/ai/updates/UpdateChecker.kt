package com.agent.ai.updates

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Remembers the last answer and its ETag, so a 304 costs the anonymous limit nothing. */
interface UpdateCache {
    fun etag(key: String): String?
    fun putEtag(key: String, etag: String)
    fun lastCheck(): Long
    fun putLastCheck(at: Long)
}

/**
 * Reads `update.json` from three independent addresses and stops at the first that answers.
 * A blocked GitHub address therefore falls through to the next one, and jsDelivr is only
 * used when both GitHub hosts are unreachable.
 */
class UpdateChecker(
    private val cache: UpdateCache,
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun check(force: Boolean = false): UpdateCheck = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val failures = mutableListOf<SourceFailure>()

        for (source in UpdateSource.order) {
            val cachedEtag = cache.etag(key(source))
            val request = Request.Builder()
                .url(url(source))
                .apply {
                    when (source) {
                        // The documented header returns the file itself instead of base64 JSON.
                        UpdateSource.ContentsApi -> header("Accept", "application/vnd.github.raw+json")
                        else -> Unit
                    }
                    if (cachedEtag != null && !force) header("If-None-Match", cachedEtag)
                    header("User-Agent", "Agent-Android")
                }
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    response.header("ETag")?.let { cache.putEtag(key(source), it) }
                    if (response.code == 304) {
                        cache.putLastCheck(now)
                        return@withContext UpdateCheck.UpToDate(source, now, notModified = true)
                    }
                    if (!response.isSuccessful) {
                        failures += SourceFailure(source, "HTTP ${response.code}")
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    val manifest = UpdateManifestParser.parse(body)
                    if (manifest == null) {
                        failures += SourceFailure(source, "The file is not a valid update.json")
                        return@use
                    }
                    cache.putLastCheck(now)
                    return@withContext if (manifest.versionCode > currentVersionCode()) {
                        UpdateCheck.Available(manifest, source, now)
                    } else {
                        UpdateCheck.UpToDate(source, now)
                    }
                }
            } catch (t: Throwable) {
                failures += SourceFailure(source, reasonFor(t))
            }
        }

        UpdateCheck.Failed(now, failures)
    }

    private fun key(source: UpdateSource) = when (source) {
        UpdateSource.ContentsApi -> "etag.contents"
        UpdateSource.Raw -> "etag.raw"
        UpdateSource.JsDelivr -> "etag.jsdelivr"
    }

    private fun url(source: UpdateSource) = when (source) {
        UpdateSource.ContentsApi -> UpdateConfig.contentsApiUrl
        UpdateSource.Raw -> UpdateConfig.rawUrl
        UpdateSource.JsDelivr -> UpdateConfig.jsDelivrUrl
    }

    private fun reasonFor(t: Throwable): String = when (t) {
        is java.net.SocketTimeoutException -> "Timed out after 8 s"
        is java.net.UnknownHostException -> "The address could not be resolved"
        is java.net.ConnectException -> "Connection refused or blocked"
        else -> t.message ?: t::class.java.simpleName
    }

    companion object {
        /** Set by the app so the checker can compare without touching BuildConfig here. */
        var versionCodeProvider: () -> Int = { 0 }

        private fun currentVersionCode(): Int = versionCodeProvider()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(UpdateConfig.SOURCE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(UpdateConfig.SOURCE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(UpdateConfig.SOURCE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }
}

/** Hand written parsing, following the same rule as the message parts: no plugin needed. */
object UpdateManifestParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): UpdateManifest? {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val versionCode = root.int("versionCode") ?: return null
        val versionName = root.str("versionName") ?: return null
        val apk = root["apk"] as? JsonObject ?: return null
        val url = apk.str("url") ?: return null
        val sha = (apk.str("sha256") ?: "").lowercase()
        return UpdateManifest(
            versionCode = versionCode,
            versionName = versionName,
            publishedAt = root.str("publishedAt").orEmpty(),
            notes = (root["notes"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .orEmpty(),
            apk = ApkAsset(
                url = url,
                sha256 = sha,
                sizeBytes = root["apk"]?.let { (it as? JsonObject)?.long("sizeBytes") } ?: 0L,
            ),
            mirrors = (root["mirrors"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .orEmpty(),
            minVersionCode = root.int("minVersionCode"),
        )
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
}
