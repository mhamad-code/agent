package com.agent.ai.updates

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The update settings: the mirror the user typed, the last result and the ETags. */
class UpdatePreferences(context: Context) : UpdateCache {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("agent_updates", Context.MODE_PRIVATE)

    private val _mirror = MutableStateFlow(prefs.getString(KEY_MIRROR, "").orEmpty())
    val mirror: StateFlow<String> = _mirror.asStateFlow()

    private val _skippedVersion = MutableStateFlow(prefs.getInt(KEY_SKIPPED, 0))
    val skippedVersion: StateFlow<Int> = _skippedVersion.asStateFlow()

    private val _lastSource = MutableStateFlow(prefs.getString(KEY_LAST_SOURCE, null))
    val lastSource: StateFlow<String?> = _lastSource.asStateFlow()

    private val _lastStatus = MutableStateFlow(prefs.getString(KEY_LAST_STATUS, null))
    val lastStatus: StateFlow<String?> = _lastStatus.asStateFlow()

    fun setMirror(value: String) {
        val trimmed = value.trim()
        prefs.edit().putString(KEY_MIRROR, trimmed).apply()
        _mirror.value = trimmed
    }

    fun resetMirror() = setMirror("")

    /** Only the host is used for the download allow list. */
    fun mirrorHost(): String? = UpdateConfig.hostOf(_mirror.value)

    fun skip(versionCode: Int) {
        prefs.edit().putInt(KEY_SKIPPED, versionCode).apply()
        _skippedVersion.value = versionCode
    }

    fun clearSkip() {
        prefs.edit().remove(KEY_SKIPPED).apply()
        _skippedVersion.value = 0
    }

    fun rememberResult(source: String?, status: String) {
        prefs.edit().putString(KEY_LAST_SOURCE, source).putString(KEY_LAST_STATUS, status).apply()
        _lastSource.value = source
        _lastStatus.value = status
    }

    fun installResult(): String? = prefs.getString(KEY_INSTALL_RESULT, null)

    fun rememberInstallResult(message: String?) {
        prefs.edit().putString(KEY_INSTALL_RESULT, message).apply()
    }

    // ----- UpdateCache ----------------------------------------------------------------

    override fun etag(key: String): String? = prefs.getString(key, null)

    override fun putEtag(key: String, etag: String) {
        prefs.edit().putString(key, etag).apply()
    }

    override fun lastCheck(): Long = prefs.getLong(KEY_LAST_CHECK, 0L)

    override fun putLastCheck(at: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK, at).apply()
    }

    private companion object {
        const val KEY_MIRROR = "mirror"
        const val KEY_SKIPPED = "skipped_version"
        const val KEY_LAST_SOURCE = "last_source"
        const val KEY_LAST_STATUS = "last_status"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_INSTALL_RESULT = "install_result"
    }
}
