package com.agent.ai.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.agent.ai.R
import com.agent.ai.data.chat.AttachmentDraft
import com.agent.ai.data.chat.MessagePart
import com.agent.ai.ui.components.AgentIcons
import com.agent.ai.ui.components.LineIcon
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.MonoStyle
import java.io.File

/**
 * One attachment square: 48dp with 14dp corners. The composer version carries a small dark
 * X on its top right corner with a 48dp touch area; the sent version has no X.
 */
@Composable
fun AttachmentCell(
    part: MessagePart?,
    mime: String,
    name: String,
    preparing: Boolean = false,
    error: String? = null,
    onOpen: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onShowError: (() -> Unit)? = null,
) {
    val palette = AgentTheme.palette
    Box(Modifier.size(if (onRemove != null) 64.dp else 56.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.c1)
                .border(1.dp, palette.c3, RoundedCornerShape(14.dp))
                .clickable(
                    onClickLabel = if (error != null) {
                        stringResource(R.string.attachment_failed_open)
                    } else {
                        stringResource(R.string.attachment_open)
                    },
                ) { if (error != null) onShowError?.invoke() else onOpen() },
            contentAlignment = Alignment.Center,
        ) {
            when {
                preparing -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = palette.c4,
                )

                error != null -> LineIcon(AgentIcons.Alert, size = 18.dp, tint = palette.c5)

                part is MessagePart.Image -> ImageThumb(part, size = 48)
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    LineIcon(
                        if (mime == "application/pdf") AgentIcons.FileText else AgentIcons.Paperclip,
                        size = 16.dp,
                        tint = palette.c4,
                    )
                    Text(
                        text = name.substringAfterLast('.', "").uppercase().take(4)
                            .ifBlank { stringResource(R.string.attachment_file_short) },
                        fontSize = 9.sp,
                        color = palette.c4,
                        maxLines = 1,
                    )
                }
            }
        }

        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .clickable(
                        onClickLabel = stringResource(R.string.attachment_remove),
                        onClick = onRemove,
                    ),
                contentAlignment = Alignment.TopEnd,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp, end = 2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.82f))
                        .border(1.dp, palette.c3, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(AgentIcons.X, size = 10.dp, tint = Color.White, strokeWidth = 2.2f)
                }
            }
        }
    }
}

@Composable
private fun ImageThumb(part: MessagePart.Image, size: Int) {
    val bitmap = remember(part.path) { decodeSampled(part.path, size * 3) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = part.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
    } else {
        LineIcon(AgentIcons.Image, size = 18.dp, tint = AgentTheme.palette.c4)
    }
}

/** The strip inside the input card: scrolls sideways, 8dp gaps, five at most. */
@Composable
fun DraftAttachmentStrip(
    drafts: List<AttachmentDraft>,
    onOpen: (AttachmentDraft) -> Unit,
    onRemove: (AttachmentDraft) -> Unit,
    onShowError: (AttachmentDraft) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        drafts.forEach { draft ->
            AttachmentCell(
                part = draft.part,
                mime = draft.mime,
                name = draft.name.ifBlank { draft.part?.let(::partName).orEmpty() },
                preparing = draft.preparing,
                error = draft.error,
                onOpen = { if (draft.part != null) onOpen(draft) },
                onRemove = { onRemove(draft) },
                onShowError = { onShowError(draft) },
            )
        }
    }
}

/** The strip a sent message shows above its text: no X, tap opens the full size. */
@Composable
fun MessageAttachmentStrip(parts: List<MessagePart>, onOpen: (MessagePart) -> Unit) {
    val files = parts.filter { it is MessagePart.Image || it is MessagePart.File }
    if (files.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        files.forEach { part ->
            AttachmentCell(
                part = part,
                mime = partMime(part),
                name = partName(part),
                onOpen = { onOpen(part) },
            )
        }
    }
}

/** Full size image, or the text of a document. */
@Composable
fun AttachmentPreviewDialog(part: MessagePart, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            when (part) {
                is MessagePart.Image -> {
                    val bitmap = remember(part.path) { decodeSampled(part.path, 2048) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = part.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        )
                    } else {
                        Text(
                            stringResource(R.string.attachment_missing),
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                }

                is MessagePart.File -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .heightIn(max = 620.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(AgentTheme.palette.c0)
                        .border(1.dp, AgentTheme.palette.c3, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        part.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AgentTheme.palette.c5,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.attachment_file_size, part.size / 1024),
                        fontSize = 11.sp,
                        color = AgentTheme.palette.c4,
                    )
                    Text(
                        text = part.extracted.take(12_000),
                        style = MonoStyle.copy(fontSize = 11.sp),
                        color = AgentTheme.palette.c5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    )
                }

                else -> Unit
            }
        }
    }
}

private fun partName(part: MessagePart): String = when (part) {
    is MessagePart.Image -> part.name
    is MessagePart.File -> part.name
    else -> ""
}

private fun partMime(part: MessagePart): String = when (part) {
    is MessagePart.Image -> part.mime
    is MessagePart.File -> part.mime
    else -> ""
}

/** Decodes a thumbnail without loading the full camera file into memory. */
internal fun decodeSampled(path: String, target: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > target || bounds.outHeight / sample > target) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, options)
}.getOrNull()

/** A file icon for the composer's empty slot, reused by the attach sheet. */
@Composable
fun AttachmentEmptyHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIcon(AgentIcons.Paperclip, size = 14.dp, tint = AgentTheme.palette.c4)
        Text(
            text = stringResource(R.string.attachment_hint),
            fontSize = 11.sp,
            color = AgentTheme.palette.c4,
            textAlign = TextAlign.Start,
            modifier = Modifier.width(240.dp),
        )
    }
}

/** True when the private file behind a part still exists. */
fun MessagePart.exists(): Boolean = when (this) {
    is MessagePart.Image -> File(path).exists()
    is MessagePart.File -> File(path).exists()
    else -> false
}
