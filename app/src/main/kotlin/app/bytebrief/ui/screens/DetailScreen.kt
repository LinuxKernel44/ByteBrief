package app.bytebrief.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bytebrief.ai.AiResult
import app.bytebrief.ai.model.Answer
import app.bytebrief.ai.model.ExplainLevel
import app.bytebrief.data.db.ContentItem
import app.bytebrief.domain.BodyState
import app.bytebrief.ui.LocalNav
import app.bytebrief.ui.Route
import app.bytebrief.ui.components.Badge
import app.bytebrief.ui.components.CacheIndicator
import app.bytebrief.ui.components.Chip
import app.bytebrief.ui.components.GlassButton
import app.bytebrief.ui.components.GlassTopBar
import app.bytebrief.ui.glass.BackdropSource
import app.bytebrief.ui.glass.GlassScreen
import app.bytebrief.ui.components.Ic
import app.bytebrief.ui.components.Icon
import app.bytebrief.ui.components.MarkdownLite
import app.bytebrief.ui.components.MdBlockView
import app.bytebrief.ui.components.MetaChips
import app.bytebrief.ui.components.Panel
import app.bytebrief.ui.components.PlainIconButton
import app.bytebrief.ui.components.RemoteImage
import app.bytebrief.ui.components.SectionHeader
import app.bytebrief.ui.components.StateMessage
import app.bytebrief.ui.components.SwitchRow
import app.bytebrief.ui.components.TextInput
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
import app.bytebrief.ui.vm.DetailViewModel
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun DetailScreen(id: Long, showBack: Boolean) {
    val vm = viewModelOf<DetailViewModel>(key = "detail-$id", itemId = id)
    val nav = LocalNav.current
    val item by vm.item.observeAsState()
    val group by vm.group.observeAsState()
    val modelReady by vm.modelReady.observeAsState()
    val article by vm.article.observeAsState()
    val summary by vm.summary.observeAsState()
    val takeaways by vm.takeaways.observeAsState()
    val explanation by vm.explanation.observeAsState()
    val level by vm.level.observeAsState()
    val qa by vm.qa.observeAsState()
    val context = LocalContext.current
    val it0 = item

    GlassScreen(Modifier.fillMaxSize(), tabs = false) {
        if (it0 == null) {
            StateMessage(Ic.WARNING, "Item not found", "It may have been removed by the retention policy.", Modifier.padding(top = topContentPadding()))
        } else {
            val blocks = remember(it0.bodyText, it0.description, it0.sourceType) {
                val text = it0.bodyText ?: it0.description ?: ""
                MarkdownLite.parse(text, markdown = it0.sourceType == "gh_trending" || it0.sourceType == "gh_release")
            }
            BackdropSource {
LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(false)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Header(it0) }
                item { ActionsRow(it0, vm, onOpen = { openUrl(context, it0.originalUrl) }) }
                item { OfflineCard(it0, article?.working == true, article?.message, onSave = { vm.saveFullText() }) }
                val others = group.orEmpty().filter { o -> o.id != it0.id }
                if (others.isNotEmpty()) item { OtherSources(others, onOpen = { nav.push(Route.Detail(it)) }) }
                item {
                    AiSection(
                        item = it0, modelReady = modelReady == true, summary = summary, takeaways = takeaways, explanation = explanation,
                        level = level ?: ExplainLevel.INTERMEDIATE, qa = qa.orEmpty(), vm = vm,
                    )
                }
                item { SectionHeader(if (it0.bodyState == BodyState.FULL) "Text" else "Preview (not the full text)") }
                if (blocks.isEmpty()) item { Txt("No text is stored for this item. Open the original to read it.", style = BBType.BodySecondary) }
                items(blocks.take(600)) { b -> MdBlockView(b) }
            }
}
        }
        GlassTopBar("", onBack = if (showBack) ({ nav.pop() }) else null, actions = {
            if (it0 != null) PlainIconButton(Ic.EXTERNAL, "Open original in browser", { openUrl(context, it0.originalUrl) }, tint = BB.TextPrimary)
        })
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    // URLs were validated as http(s) when collected; re-check before handing them to another app.
    val uri = Uri.parse(url)
    if (uri.scheme != "https" && uri.scheme != "http") return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No browser installed; nothing to do.
    }
}

