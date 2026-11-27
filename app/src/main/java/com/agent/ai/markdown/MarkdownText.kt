package com.agent.ai.markdown

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.ai.R
import com.agent.ai.ui.theme.AgentTheme
import com.agent.ai.ui.theme.InterFamily
import com.agent.ai.ui.theme.MonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Set by the host screen so a link tap can ask before opening a browser. */
val LocalLinkHandler = staticCompositionLocalOf<(String) -> Unit> { {} }

private val RemoteImages by lazy { OkHttpClient() }

/**
 * Renders an assistant reply, including a reply that is still streaming in.
 *
 * Finished blocks are parsed once by [IncrementalMarkdown] and only the tail is re-parsed,
 * code highlighting runs off the main thread and is throttled, and a tapped link goes
 * through [LocalLinkHandler] so the user can confirm before leaving the app.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
) {
    val palette = AgentTheme.palette
    val colors = remember(palette) { mdColors(palette) }
    val parser = remember { IncrementalMarkdown() }
    val blocks = remember(text) {
        if (text.isBlank()) emptyList() else parser.blocks(text)
    }
    val onOpenLink = LocalLinkHandler.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEachIndexed { index, block ->
            // Stable keys: a completed block keeps its identity while the tail grows.
            key(index) {
                Block(
                    block = block,
                    colors = colors,
                    streaming = streaming && index == blocks.lastIndex,
                    onOpenLink = onOpenLink,
                )
            }
        }
    }
}

@Composable
private fun Block(
    block: MdBlock,
    colors: MdColors,
    streaming: Boolean,
    onOpenLink: (String) -> Unit,
) {
    when (block) {
        is MdBlock.Heading -> {
            val size = when (block.level) {
                1 -> 24.sp
                2 -> 20.sp
                3 -> 17.sp
                else -> 15.sp
            }
            val lineHeight = size * 1.35f
            Text(
                text = buildInline(block.text, colors, onOpenLink),
                style = TextStyle(
                    fontFamily = InterFamily,
                    fontSize = size,
                    lineHeight = lineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    textDirection = directionOf(block.text),
                ),
                modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 0.dp),
            )
        }

        is MdBlock.Paragraph -> Text(
            text = buildInline(block.text, colors, onOpenLink),
            style = TextStyle(
                fontFamily = InterFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = colors.text,
                textDirection = directionOf(block.text),
            ),
        )

        is MdBlock.Code -> CodeBlock(block, colors, streaming)

        is MdBlock.Table -> TableBlock(block, colors, onOpenLink)

        is MdBlock.Bullets -> ListBlock(block.items, ordered = false, colors, onOpenLink)

        is MdBlock.Ordered -> ListBlock(block.items, ordered = true, colors, onOpenLink)

        is MdBlock.Quote -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.quoteBar)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.lines.filter { it.isNotBlank() }.forEach { line ->
                    Text(
                        text = buildInline(line, colors, onOpenLink),
                        style = TextStyle(
                            fontFamily = InterFamily,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            fontStyle = FontStyle.Italic,
                            color = colors.muted,
                            textDirection = directionOf(line),
                        ),
                    )
                }
            }
        }

        MdBlock.Rule -> Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(1.dp)
                .background(colors.rule)
        )

        is MdBlock.Image -> ImageBlock(block, colors)
    }
}

// ----- Inline text ---------------------------------------------------------------------

@Composable
private fun buildInline(
    text: String,
    colors: MdColors,
    onOpenLink: (String) -> Unit,
): AnnotatedString {
    val handler by rememberUpdatedState(onOpenLink)
    return remember(text, colors) {
        buildAnnotatedString {
            appendInline(
                nodes = MdInlineParser.parse(text),
                colors = colors,
                onOpenLink = { url -> handler(url) },
            )
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(
    nodes: List<MdInline>,
    colors: MdColors,
    onOpenLink: (String) -> Unit,
    style: SpanStyle = SpanStyle(),
) {
    nodes.forEach { node ->
        when (node) {
            is MdInline.Text -> withStyle(style) { append(node.text) }

            is MdInline.Code -> withStyle(
                style + SpanStyle(
                    fontFamily = MonoFamily,
                    fontSize = 13.sp,
                    color = colors.accent,
                    background = colors.codeBackground,
                )
            ) { append(" ${node.text} ") }

            is MdInline.Bold -> appendInline(
                node.children, colors, onOpenLink, style + SpanStyle(fontWeight = FontWeight.SemiBold)
            )

            is MdInline.Italic -> appendInline(
                node.children, colors, onOpenLink, style + SpanStyle(fontStyle = FontStyle.Italic)
            )

            is MdInline.BoldItalic -> appendInline(
                node.children,
                colors,
                onOpenLink,
                style + SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic),
            )

            is MdInline.Strike -> appendInline(
                node.children,
                colors,
                onOpenLink,
                style + SpanStyle(textDecoration = TextDecoration.LineThrough),
            )

            is MdInline.Link -> withLink(
                LinkAnnotation.Clickable(
                    tag = node.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = colors.link,
                            textDecoration = TextDecoration.Underline,
                        )
                    ),
                    linkInteractionListener = LinkInteractionListener { onOpenLink(node.url) },
                )
            ) {
                appendInline(node.children, colors, onOpenLink, style)
            }

            is MdInline.Image -> withStyle(
                style + SpanStyle(color = colors.muted, fontStyle = FontStyle.Italic)
            ) {
                append(if (node.alt.isBlank()) node.url else "${node.alt} (${node.url})")
            }

            is MdInline.LineBreak -> append('\n')
        }
    }
}

// ----- Lists, tables, quotes -----------------------------------------------------------

@Composable
private fun ListBlock(
    items: List<MdListItem>,
    ordered: Boolean,
    colors: MdColors,
    onOpenLink: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (item.level * 16).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                val marker = when {
                    item.task == true -> "\u2610" // empty box
                    item.task == false -> "\u2611" // ticked box
                    ordered -> "${item.number ?: 1}."
                    else -> "\u2022"
                }
                Text(
                    text = marker,
                    style = TextStyle(
                        fontFamily = InterFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = colors.muted,
                    ),
                    modifier = Modifier.widthIn(min = 14.dp),
                )
                Text(
                    text = buildInline(item.text, colors, onOpenLink),
                    style = TextStyle(
                        fontFamily = InterFamily,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = colors.text,
                        textDirection = directionOf(item.text),
                    ),
                )
            }
        }
    }
}

@Composable
private fun TableBlock(
    table: MdBlock.Table,
    colors: MdColors,
    onOpenLink: (String) -> Unit,
) {
    val columns = table.header.size
    if (columns == 0) return

    val widths = remember(table) {
        List(columns) { column ->
            val headerLength = table.header.getOrElse(column) { "" }.length
            val longest = table.rows.maxOfOrNull { it.getOrElse(column) { "" }.length } ?: 0
            maxOf(headerLength, longest).coerceIn(5, 26)
        }.map { (it * 7.5f + 24f).dp }
    }

    // Tables are always left to right, even if a cell holds right to left text.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, colors.tableBorder, RoundedCornerShape(10.dp))
            ) {
                Row(Modifier.background(colors.tableHeader)) {
                    table.header.forEachIndexed { index, cell ->
                        TableCell(
                            text = cell,
                            width = widths[index],
                            align = table.alignments.getOrElse(index) { MdAlign.None },
                            colors = colors,
                            header = true,
                            onOpenLink = onOpenLink,
                        )
                    }
                }
                table.rows.forEach { row ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.tableBorder))
                    Row {
                        table.header.indices.forEach { index ->
                            TableCell(
                                text = row.getOrElse(index) { "" },
                                width = widths[index],
                                align = table.alignments.getOrElse(index) { MdAlign.None },
                                colors = colors,
                                header = false,
                                onOpenLink = onOpenLink,
                            )
                        }
                    }
                }
                if (!table.complete && table.rows.isEmpty()) {
                    TableCell(
                        text = " ",
                        width = widths.first(),
                        align = MdAlign.None,
                        colors = colors,
                        header = false,
                        onOpenLink = onOpenLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    align: MdAlign,
    colors: MdColors,
    header: Boolean,
    onOpenLink: (String) -> Unit,
) {
    Text(
        text = buildInline(text, colors, onOpenLink),
        modifier = Modifier
            .width(width)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        style = TextStyle(
            fontFamily = InterFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            color = colors.text,
            textDirection = TextDirection.Ltr,
            textAlign = when (align) {
                MdAlign.Center -> TextAlign.Center
                MdAlign.Right -> TextAlign.End
                else -> TextAlign.Start
            },
        ),
    )
}

// ----- Code ----------------------------------------------------------------------------

@Composable
private fun CodeBlock(
    block: MdBlock.Code,
    colors: MdColors,
    streaming: Boolean,
) {
    var wrap by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    // Long blocks start collapsed, and the header with Copy stays above the scroll area.
    var expanded by remember(block.code) { mutableStateOf(false) }
    val lines = remember(block.code) { block.code.lines() }
    val collapsible = lines.size > COLLAPSE_LINES
    val shown = if (collapsible && !expanded) {
        lines.take(COLLAPSE_LINES).joinToString("\n")
    } else {
        block.code
    }
    val clipboard = LocalClipboardManager.current
    val highlighted = rememberHighlighted(shown, block.language, colors)
    val scroll = rememberScrollState()

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }

    val languageLabel = SyntaxHighlighter.normalize(block.language)
        .takeIf { it.isNotBlank() }
        ?.uppercase()
        ?: "TEXT"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.codeBackground)
            .border(1.dp, colors.tableBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.codeHeader)
                .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = languageLabel,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontFamily = MonoFamily,
                color = colors.muted,
            )
            Spacer(Modifier.weight(1f))
            CodeHeaderButton(
                label = stringResource(
                    if (wrap) R.string.code_wrap_on else R.string.code_wrap_off
                ),
                tint = colors.muted,
            ) { wrap = !wrap }
            CodeHeaderButton(
                label = stringResource(if (copied) R.string.code_copied else R.string.code_copy),
                tint = if (copied) colors.accent else colors.muted,
            ) {
                clipboard.setText(AnnotatedString(block.code))
                copied = true
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (wrap) Modifier else Modifier.horizontalScroll(scroll))
            ) {
                SelectionContainer {
                    Text(
                        text = highlighted,
                        modifier = Modifier
                            .widthIn(min = 120.dp)
                            .padding(12.dp),
                        softWrap = wrap,
                        style = TextStyle(
                            fontFamily = MonoFamily,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.text,
                            textDirection = TextDirection.Ltr,
                        ),
                    )
                }
            }
        }

        if (collapsible && !expanded) {
            Text(
                text = stringResource(R.string.code_show_all, lines.size),
                fontSize = 12.sp,
                fontFamily = InterFamily,
                color = colors.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (!block.closed && streaming) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.accent.copy(alpha = 0.5f))
            )
        }
    }
}

/** A code block longer than this starts collapsed. */
private const val COLLAPSE_LINES = 30

