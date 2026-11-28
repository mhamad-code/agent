package com.agent.ai.updates

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** One attempted download address, with the reason it did not work. */
data class DownloadAttempt(val url: String, val reason: String)

sealed interface DownloadOutcome {
    data class Ready(
        val file: File,
        /** False when the hash could only be read from a mirror, so the screen warns. */
        val hashConfirmed: Boolean,
        val source: String,
    ) : DownloadOutcome

    data class Failed(val attempts: List<DownloadAttempt>) : DownloadOutcome

    data object Cancelled : DownloadOutcome
}

/**
 * Downloads the APK from the release address, then the mirrors in `update.json`, then the
 * user's own mirror. A partial file is resumed with HTTP Range when the same address is
 * used again, and the finished file is checked against the manifest's SHA-256.
 */
class UpdateDownloader(
    private val directory: File,
    private val client: OkHttpClient = defaultClient(),
    /** The mirror the user typed in Settings, or null when there is none. */
    private val userMirrorUrl: () -> String?,
) {

    suspend fun download(
        manifest: UpdateManifest,
        hashFromGitHub: String?,
        onProgress: (downloaded: Long, total: Long, source: String) -> Unit,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val attempts = mutableListOf<DownloadAttempt>()
        val candidates = candidates(manifest)

        if (candidates.isEmpty()) {
            return@withContext DownloadOutcome.Failed(
                listOf(DownloadAttempt(manifest.apk.url, "The address is not on the allow list")),
            )
        }

        for (url in candidates) {
            try {
                val file = fetch(manifest, url, onProgress)
                val expected = manifest.apk.sha256.lowercase()
                if (expected.length == 64) {
                    val actual = sha256(file)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        file.delete()
                        attempts += DownloadAttempt(url, "The SHA-256 did not match")
                        continue
                    }
                }
                val confirmedHash = hashFromGitHub?.takeIf { it.length == 64 }
                return@withContext DownloadOutcome.Ready(
                    file = file,
                    hashConfirmed = confirmedHash != null &&
                        confirmedHash.equals(manifest.apk.sha256, ignoreCase = true),
                    source = UpdateConfig.hostOf(url) ?: url,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                attempts += DownloadAttempt(url, t.message ?: "Download failed")
            }
        }
        DownloadOutcome.Failed(attempts)
    }

    /** The main address first, then `update.json` mirrors, then the user's mirror. */
    private fun candidates(manifest: UpdateManifest): List<String> {
        val stored = userMirrorUrl()
        val host = stored?.let { UpdateConfig.hostOf(it) }
        val all = buildList {
            add(manifest.apk.url)
            addAll(manifest.mirrors)
            userMirror(manifest, stored)?.let { add(it) }
        }
        return all.distinct().filter { UpdateConfig.isAllowed(it, host) }
    }

    /** A mirror may be a folder or a full file address; a folder gets the file name. */
    private fun userMirror(manifest: UpdateManifest, stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        if (stored.endsWith(".apk", ignoreCase = true)) return stored
        val name = manifest.apk.url.substringAfterLast('/').ifBlank { "Agent.apk" }
        return stored.trimEnd('/') + "/" + name
    }

    private suspend fun fetch(
        manifest: UpdateManifest,
        url: String,
        onProgress: (Long, Long, String) -> Unit,
    ): File {
        val name = "Agent-${manifest.versionCode}.apk"
        val target = File(directory, name)
        val part = File(directory, "$name.part")
        val marker = File(directory, "$name.part.url")
        directory.mkdirs()

        // Resume only from the same address, otherwise the bytes belong to another file.
        val resumable = part.exists() && marker.exists() && marker.readText() == url
        if (!resumable) {
            part.delete()
            target.delete()
            marker.writeText(url)
        }
        var existing = if (resumable) part.length() else 0L

        val request = Request.Builder()
            .url(url)
            .apply {
                if (existing > 0) header("Range", "bytes=$existing-")
                header("User-Agent", "Agent-Android")
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 416) {
                // The partial file is already complete or unusable; start over once.
                part.delete()
                existing = 0
                return fetch(manifest, url, onProgress)
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val declared = manifest.apk.sizeBytes
            val total = when {
                response.code == 206 -> {
                    val range = response.header("Content-Range").orEmpty()
                    range.substringAfter('/').toLongOrNull() ?: declared
                }

                else -> body.contentLength().takeIf { it > 0 } ?: declared
            }
            onProgress(existing, total, UpdateConfig.hostOf(url) ?: url)

            body.byteStream().use { input ->
                FileOutputStream(part, existing > 0).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = existing
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, if (total > 0) total else written, UpdateConfig.hostOf(url) ?: url)
                    }
                    output.flush()
                }
            }
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("The download could not be saved")
        marker.delete()
        return target
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .build()

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