@Composable
private fun Header(item: ContentItem) {
    val style = sourceStyle(item.sourceType)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(style.icon, style.color, size = 16.dp, stroke = 2.2f)
            Txt(item.sourceLabel?.takeIf { item.sourceType == "rss" } ?: style.label, style = BBType.Caption, color = style.color, maxLines = 1)
            Txt("· ${Format.ago(item.publishedAt)}", style = BBType.Caption)
        }
        Txt(item.title, style = BBType.Display.copy(fontSize = 26.sp, lineHeight = 32.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!item.author.isNullOrBlank()) Txt("by ${item.author}", style = BBType.Caption, maxLines = 1)
            MetaChips(item)
        }
        if (item.imageUrl != null) RemoteImage(item.imageUrl, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)), widthPx = 900)
    }
}

@Composable
private fun ActionsRow(item: ContentItem, vm: DetailViewModel, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(if (item.isBookmarked) "Saved" else "Save", item.isBookmarked, { vm.setBookmarked(!item.isBookmarked) }, icon = if (item.isBookmarked) Ic.BOOKMARK_FILLED else Ic.BOOKMARK)
        Chip(if (item.isRead) "Read" else "Mark read", item.isRead, { vm.setRead(!item.isRead) }, icon = if (item.isRead) Ic.EYE else Ic.EYE_OFF)
        Chip("Relevant", item.feedback > 0, { vm.setFeedback(if (item.feedback > 0) 0 else 1) }, icon = Ic.THUMB_UP, accent = BB.Success)
        Chip("Not for me", item.feedback < 0, { vm.setFeedback(if (item.feedback < 0) 0 else -1) }, icon = Ic.THUMB_DOWN, accent = BB.Danger)
        Chip("Open original", false, onOpen, icon = Ic.EXTERNAL)
    }
}

@Composable
private fun OfflineCard(item: ContentItem, working: Boolean, message: String?, onSave: () -> Unit) {
    Column(Modifier.fillMaxWidth().frostedCard(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CacheIndicator(item)
        }
        val explanation = when (item.bodyState) {
            BodyState.FULL -> "The complete text is stored on this device and can be read offline."
            BodyState.SNIPPET -> "Only the short description is stored offline. Save the full text to read and analyze the whole thing."
            else -> "Only the title is stored offline."
        }
        Txt(explanation, style = BBType.BodySecondary)
        if (item.bodyState != BodyState.FULL) {
            GlassButton(if (working) "Saving…" else "Save full text", onSave, icon = Ic.DOWNLOAD, enabled = !working)
        }
        if (message != null) Txt(message, style = BBType.Caption, color = BB.TextSecondary)
    }
}

@Composable
private fun OtherSources(others: List<ContentItem>, onOpen: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().frostedCard(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ic.LAYERS, BB.Accent, size = 16.dp)
            Txt("Also covered by", style = BBType.Label)
        }
        for (o in others) {
            val st = sourceStyle(o.sourceType)
            Row(Modifier.fillMaxWidth().tappable({ onOpen(o.id) }, "${st.label}: ${o.title}", Role.Button), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(st.icon, st.color, size = 16.dp)
                Column(Modifier.weight(1f)) {
                    Txt(o.sourceLabel?.takeIf { o.sourceType == "rss" } ?: st.label, style = BBType.Caption, color = st.color, maxLines = 1)
                    Txt(o.title, style = BBType.Body, maxLines = 2)
                }
                Icon(Ic.CHEVRON_RIGHT, BB.TextTertiary, size = 18.dp)
            }
        }
    }
}

