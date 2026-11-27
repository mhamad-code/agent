package com.agent.ai.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures that a streaming reply actually produces, including half written blocks. */
class MarkdownParserTest {

    @Test
    fun `an unclosed fence renders as a growing code block`() {
        val blocks = MdBlocks.parse("Here:\n\n```kotlin\nval x = 1\n", streaming = true)
        assertEquals(2, blocks.size)
        val code = blocks[1] as MdBlock.Code
        assertFalse(code.closed)
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code.trim())
    }

    @Test
    fun `a table cut in the middle of a row keeps the rows it has`() {
        val text = """
            | Name | Score |
            |:-----|------:|
            | Ada  | 10    |
            | Lin  | 9
        """.trimIndent()
        val table = MdBlocks.parse(text).filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("Name", "Score"), table.header)
        assertEquals(listOf(MdAlign.Left, MdAlign.Right), table.alignments)
        assertEquals(listOf("Ada", "10"), table.rows[0])
        // The unfinished last row is still rendered, just short.
        assertEquals("Lin", table.rows[1][0])
    }

    @Test
    fun `a half written bold marker is held back while streaming`() {
        val blocks = MdBlocks.parse("Hello **", streaming = true)
        assertEquals("Hello", (blocks.single() as MdBlock.Paragraph).text)

        val finished = MdBlocks.parse("Hello **world**")
        val inline = MdInlineParser.parse((finished.single() as MdBlock.Paragraph).text)
        assertTrue(inline.any { it is MdInline.Bold })
    }

    @Test
    fun `nested lists keep their level`() {
        val text = """
            - one
              - two
                - three
            - four
        """.trimIndent()
        val list = MdBlocks.parse(text).filterIsInstance<MdBlock.Bullets>().single()
        assertEquals(4, list.items.size)
        assertEquals(listOf(0, 1, 2, 0), list.items.map { it.level })
        assertEquals("three", list.items[2].text)
    }

    @Test
    fun `ordered lists keep their numbers and task lists their state`() {
        val ordered = MdBlocks.parse("1. one\n2. two").filterIsInstance<MdBlock.Ordered>().single()
        assertEquals(listOf(1, 2), ordered.items.map { it.number })

        val tasks = MdBlocks.parse("- [ ] todo\n- [x] done").filterIsInstance<MdBlock.Bullets>().single()
        assertEquals(listOf(true, false), tasks.items.map { it.task })
    }

    @Test
    fun `a pipe inside inline code does not split a table cell`() {
        val cells = MdBlocks.splitRow("| `a|b` | c\\|d |")
        assertEquals(2, cells.size)
        assertEquals("`a|b`", cells[0].trim())
        assertEquals("c|d", cells[1].trim())
    }

    @Test
    fun `a very long single line is one paragraph`() {
        val line = "a".repeat(50_000)
        val blocks = MdBlocks.parse(line)
        assertEquals(1, blocks.size)
        assertEquals(line, (blocks.single() as MdBlock.Paragraph).text)
    }

    @Test
    fun `inline elements are parsed`() {
        val inline = MdInlineParser.parse(
            "**b** _i_ ***bi*** ~~s~~ `code` [link](https://example.com)"
        )
        assertTrue(inline.any { it is MdInline.Bold })
        assertTrue(inline.any { it is MdInline.Italic })
        assertTrue(inline.any { it is MdInline.BoldItalic })
        assertTrue(inline.any { it is MdInline.Strike })
        assertEquals("code", inline.filterIsInstance<MdInline.Code>().single().text)
        assertEquals(
            "https://example.com",
            inline.filterIsInstance<MdInline.Link>().single().url,
        )
    }

    @Test
    fun `the incremental parser caches finished blocks`() {
        val parser = IncrementalMarkdown()
        val first = parser.blocks("One\n\nTwo")
        val second = parser.blocks("One\n\nTwo and more")
        assertEquals(first.first(), second.first())
        assertTrue((second.last() as MdBlock.Paragraph).text.startsWith("Two and more"))
    }

    @Test
    fun `headings, quotes and rules are recognised`() {
        val blocks = MdBlocks.parse("## Title\n\n> quoted\n\n---")
        assertTrue(blocks[0] is MdBlock.Heading)
        assertTrue(blocks[1] is MdBlock.Quote)
        assertTrue(blocks[2] is MdBlock.Rule)
    }
}
