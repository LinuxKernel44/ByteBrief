package app.bytebrief.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bytebrief.ui.LocalNav
import app.bytebrief.ui.LocalRequestNotifications
import app.bytebrief.ui.components.Chip
import app.bytebrief.ui.components.Divider
import app.bytebrief.ui.components.GlassButton
import app.bytebrief.ui.components.GlassTopBar
import app.bytebrief.ui.glass.BackdropSource
import app.bytebrief.ui.glass.GlassScreen
import app.bytebrief.ui.components.Ic
import app.bytebrief.ui.components.Panel
import app.bytebrief.ui.components.PlainIconButton
import app.bytebrief.settings.AppSettings
import app.bytebrief.ui.components.SectionHeader
import app.bytebrief.ui.components.SwitchRow
import app.bytebrief.ui.components.TextInput
import app.bytebrief.ui.components.Txt
import app.bytebrief.ui.components.bottomContentPadding
import app.bytebrief.ui.components.topContentPadding
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType
import app.bytebrief.ui.util.Versioned
import app.bytebrief.ui.util.observeAsState
import app.bytebrief.ui.viewModelOf
import app.bytebrief.ui.vm.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val vm = viewModelOf<SettingsViewModel>()
    val nav = LocalNav.current
    val requestNotifications = LocalRequestNotifications.current
    val version by vm.version.observeAsState()
    val interests by vm.interests.observeAsState()
    // Keyed on the version so this scope reads (and therefore subscribes to) the counter; the settings object itself is mutable.
    val s = remember(version) { Versioned(vm.s(), version) }
    var newInterest by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDeleteSummaries by remember { mutableStateOf(false) }
    // Actions that change stored data rather than a visible switch would otherwise look like dead buttons.
    var aiNotice by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    GlassScreen(Modifier.fillMaxSize().imePadding(), tabs = false) {
        BackdropSource {
LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(false)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { SectionHeader("DAILY BRIEFING") }
            item {
                Panel {
                    SwitchRow("Daily briefing notification", "A short digest of what matters, built on your phone at the time you choose.", s.v.briefingEnabled(), {
                        vm.setBriefing(it)
                        if (it) requestNotifications()
                    })
                    if (s.v.briefingEnabled()) {
                        Divider()
                        val m = s.v.briefingMinuteOfDay()
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Txt("Time", style = BBType.Body, modifier = Modifier.weight(1f))
                            PlainIconButton(Ic.MINUS, "Earlier by 15 minutes", { vm.setBriefingTime((m - 15 + 1440) % 1440) }, tint = BB.TextPrimary)
                            Txt("%02d:%02d".format(m / 60, m % 60), style = BBType.Title, modifier = Modifier.padding(horizontal = 6.dp))
                            PlainIconButton(Ic.PLUS, "Later by 15 minutes", { vm.setBriefingTime((m + 15) % 1440) }, tint = BB.TextPrimary)
                        }
                        if (!vm.notificationsAllowed()) {
                            Txt("Notifications are blocked for ByteBrief. The briefing is still available in the app.", style = BBType.Caption, color = BB.Warm, modifier = Modifier.padding(bottom = 8.dp))
                            GlassButton("Allow notifications", { requestNotifications() }, icon = Ic.CHECK)
                        }
                        Txt("The briefing uses your loaded language model if there is one; otherwise it lists your top picks without AI text.", style = BBType.Caption, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Divider()
                    SwitchRow("Release alerts", "Notify when a repository you follow publishes a release.", s.v.releaseAlertsEnabled(), {
                        vm.setReleaseAlerts(it)
                        if (it) requestNotifications()
                    })
                }
            }

            item { SectionHeader("SYNC") }
            item {
                Panel {
                    SwitchRow("Refresh in the background", "Keeps your feed up to date when the phone has a network.", s.v.backgroundSyncEnabled(), { vm.setBackgroundSync(it) })
                    if (s.v.backgroundSyncEnabled()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (h in listOf(1, 3, 6, 12, 24)) Chip("Every $h h", s.v.syncIntervalHours() == h, { vm.setSyncInterval(h) })
                        }
                        SwitchRow("Wi-Fi only", "Skip background refreshes on mobile data.", s.v.syncOnUnmeteredOnly(), { vm.setUnmeteredOnly(it) })
                    }
                    Divider()
                    SwitchRow("Refresh sources in parallel", "Fetches several sources at once, which is faster. Turn off to go one by one.", s.v.parallelSync(), { vm.setParallelSync(it) })
                    if (s.v.parallelSync()) {
                        Txt("Simultaneous sources", style = BBType.Body, modifier = Modifier.padding(top = 4.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (n in AppSettings.MIN_SYNC_THREADS..AppSettings.MAX_SYNC_THREADS) Chip("$n", s.v.syncThreads() == n, { vm.setSyncThreads(n) })
                        }
                        Txt("More is faster but uses more battery and data at once. Default: 4.", style = BBType.Caption, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Divider()
                    Txt("Keep items for", style = BBType.Body, modifier = Modifier.padding(top = 10.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (d in listOf(7, 14, 30, 90)) Chip("$d days", s.v.retentionDays() == d, { vm.setRetention(d) })
                    }
                    Txt("Bookmarked items are always kept.", style = BBType.Caption, modifier = Modifier.padding(bottom = 10.dp))
                    Divider()
                    SwitchRow("Save full text when bookmarking", "Downloads the article so it can be read offline.", s.v.cacheBookmarkedText(), { vm.setCacheBookmarks(it) })
                    SwitchRow("Show images", "Loads thumbnails from the sites that publish them, which tells those sites your IP address.", s.v.showImages(), { vm.setShowImages(it) })
                }
            }

            item { SectionHeader("PERSONALIZATION") }
            item {
                Panel {
                    SwitchRow("Learn from what I read", "Opening, saving and rating items adjust your topics and sources. Stored only on this phone.", s.v.learningEnabled(), { vm.setLearning(it) })
                    Divider()
                    Txt("Topics I follow", style = BBType.Body, modifier = Modifier.padding(top = 10.dp))
                    val list = interests.orEmpty()
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (list.isEmpty()) Txt("None yet", style = BBType.Caption)
                        for (a in list) Chip(a.key + "  ✕", true, { vm.removeInterest(a.key) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { TextInput(newInterest, { newInterest = it }, "Add a topic, e.g. rust", onSearch = { vm.addInterest(newInterest); newInterest = "" }) }
                        GlassButton("Add", { vm.addInterest(newInterest); newInterest = "" }, enabled = newInterest.isNotBlank())
                    }
                    Divider()
                    FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("Forget what it learned", { vm.resetLearned(); notice = "Learned preferences were cleared. Topics you follow are kept." }, icon = Ic.REFRESH, confirmLabel = "Cleared")
                        GlassButton("Reset everything", { confirmReset = true }, icon = Ic.TRASH)
                    }
                    notice?.let { Txt(it, style = BBType.Caption, color = BB.Accent, modifier = Modifier.padding(bottom = 8.dp)) }
                    Txt("Ranking is explained on every item: look for the line that says why it was picked. Total GitHub stars are never used as a quality signal.", style = BBType.Caption, modifier = Modifier.padding(bottom = 10.dp))
                }
            }

            item { SectionHeader("APPEARANCE") }
            item {
                Panel {
                    SwitchRow("Use solid surfaces instead of glass", "Turns off the Liquid Glass effect. It is also off automatically in battery saver.", s.v.opaqueSurfaces(), { vm.setOpaque(it) })
                }
            }

            item { SectionHeader("AI DATA") }
            item {
                Panel {
                    SwitchRow("Allow model downloads on mobile data", "Large downloads still ask for confirmation each time.", s.v.allowMeteredModelDownload(), { vm.setMeteredModelDownload(it) })
                    Divider()
                    Row(Modifier.padding(vertical = 10.dp)) { GlassButton("Delete saved summaries", { confirmDeleteSummaries = true }, icon = Ic.TRASH) }
                    aiNotice?.let { Txt(it, style = BBType.Caption, color = BB.Accent, modifier = Modifier.padding(bottom = 8.dp)) }
                }
            }
            item { GlassButton("About ByteBrief", { nav.push(app.bytebrief.ui.Route.About) }, Modifier.padding(top = 16.dp), icon = Ic.QUESTION) }
        }
}
        GlassTopBar("Settings", onBack = { nav.pop() })
        if (confirmReset) ConfirmDialog("Reset personalization?", "Forgets everything learned and the topics you follow. Your saved items are not affected.",
            "Reset", { vm.resetAll(); confirmReset = false; notice = "Personalization was reset." }, { confirmReset = false }, danger = true)
        if (confirmDeleteSummaries) ConfirmDialog("Delete saved summaries?", "Removes every summary, explanation and answer generated on this phone. They can be generated again.",
            "Delete", { vm.clearAiOutputs(); confirmDeleteSummaries = false; aiNotice = "Saved summaries were deleted." }, { confirmDeleteSummaries = false }, danger = true)
    }
}
