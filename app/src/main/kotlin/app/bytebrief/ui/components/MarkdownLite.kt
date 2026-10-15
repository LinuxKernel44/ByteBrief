package app.bytebrief.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType

/**
 * A deliberately small Markdown subset for READMEs and release notes: headings, paragraphs, lists,
 * quotes, fenced code, inline code and bold. Text is only ever drawn, never interpreted: links are
 * shown as their label and nothing in the text can trigger an action.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
}

object MarkdownLite {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+(.*)$")
    private val tableSeparator = Regex("^\\s*\\|?[\\s:|-]+\\|?\\s*$")

    fun parse(text: String, markdown: Boolean): List<MdBlock> {
        val lines = text.replace("\r\n", "\n").split('\n')
        val out = ArrayList<MdBlock>()
        val para = StringBuilder()
        fun flush() { if (para.isNotBlank()) out.add(MdBlock.Paragraph(para.toString().trim())); para.clear() }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!markdown) {
                if (line.isBlank()) flush() else { if (para.isNotEmpty()) para.append(' '); para.append(line.trim()) }
                i++; continue
            }
            if (line.trimStart().startsWith("```")) {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.append(lines[i]).append('\n'); i++ }
                out.add(MdBlock.Code(code.toString().trimEnd()))
                i++; continue
            }
            if (line.trimStart().startsWith("|")) {
                // Tables are shown as one line per row, cells joined by " · "; separator rows are dropped.
                flush()
                if (!tableSeparator.matches(line) || !line.contains("-")) {
                    val cells = line.trim().trim('|').split('|').map { it.trim() }.filter { it.isNotEmpty() }
                    if (cells.isNotEmpty()) out.add(MdBlock.Bullet(cells.joinToString("  ·  ")))
                }
                i++; continue
            }
            val h = heading.matchEntire(line)
            val b = bullet.matchEntire(line)
            when {
                h != null -> { flush(); out.add(MdBlock.Heading(h.groupValues[1].length, h.groupValues[2].trim())) }
                b != null -> { flush(); out.add(MdBlock.Bullet(b.groupValues[1].trim())) }
                line.trimStart().startsWith(">") -> { flush(); out.add(MdBlock.Quote(line.trimStart().removePrefix(">").trim())) }
                line.isBlank() -> flush()
                // Images and HTML-only lines are noise in a text reader.
                line.trim().startsWith("<") || line.trim().startsWith("![") -> flush()
                else -> { if (para.isNotEmpty()) para.append(' '); para.append(line.trim()) }
            }
            i++
        }
        flush()
        return out
    }

    /** Inline formatting: `code`, **bold**, and [label](url) shown as just the label. */
    fun inline(text: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        val t = text.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        while (i < t.length) {
            when {
                t.startsWith("**", i) && t.indexOf("**", i + 2) > 0 -> {
                    val end = t.indexOf("**", i + 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.substring(i + 2, end)) }
                    i = end + 2
                }
                t[i] == '`' && t.indexOf('`', i + 1) > 0 -> {
                    val end = t.indexOf('`', i + 1)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x26FFFFFF))) { append(t.substring(i + 1, end)) }
                    i = end + 1
                }
                else -> { append(t[i]); i++ }
            }
        }
    }
}

@Composable
fun MdBlockView(block: MdBlock, modifier: Modifier = Modifier) {
    when (block) {
        is MdBlock.Heading -> BasicText(
            MarkdownLite.inline(block.text),
            modifier.padding(top = if (block.level <= 2) 14.dp else 8.dp).semantics { heading() },
            style = when (block.level) { 1 -> BBType.Title; 2 -> BBType.Heading.copy(fontWeight = FontWeight.Bold); else -> BBType.Heading },
        )
        is MdBlock.Paragraph -> BasicText(MarkdownLite.inline(block.text), modifier, style = BBType.Body)
        is MdBlock.Bullet -> Row(modifier) {
            Txt("•", style = BBType.Body, color = BB.TextSecondary, modifier = Modifier.padding(end = 10.dp, start = 4.dp))
            BasicText(MarkdownLite.inline(block.text), Modifier.weight(1f), style = BBType.Body)
        }
        is MdBlock.Quote -> Row(modifier) {
            Column(Modifier.padding(end = 10.dp).background(BB.Accent.copy(alpha = 0.6f)).padding(horizontal = 1.5.dp)) { Txt(" ", style = BBType.Body) }
            BasicText(MarkdownLite.inline(block.text), Modifier.weight(1f), style = BBType.BodySecondary)
        }
        is MdBlock.Code -> Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x33000000)).horizontalScroll(rememberScrollState()).padding(12.dp)) {
            BasicText(block.text, style = BBType.Mono)
        }
    }
}
