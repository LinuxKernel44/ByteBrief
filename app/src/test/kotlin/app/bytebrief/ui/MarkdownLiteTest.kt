package app.bytebrief.ui

import app.bytebrief.ui.components.MarkdownLite
import app.bytebrief.ui.components.MdBlock
import app.bytebrief.ui.util.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLiteTest {
    @Test
    fun parsesHeadingsParagraphsListsQuotesAndCode() {
        val blocks = MarkdownLite.parse(
            "# Title\n\nFirst line\ncontinues here.\n\n- one\n- two\n1. three\n\n> quoted\n\n```kotlin\nval x = 1\n```\nAfter.",
            markdown = true,
        )
        assertEquals(MdBlock.Heading(1, "Title"), blocks[0])
        assertEquals(MdBlock.Paragraph("First line continues here."), blocks[1])
        assertEquals(listOf(MdBlock.Bullet("one"), MdBlock.Bullet("two"), MdBlock.Bullet("three")), blocks.subList(2, 5))
        assertEquals(MdBlock.Quote("quoted"), blocks[5])
        assertEquals(MdBlock.Code("val x = 1"), blocks[6])
        assertEquals(MdBlock.Paragraph("After."), blocks[7])
    }

    @Test
    fun tablesBecomeReadableRowsAndSeparatorsAreDropped() {
        val blocks = MarkdownLite.parse("| Create | Produce |\n| :--- | :--- |\n| Clone a voice | Dub videos |", markdown = true)
        assertEquals(listOf(MdBlock.Bullet("Create  ·  Produce"), MdBlock.Bullet("Clone a voice  ·  Dub videos")), blocks)
    }

    @Test
    fun htmlAndImageLinesAreDroppedInsteadOfRendered() {
        val blocks = MarkdownLite.parse("<p align=\"center\"><img src=\"x\"></p>\n![logo](x.png)\n\nReal text.", markdown = true)
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph("Real text.")), blocks)
    }

    @Test
    fun plainTextKeepsParagraphsAndIgnoresMarkdownSyntax() {
        val blocks = MarkdownLite.parse("# not a heading\nsecond line\n\nnext", markdown = false)
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph("# not a heading second line"), MdBlock.Paragraph("next")), blocks)
    }

    @Test
    fun unclosedFenceDoesNotLoopForever() {
        val blocks = MarkdownLite.parse("```\ncode without end", markdown = true)
        assertTrue(blocks.single() is MdBlock.Code)
    }

    @Test
    fun formatUsesDecimalUnitsAndRelativeTimes() {
        assertEquals("2.38 GB", Format.bytes(2_375_773_280L))
        assertEquals("1.07 GB", Format.bytes(1_066_227_232L))
        assertEquals("250 MB", Format.bytes(250_000_000L))
        assertEquals("512 B", Format.bytes(512))
        assertEquals("now", Format.relativeTime(1_000, 1_030))
        assertEquals("5m", Format.relativeTime(0, 5 * 60_000L))
        assertEquals("3h", Format.relativeTime(0, 3 * 3_600_000L))
        assertEquals("2d", Format.relativeTime(0, 2 * 86_400_000L))
        assertEquals("1.2k", Format.count(1_234))
        assertEquals("41k", Format.count(41_034))
        assertEquals("2.5M", Format.count(2_500_000))
    }
}