@Composable
private fun CodeHeaderButton(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 11.sp,
        fontFamily = InterFamily,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Highlighting runs on a background dispatcher. The effect restarts on every new token,
 * so the 110ms delay also throttles it: a long block is at most highlighted about nine
 * times a second instead of once per token.
 */
@Composable
private fun rememberHighlighted(code: String, language: String, colors: MdColors): AnnotatedString {
    var annotated by remember(code, language, colors) { mutableStateOf(AnnotatedString(code)) }
    LaunchedEffect(code, language, colors) {
        delay(110)
        annotated = withContext(Dispatchers.Default) { highlight(code, language, colors) }
    }
    return annotated
}

private fun highlight(code: String, language: String, colors: MdColors): AnnotatedString {
    val tokens = SyntaxHighlighter.highlight(code, language)
    if (tokens.isEmpty()) return AnnotatedString(code)
    return buildAnnotatedString {
        append(code)
        tokens.sortedBy { it.start }.forEach { token ->
            val start = token.start.coerceIn(0, code.length)
            val end = token.end.coerceIn(start, code.length)
            if (end <= start) return@forEach
            addStyle(SpanStyle(color = colors.colorFor(token.kind)), start, end)
            if (token.kind == TokenKind.Added) {
                addStyle(SpanStyle(background = colors.addedBackground), start, end)
            }
            if (token.kind == TokenKind.Removed) {
                addStyle(SpanStyle(background = colors.removedBackground), start, end)
            }
        }
    }
}

// ----- Images --------------------------------------------------------------------------

@Composable
private fun ImageBlock(image: MdBlock.Image, colors: MdColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.tableBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = image.alt.ifBlank { stringResource(R.string.image_remote_alt, image.url) },
            style = TextStyle(fontSize = 13.sp, fontFamily = InterFamily, color = colors.text),
        )
        Text(
            text = image.url,
            style = TextStyle(fontSize = 11.sp, fontFamily = MonoFamily, color = colors.muted),
        )
        RemoteImage(url = image.url, alt = image.alt, colors = colors)
    }
}

