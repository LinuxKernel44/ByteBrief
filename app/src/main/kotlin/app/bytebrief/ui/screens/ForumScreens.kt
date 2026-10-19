package app.bytebrief.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.bytebrief.net.WebViewPageFetcher
import app.bytebrief.source.forum.ForumPageState
import app.bytebrief.source.forum.ForumUrls
import app.bytebrief.ui.LocalNav
import app.bytebrief.ui.Route
import app.bytebrief.ui.components.GlassButton
import app.bytebrief.ui.components.GlassTopBar
import app.bytebrief.ui.components.Ic
import app.bytebrief.ui.components.Icon
import app.bytebrief.ui.components.PlainIconButton
import app.bytebrief.ui.components.Txt
import app.bytebrief.ui.components.bottomContentPadding
import app.bytebrief.ui.components.tappable
import app.bytebrief.ui.components.topContentPadding
import app.bytebrief.ui.glass.BackdropSource
import app.bytebrief.ui.glass.GlassScreen
import app.bytebrief.ui.glass.frostedCard
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType
import app.bytebrief.ui.util.observeAsState
import app.bytebrief.ui.viewModelOf
import app.bytebrief.ui.vm.ForumBrowserViewModel
import app.bytebrief.ui.vm.ForumsViewModel
import kotlinx.coroutines.delay

/**
 * Bottom sheet that lets XDA Forums' human-verification check finish. The check runs in a real
 * WebView whose cookies are shared with the background loader. It starts hidden and usually passes
 * on its own; the eye button shows the page for checks that need a tap.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerifyScreen() {
    val vm = viewModelOf<ForumsViewModel>()
    val nav = LocalNav.current
    var showPage by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    val web = remember { arrayOfNulls<WebView>(1) }

    LaunchedEffect(Unit) { vm.verificationShown() }
    LaunchedEffect(solved) {
        while (!solved) {
            delay(700)
            seconds++
            web[0]?.evaluateJavascript(WebViewPageFetcher.DOM_JS) { v ->
                if (WebViewPageFetcher.stateOf(v) == ForumPageState.State.READY) solved = true
            }
        }
        vm.onVerified()
        delay(700)
        nav.pop()
    }
    DisposableEffect(Unit) { onDispose { web[0]?.destroy(); web[0] = null } }

    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(remember { MutableInteractionSource() }, null) { nav.pop() }) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(BB.Opaque)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .navigationBarsPadding(),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF)))
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("XDA Forums check", style = BBType.Title, modifier = Modifier.weight(1f).semantics { heading() })
                PlainIconButton(if (showPage) Ic.EYE_OFF else Ic.EYE, if (showPage) "Hide the page" else "Show the page to complete the check yourself", { showPage = !showPage }, tint = BB.TextPrimary)
                PlainIconButton(Ic.CLOSE, "Close", { nav.pop() }, tint = BB.TextPrimary)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp))) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportMultipleWindows(false)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            setBackgroundColor(android.graphics.Color.BLACK)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    request.url.scheme != "https"
                                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = Unit
                            }
                            loadUrl(ForumUrls.HOME)
                            web[0] = this
                        }
                    },
                )
                if (!showPage) {
                    Column(
                        Modifier.fillMaxSize().background(BB.Opaque).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (solved) Icon(Ic.CHECK, BB.Success, size = 44.dp, stroke = 2.6f) else Spinner()
                        Txt(if (solved) "Verified" else "Solving the check…", style = BBType.Heading)
                        Txt(
                            if (solved) "You can go back. Forums that were waiting will refresh now."
                            else "XDA Forums uses a security service to block bots. ByteBrief is completing it in the background" +
                                (if (seconds > 12) ". It is taking a while: tap the eye at the top to show the page and finish it yourself." else "."),
                            style = BBType.BodySecondary,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Spinner() {
    val t = rememberInfiniteTransition(label = "spinner")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Canvas(Modifier.size(40.dp)) {
        val w = 4.dp.toPx()
        drawArc(BB.Accent, angle, 260f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Walks the XDA forum tree so a forum can be followed without typing its address. */
@Composable
fun ForumBrowserScreen() {
    val vm = viewModelOf<ForumBrowserViewModel>()
    val nav = LocalNav.current
    val state by vm.state.observeAsState()
    val message by vm.message.observeAsState()
    val followed by vm.followed.observeAsState()
    val following by vm.following.observeAsState()
    var filter by remember(state?.url) { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.start() }

    GlassScreen(Modifier.fillMaxSize(), tabs = false) {
        BackdropSource {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding(), bottom = bottomContentPadding(false)),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (message != null) item { Txt(message!!, style = BBType.Body, color = BB.Accent, modifier = Modifier.padding(bottom = 4.dp)) }
                val s = state
                if (s == null || s.loading) item { Txt("Loading…", style = BBType.BodySecondary, modifier = Modifier.padding(8.dp)) }
                if (s != null && s.error != null) item {
                    Column(Modifier.fillMaxWidth().frostedCard(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Txt(s.error!!, style = BBType.Body, color = BB.Warm)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (s.needsVerification) GlassButton("Verify", { nav.push(Route.Verify) }, icon = Ic.LOCK, prominent = true)
                            GlassButton("Try again", { vm.reload() }, icon = Ic.REFRESH)
                        }
                    }
                }
                if (s != null && s.canFollow) item {
                    Column(Modifier.fillMaxWidth().frostedCard(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Txt(s.title ?: "This forum", style = BBType.Heading)
                        Txt("New threads will appear in your feed, and you can read them in full.", style = BBType.BodySecondary)
                        val isFollowed = followed.orEmpty().any { it.url.trimEnd('/').equals(s.url.trimEnd('/'), ignoreCase = true) }
                        when {
                            isFollowed -> GlassButton("Following", {}, icon = Ic.CHECK, done = true)
                            following == true -> GlassButton("Following…", {}, icon = Ic.PLUS, prominent = true, enabled = false)
                            else -> GlassButton("Follow this forum", { vm.followCurrent() }, icon = Ic.PLUS, prominent = true)
                        }
                    }
                }
                if (s != null && s.nodes.size > 8) item {
                    app.bytebrief.ui.components.TextInput(filter, { filter = it }, "Filter this list", leading = Ic.SEARCH)
                }
                val shown = s?.nodes?.filter { filter.isBlank() || it.title.contains(filter.trim(), ignoreCase = true) }.orEmpty()
                if (s != null) items(shown, key = { it.url }) { n ->
                    Row(
                        Modifier.fillMaxWidth().frostedCard(16.dp).tappable({ vm.open(n) }, description = n.title, role = Role.Button, minSize = 64.dp).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(if (n.category) Ic.LAYERS else Ic.FORUM, BB.Forum, size = 20.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Txt(n.title, style = BBType.Heading, maxLines = 2)
                            if (n.description != null) Txt(n.description!!, style = BBType.Caption, maxLines = 2)
                        }
                        Icon(Ic.CHEVRON_RIGHT, BB.TextTertiary, size = 18.dp)
                    }
                }
            }
        }
        GlassTopBar(state?.title ?: "XDA Forums", onBack = { if (!vm.up()) nav.pop() })
    }
}
