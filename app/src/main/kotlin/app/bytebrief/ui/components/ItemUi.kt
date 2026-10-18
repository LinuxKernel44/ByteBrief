package app.bytebrief.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.bytebrief.data.db.ContentItem
import app.bytebrief.domain.BodyState
import app.bytebrief.ui.glass.frostedCard
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType
import app.bytebrief.ui.images.ImageLoader
import app.bytebrief.ui.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

val LocalImageLoader = compositionLocalOf<ImageLoader?> { null }

/** Visual identity of a source: label, color and icon. */
data class SourceStyle(val label: String, val color: Color, val icon: Ic)

fun sourceStyle(code: String): SourceStyle = when (code) {
    "hn" -> SourceStyle("Hacker News", BB.HackerNews, Ic.HN)
    "gh_trending" -> SourceStyle("GitHub Trending", BB.Trending, Ic.FLAME)
    "gh_release" -> SourceStyle("Release", BB.Release, Ic.TAG)
    "xda" -> SourceStyle("XDA Forums", BB.Forum, Ic.FORUM)
    else -> SourceStyle("RSS", BB.Rss, Ic.RSS)
}

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, widthPx: Int = 480) {
    val loader = LocalImageLoader.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, loader) {
        if (url == null || loader == null || !loader.isEnabled) return@LaunchedEffect
        bitmap = loader.cached(url, widthPx)?.asImageBitmap()
            ?: withContext(Dispatchers.IO) { loader.load(url, widthPx)?.asImageBitmap() }
    }
    val b = bitmap
    if (b != null) Image(b, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}

private fun meta(item: ContentItem): JSONObject = try { JSONObject(item.metadataJson ?: "{}") } catch (e: Exception) { JSONObject() }

/** The offline state of an item's text: complete, partial, or nothing but a title. */
@Composable
fun CacheIndicator(item: ContentItem, modifier: Modifier = Modifier) {
    val (icon, text, color) = when (item.bodyState) {
        BodyState.FULL -> Triple(Ic.CHECK, "Full text saved", BB.Success)
        BodyState.SNIPPET -> Triple(Ic.CLOUD_OFF, "Preview only", BB.Warm)
        else -> Triple(Ic.CLOUD_OFF, "Title only", BB.TextTertiary)
    }
    Row(modifier.semantics { contentDescription = "Offline: $text" }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, color, size = 12.dp, stroke = 2.4f)
        Txt(text, style = BBType.Caption, color = color, maxLines = 1)
    }
}

@Composable
fun ItemCard(
    item: ContentItem,
    groupSize: Int,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier,
    reason: String? = null,
) {
    val style = sourceStyle(item.sourceType)
    val label = item.sourceLabel?.takeIf { it.isNotBlank() && item.sourceType == "rss" } ?: style.label
    Column(
        modifier
            .fillMaxWidth()
            .frostedCard(20.dp)
            .tappable(onClick, description = "${item.title}. ${style.label}.", role = androidx.compose.ui.semantics.Role.Button, minSize = 96.dp)
            .padding(start = 16.dp, top = 14.dp, bottom = 12.dp, end = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!item.isRead) Box(Modifier.size(8.dp).clip(CircleShape).background(BB.Accent).semantics { contentDescription = "Unread" })
            Icon(style.icon, style.color, size = 14.dp, stroke = 2.2f)
            Txt(label, style = BBType.Caption, color = style.color, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            Txt("· ${Format.relativeTime(item.publishedAt)}", style = BBType.Caption, maxLines = 1)
            if (groupSize > 1) Badge("+${groupSize - 1} sources", BB.Accent, icon = Ic.LAYERS)
            Box(Modifier.weight(1f))
            PlainIconButton(
                if (item.isBookmarked) Ic.BOOKMARK_FILLED else Ic.BOOKMARK,
                if (item.isBookmarked) "Remove bookmark" else "Bookmark",
                onBookmark,
                tint = if (item.isBookmarked) BB.Accent else BB.TextTertiary,
            )
        }
        Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(item.title, style = BBType.Heading.copy(color = if (item.isRead) BB.TextSecondary else BB.TextPrimary), maxLines = 3)
                val snippet = item.description
                if (!snippet.isNullOrBlank()) Txt(snippet, style = BBType.BodySecondary, maxLines = 2)
            }
            RemoteImage(item.imageUrl, Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)))
        }
        Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MetaChips(item)
            Box(Modifier.weight(1f))
            CacheIndicator(item)
        }
        if (reason != null) Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.SPARK, BB.Accent, size = 13.dp, stroke = 2.2f)
            Txt(reason, style = BBType.Caption, color = BB.Accent, maxLines = 1)
        }
    }
}

@Composable
fun MetaChips(item: ContentItem) {
    val m = remember(item.metadataJson) { meta(item) }
    when (item.sourceType) {
        "hn" -> Txt("▲ ${Format.count(item.score)}  ·  ${item.commentCount} comments", style = BBType.Caption, maxLines = 1)
        "gh_trending" -> {
            val gained = if (m.has("stars_in_period")) "+${Format.count(m.optLong("stars_in_period"))} " + when (m.optString("period")) { "weekly" -> "this week"; "monthly" -> "this month"; else -> "today" } else null
            val lang = if (m.has("programming_language")) m.optString("programming_language") else null
            Txt(listOfNotNull(gained, lang).joinToString("  ·  ").ifEmpty { "${Format.count(item.score)} stars" }, style = BBType.Caption, maxLines = 1)
        }
        "gh_release" -> {
            val pre = if (m.optBoolean("prerelease")) "  ·  pre-release" else ""
            Txt(m.optString("tag").ifEmpty { "release" } + pre, style = BBType.Caption, maxLines = 1)
        }
        "xda" -> Txt(listOfNotNull(m.optString("prefix").takeIf { it.isNotEmpty() }, "${item.commentCount} replies", "${Format.count(item.score)} views").joinToString("  ·  "), style = BBType.Caption, maxLines = 1)
        else -> if (!item.author.isNullOrBlank()) Txt(item.author!!, style = BBType.Caption, maxLines = 1)
    }
}