/** Images are never fetched until the reader asks for them. */
@Composable
private fun RemoteImage(url: String, alt: String, colors: MdColors) {
    var requested by remember(url) { mutableStateOf(false) }
    var loaded by remember(url, requested) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url, requested) {
        if (!requested) return@LaunchedEffect
        loaded = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                RemoteImages.newCall(request).execute().use { response ->
                    val bytes = response.body?.bytes()
                    if (bytes == null) null
                    else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val image = loaded
    when {
        image != null -> Image(
            bitmap = image,
            contentDescription = alt,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
        )

        !requested -> Text(
            text = stringResource(R.string.image_load),
            fontSize = 12.sp,
            color = colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { requested = true }
                .padding(vertical = 4.dp, horizontal = 2.dp),
        )

        else -> Text(
            text = alt.ifBlank { url },
            fontSize = 12.sp,
            color = colors.muted,
        )
    }
}

// ----- Direction -----------------------------------------------------------------------

/**
 * English only for now, but each paragraph decides its own direction from its first strong
 * character, so Arabic replies will read correctly later. Code and tables are pinned to
 * left to right by their own renderers.
 */
internal fun directionOf(text: String): TextDirection {
    var rtl = 0
    var ltr = 0
    for (char in text) {
        val code = char.code
        when {
            code in 0x0590..0x08FF || code in 0xFB1D..0xFDFF || code in 0xFE70..0xFEFF -> rtl++
            char.isLetter() -> ltr++
        }
        if (rtl > 0 || ltr > 0) break
    }
    return if (rtl > ltr) TextDirection.Rtl else TextDirection.Ltr
}