// ---- AI --------------------------------------------------------------------------------------

@Composable
private fun AiSection(
    item: ContentItem, modelReady: Boolean,
    summary: AiUi<app.bytebrief.ai.model.Summary>?, takeaways: AiUi<app.bytebrief.ai.model.Takeaways>?,
    explanation: AiUi<app.bytebrief.ai.model.Explanation>?, level: ExplainLevel, qa: List<DetailViewModel.Qa>, vm: DetailViewModel,
) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxWidth().frostedCard(22.dp, highlight = true).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ic.SPARK, BB.Accent, size = 20.dp)
            Txt("On-device AI", style = BBType.Title)
        }
        if (!modelReady) {
            Txt("No model is loaded, so new summaries can't be generated. Anything already generated is shown below.", style = BBType.BodySecondary)
            GlassButton("Open Models", { nav.push(Route.Models) }, icon = Ic.CHIP)
        }
        if (item.bodyState != BodyState.FULL) {
            Txt("Only a short excerpt is stored, so AI results will be based on that excerpt alone.", style = BBType.Caption, color = BB.Warm)
        }

        AiBlock("Summary", Ic.LIST, summary, modelReady, "Summarize", onRun = { vm.summarize(false) }, onRegenerate = { vm.summarize(true) }, onCancel = { vm.cancelAi() }) { s ->
            Txt(s.summary, style = BBType.Body)
            if (s.interpretation.isNotBlank()) {
                Txt("Model's interpretation", style = BBType.Caption, color = BB.TextTertiary)
                Txt(s.interpretation, style = BBType.BodySecondary)
            }
            if (s.truncatedInput) Txt("The text was cut to fit the model's context window.", style = BBType.Caption, color = BB.Warm)
            if (s.chunked) Txt("This long text was condensed in parts first.", style = BBType.Caption, color = BB.Warm)
        }

        AiBlock("Key takeaways", Ic.CHECK, takeaways, modelReady, "Extract takeaways", onRun = { vm.extractTakeaways(false) }, onRegenerate = { vm.extractTakeaways(true) }, onCancel = { vm.cancelAi() }) { t ->
            if (t.mainContribution.isNotBlank()) { Txt("Main contribution", style = BBType.Caption, color = BB.TextTertiary); Txt(t.mainContribution, style = BBType.Body) }
            BulletList("Key points", t.keyPoints)
            if (t.technologies.isNotEmpty()) { Txt("Technologies", style = BBType.Caption, color = BB.TextTertiary); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { t.technologies.forEach { Badge(it, BB.Accent) } } }
            if (t.audience.isNotBlank()) { Txt("Audience", style = BBType.Caption, color = BB.TextTertiary); Txt(t.audience, style = BBType.BodySecondary) }
            BulletList("Use cases", t.useCases)
            BulletList("Caveats", t.caveats)
            if (t.truncatedInput) Txt("The text was cut to fit the model's context window.", style = BBType.Caption, color = BB.Warm)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in ExplainLevel.entries) Chip(l.label, l == level, { vm.selectLevel(l) })
            }
            AiBlock("Explanation (${level.label})", Ic.QUESTION, explanation, modelReady, "Explain", onRun = { vm.explain(level, false) }, onRegenerate = { vm.explain(level, true) }, onCancel = { vm.cancelAi() }) { e ->
                Txt(e.explanation, style = BBType.Body)
                if (e.inferred.isNotEmpty()) BulletList("Inferred by the model, not stated in the text", e.inferred, color = BB.Warm)
                Txt("Based only on the stored text; no source files were inspected.", style = BBType.Caption)
            }
        }

        AskBlock(item, modelReady, qa, vm)
    }
}

@Composable
private fun BulletList(title: String, items: List<String>, color: androidx.compose.ui.graphics.Color = BB.TextTertiary) {
    if (items.isEmpty()) return
    Txt(title, style = BBType.Caption, color = color)
    for (i in items) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("•", style = BBType.Body, color = BB.TextSecondary)
        Txt(i, style = BBType.Body, modifier = Modifier.weight(1f))
    }
}

