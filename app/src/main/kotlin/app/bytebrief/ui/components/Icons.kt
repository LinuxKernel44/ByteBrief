package app.bytebrief.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The icon set: one consistent 24-unit stroke style, drawn directly so it needs no icon font or asset. */
enum class Ic {
    SPARK, LIST, SUN, BOOKMARK, BOOKMARK_FILLED, SLIDERS, SEARCH, BACK, CLOSE, REFRESH, LINK, CHECK, PLUS, MINUS,
    FLAME, TAG, RSS, HN, CHIP, DOWNLOAD, TRASH, PAUSE, PLAY, THUMB_UP, THUMB_DOWN, EYE, EYE_OFF, EXTERNAL, CLOUD_OFF,
    LAYERS, CHEVRON_RIGHT, FILTER, QUESTION, WARNING, STAR, FORK, LOCK
}

@Composable
fun Icon(icon: Ic, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp, stroke: Float = 1.9f) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val s = Stroke(width = stroke * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        draw(icon, tint, u, s)
    }
}

private fun DrawScope.draw(icon: Ic, c: Color, u: Float, s: Stroke) {
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, p(x1, y1), p(x2, y2), s.width, StrokeCap.Round)
    fun path(block: Path.() -> Unit, fill: Boolean = false) {
        val path = Path().apply(block)
        if (fill) drawPath(path, c) else drawPath(path, c, style = s)
    }
    fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) {
        if (fill) drawCircle(c, r * u, p(cx, cy)) else drawCircle(c, r * u, p(cx, cy), style = s)
    }
    when (icon) {
        Ic.SPARK -> path({
            moveTo(12 * u, 3 * u); quadraticTo(13.2f * u, 10.8f * u, 21 * u, 12 * u)
            quadraticTo(13.2f * u, 13.2f * u, 12 * u, 21 * u); quadraticTo(10.8f * u, 13.2f * u, 3 * u, 12 * u)
            quadraticTo(10.8f * u, 10.8f * u, 12 * u, 3 * u); close()
        })
        Ic.LIST -> { line(5f, 7f, 19f, 7f); line(5f, 12f, 19f, 12f); line(5f, 17f, 14f, 17f) }
        Ic.SUN -> { circle(12f, 12f, 4f); for (i in 0 until 8) { val a = Math.PI / 4 * i; line(12f + (7f * Math.cos(a)).toFloat(), 12f + (7f * Math.sin(a)).toFloat(), 12f + (9.5f * Math.cos(a)).toFloat(), 12f + (9.5f * Math.sin(a)).toFloat()) } }
        Ic.BOOKMARK -> path({ moveTo(7 * u, 4 * u); lineTo(17 * u, 4 * u); lineTo(17 * u, 20 * u); lineTo(12 * u, 16 * u); lineTo(7 * u, 20 * u); close() })
        Ic.BOOKMARK_FILLED -> path({ moveTo(7 * u, 4 * u); lineTo(17 * u, 4 * u); lineTo(17 * u, 20 * u); lineTo(12 * u, 16 * u); lineTo(7 * u, 20 * u); close() }, fill = true)
        Ic.SLIDERS -> { line(4f, 8f, 20f, 8f); line(4f, 16f, 20f, 16f); circle(9f, 8f, 2.3f, true); circle(15f, 16f, 2.3f, true) }
        Ic.SEARCH -> { circle(11f, 11f, 6f); line(15.5f, 15.5f, 20f, 20f) }
        Ic.BACK -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
        Ic.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Ic.REFRESH -> { path({ moveTo(19 * u, 12 * u); arcTo(androidx.compose.ui.geometry.Rect(5 * u, 5 * u, 19 * u, 19 * u), 0f, -270f, false) }); line(19f, 5f, 19f, 9f); line(19f, 9f, 15f, 9f) }
        Ic.LINK -> { path({ moveTo(10 * u, 14 * u); lineTo(14 * u, 10 * u) }); path({ moveTo(11 * u, 7 * u); lineTo(12.5f * u, 5.5f * u); quadraticTo(15.5f * u, 3 * u, 18 * u, 5.5f * u); quadraticTo(20.5f * u, 8 * u, 18 * u, 11 * u); lineTo(16.5f * u, 12.5f * u) }); path({ moveTo(13 * u, 17 * u); lineTo(11.5f * u, 18.5f * u); quadraticTo(8.5f * u, 21 * u, 6 * u, 18.5f * u); quadraticTo(3.5f * u, 16 * u, 6 * u, 13 * u); lineTo(7.5f * u, 11.5f * u) }) }
        Ic.CHECK -> { line(5f, 12.5f, 10f, 17.5f); line(10f, 17.5f, 19f, 7f) }
        Ic.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        Ic.MINUS -> line(5f, 12f, 19f, 12f)
        Ic.FLAME -> path({ moveTo(12 * u, 3 * u); quadraticTo(13 * u, 8 * u, 17 * u, 11 * u); quadraticTo(19 * u, 14 * u, 17.5f * u, 17.5f * u); quadraticTo(15.5f * u, 21 * u, 12 * u, 21 * u); quadraticTo(8 * u, 21 * u, 6.5f * u, 17.5f * u); quadraticTo(5.5f * u, 14.5f * u, 8 * u, 12 * u); quadraticTo(8.5f * u, 14 * u, 10 * u, 14.5f * u); quadraticTo(9 * u, 8 * u, 12 * u, 3 * u); close() })
        Ic.TAG -> { path({ moveTo(4 * u, 12 * u); lineTo(12 * u, 4 * u); lineTo(20 * u, 4 * u); lineTo(20 * u, 12 * u); lineTo(12 * u, 20 * u); close() }); circle(16f, 8f, 1.2f, true) }
        Ic.RSS -> { circle(6f, 18f, 1.6f, true); path({ moveTo(5 * u, 11 * u); quadraticTo(13 * u, 11 * u, 13 * u, 19 * u) }); path({ moveTo(5 * u, 5 * u); quadraticTo(19 * u, 5 * u, 19 * u, 19 * u) }) }
        Ic.HN -> { path({ moveTo(6 * u, 5 * u); lineTo(12 * u, 13 * u); lineTo(18 * u, 5 * u) }); line(12f, 13f, 12f, 20f) }
        Ic.CHIP -> { drawRoundRect(c, p(7f, 7f), Size(10 * u, 10 * u), androidx.compose.ui.geometry.CornerRadius(2 * u), style = s); for (i in 0..2) { val t = 9.5f + i * 2.5f; line(t, 3.5f, t, 7f); line(t, 17f, t, 20.5f); line(3.5f, t, 7f, t); line(17f, t, 20.5f, t) } }
        Ic.DOWNLOAD -> { line(12f, 4f, 12f, 15f); line(7f, 10.5f, 12f, 15.5f); line(17f, 10.5f, 12f, 15.5f); line(5f, 20f, 19f, 20f) }
        Ic.TRASH -> { line(5f, 7f, 19f, 7f); line(10f, 4f, 14f, 4f); path({ moveTo(7 * u, 7 * u); lineTo(8 * u, 20 * u); lineTo(16 * u, 20 * u); lineTo(17 * u, 7 * u) }) }
        Ic.PAUSE -> { line(9f, 6f, 9f, 18f); line(15f, 6f, 15f, 18f) }
        Ic.PLAY -> path({ moveTo(8 * u, 5 * u); lineTo(19 * u, 12 * u); lineTo(8 * u, 19 * u); close() })
        Ic.THUMB_UP -> path({ moveTo(8 * u, 11 * u); lineTo(12 * u, 4 * u); quadraticTo(15 * u, 4 * u, 14.5f * u, 8 * u); lineTo(14 * u, 10 * u); lineTo(19 * u, 10 * u); quadraticTo(20.5f * u, 10.5f * u, 20 * u, 12.5f * u); lineTo(18.5f * u, 18 * u); quadraticTo(18 * u, 19.5f * u, 16.5f * u, 19.5f * u); lineTo(8 * u, 19.5f * u); close() })
        Ic.THUMB_DOWN -> path({ moveTo(8 * u, 13 * u); lineTo(12 * u, 20 * u); quadraticTo(15 * u, 20 * u, 14.5f * u, 16 * u); lineTo(14 * u, 14 * u); lineTo(19 * u, 14 * u); quadraticTo(20.5f * u, 13.5f * u, 20 * u, 11.5f * u); lineTo(18.5f * u, 6 * u); quadraticTo(18 * u, 4.5f * u, 16.5f * u, 4.5f * u); lineTo(8 * u, 4.5f * u); close() })
        Ic.EYE -> { path({ moveTo(3 * u, 12 * u); quadraticTo(12 * u, 2 * u, 21 * u, 12 * u); quadraticTo(12 * u, 22 * u, 3 * u, 12 * u); close() }); circle(12f, 12f, 3f) }
        Ic.EYE_OFF -> { path({ moveTo(3 * u, 12 * u); quadraticTo(12 * u, 2 * u, 21 * u, 12 * u); quadraticTo(12 * u, 22 * u, 3 * u, 12 * u); close() }); line(4f, 4f, 20f, 20f) }
        Ic.EXTERNAL -> { path({ moveTo(14 * u, 4 * u); lineTo(20 * u, 4 * u); lineTo(20 * u, 10 * u) }); line(20f, 4f, 11f, 13f); path({ moveTo(18 * u, 14 * u); lineTo(18 * u, 19 * u); lineTo(5 * u, 19 * u); lineTo(5 * u, 6 * u); lineTo(10 * u, 6 * u) }) }
        Ic.CLOUD_OFF -> { path({ moveTo(7 * u, 18 * u); quadraticTo(3 * u, 18 * u, 3 * u, 14 * u); quadraticTo(3 * u, 10.5f * u, 7 * u, 10 * u); quadraticTo(8 * u, 6 * u, 12 * u, 6 * u); quadraticTo(17 * u, 6 * u, 17.5f * u, 10.5f * u); quadraticTo(21 * u, 11 * u, 21 * u, 14 * u); quadraticTo(21 * u, 18 * u, 17 * u, 18 * u); close() }); line(4f, 4f, 20f, 20f) }
        Ic.LAYERS -> { path({ moveTo(12 * u, 4 * u); lineTo(21 * u, 9 * u); lineTo(12 * u, 14 * u); lineTo(3 * u, 9 * u); close() }); path({ moveTo(3 * u, 13 * u); lineTo(12 * u, 18 * u); lineTo(21 * u, 13 * u) }) }
        Ic.CHEVRON_RIGHT -> { line(9f, 5f, 16f, 12f); line(16f, 12f, 9f, 19f) }
        Ic.FILTER -> path({ moveTo(4 * u, 5 * u); lineTo(20 * u, 5 * u); lineTo(14 * u, 12.5f * u); lineTo(14 * u, 19 * u); lineTo(10 * u, 17 * u); lineTo(10 * u, 12.5f * u); close() })
        Ic.QUESTION -> { circle(12f, 12f, 9f); path({ moveTo(9.5f * u, 9.5f * u); quadraticTo(9.5f * u, 7 * u, 12 * u, 7 * u); quadraticTo(14.5f * u, 7 * u, 14.5f * u, 9.5f * u); quadraticTo(14.5f * u, 11 * u, 12 * u, 12 * u); lineTo(12 * u, 13.5f * u) }); circle(12f, 16.5f, 0.9f, true) }
        Ic.WARNING -> { path({ moveTo(12 * u, 4 * u); lineTo(21 * u, 20 * u); lineTo(3 * u, 20 * u); close() }); line(12f, 10f, 12f, 14.5f); circle(12f, 17.2f, 0.9f, true) }
        Ic.STAR -> path({ moveTo(12 * u, 3.5f * u); lineTo(14.6f * u, 9.2f * u); lineTo(20.8f * u, 9.8f * u); lineTo(16.1f * u, 14 * u); lineTo(17.5f * u, 20.2f * u); lineTo(12 * u, 17 * u); lineTo(6.5f * u, 20.2f * u); lineTo(7.9f * u, 14 * u); lineTo(3.2f * u, 9.8f * u); lineTo(9.4f * u, 9.2f * u); close() })
        Ic.FORK -> { circle(7f, 5f, 2f); circle(17f, 5f, 2f); circle(12f, 19f, 2f); path({ moveTo(7 * u, 7 * u); lineTo(7 * u, 10 * u); quadraticTo(7 * u, 12 * u, 9 * u, 12 * u); lineTo(15 * u, 12 * u); quadraticTo(17 * u, 12 * u, 17 * u, 10 * u); lineTo(17 * u, 7 * u) }); line(12f, 12f, 12f, 17f) }
        Ic.LOCK -> { drawRoundRect(c, p(5f, 10.5f), Size(14 * u, 10 * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = s); path({ moveTo(8 * u, 10.5f * u); lineTo(8 * u, 8 * u); quadraticTo(8 * u, 4 * u, 12 * u, 4 * u); quadraticTo(16 * u, 4 * u, 16 * u, 8 * u); lineTo(16 * u, 10.5f * u) }) }
    }
}
