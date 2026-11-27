package com.agent.ai.data

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.rotationMatrix
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.agent.ai.ui.theme.AgentBackground
import com.agent.ai.ui.theme.AgentPalette
import com.agent.ai.ui.theme.Backgrounds
import com.agent.ai.ui.theme.Palettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.Matrix

private val Context.appearanceDataStore by preferencesDataStore(name = "agent_appearance")

private object Keys {
    val ThemeId = stringPreferencesKey("theme_id")
    val BackgroundId = stringPreferencesKey("background_id")
    val ReduceMotion = booleanPreferencesKey("reduce_motion")
    val MatchIcon = booleanPreferencesKey("match_icon")
    val Wallpapers = stringPreferencesKey("wallpapers")
}

data class AppearanceSettings(
    val themeId: String = Palettes.default.id,
    val backgroundId: String = Backgrounds.default.id,
    val reduceMotion: Boolean = false,
    val matchIconTheme: Boolean = false,
    val wallpapers: List<AgentBackground> = emptyList(),
) {
    val theme: AgentPalette get() = Palettes.byId(themeId)

    /** Every wallpaper available to the picker: the shipped ones plus the user's own. */
    val backgrounds: List<AgentBackground> get() = Backgrounds.all + wallpapers

    val background: AgentBackground
        get() = backgrounds.firstOrNull { it.id == backgroundId } ?: Backgrounds.default

    /** The wallpaper palette wins when a wallpaper is selected, so content stays readable. */
    val palette: AgentPalette get() = background.palette ?: theme
}

private const val SEP = '|'
private const val LINE = '\n'

private fun encodeWallpapers(list: List<AgentBackground>): String = list.joinToString(LINE.toString()) { bg ->
    val colors = (bg.palette?.swatches ?: emptyList()).take(6).joinToString(",") { c ->
        String.format("#%08X", c.toArgb())
    }
    listOf(
        bg.id,
        bg.name.replace(LINE, ' ').replace(SEP, '/'),
        bg.description.replace(LINE, ' ').replace(SEP, '/'),
        bg.imagePath ?: "",
        colors,
    ).joinToString(SEP.toString())
}

private fun decodeWallpapers(raw: String?): List<AgentBackground> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(LINE).mapNotNull { line ->
        val parts = line.split(SEP)
        if (parts.size < 5) return@mapNotNull null
        val colors = parts[4].split(",").mapNotNull { hex ->
            runCatching { Color(hex.trim().toLong(16)) }.getOrNull()
        }
        if (parts[3].isBlank() || colors.size < 6) return@mapNotNull null
        AgentBackground(
            id = parts[0],
            name = parts[1],
            description = parts[2],
            imagePath = parts[3],
            palette = AgentPalette(
                id = parts[0],
                name = parts[1],
                c0 = colors[0],
                c1 = colors[1],
                c2 = colors[2],
                c3 = colors[3],
                c4 = colors[4],
                c5 = colors[5],
                accent = colors[4],
                isLight = false,
                swatches = colors.take(6),
            ),
        )
    }
}

class AppearanceRepository(private val context: Context) {

    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.map { prefs ->
        AppearanceSettings(
            themeId = prefs[Keys.ThemeId] ?: Palettes.default.id,
            backgroundId = prefs[Keys.BackgroundId] ?: Backgrounds.default.id,
            reduceMotion = prefs[Keys.ReduceMotion] ?: false,
            matchIconTheme = prefs[Keys.MatchIcon] ?: false,
            wallpapers = decodeWallpapers(prefs[Keys.Wallpapers]),
        )
    }

    suspend fun setTheme(themeId: String) {
        context.appearanceDataStore.edit { it[Keys.ThemeId] = themeId }
    }

    suspend fun setBackground(backgroundId: String) {
        context.appearanceDataStore.edit { it[Keys.BackgroundId] = backgroundId }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.appearanceDataStore.edit { it[Keys.ReduceMotion] = enabled }
    }

    suspend fun setMatchIcon(enabled: Boolean) {
        context.appearanceDataStore.edit { it[Keys.MatchIcon] = enabled }
    }

    /**
     * Copies the picked image into app storage, samples a six colour palette from it and
     * selects it. The file never leaves the device.
     */
    suspend fun addWallpaper(uri: Uri): AgentBackground? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(uri, maxEdge = 1440) ?: return@withContext null
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val id = "custom_" + System.currentTimeMillis().toString(36)
        val file = File(dir, "$id.jpg")

        val rotated = applyExifRotation(uri, bitmap)
        runCatching {
            file.outputStream().use { out -> rotated.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        }
        if (!file.exists()) return@withContext null

        val sample = Bitmap.createScaledBitmap(
            rotated,
            180,
            (180f * rotated.height / rotated.width).toInt().coerceAtLeast(1),
            true,
        )
        val swatches = runCatching {
            Palette.from(sample).maximumColorCount(24).generate().swatches.map { Color(it.rgb) }
        }.getOrElse { emptyList() }

        val name = displayName(uri) ?: "My wallpaper"
        val palette = Palettes.fromSwatches(id, name, swatches)
        val wallpaper = AgentBackground(
            id = id,
            name = name,
            description = "From your gallery",
            imagePath = file.absolutePath,
            palette = palette,
        )

        context.appearanceDataStore.edit { prefs ->
            val existing = decodeWallpapers(prefs[Keys.Wallpapers]).filterNot { it.id == id }
            prefs[Keys.Wallpapers] = encodeWallpapers(existing + wallpaper)
            prefs[Keys.BackgroundId] = id
        }
        wallpaper
    }

    private fun decodeScaled(uri: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth > 0 && bounds.outWidth / sample > maxEdge) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val orientation = ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        if (degrees == 0) return bitmap
        val matrix = rotationMatrix(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.substringBeforeLast('.')
            } else {
                null
            }
        }
    }.getOrNull()
}

class AppearanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppearanceRepository(application)

    val settings = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppearanceSettings(),
    )

    fun selectTheme(themeId: String) {
        viewModelScope.launch {
            repository.setTheme(themeId)
            if (settings.value.matchIconTheme) LauncherIcons.apply(getApplication(), themeId)
        }
    }

    fun selectBackground(backgroundId: String) {
        viewModelScope.launch { repository.setBackground(backgroundId) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { repository.setReduceMotion(enabled) }
    }

    /** The caller shows the "may close briefly" warning before turning this on. */
    fun setMatchIconTheme(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMatchIcon(enabled)
            LauncherIcons.apply(
                getApplication(),
                if (enabled) settings.value.themeId else LauncherIcons.DefaultTheme,
            )
        }
    }

    fun addWallpaper(uri: Uri) {
        viewModelScope.launch { repository.addWallpaper(uri) }
    }
}