/** One AI feature: idle -> button, running -> stage + cancel, done -> content + provenance, failed -> typed message. */
@Composable
private fun <T> AiBlock(
    title: String, icon: Ic, state: AiUi<T>?, modelReady: Boolean, actionLabel: String,
    onRun: () -> Unit, onRegenerate: () -> Unit, onCancel: () -> Unit, content: @Composable (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, BB.TextSecondary, size = 16.dp)
            Txt(title, style = BBType.Heading)
        }
        when (state?.state) {
            AiUi.State.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Txt((state.stage ?: "Working") + "…", style = BBType.BodySecondary, modifier = Modifier.weight(1f))
                GlassButton("Cancel", onCancel, icon = Ic.CLOSE)
            }
            AiUi.State.DONE -> {
                state.value?.let { content(it) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val provenance = when {
                        state.stale -> "Saved · generated by another model"
                        state.fromCache -> "Saved on this device"
                        else -> "Generated on this device"
                    }
                    Txt(provenance, style = BBType.Caption, color = if (state.stale) BB.Warm else BB.TextTertiary, modifier = Modifier.weight(1f))
                    if (modelReady) GlassButton("Regenerate", onRegenerate, icon = Ic.REFRESH)
                }
            }
            AiUi.State.FAILED -> {
                Txt(state.message.orEmpty(), style = BBType.Body, color = BB.Warm)
                if (modelReady && state.status != AiResult.Status.INSUFFICIENT_CONTENT) GlassButton("Try again", onRun, icon = Ic.REFRESH)
            }
            else -> GlassButton(actionLabel, onRun, icon = Ic.SPARK, prominent = modelReady, enabled = modelReady)
        }
    }
}

@Composable
private fun AskBlock(item: ContentItem, modelReady: Boolean, qa: List<DetailViewModel.Qa>, vm: DetailViewModel) {
    var question by remember { mutableStateOf("") }
    var withDiscussion by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ic.QUESTION, BB.TextSecondary, size = 16.dp)
            Txt("Ask about this", style = BBType.Heading)
        }
        TextInput(question, { question = it }, "e.g. What problem does it solve?", onSearch = { if (modelReady && question.isNotBlank()) { vm.ask(question, withDiscussion); question = "" } })
        if (item.sourceType == "hn") {
            SwitchRow("Include the discussion", "Fetches the top comments from Hacker News (needs a connection).", withDiscussion, { withDiscussion = it })
        }
        GlassButton("Ask", { vm.ask(question, withDiscussion); question = "" }, icon = Ic.SPARK, prominent = true, enabled = modelReady && question.isNotBlank())
        for (entry in qa.asReversed()) QaCard(entry)
    }
}

@Composable
private fun QaCard(entry: DetailViewModel.Qa) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(entry.question, style = BBType.Label, color = BB.Accent)
        val a = entry.answer
        when (a.state) {
            AiUi.State.RUNNING -> Txt("Thinking…", style = BBType.BodySecondary)
            AiUi.State.DONE -> {
                val ans: Answer = a.value ?: return
                Txt(ans.answer, style = BBType.Body)
                if (!ans.answerable) Txt("The stored text doesn't contain this.", style = BBType.Caption, color = BB.Warm)
                else if (ans.grounded) Badge("Quote found in the text", BB.Success, icon = Ic.CHECK)
                else Badge("Could not verify against the text", BB.Warm, icon = Ic.WARNING)
                if (ans.answerable && ans.evidence.isNotBlank()) Txt("“${ans.evidence}”", style = BBType.Caption, maxLines = 4)
                if (entry.withDiscussion) Txt("Includes top comments when they could be fetched.", style = BBType.Caption)
            }
            else -> Txt(a.message.orEmpty(), style = BBType.BodySecondary, color = BB.Warm)
        }
    }
}
