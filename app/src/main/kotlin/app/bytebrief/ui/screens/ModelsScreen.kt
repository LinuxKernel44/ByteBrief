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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bytebrief.ai.ModelDescriptor
import app.bytebrief.ai.ModelManager
import app.bytebrief.ui.LocalNav
import app.bytebrief.ui.components.Badge
import app.bytebrief.ui.components.GlassButton
import app.bytebrief.ui.components.GlassTopBar
import app.bytebrief.ui.glass.BackdropSource
import app.bytebrief.ui.glass.GlassScreen
import app.bytebrief.ui.components.Ic
import app.bytebrief.ui.components.Icon
import app.bytebrief.ui.components.PlainIconButton
import app.bytebrief.ui.components.ProgressBar
import app.bytebrief.ui.components.SectionHeader
import app.bytebrief.ui.components.Txt
import app.bytebrief.ui.components.tappable
import app.bytebrief.ui.components.bottomContentPadding
import app.bytebrief.ui.components.topContentPadding
import app.bytebrief.ui.glass.frostedCard
import app.bytebrief.ui.glass.opaqueSurface
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType
import app.bytebrief.ui.util.Format
import app.bytebrief.ui.util.observeAsState
import app.bytebrief.ui.viewModelOf
import app.bytebrief.ui.vm.ModelsViewModel
import androidx.compose.foundation.lazy.items

@Composable
fun ModelsScreen() {
    val vm = viewModelOf<ModelsViewModel>()
    val nav = LocalNav.current
    val message by vm.message.observeAsState()
    val bench by vm.benchmark.observeAsState()
    val benchmarking by vm.benchmarking.observeAsState()
    var confirmMobile by remember { mutableStateOf<ModelDescriptor?>(null) }
    var confirmDelete by remember { mutableStateOf<ModelDescriptor?>(null) }

    GlassScreen(Modifier.fillMaxSize(), tabs = false) {
        BackdropSource {
LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(false)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().frostedCard(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt("Your language model runs entirely on this phone. It is downloaded once, on request, and nothing you read is ever sent anywhere.", style = BBType.Body)
                    Txt("Device memory: ${Format.bytes(vm.totalRamBytes())}  ·  Free storage: ${Format.bytes(vm.freeStorageBytes())}", style = BBType.Caption)
                }
            }
            if (message != null) item { Txt(message!!, style = BBType.Body, color = BB.Warm, modifier = Modifier.padding(horizontal = 4.dp)) }
            items(vm.models.size) { index ->
                val m = vm.models[index]
                val status by vm.status(index).observeAsState()
                ModelCard(vm, m, status, onMobile = { confirmMobile = m }, onDelete = { confirmDelete = m })
            }
            item { SectionHeader("PERFORMANCE") }
            item {
                Column(Modifier.fillMaxWidth().frostedCard(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt("Measure how fast the loaded model runs on this device. Speed varies with temperature, battery saver and what else is running.", style = BBType.BodySecondary)
                    GlassButton(if (benchmarking == true) "Measuring…" else "Run benchmark", { vm.runBenchmark() }, icon = Ic.CHIP, enabled = benchmarking != true)
                    bench?.let {
                        Txt("Reading the prompt: %.1f tokens/s (%d tokens)".format(it.prefillTokensPerSecond, it.promptTokens), style = BBType.Body)
                        Txt("Writing the answer: %.1f tokens/s  ·  %d threads".format(it.decodeTokensPerSecond, it.threads), style = BBType.Body)
                    }
                }
            }
        }
}
        GlassTopBar("Language models", onBack = { nav.pop() })
        confirmMobile?.let { m ->
            ConfirmDialog(
                "Download over mobile data?", "${m.name} is ${Format.bytes(m.sizeBytes)}. This can use a large part of a data plan. It will resume if it is interrupted.",
                "Download", { vm.downloadOnMobileData(m); confirmMobile = null }, { confirmMobile = null },
            )
        }
        confirmDelete?.let { m ->
            ConfirmDialog("Delete ${m.name}?", "The ${Format.bytes(m.sizeBytes)} file is removed from this phone. Saved summaries stay. You can download it again later.",
                "Delete", { vm.delete(m); confirmDelete = null }, { confirmDelete = null }, danger = true)
        }
    }
}

