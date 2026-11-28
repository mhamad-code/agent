package com.agent.ai.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen model is hand written, so these tests pin the behaviour a shell depends on:
 * line breaks, carriage return, backspace, colours and the scrollback limit.
 */
class AnsiScreenTest {

    @Test
    fun `plain text becomes one line per newline`() {
        val screen = AnsiScreen()
        screen.feed("one\ntwo")

        val lines = screen.snapshot().lines
        assertEquals(2, lines.size)
        assertEquals("one", lines[0].joinToString("") { it.text })
        assertEquals("two", lines[1].joinToString("") { it.text })
    }

    @Test
    fun `carriage return redraws the line instead of appending`() {
        val screen = AnsiScreen()
        screen.feed("downloading 40%\rdone")
        val line = screen.snapshot().lines.last().joinToString("") { it.text }
        assertEquals("done", line)
    }

    @Test
    fun `backspace removes one column`() {
        val screen = AnsiScreen()
        screen.feed("abc\b\bX")
        val snapshot = screen.snapshot()
        assertEquals("aX", snapshot.lines.last().joinToString("") { it.text })
        assertEquals(2, snapshot.cursorColumn)
    }

    @Test
    fun `sgr colours are kept on the run and reset`() {
        val screen = AnsiScreen()
        screen.feed("\u001b[31mred\u001b[0m plain")
        val runs = screen.snapshot().lines.last()

        assertEquals("red", runs[0].text)
        assertEquals(1, runs[0].fg)
        assertEquals(" plain", runs[1].text)
        assertEquals(null, runs[1].fg)
    }

    @Test
    fun `erasing to the end of the line empties it`() {
        val screen = AnsiScreen()
        screen.feed("secret\u001b[2Kdone")
        assertEquals("done", screen.snapshot().lines.last().joinToString("") { it.text })
    }

    @Test
    fun `private mode sequences are ignored instead of printed`() {
        val screen = AnsiScreen()
        screen.feed("\u001b[?25lhello\u001b[?25h")
        assertEquals("hello", screen.snapshot().lines.last().joinToString("") { it.text })
    }

    @Test
    fun `the scrollback keeps only the newest lines`() {
        val screen = AnsiScreen(scrollbackLimit = 50)
        repeat(500) { index -> screen.feed("line $index\n") }
        val lines = screen.snapshot().lines

        assertTrue(lines.size <= 52)
        val texts = lines.map { line -> line.joinToString("") { it.text } }
        assertEquals("line 499", texts.last { it.isNotBlank() })
        assertTrue(texts.none { it == "line 0" })
    }

    @Test
    fun `clear empties the screen`() {
        val screen = AnsiScreen()
        screen.feed("one\ntwo")
        screen.clear()
        val snapshot = screen.snapshot()
        assertEquals(1, snapshot.lines.size)
        assertEquals("", snapshot.lines[0].joinToString("") { it.text })
    }

    @Test
    fun `an escape sequence split across two reads still works`() {
        val screen = AnsiScreen()
        screen.feed("\u001b[3")
        screen.feed("2mgreen")
        val run = screen.snapshot().lines.last().first()
        assertEquals("green", run.text)
        assertEquals(2, run.fg)
    }
}
