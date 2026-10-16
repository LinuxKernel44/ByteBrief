package app.bytebrief.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bytebrief.data.db.ContentItem
import app.bytebrief.data.db.ContentListItem
import app.bytebrief.ranking.RankedItem
import app.bytebrief.repo.ContentRepository
import app.bytebrief.ui.LocalNav
import app.bytebrief.ui.Route
import app.bytebrief.ui.components.GlassButton
import app.bytebrief.ui.components.GlassIconButton
import app.bytebrief.ui.components.GlassTopBar
import app.bytebrief.ui.glass.BackdropSource
import app.bytebrief.ui.glass.GlassScreen
import app.bytebrief.ui.components.Chip
import app.bytebrief.ui.components.Ic
import app.bytebrief.ui.components.ItemCard
import app.bytebrief.ui.components.PlainIconButton
import app.bytebrief.ui.components.SkeletonCard
import app.bytebrief.ui.components.StateMessage
import app.bytebrief.ui.components.Txt
import app.bytebrief.ui.components.bottomContentPadding
import app.bytebrief.ui.components.topContentPadding
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType
import app.bytebrief.ui.util.Format
import app.bytebrief.ui.util.observeAsState
import app.bytebrief.ui.viewModelOf
import app.bytebrief.ui.vm.FeedViewModel

/** Sync progress and last result shown under the title bar. */
@Composable
private fun SyncBanner(status: ContentRepository.SyncStatus?) {
    val report = status?.lastReport
    val failed = report?.outcomes?.values?.filter { it.status == app.bytebrief.repo.SyncReport.Status.FAILED || it.status == app.bytebrief.repo.SyncReport.Status.PARTIAL }.orEmpty()
    val text = when {
        status?.running == true -> "Refreshing sources…"
        failed.isNotEmpty() -> "Some sources couldn't be refreshed: " + failed.mapNotNull { it.error }.firstOrNull().orEmpty()
        status != null && status.lastSyncAt > 0 -> "Updated ${Format.ago(status.lastSyncAt)}"
        else -> null
    } ?: return
    val color = if (failed.isNotEmpty() && status?.running != true) BB.Warm else BB.TextTertiary
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (failed.isNotEmpty() && status?.running != true) app.bytebrief.ui.components.Icon(Ic.WARNING, color, size = 14.dp)
        Txt(text, style = BBType.Caption, color = color, maxLines = 2)
    }
}

@Composable
private fun EmptyFeed(onRefresh: () -> Unit, onSources: () -> Unit, syncing: Boolean) {
    StateMessage(
        Ic.SPARK, if (syncing) "Fetching your first items…" else "Nothing here yet",
        if (syncing) "ByteBrief is collecting content from your sources." else "Pull in content from Hacker News, GitHub Trending, releases and your RSS feeds.",
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Refresh", onRefresh, icon = Ic.REFRESH, prominent = true)
                GlassButton("Sources", onSources, icon = Ic.RSS)
            }
        },
    )
}

@Composable
private fun FeedTopBar(title: String, onRefresh: () -> Unit, syncing: Boolean) {
    val nav = LocalNav.current
    GlassTopBar(title, actions = {
        PlainIconButton(Ic.SEARCH, "Search", { nav.push(Route.Search) }, tint = BB.TextPrimary)
        PlainIconButton(Ic.REFRESH, if (syncing) "Refreshing" else "Refresh", onRefresh, tint = if (syncing) BB.Accent else BB.TextPrimary, enabled = !syncing)
    })
}

@Composable
fun ForYouScreen() {
    val vm = viewModelOf<FeedViewModel>()
    val nav = LocalNav.current
    val items by vm.forYou.observeAsState()
    val sync by vm.syncStatus.observeAsState()
    val state = rememberLazyListState()
    GlassScreen(Modifier.fillMaxSize(), tabs = true) {
        val list: List<RankedItem>? = items
        BackdropSource {
LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(true)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SyncBanner(sync) }
            when {
                list == null -> items(4) { SkeletonCard() }
                list.isEmpty() -> item { EmptyFeed({ vm.refresh() }, { nav.push(Route.Sources) }, sync?.running == true) }
                else -> items(list, key = { it.entry.item.id }) { r ->
                    val it = r.entry.item
                    ItemCard(
                        it, r.entry.groupSize, onClick = { nav.push(Route.Detail(it.id)) }, onBookmark = { vm.toggleBookmark(it.id, !it.isBookmarked) },
                        reason = r.reasons.firstOrNull { reason -> reason.contribution > 0.0 && reason.label != "Recent" }?.label,
                    )
                }
            }
        }
}
        FeedTopBar("For You", { vm.refresh() }, sync?.running == true)
    }
}

private val SOURCE_FILTERS = listOf<Triple<String?, String, Ic?>>(
    Triple(null, "All", null), Triple("hn", "Hacker News", Ic.HN), Triple("gh_trending", "Trending", Ic.FLAME),
    Triple("gh_release", "Releases", Ic.TAG), Triple("rss", "RSS", Ic.RSS),
)

@Composable
fun BrowseScreen() {
    val vm = viewModelOf<FeedViewModel>()
    val nav = LocalNav.current
    val items by vm.latest.observeAsState()
    val sync by vm.syncStatus.observeAsState()
    var unread by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<String?>(null) }
    val state = rememberLazyListState()
    GlassScreen(Modifier.fillMaxSize(), tabs = true) {
        val list: List<ContentListItem>? = items
        BackdropSource {
LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(true)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((code, label, icon) in SOURCE_FILTERS) Chip(label, source == code, { source = code; vm.setSource(code) }, icon = icon)
                    Chip("Unread", unread, { unread = !unread; vm.setUnreadOnly(unread) }, icon = Ic.EYE_OFF)
                }
            }
            item { SyncBanner(sync) }
            when {
                list == null -> items(4) { SkeletonCard() }
                list.isEmpty() -> item {
                    if (source != null || unread) StateMessage(Ic.FILTER, "No matches", "Nothing matches these filters. Try another source or turn off Unread.")
                    else EmptyFeed({ vm.refresh() }, { nav.push(Route.Sources) }, sync?.running == true)
                }
                else -> items(list, key = { it.item.id }) { c ->
                    ItemCard(c.item, c.groupSize, onClick = { nav.push(Route.Detail(c.item.id)) }, onBookmark = { vm.toggleBookmark(c.item.id, !c.item.isBookmarked) })
                }
            }
        }
}
        FeedTopBar("Browse", { vm.refresh() }, sync?.running == true)
    }
}

@Composable
fun SavedScreen() {
    val vm = viewModelOf<FeedViewModel>()
    val nav = LocalNav.current
    val items by vm.bookmarks.observeAsState()
    val state = rememberLazyListState()
    GlassScreen(Modifier.fillMaxSize(), tabs = true) {
        val list: List<ContentListItem>? = items
        BackdropSource {
LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(true)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                list == null -> items(3) { SkeletonCard() }
                list.isEmpty() -> item { StateMessage(Ic.BOOKMARK, "Nothing saved yet", "Bookmark an item and its full text is saved so you can read it offline.") }
                else -> items(list, key = { it.item.id }) { c ->
                    ItemCard(c.item, c.groupSize, onClick = { nav.push(Route.Detail(c.item.id)) }, onBookmark = { vm.toggleBookmark(c.item.id, !c.item.isBookmarked) })
                }
            }
        }
}
        GlassTopBar("Saved", actions = { PlainIconButton(Ic.SEARCH, "Search", { nav.push(Route.Search) }, tint = BB.TextPrimary) })
    }
}