@Composable
private fun ModelCard(vm: ModelsViewModel, m: ModelDescriptor, status: ModelManager.Status?, onMobile: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val fit = vm.manager().fit(m)
    val phase = status?.phase ?: ModelManager.Phase.NOT_DOWNLOADED
    Column(Modifier.fillMaxWidth().frostedCard(22.dp, highlight = phase == ModelManager.Phase.READY).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Ic.CHIP, BB.Accent, size = 22.dp)
            Column(Modifier.weight(1f)) {
                Txt(m.name, style = BBType.Title, maxLines = 2)
                Txt(m.publisher, style = BBType.Caption, maxLines = 2)
            }
            if (phase == ModelManager.Phase.READY) Badge("Ready", BB.Success, icon = Ic.CHECK)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Badge(m.parameters + " parameters", BB.Accent)
            Badge(m.quantization, BB.Accent)
            Badge(Format.bytes(m.sizeBytes), BB.TextSecondary)
        }
        Txt(m.notes, style = BBType.BodySecondary)
        val (fitText, fitColor) = when (fit) {
            ModelManager.Fit.COMFORTABLE -> "Fits this device's memory comfortably" to BB.Success
            ModelManager.Fit.TIGHT -> "May be tight on memory; close other apps first" to BB.Warm
            ModelManager.Fit.TOO_LARGE -> "Probably too large for this device" to BB.Danger
        }
        Txt(fitText, style = BBType.Caption, color = fitColor)
        Txt("License: ${m.license}  ·  Context: ${m.contextTokens} tokens", style = BBType.Caption)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Txt("License terms", style = BBType.Caption, color = BB.Accent, modifier = Modifier.clickableUrl(context, m.licenseUrl))
            Txt("Model page", style = BBType.Caption, color = BB.Accent, modifier = Modifier.clickableUrl(context, m.sourceUrl))
        }

        when (phase) {
            ModelManager.Phase.DOWNLOADING -> {
                ProgressBar((status?.percent() ?: 0) / 100f)
                Txt("${Format.bytes(status?.downloadedBytes ?: 0)} of ${Format.bytes(m.sizeBytes)}  ·  ${status?.percent() ?: 0}%", style = BBType.Caption)
                GlassButton("Pause", { vm.pause(m) }, icon = Ic.PAUSE)
            }
            ModelManager.Phase.WAITING_FOR_NETWORK -> {
                if ((status?.downloadedBytes ?: 0) > 0) ProgressBar((status?.percent() ?: 0) / 100f)
                Txt("Waiting for Wi-Fi. The download starts automatically and resumes if interrupted.", style = BBType.BodySecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Use mobile data…", onMobile, icon = Ic.DOWNLOAD)
                    GlassButton("Cancel", { vm.pause(m) }, icon = Ic.CLOSE)
                }
            }
            ModelManager.Phase.VERIFYING -> { ProgressBar(1f); Txt("Verifying the download (SHA-256)…", style = BBType.BodySecondary) }
            ModelManager.Phase.PAUSED -> {
                ProgressBar((status?.percent() ?: 0) / 100f)
                Txt("Paused at ${Format.bytes(status?.downloadedBytes ?: 0)}", style = BBType.Caption)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Resume on Wi-Fi", { vm.download(m) }, icon = Ic.PLAY, prominent = true)
                    GlassButton("Mobile data…", onMobile, icon = Ic.DOWNLOAD)
                    PlainIconButton(Ic.TRASH, "Discard partial download", onDelete, tint = BB.Danger)
                }
            }
            ModelManager.Phase.FAILED -> {
                Txt(status?.message ?: "The download failed.", style = BBType.Body, color = BB.Warm)
                GlassButton("Try again", { vm.download(m) }, icon = Ic.REFRESH, prominent = true)
            }
            ModelManager.Phase.INSTALLED -> {
                if (status?.message != null) Txt("Couldn't load: ${status.message}", style = BBType.Body, color = BB.Warm)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton("Load model", { vm.load(m) }, icon = Ic.PLAY, prominent = true)
                    PlainIconButton(Ic.TRASH, "Delete ${m.name}", onDelete, tint = BB.Danger)
                }
                Txt("Downloaded and verified. Loading takes a moment and uses memory while it is loaded.", style = BBType.Caption)
            }
            ModelManager.Phase.LOADING -> Txt("Loading the model and checking that it works…", style = BBType.BodySecondary)
            ModelManager.Phase.READY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassButton("Unload", { vm.unload() }, icon = Ic.PAUSE)
                PlainIconButton(Ic.TRASH, "Delete ${m.name}", onDelete, tint = BB.Danger)
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Download on Wi-Fi", { vm.download(m) }, icon = Ic.DOWNLOAD, prominent = true)
                GlassButton("Mobile data…", onMobile, icon = Ic.DOWNLOAD)
            }
        }
    }
}

@Composable
private fun Modifier.clickableUrl(context: android.content.Context, url: String): Modifier =
    this.tappable({
        val uri = Uri.parse(url)
        if (uri.scheme == "https") runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }, description = "Open $url", minSize = 40.dp)

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().opaqueSurface(28.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt(title, style = BBType.Title)
            Txt(body, style = BBType.BodySecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                GlassButton("Cancel", onDismiss)
                GlassButton(confirm, onConfirm, prominent = !danger)
            }
        }
    }
}
