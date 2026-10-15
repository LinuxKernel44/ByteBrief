package app.bytebrief.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.bytebrief.ai.LocalAiEngine
import app.bytebrief.ui.LocalContainer
import app.bytebrief.ui.LocalNav
import app.bytebrief.ui.Route
import app.bytebrief.ui.components.Divider
import app.bytebrief.ui.components.GlassTopBar
import app.bytebrief.ui.glass.BackdropSource
import app.bytebrief.ui.glass.GlassScreen
import app.bytebrief.ui.components.Ic
import app.bytebrief.ui.components.Icon
import app.bytebrief.ui.components.Panel
import app.bytebrief.ui.components.SectionHeader
import app.bytebrief.ui.components.Txt
import app.bytebrief.ui.components.bottomContentPadding
import app.bytebrief.ui.components.tappable
import app.bytebrief.ui.components.topContentPadding
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType
import app.bytebrief.ui.util.observeAsState

@Composable
fun MoreScreen() {
    val nav = LocalNav.current
    val c = LocalContainer.current
    val state by c.engine.stateLive().observeAsState()
    val active = c.engine.activeModel()
    GlassScreen(Modifier.fillMaxSize(), tabs = true) {
        BackdropSource {
LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(true)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Panel {
                    MoreRow(Ic.CHIP, "Language models",
                        when {
                            state == LocalAiEngine.State.READY && active != null -> "${active.name} is loaded"
                            state == LocalAiEngine.State.LOADING -> "Loading…"
                            else -> "No model loaded. Download one to enable summaries."
                        }, { nav.push(Route.Models) })
                    Divider()
                    MoreRow(Ic.RSS, "Sources", "Hacker News, GitHub, releases and your feeds", { nav.push(Route.Sources) })
                    Divider()
                    MoreRow(Ic.SLIDERS, "Settings", "Briefing, sync, personalization, appearance", { nav.push(Route.Settings) })
                    Divider()
                    MoreRow(Ic.QUESTION, "About", "Version, licenses and privacy", { nav.push(Route.About) })
                }
            }
        }
}
        GlassTopBar("More")
    }
}

@Composable
private fun MoreRow(icon: Ic, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tappable(onClick, "$title. $subtitle", Role.Button, minSize = 64.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, BB.Accent, size = 24.dp)
        Column(Modifier.weight(1f)) {
            Txt(title, style = BBType.Heading)
            Txt(subtitle, style = BBType.Caption, maxLines = 2)
        }
        Icon(Ic.CHEVRON_RIGHT, BB.TextTertiary, size = 20.dp)
    }
}

private val LIBRARIES = listOf(
    "llama.cpp" to "MIT · runs the language model on your device",
    "Backdrop (Liquid Glass) by Kyant0" to "Apache-2.0 · the glass effect",
    "Jetpack Compose, AndroidX (Room, WorkManager, Lifecycle)" to "Apache-2.0",
    "OkHttp and Okio" to "Apache-2.0 · networking",
    "jsoup" to "MIT · reading HTML and GitHub Trending",
    "Qwen3 / Qwen2.5 models" to "Apache-2.0 · by the Qwen team; you download them yourself",
)

@Composable
fun AboutScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val version = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev" }
    GlassScreen(Modifier.fillMaxSize(), tabs = false) {
        BackdropSource {
LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(false)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt("ByteBrief", style = BBType.Display)
                    Txt("Version $version · Apache-2.0 licensed", style = BBType.Caption)
                    Txt("A tech reader with a language model that runs on your phone.", style = BBType.Body)
                }
            }
            item { SectionHeader("PRIVACY") }
            item {
                Panel {
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt("• No account, no analytics, no ads.", style = BBType.Body)
                        Txt("• Summaries and answers are produced on this phone. No text you read is sent to an AI service.", style = BBType.Body)
                        Txt("• The network is used to fetch articles from the sources you enable and, if you ask, to download a model file.", style = BBType.Body)
                        Txt("• Your reading history, bookmarks and learned preferences stay in this app's private storage.", style = BBType.Body)
                        Txt("• An optional GitHub token is encrypted with a key that cannot leave the device.", style = BBType.Body)
                        Txt("• Language models can make mistakes. ByteBrief checks their output and shows when a quote could not be verified, but you should read the original for anything important.", style = BBType.Body, color = BB.Warm)
                    }
                }
            }
            item { SectionHeader("OPEN-SOURCE SOFTWARE") }
            item {
                Panel {
                    for ((i, lib) in LIBRARIES.withIndex()) {
                        if (i > 0) Divider()
                        Column(Modifier.padding(vertical = 10.dp)) {
                            Txt(lib.first, style = BBType.Body)
                            Txt(lib.second, style = BBType.Caption)
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(top = 12.dp).tappable({
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LinuxKernel44/ByteBrief")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, "Open the project on GitHub"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.EXTERNAL, BB.Accent, size = 18.dp)
                    Txt("Source code on GitHub", style = BBType.Label, color = BB.Accent)
                }
            }
        }
}
        GlassTopBar("About", onBack = { nav.pop() })
    }
}
