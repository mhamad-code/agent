package com.agent.ai.terminal

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Files and checks for the Linux environment. The root file system is downloaded on first
 * use and never packaged in the APK, so the app stays small and the download can be resumed.
 */
object LinuxRuntime {

    const val ALPINE_VERSION = "3.20.3"
    const val ALPINE_BRANCH = "v3.20"
    const val MIRROR = "https://dl-cdn.alpinelinux.org/alpine"

    fun root(context: Context) = File(context.filesDir, "linux")
    fun binDir(context: Context) = File(root(context), "bin")
    fun prootBinary(context: Context) = File(binDir(context), "proot")
    fun rootfs(context: Context) = File(root(context), "rootfs")
    fun workspace(context: Context) = File(context.filesDir, "workspace").apply { mkdirs() }
    fun home(context: Context) = File(context.filesDir, "home").apply { mkdirs() }

    fun isProotInstalled(context: Context): Boolean =
        prootBinary(context).let { it.isFile && it.canExecute() } &&
            File(rootfs(context), "bin/sh").isFile

    fun sizeOf(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { sizeOf(it) } ?: 0L
    }

    /** The Alpine name for this device's CPU. Unknown ABIs are reported, never guessed. */
    fun arch(): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        return when {
            abi == "arm64-v8a" -> "aarch64"
            abi == "x86_64" -> "x86_64"
            abi.startsWith("armeabi") -> "armv7"
            abi == "x86" -> "x86"
            else -> null
        }
    }

    fun rootfsUrl(arch: String): String =
        "$MIRROR/$ALPINE_BRANCH/releases/$arch/alpine-minirootfs-$ALPINE_VERSION-$arch.tar.gz"

    /**
     * Downloads and unpacks the root file system. The checksum comes from the `.sha256`
     * file published next to the archive, and an archive that cannot be verified is not used.
     */
    suspend fun installRootfs(
        context: Context,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val arch = arch() ?: error("This device's CPU type is not supported by Alpine.")
            val url = rootfsUrl(arch)
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.MINUTES)
                .build()
            val dir = root(context).apply { mkdirs() }
            val archive = File(dir, "alpine-$arch.tar.gz")
            val part = File(dir, "alpine-$arch.tar.gz.part")

            val expected = expectedChecksum(client, "$url.sha256")
            if (part.length() == 0L && archive.length() == 0L) {
                // Nothing to resume from: start clean so a stale file can never be unpacked.
                part.delete()
            }
            if (archive.length() == 0L) {
                val existing = if (part.exists()) part.length() else 0L
                val request = Request.Builder().url(url)
                    .apply { if (existing > 0) header("Range", "bytes=$existing-") }
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 416) {
                        part.delete()
                        error("The partial download could not be resumed. Start the download again.")
                    }
                    if (!response.isSuccessful) error("HTTP ${response.code} from the mirror")
                    val body = response.body ?: error("The mirror sent an empty file")
                    val total = when {
                        response.code == 206 -> response.header("Content-Range")
                            ?.substringAfter('/')?.toLongOrNull() ?: body.contentLength()

                        else -> body.contentLength()
                    }
                    onProgress(existing, if (total > 0) total else 0L)
                    body.byteStream().use { input ->
                        FileOutputStream(part, existing > 0).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var written = existing
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                written += read
                                onProgress(written, if (total > 0) total else written)
                            }
                        }
                    }
                }
                if (!part.renameTo(archive)) error("The download could not be saved.")
            }

            val actual = sha256(archive)
            if (!actual.equals(expected, ignoreCase = true)) {
                archive.delete()
                error("The checksum did not match. The download was discarded.")
            }

            val target = rootfs(context)
            if (target.exists()) target.deleteRecursively()
            target.mkdirs()
            GZIPInputStream(archive.inputStream().buffered()).use { input ->
                TarGz.extract(input, target)
            }
            archive.delete()
            File(target, "bin/sh").setExecutable(true)
            File(target, "bin/busybox").setExecutable(true)
            Unit
        }
    }

    private fun expectedChecksum(client: OkHttpClient, url: String): String {
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("The checksum file is not published (HTTP ${response.code})")
            }
            response.body?.string().orEmpty()
        }
        val hash = body.trim().substringBefore(' ')
        if (hash.length != 64) throw IOException("The checksum file is not readable")
        return hash
    }

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

    fun deleteAll(context: Context) {
        root(context).deleteRecursively()
    }
}

/** A minimal tar reader, enough for an Alpine minirootfs. Paths are kept inside the target. */
object TarGz {

    fun extract(input: InputStream, target: File) {
        val header = ByteArray(512)
        while (true) {
            if (!readFully(input, header)) return
            if (header.all { it == 0.toByte() }) return

            val name = string(header, 0, 100)
            if (name.isEmpty()) return
            val size = string(header, 124, 12).trim().toLongOrNull(8) ?: 0L
            val type = header[156].toInt().toChar()
            val prefix = string(header, 345, 155)
            val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name
            val clean = File(target, fullName).canonicalFile
            if (!clean.path.startsWith(target.canonicalFile.path)) {
                throw IOException("The archive tried to write outside its folder")
            }

            when (type) {
                '5' -> clean.mkdirs()
                '2' -> {
                    clean.parentFile?.mkdirs()
                    val link = string(header, 157, 100)
                    runCatching { clean.delete() }
                    // Os.symlink exists from API 21; the java.nio call would need 26.
                    runCatching { android.system.Os.symlink(link, clean.absolutePath) }
                }

                '1' -> {
                    clean.parentFile?.mkdirs()
                    val linkTarget = File(target, string(header, 157, 100))
                    runCatching { linkTarget.copyTo(clean, overwrite = true) }
                }

                '0', '\u0000' -> {
                    clean.parentFile?.mkdirs()
                    FileOutputStream(clean).use { output ->
                        var left = size
                        val buffer = ByteArray(64 * 1024)
                        while (left > 0) {
                            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            left -= read
                        }
                    }
                    val mode = string(header, 100, 8).trim().toIntOrNull(8) ?: 0
                    if (mode and 0b001_001_001 != 0) clean.setExecutable(true)
                }
            }

            // Every entry is padded to a 512 byte boundary.
            val padding = (512 - size % 512) % 512
            if (padding > 0) com.agent.ai.util.Processes.skip(input, padding)
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun string(buffer: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = minOf(offset + length, buffer.size)
        while (end < limit && buffer[end] != 0.toByte()) end++
        return String(buffer, offset, end - offset, Charsets.UTF_8)
    }
}
