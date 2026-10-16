package app.bytebrief.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bytebrief.ui.LocalContainer
import app.bytebrief.ui.LocalNav
import app.bytebrief.ui.LocalRequestNotifications
import app.bytebrief.ui.Route
import app.bytebrief.ui.components.Chip
import app.bytebrief.ui.components.GlassButton
import app.bytebrief.ui.components.Ic
import app.bytebrief.ui.components.Icon
import app.bytebrief.ui.components.TextInput
import app.bytebrief.ui.components.Txt
import app.bytebrief.ui.glass.BackdropSource
import app.bytebrief.ui.glass.GlassLevel
import app.bytebrief.ui.glass.GlassScreen
import app.bytebrief.ui.glass.glassSurface
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType

private val SUGGESTED = listOf("android", "kotlin", "java", "rust", "go", "python", "javascript", "ai", "llm", "security", "linux", "devops", "databases", "open-source")

/** First-run flow: what the app does, what you care about, and how to get the AI model (all skippable). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val requestNotifications = LocalRequestNotifications.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val picked = remember { mutableStateListOf<String>() }
    var custom by remember { mutableStateOf("") }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BackHandler(enabled = step > 0) { step -= 1 }

    fun finish(openModels: Boolean) {
        for (t in picked) c.personalizer.let { p -> c.io.execute { p.addInterest(t) } }
        onDone()
        if (openModels) { nav.selectTab(app.bytebrief.ui.Tab.More); nav.push(Route.Models) }
        c.content.refresh()
    }

    GlassScreen {
    BackdropSource { }
    Box(Modifier.fillMaxSize().imePadding().padding(top = top, bottom = bottom, start = 20.dp, end = 20.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().glassSurface(32.dp, GlassLevel.Sheet, BB.GlassTintStrong).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0..2) Box(Modifier.weight(1f).padding(vertical = 2.dp).let { it }.glassSurface(3.dp, GlassLevel.Control, if (i <= step) BB.Accent.copy(alpha = 0.8f) else BB.GlassTint).padding(2.dp))
            }
            when (step) {
                0 -> {
                    Icon(Ic.SPARK, BB.Accent, size = 40.dp)
                    Txt("Welcome to ByteBrief", style = BBType.Display)
                    Txt("The best of Hacker News, GitHub Trending, new releases and your own RSS feeds, explained by a language model that runs on your phone.", style = BBType.Body)
                    Txt("No account. No tracking. Your reading stays on this device.", style = BBType.BodySecondary)
                    GlassButton("Continue", { step = 1 }, prominent = true, modifier = Modifier.fillMaxWidth())
                }
                1 -> {
                    Txt("What are you into?", style = BBType.Title)
                    Txt("Pick a few topics to start with. You can change them any time in Settings.", style = BBType.BodySecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (t in SUGGESTED + picked.filter { it !in SUGGESTED }) Chip(t, t in picked, { if (t in picked) picked.remove(t) else picked.add(t) })
                    }
                    TextInput(custom, { custom = it }, "Add your own", onSearch = { val v = custom.trim().lowercase(); if (v.isNotEmpty() && v !in picked) picked.add(v); custom = "" })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Back", { step = 0 })
                        GlassButton("Continue", { val v = custom.trim().lowercase(); if (v.isNotEmpty() && v !in picked) picked.add(v); custom = ""; step = 2 }, prominent = true)
                    }
                }
                else -> {
                    Txt("Add on-device AI", style = BBType.Title)
                    Txt("Summaries, key points, explanations and questions need a language model. It's a one-time download of about 1–2.4 GB that stays on your phone and works offline. It only downloads on Wi-Fi unless you say otherwise.", style = BBType.Body)
                    Txt("Reading, search and bookmarks work fine without it, so this can wait.", style = BBType.BodySecondary)
                    GlassButton("Choose a model", { finish(true) }, prominent = true, icon = Ic.CHIP, modifier = Modifier.fillMaxWidth())
                    GlassButton("Start reading", { finish(false) }, icon = Ic.LIST, modifier = Modifier.fillMaxWidth())
                    GlassButton("Back", { step = 1 })
                }
            }
        }
    }
}
}
