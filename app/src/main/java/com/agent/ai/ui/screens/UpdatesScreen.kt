package com.agent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.agent.ai.R
import com.agent.ai.ui.components.AccentBar
import com.agent.ai.ui.components.AgentScreen
import com.agent.ai.ui.components.AppHeader
import com.agent.ai.ui.components.OutlineButton
import com.agent.ai.ui.components.Panel
import com.agent.ai.ui.components.PanelDivider
import com.agent.ai.ui.components.PanelRow
import com.agent.ai.ui.components.PrimaryButton
import com.agent.ai.ui.components.SectionLabel
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.InterFamily
import com.agent.ai.ui.theme.MonoStyle
import com.agent.ai.updates.DownloadProgress
import com.agent.ai.updates.SignatureCheck
import com.agent.ai.updates.UpdateCheck
import com.agent.ai.updates.UpdateManifest
import com.agent.ai.updates.UpdateUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the update system knows, on one page: version, check, release, mirror. */
@Composable
fun UpdatesScreen(
    state: UpdateUiState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onUpdate: () -> Unit,
    onSkip: () -> Unit,
    onClearSkip: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onOpenReleases: () -> Unit,
    onMirrorChange: (String) -> Unit,
    onMirrorReset: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val palette = AgentTheme.palette
    val manifest = state.offered
    var mirror by remember(state.mirror) { mutableStateOf(state.mirror) }

    AgentScreen(scrollable = true) {
        AppHeader(title = stringResource(R.string.updates_title), onBack = onBack)

        Panel(padding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.updates_current_version, state.currentVersionName),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                )
                Text(
                    stringResource(R.string.updates_version_code, state.currentVersionCode),
                    fontSize = 12.sp,
                    color = palette.c4,
                    style = MonoStyle,
                )
                Text(
                    text = checkStatusText(state),
                    fontSize = 12.sp,
                    color = palette.c4,
                )
                state.answeredBy?.let { source ->
                    Text(
                        stringResource(R.string.updates_answered_by, source.label),
                        fontSize = 12.sp,
                        color = palette.c4,
                    )
                }
            }
        }

        PrimaryButton(
            text = if (state.checking) {
                stringResource(R.string.updates_checking)
            } else {
                stringResource(R.string.updates_check)
            },
            onClick = { if (!state.checking) onCheck() },
        )

        if (state.downloading) {
            val progress = state.download ?: DownloadProgress(0, 0, "")
            Panel(padding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.updates_downloading),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                    )
                    AccentBar(progress.fraction)
                    Text(
                        stringResource(
                            R.string.updates_download_progress,
                            humanBytes(progress.downloaded),
                            humanBytes(progress.total),
                        ),
                        fontSize = 12.sp,
                        color = palette.c4,
                    )
                    Text(
                        stringResource(R.string.updates_download_source, progress.source),
                        fontSize = 12.sp,
                        color = palette.c4,
                    )
                    OutlineButton(stringResource(R.string.cancel), onClick = onCancelDownload)
                }
            }
        }

        if (manifest != null) {
            SectionLabel(stringResource(R.string.updates_latest))
            Panel(padding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.updates_version_line, manifest.versionName),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                    )
                    Text(
                        stringResource(R.string.updates_published, shortDate(manifest.publishedAt)),
                        fontSize = 12.sp,
                        color = palette.c4,
                    )
                    if (manifest.apk.sizeBytes > 0) {
                        Text(
                            stringResource(R.string.updates_size, humanBytes(manifest.apk.sizeBytes)),
                            fontSize = 12.sp,
                            color = palette.c4,
                        )
                    }
                    manifest.notes.forEach { note ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("·", fontSize = 13.sp, color = palette.c4)
                            Text(note, fontSize = 13.sp, color = palette.c5)
                        }
                    }
                    if (!state.hashConfirmed && state.downloadedBytes > 0) {
                        Text(
                            stringResource(R.string.updates_hash_unconfirmed),
                            fontSize = 12.sp,
                            color = palette.c5,
                        )
                    }
                    if (state.downloadedBytes > 0 && !state.downloading) {
                        when (val signature = state.signature) {
                            is SignatureCheck.Mismatch -> AlertText(
                                stringResource(R.string.updates_signature_mismatch),
                            )

                            is SignatureCheck.Unreadable -> AlertText(
                                stringResource(R.string.updates_signature_unreadable, signature.reason),
                            )

                            else -> Unit
                        }
                        if (state.needsInstallPermission) {
                            Text(
                                stringResource(R.string.updates_install_permission),
                                fontSize = 12.sp,
                                color = palette.c4,
                            )
                            OutlineButton(
                                stringResource(R.string.updates_allow_install),
                                onClick = onOpenInstallSettings,
                            )
                        } else {
                            PrimaryButton(
                                text = stringResource(R.string.updates_install),
                                onClick = onInstall,
                            )
                        }
                    }
                    if (state.skippedVersion == manifest.versionCode) {
                        OutlineButton(
                            stringResource(R.string.updates_show_again),
                            onClick = onClearSkip,
                        )
                    } else {
                        OutlineButton(
                            stringResource(R.string.updates_skip),
                            onClick = onSkip,
                        )
                    }
                    if (state.downloadedBytes == 0L && !state.downloading) {
                        PrimaryButton(
                            text = stringResource(R.string.updates_update),
                            onClick = onUpdate,
                        )
                    }
                }
            }
        }

        if (state.check is UpdateCheck.UpToDate) {
            Panel(padding = PaddingValues(14.dp)) {
                Text(
                    stringResource(R.string.updates_no_update),
                    fontSize = 13.sp,
                    color = palette.c5,
                )
            }
        }

        if (state.failures.isNotEmpty()) {
            SectionLabel(stringResource(R.string.updates_failed_title))
            Panel {
                state.failures.forEachIndexed { index, failure ->
                    if (index > 0) PanelDivider()
                    PanelRow {
                        Column(Modifier.weight(1f)) {
                            Text(
                                failure.source.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.c5,
                            )
                            Text(failure.reason, fontSize = 12.sp, color = palette.c4)
                        }
                    }
                }
                PanelDivider()
                PanelRow(onClick = onOpenReleases) {
                    Text(
                        stringResource(R.string.updates_open_releases),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.c5,
                    )
                }
            }
        }

        SectionLabel(stringResource(R.string.updates_source_section))
        Panel(padding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.updates_source_order),
                    fontSize = 12.sp,
                    color = palette.c4,
                )
                Text(
                    stringResource(R.string.updates_mirror),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.c5,
                )
                UpdateField(
                    value = mirror,
                    placeholder = stringResource(R.string.updates_mirror_hint),
                    onChange = {
                        mirror = it
                        onMirrorChange(it)
                    },
                )
                state.mirrorError?.let { AlertText(it) }
                OutlineButton(
                    stringResource(R.string.updates_mirror_reset),
                    onClick = {
                        mirror = ""
                        onMirrorReset()
                    },
                )
            }
        }

        if (state.message != null) {
            Panel(onClick = onDismissMessage, padding = PaddingValues(14.dp)) {
                Text(state.message, fontSize = 12.sp, color = palette.c5)
            }
        }

        if (state.releases.isNotEmpty()) {
            SectionLabel(stringResource(R.string.updates_past))
            Panel {
                state.releases.forEachIndexed { index, release ->
                    if (index > 0) PanelDivider()
                    PanelRow {
                        Column(Modifier.weight(1f)) {
                            Text(
                                release.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.c5,
                            )
                            Text(
                                stringResource(R.string.updates_published, shortDate(release.publishedAt)),
                                fontSize = 12.sp,
                                color = palette.c4,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AlertText(text: String) {
    Text(text, fontSize = 12.sp, color = AgentTheme.palette.c5, fontWeight = FontWeight.Medium)
}

@Composable
private fun UpdateField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val palette = AgentTheme.palette
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = InterFamily,
            fontSize = 13.sp,
            color = palette.c5,
        ),
        cursorBrush = SolidColor(palette.c5),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.c1)
            .border(1.dp, palette.c3, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 13.sp, color = palette.c4)
                }
                inner()
            }
        },
    )
}

