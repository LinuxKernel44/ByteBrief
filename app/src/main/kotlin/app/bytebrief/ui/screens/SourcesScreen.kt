package app.bytebrief.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bytebrief.domain.SourceType
import app.bytebrief.source.github.TrendingSource
import app.bytebrief.source.hn.HackerNewsSource
import app.bytebrief.ui.LocalNav
import app.bytebrief.ui.Route
import app.bytebrief.ui.components.Chip
import app.bytebrief.ui.components.Divider
import app.bytebrief.ui.components.GlassButton
import app.bytebrief.ui.components.GlassTopBar
import app.bytebrief.ui.glass.BackdropSource
import app.bytebrief.ui.glass.GlassScreen
import app.bytebrief.ui.components.Ic
import app.bytebrief.ui.components.Icon
import app.bytebrief.ui.components.Panel
import app.bytebrief.ui.components.PlainIconButton
import app.bytebrief.ui.components.SectionHeader
import app.bytebrief.ui.components.SwitchRow
import app.bytebrief.ui.components.TextInput
import app.bytebrief.ui.components.Txt
import app.bytebrief.ui.components.bottomContentPadding
import app.bytebrief.ui.components.topContentPadding
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType
import app.bytebrief.ui.util.Format
import app.bytebrief.ui.util.Versioned
import app.bytebrief.ui.util.observeAsState
import app.bytebrief.ui.viewModelOf
import app.bytebrief.ui.vm.ForumsViewModel
import app.bytebrief.ui.vm.SourcesViewModel
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SourcesScreen() {
    val vm = viewModelOf<SourcesViewModel>()
    val fvm = viewModelOf<ForumsViewModel>()
    val forums by fvm.forums.observeAsState()
    val forumMessage by fvm.message.observeAsState()
    val forumBusy by fvm.busy.observeAsState()
    val verifyRequested by fvm.verificationRequested.observeAsState()
    val forumVersion by fvm.settingsVersion.observeAsState()
    val fs = remember(forumVersion) { Versioned(fvm.settings(), forumVersion) }
    var forumUrl by remember { mutableStateOf("") }
    val nav = LocalNav.current
    LaunchedEffect(verifyRequested) { if (verifyRequested == true) nav.push(Route.Verify) }
    val context = LocalContext.current
    val feeds by vm.feeds.observeAsState()
    val repos by vm.repos.observeAsState()
    val message by vm.message.observeAsState()
    val busy by vm.busy.observeAsState()
    val version by vm.settingsVersion.observeAsState()
    // Keyed on the version so this scope reads (and therefore subscribes to) the counter; the settings object itself is mutable.
    val s = remember(version) { Versioned(vm.settings(), version) }
    var feedUrl by remember { mutableStateOf("") }
    var repoRef by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(s.v.trendingLanguage().orEmpty()) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readNBytes(2 * 1024 * 1024).toString(Charsets.UTF_8) } }.getOrNull()
            if (text != null) vm.importOpml(text) else vm.message.value = "That file couldn't be read."
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri: Uri? ->
        if (uri != null) Thread {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportOpml().toByteArray(Charsets.UTF_8)) } }.isSuccess
            vm.message.postValue(if (ok) "Feeds exported." else "Couldn't write the file.")
        }.start()
    }

    GlassScreen(Modifier.fillMaxSize().imePadding(), tabs = false) {
        BackdropSource {
LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(false)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (message != null) item {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(message!!, style = BBType.Body, color = BB.Accent, modifier = Modifier.weight(1f))
                    PlainIconButton(Ic.CLOSE, "Dismiss message", { vm.consumeMessage() })
                }
            }
            item { SectionHeader("BUILT-IN SOURCES") }
            item {
                Panel {
                    SwitchRow("Hacker News", "Stories from the official API", s.v.isSourceEnabled(SourceType.HACKER_NEWS), { vm.setSourceEnabled(SourceType.HACKER_NEWS, it) })
                    if (s.v.isSourceEnabled(SourceType.HACKER_NEWS)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val on = s.v.hackerNewsFeeds()
                            for (f in HackerNewsSource.Feed.entries) Chip(when (f) { HackerNewsSource.Feed.TOP -> "Top"; HackerNewsSource.Feed.NEW -> "New"; HackerNewsSource.Feed.BEST -> "Best"; HackerNewsSource.Feed.SHOW -> "Show HN"; HackerNewsSource.Feed.ASK -> "Ask HN" }, f in on, { vm.toggleHnFeed(f) })
                        }
                    }
                    Divider()
                    SwitchRow("GitHub Trending", "Read from the public trending page, enriched with the GitHub API", s.v.isSourceEnabled(SourceType.GITHUB_TRENDING), { vm.setSourceEnabled(SourceType.GITHUB_TRENDING, it) })
                    if (s.v.isSourceEnabled(SourceType.GITHUB_TRENDING)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (p in TrendingSource.Period.entries) Chip(p.name.lowercase().replaceFirstChar { it.uppercase() }, s.v.trendingPeriod() == p, { vm.setTrendingPeriod(p) })
                        }
                        TextInput(language, { language = it; vm.setTrendingLanguage(it) }, "Language filter, e.g. java or c++ (optional)")
                        Txt("GitHub Trending has no official API, so this reads the page and may break if GitHub changes it.", style = BBType.Caption, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Divider()
                    SwitchRow("Releases", "New releases of repositories you follow", s.v.isSourceEnabled(SourceType.GITHUB_RELEASE), { vm.setSourceEnabled(SourceType.GITHUB_RELEASE, it) })
                    SwitchRow("Include pre-releases", null, s.v.includePrereleases(), { vm.setIncludePrereleases(it) })
                    Divider()
                    SwitchRow("RSS and Atom feeds", "Your own feeds, below", s.v.isSourceEnabled(SourceType.RSS), { vm.setSourceEnabled(SourceType.RSS, it) })
                }
            }

            item { SectionHeader("RSS FEEDS") }
            item {
                Panel {
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextInput(feedUrl, { feedUrl = it }, "Feed or website address", onSearch = { vm.addFeed(feedUrl); feedUrl = "" }, leading = Ic.RSS)
                        GlassButton(if (busy == true) "Checking…" else "Add feed", { vm.addFeed(feedUrl); feedUrl = "" }, icon = Ic.PLUS, prominent = true, enabled = busy != true && feedUrl.isNotBlank())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassButton("Import OPML", { importer.launch(arrayOf("*/*")) }, icon = Ic.DOWNLOAD)
                            GlassButton("Export OPML", { exporter.launch("bytebrief-feeds.opml") }, icon = Ic.EXTERNAL)
                        }
                        Txt("Only https feeds are accepted. Pasting a website address finds its feed automatically.", style = BBType.Caption)
                    }
                    val list = feeds.orEmpty()
                    if (list.isEmpty()) Txt("No feeds yet.", style = BBType.BodySecondary, modifier = Modifier.padding(bottom = 12.dp))
                    for (f in list) {
                        Divider()
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Txt(f.title ?: f.url, style = BBType.Body, maxLines = 1)
                                    Txt(f.url, style = BBType.Caption, maxLines = 1)
                                }
                                PlainIconButton(Ic.REFRESH, "Refresh ${f.title ?: f.url}", { vm.refreshFeed(f) })
                                PlainIconButton(Ic.TRASH, "Remove ${f.title ?: f.url}", { vm.removeFeed(f) }, tint = BB.Danger)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt(
                                    when {
                                        f.lastError != null -> "⚠ ${f.lastError}"
                                        f.lastFetchedAt > 0 -> "Updated ${Format.ago(f.lastFetchedAt)}"
                                        else -> "Not fetched yet"
                                    },
                                    style = BBType.Caption, color = if (f.lastError != null) BB.Warm else BB.TextTertiary, maxLines = 2, modifier = Modifier.weight(1f),
                                )
                                Chip(if (f.enabled) "Enabled" else "Disabled", f.enabled, { vm.setFeedEnabled(f, !f.enabled) })
                            }
                        }
                    }
                }
            }

            item { SectionHeader("XDA FORUMS") }
            item {
                Panel {
                    SwitchRow("XDA Forums", "Threads of the forums you follow. Pages are loaded through the system web view.", s.v.isSourceEnabled(SourceType.XDA_FORUM), { vm.setSourceEnabled(SourceType.XDA_FORUM, it) })
                    SwitchRow("Only on Wi-Fi", "Forum pages are large; background refreshes wait for an unmetered network.", fs.v.forumsWifiOnly(), { fvm.setWifiOnly(it) })
                    Divider()
                    if (forumMessage != null) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Txt(forumMessage!!, style = BBType.Body, color = BB.Accent, modifier = Modifier.weight(1f))
                            PlainIconButton(Ic.CLOSE, "Dismiss message", { fvm.consumeMessage() })
                        }
                    }
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextInput(forumUrl, { forumUrl = it }, "Forum address, e.g. xdaforums.com/f/oneplus-15.12971/", onSearch = { fvm.addForum(forumUrl); forumUrl = "" }, leading = Ic.FORUM)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassButton(if (forumBusy == true) "Checking…" else "Follow forum", { fvm.addForum(forumUrl); forumUrl = "" }, icon = Ic.PLUS, prominent = true, enabled = forumBusy != true && forumUrl.isNotBlank())
                            GlassButton("Browse forums", { nav.push(Route.ForumBrowser) }, icon = Ic.LAYERS)
                        }
                        Txt("Public pages only. ByteBrief never signs in to XDA and loads at most a few pages per forum every few hours.", style = BBType.Caption)
                    }
                    val list = forums.orEmpty()
                    if (list.isEmpty()) Txt("You're not following any forums.", style = BBType.BodySecondary, modifier = Modifier.padding(bottom = 12.dp))
                    for (f in list) {
                        Divider()
                        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Txt(f.title ?: f.url, style = BBType.Body, maxLines = 1)
                                    Txt(f.url, style = BBType.Caption, maxLines = 1)
                                }
                                PlainIconButton(Ic.REFRESH, "Refresh ${f.title ?: f.url}", { fvm.refresh(f) })
                                PlainIconButton(Ic.TRASH, "Stop following ${f.title ?: f.url}", { fvm.remove(f) }, tint = BB.Danger)
                            }
                            if (f.needsVerification) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Ic.LOCK, BB.Warm, size = 18.dp)
                                    Txt("Action required: XDA Forums wants to check that you are human.", style = BBType.Caption, color = BB.Warm, maxLines = 3, modifier = Modifier.weight(1f))
                                    GlassButton("Verify", { nav.push(Route.Verify) }, prominent = true)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Txt(
                                    when {
                                        f.needsVerification -> "Waiting for verification"
                                        f.lastError != null -> "⚠ ${f.lastError}"
                                        f.lastFetchedAt > 0 -> "Updated ${Format.ago(f.lastFetchedAt)}"
                                        else -> "Not fetched yet"
                                    },
                                    style = BBType.Caption, color = if (f.lastError != null) BB.Warm else BB.TextTertiary, maxLines = 2, modifier = Modifier.weight(1f),
                                )
                                Chip(if (f.notifyNewThreads) "Alerts on" else "Alerts off", f.notifyNewThreads, { fvm.setNotify(f, !f.notifyNewThreads) })
                                Chip(if (f.enabled) "On" else "Off", f.enabled, { fvm.setEnabled(f, !f.enabled) })
                            }
                        }
                    }
                }
            }

            item { SectionHeader("FOLLOWED REPOSITORIES") }
            item {
                Panel {
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextInput(repoRef, { repoRef = it }, "owner/name or a github.com link", onSearch = { vm.trackRepo(repoRef); repoRef = "" }, leading = Ic.TAG)
                        GlassButton("Follow repository", { vm.trackRepo(repoRef); repoRef = "" }, icon = Ic.PLUS, prominent = true, enabled = busy != true && repoRef.isNotBlank())
                    }
                    val list = repos.orEmpty()
                    if (list.isEmpty()) Txt("You're not following any repositories.", style = BBType.BodySecondary, modifier = Modifier.padding(bottom = 12.dp))
                    for (r in list) {
                        Divider()
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Txt("${r.owner}/${r.name}", style = BBType.Body, maxLines = 1)
                                Txt(r.lastError?.let { "⚠ $it" } ?: if (r.lastCheckedAt > 0) "Checked ${Format.ago(r.lastCheckedAt)}" else "Not checked yet", style = BBType.Caption, color = if (r.lastError != null) BB.Warm else BB.TextTertiary, maxLines = 2)
                            }
                            Chip(if (r.enabled) "On" else "Off", r.enabled, { vm.setRepoEnabled(r, !r.enabled) })
                            PlainIconButton(Ic.TRASH, "Stop following ${r.owner}/${r.name}", { vm.untrack(r) }, tint = BB.Danger)
                        }
                    }
                }
            }

            item { SectionHeader("GITHUB ACCESS (OPTIONAL)") }
            item {
                Panel {
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Txt("ByteBrief works without an account. A personal access token only raises GitHub's request limit from 60 to 5,000 per hour. It is stored encrypted with a key in the Android Keystore and never shown again.", style = BBType.BodySecondary)
                        if (vm.hasToken()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Ic.LOCK, BB.Success, size = 18.dp)
                                Txt("A token is saved on this device.", style = BBType.Body, modifier = Modifier.weight(1f))
                                GlassButton("Remove", { vm.clearToken() }, icon = Ic.TRASH)
                            }
                        } else {
                            TextInput(token, { token = it }, "Paste a token (read-only access is enough)", leading = Ic.LOCK)
                            GlassButton("Save token", { vm.saveToken(token); token = "" }, icon = Ic.CHECK, enabled = token.isNotBlank(), confirmLabel = "Saved")
                        }
                    }
                }
            }
        }
}
        GlassTopBar("Sources", onBack = { nav.pop() })
    }
}
