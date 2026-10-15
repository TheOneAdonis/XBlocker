package io.github.xblocker.core

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.*

class RuleCleaningTest {
    // Preserve the previous cleaner as an oracle for the bounded overload's exact output.
    private fun previousClean(text: String) = buildString(text.length) {
        text.codePoints().forEach { cp ->
            if (Character.getType(cp) != Character.FORMAT.toInt() && cp !in 0xFE00..0xFE0F &&
                cp !in 0xE0100..0xE01EF && cp !in 0x180B..0x180F &&
                cp !in listOf(0x034F, 0x115F, 0x1160, 0x3164, 0xFFA0)) appendCodePoint(cp)
        }
    }

    @Test fun `bounded cleaning preserves Unicode and UTF16 truncation semantics`() {
        val random = Random(42)
        val samples = listOf("", "A\u200B\t 中\uFE0F😀B", "\uD800x\uDC00", buildString {
            for (cp in listOf(0x034F, 0x115F, 0x1160, 0x180B, 0x180F, 0x3164, 0xFFA0, 0xE0100, 0xE01EF)) appendCodePoint(cp)
            append("ordinary\ntext")
        }) + List(20) { buildString { repeat(200) { appendCodePoint(random.nextInt(0x110000)) } } }
        for (input in samples) {
            val expected = previousClean(input)
            assertEquals(expected, RuleParser.clean(input))
            for (limit in 0..expected.length + 1) {
                assertEquals(expected.take(limit), RuleParser.clean(input, limit), "limit=$limit")
            }
        }
        assertFailsWith<IllegalArgumentException> { RuleParser.clean("test", -1) }
    }

    @Test fun `visible output limit is applied after removing evasion characters`() {
        val input = "\u200B".repeat(40_000) + "needle" + "z".repeat(1_000_000)
        assertEquals("needle", RuleParser.clean(input, 6))
        val engine = RuleEngine(FilterSettings(cloudEnabled = false, customRules = "needle"), "")
        assertNotNull(engine.match(Tweet("1", input, isReply = true)))
        assertNull(engine.match(Tweet("2", "z".repeat(32_768) + "needle", isReply = true)))
        assertNull(engine.match(Tweet("3", "clean", name = "z".repeat(512) + "needle", isReply = true)))
        assertNull(engine.match(Tweet("4", "clean", isReply = true, urls = listOf("z".repeat(2048) + "needle"))))
    }
}