/** The update dialog: version, notes, size and the three choices. */
@Composable
fun UpdateAvailableDialog(
    manifest: UpdateManifest,
    downloading: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    val palette = AgentTheme.palette
    Dialog(onDismissRequest = onLater) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(palette.c0)
                .border(1.dp, palette.c3, RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.updates_dialog_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.c5,
            )
            Text(
                stringResource(R.string.updates_version_line, manifest.versionName),
                fontSize = 14.sp,
                color = palette.c5,
            )
            Text(
                stringResource(R.string.updates_size, humanBytes(manifest.apk.sizeBytes)),
                fontSize = 12.sp,
                color = palette.c4,
            )
            manifest.notes.forEach { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("·", fontSize = 13.sp, color = palette.c4)
                    Text(note, fontSize = 13.sp, color = palette.c5)
                }
            }
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = if (downloading) {
                    stringResource(R.string.updates_downloading)
                } else {
                    stringResource(R.string.updates_update)
                },
                onClick = onUpdate,
            )
            OutlineButton(stringResource(R.string.updates_later), onClick = onLater)
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.c1),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.updates_skip),
                    fontSize = 13.sp,
                    color = palette.c4,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

private fun checkStatusText(state: UpdateUiState): String {
    val at = state.lastCheckedAt ?: return "Not checked yet"
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0)
    val whenText = when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 1_440 -> "${minutes / 60} h ago"
        else -> "${minutes / 1_440} d ago"
    }
    val status = when (state.check) {
        is UpdateCheck.Available -> "A new version is available"
        is UpdateCheck.UpToDate ->
            if ((state.check as UpdateCheck.UpToDate).notModified) "Up to date (not modified)" else "Up to date"

        is UpdateCheck.Failed -> "The check failed"
        null -> "Not checked yet"
    }
    return "Checked $whenText · $status"
}

private fun shortDate(value: String): String {
    val parser = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(value.substringBefore('.'))
    }.getOrNull() ?: return value
    return SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(parser.time))
}

fun humanBytes(bytes: Long): String = when {
    bytes <= 0 -> "unknown size"
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576f)
}
