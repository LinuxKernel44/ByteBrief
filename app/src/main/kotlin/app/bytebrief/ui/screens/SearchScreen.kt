package app.bytebrief.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bytebrief.ui.LocalNav
import app.bytebrief.ui.Route
import app.bytebrief.ui.components.Chip
import app.bytebrief.ui.components.GlassTopBar
import app.bytebrief.ui.glass.BackdropSource
import app.bytebrief.ui.glass.GlassScreen
import app.bytebrief.ui.components.Ic
import app.bytebrief.ui.components.ItemCard
import app.bytebrief.ui.components.StateMessage
import app.bytebrief.ui.components.TextInput
import app.bytebrief.ui.components.bottomContentPadding
import app.bytebrief.ui.components.topContentPadding
import app.bytebrief.ui.util.observeAsState
import app.bytebrief.ui.viewModelOf
import app.bytebrief.ui.vm.SearchViewModel
import app.bytebrief.ui.vm.FeedViewModel

@Composable
fun SearchScreen() {
    val vm = viewModelOf<SearchViewModel>()
    val feed = viewModelOf<FeedViewModel>()
    val nav = LocalNav.current
    val text by vm.text().observeAsState()
    val results by vm.results.observeAsState()
    val languages by vm.languages.observeAsState()
    var days by remember { mutableIntStateOf(0) }
    val f = vm.filters()
    val query = text.orEmpty()

    GlassScreen(Modifier.fillMaxSize().imePadding(), tabs = false) {
        BackdropSource {
LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(false)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TextInput(query, { vm.setText(it) }, "Search titles, text and topics", leading = Ic.SEARCH) }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((code, label) in listOf<Pair<String?, String>>(null to "All", "hn" to "Hacker News", "gh_trending" to "Trending", "gh_release" to "Releases", "rss" to "RSS", "xda" to "XDA")) {
                        Chip(label, f.source == code, { vm.updateFilters { it.source = code } })
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Unread", f.readFilter == 0, { vm.updateFilters { it.readFilter = if (it.readFilter == 0) -1 else 0 } }, icon = Ic.EYE_OFF)
                    Chip("Bookmarked", f.bookmarkedOnly, { vm.updateFilters { it.bookmarkedOnly = !it.bookmarkedOnly } }, icon = Ic.BOOKMARK)
                    for ((d, label) in listOf(0 to "Any time", 1 to "24 h", 7 to "7 days", 30 to "30 days")) {
                        Chip(label, days == d, { days = d; vm.updateFilters { it.sinceMillis = SearchViewModel.since(d) } })
                    }
                    for (lang in languages.orEmpty()) Chip(lang.uppercase(), f.language == lang, { vm.updateFilters { it.language = if (it.language == lang) null else lang } })
                }
            }
            val list = results
            when {
                query.isBlank() -> item { StateMessage(Ic.SEARCH, "Search everything you've collected", "Works offline across titles, saved text, authors and topics. Prefixes match, so \"rust\" also finds \"rustc\".") }
                list == null -> Unit
                list.isEmpty() -> item { StateMessage(Ic.SEARCH, "No results", "Nothing stored matches \"$query\" with these filters.") }
                else -> items(list, key = { it.item.id }) { c ->
                    ItemCard(c.item, c.groupSize, onClick = { nav.push(Route.Detail(c.item.id)) }, onBookmark = { feed.toggleBookmark(c.item.id, !c.item.isBookmarked) })
                }
            }
        }
}
        GlassTopBar("Search", onBack = { nav.pop() })
    }
}
