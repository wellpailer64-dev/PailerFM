package com.pailer.localtune

import com.pailer.localtune.data.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses simple synced lrc`() {
        val raw = """
            [ti:Song]
            [ar:Artist]
            [00:12.00]Primeira
            [00:15.50]Segunda
        """.trimIndent()
        val lines = LrcParser.parse(raw)
        assertEquals(2, lines.size)
        assertEquals(12_000L, lines[0].timeMs)
        assertEquals("Primeira", lines[0].text)
        assertEquals(15_500L, lines[1].timeMs)
        assertTrue(LrcParser.isSynced(lines))
    }

    @Test
    fun `multi time-tag line expands and sorts`() {
        val lines = LrcParser.parse("[00:10.00][01:04.00]Refrao")
        assertEquals(2, lines.size)
        assertEquals(10_000L, lines[0].timeMs)
        assertEquals(64_000L, lines[1].timeMs)
        assertEquals("Refrao", lines[0].text)
        assertEquals("Refrao", lines[1].text)
    }

    @Test
    fun `centiseconds vs milliseconds`() {
        assertEquals(1_230L, LrcParser.parse("[00:01.23]x")[0].timeMs)   // centesimos
        assertEquals(1_234L, LrcParser.parse("[00:01.234]x")[0].timeMs)  // milis
        assertEquals(1_000L, LrcParser.parse("[00:01]x")[0].timeMs)      // sem fracao
    }

    @Test
    fun `offset tag shifts times earlier`() {
        val lines = LrcParser.parse("[offset:+500]\n[00:10.00]a")
        assertEquals(9_500L, lines[0].timeMs)
    }

    @Test
    fun `plain text produces untimed lines in order`() {
        val lines = LrcParser.parse("linha um\nlinha dois\n\nlinha tres")
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.timeMs == null })
        assertEquals("linha um", lines[0].text)
        assertEquals("linha tres", lines[2].text)
        assertFalse(LrcParser.isSynced(lines))
    }

    @Test
    fun `flattenToPlain strips tags and metadata`() {
        val raw = "[ti:T]\n[ar:A]\n[00:01.00]uma\n[00:02.00]duas"
        assertEquals("uma\nduas", LrcParser.flattenToPlain(raw))
    }

    @Test
    fun `malformed time tag treated as plain text`() {
        val lines = LrcParser.parse("[99:99:99]texto quebrado")
        assertEquals(1, lines.size)
        assertEquals(null, lines[0].timeMs)
    }

    @Test
    fun `currentLineIndex boundaries`() {
        val lines = LrcParser.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c")
        assertEquals(-1, LrcParser.currentLineIndex(lines, 0L))
        assertEquals(0, LrcParser.currentLineIndex(lines, 1_000L))
        assertEquals(0, LrcParser.currentLineIndex(lines, 4_999L))
        assertEquals(1, LrcParser.currentLineIndex(lines, 5_000L))
        assertEquals(2, LrcParser.currentLineIndex(lines, 999_999L))
        assertEquals(-1, LrcParser.currentLineIndex(emptyList(), 1_000L))
    }
}
