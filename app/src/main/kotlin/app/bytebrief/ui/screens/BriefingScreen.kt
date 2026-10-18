package app.bytebrief.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bytebrief.ai.AiResult
import app.bytebrief.ai.BriefingService
import app.bytebrief.ui.LocalNav
import app.bytebrief.ui.Route
import app.bytebrief.ui.components.Badge
import app.bytebrief.ui.components.GlassButton
import app.bytebrief.ui.components.GlassTopBar
import app.bytebrief.ui.glass.BackdropSource
import app.bytebrief.ui.glass.GlassScreen
import app.bytebrief.ui.components.Ic
import app.bytebrief.ui.components.Icon
import app.bytebrief.ui.components.PlainIconButton
import app.bytebrief.ui.components.SkeletonCard
import app.bytebrief.ui.components.StateMessage
import app.bytebrief.ui.components.Txt
import app.bytebrief.ui.components.bottomContentPadding
import app.bytebrief.ui.components.sourceStyle
import app.bytebrief.ui.components.tappable
import app.bytebrief.ui.components.topContentPadding
import app.bytebrief.ui.glass.frostedCard
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType
import app.bytebrief.ui.util.Format
import app.bytebrief.ui.util.observeAsState
import app.bytebrief.ui.viewModelOf
import app.bytebrief.ui.vm.AiUi
import app.bytebrief.ui.vm.BriefingViewModel

@Composable
fun BriefingScreen() {
    val vm = viewModelOf<BriefingViewModel>()
    val nav = LocalNav.current
    val state by vm.state().observeAsState()
    val ready by vm.modelReady().observeAsState()
    val s = state
    GlassScreen(Modifier.fillMaxSize(), tabs = true) {
        BackdropSource {
LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(true)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (s?.state) {
                AiUi.State.DONE -> {
                    val v = s.value!!
                    item { BriefingHeader(v, s, ready == true, onGenerate = { vm.generate(it) }) }
                    items(v.rows, key = { it.item.id }) { row -> BriefingRow(row) { nav.push(Route.Detail(row.item.id)) } }
                    if (v.themes.isNotEmpty()) item { Themes(v) }
                }
                AiUi.State.RUNNING -> {
                    item {
                        Column(Modifier.fillMaxWidth().frostedCard(20.dp, highlight = true).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Txt((s.stage ?: "Working") + "…", style = BBType.Title)
                            Txt("Your local model is writing a short reason for each pick. Nothing leaves your phone.", style = BBType.BodySecondary)
                            GlassButton("Cancel", { vm.cancel() }, icon = Ic.CLOSE)
                        }
                    }
                    items(3) { SkeletonCard() }
                }
                AiUi.State.FAILED -> item {
                    val empty = s.status == AiResult.Status.INSUFFICIENT_CONTENT
                    StateMessage(if (empty) Ic.SUN else Ic.WARNING, if (empty) "Nothing to brief yet" else "Couldn't build the briefing", s.message.orEmpty(),
                        action = { GlassButton("Try again", { vm.refreshSelection() }, icon = Ic.REFRESH) })
                }
                else -> items(3) { SkeletonCard() }
            }
        }
}
        GlassTopBar("Briefing", actions = { PlainIconButton(Ic.SLIDERS, "Briefing settings", { nav.push(Route.Settings) }, tint = BB.TextPrimary) })
    }
}

@Composable
private fun BriefingHeader(v: BriefingService.View, s: AiUi<BriefingService.View>, modelReady: Boolean, onGenerate: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().frostedCard(22.dp, highlight = v.aiGenerated).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt(v.dateKey, style = BBType.Caption)
        Txt(v.headline ?: "${v.rows.size} items picked for you", style = BBType.Title)
        if (v.note.isNotBlank()) Txt(v.note, style = BBType.BodySecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (modelReady) GlassButton(if (v.aiGenerated) "Regenerate" else "Generate", { onGenerate(v.aiGenerated) }, icon = Ic.SPARK, prominent = !v.aiGenerated)
            val provenance = when {
                v.aiGenerated && s.fromCache -> "Written by your local model · saved"
                v.aiGenerated -> "Written by your local model"
                else -> "Picked and ordered by on-device ranking"
            }
            Txt(provenance, style = BBType.Caption, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BriefingRow(row: BriefingService.Row, onClick: () -> Unit) {
    val st = sourceStyle(row.item.sourceType)
    Column(
        Modifier.fillMaxWidth().frostedCard(18.dp).tappable(onClick, "${row.item.title}. ${st.label}", minSize = 88.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(st.icon, st.color, size = 14.dp, stroke = 2.2f)
            Txt(row.item.sourceLabel?.takeIf { (row.item.sourceType == "rss" || row.item.sourceType == "xda") } ?: st.label, style = BBType.Caption, color = st.color, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            Txt("· ${Format.relativeTime(row.item.publishedAt)}", style = BBType.Caption)
            if (row.groupSize > 1) Badge("+${row.groupSize - 1}", BB.Accent, icon = Ic.LAYERS)
        }
        Txt(row.item.title, style = BBType.Heading, maxLines = 3)
        if (row.why != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Ic.SPARK, BB.Accent, size = 14.dp, stroke = 2.2f, modifier = Modifier.padding(top = 3.dp))
                Txt(row.why!!, style = BBType.Body, color = BB.TextPrimary)
            }
        } else if (row.rankerReasons.isNotEmpty()) {
            Txt("Why: " + row.rankerReasons.joinToString(" · "), style = BBType.Caption, color = BB.TextSecondary, maxLines = 2)
        }
    }
}

@Composable
private fun Themes(v: BriefingService.View) {
    Column(Modifier.fillMaxWidth().frostedCard(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("Themes", style = BBType.Label)
        val titles = v.rows.associate { it.item.id to it.item.title }
        for (t in v.themes) {
            Txt(t.name, style = BBType.Heading, color = BB.Accent)
            for (id in t.itemIds) titles[id]?.let { Txt("• $it", style = BBType.BodySecondary, maxLines = 2) }
        }
    }
}
