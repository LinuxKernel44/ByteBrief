package app.bytebrief.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bytebrief.ui.Tab
import app.bytebrief.ui.glass.GlassLevel
import app.bytebrief.ui.glass.glassSurface
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType

val TopBarHeight = 56.dp
val BottomBarHeight = 72.dp

/** Height of the status bar inset, so content can start below the pinned top bar. */
@Composable
fun topContentPadding(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight + 8.dp

@Composable
fun bottomContentPadding(withTabs: Boolean): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + (if (withTabs) BottomBarHeight + 24.dp else 16.dp)

/** Pinned glass bar: the list scrolling under it is blurred and refracted by the backdrop. */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .fillMaxWidth()
            .glassSurface(0.dp, GlassLevel.Bar, BB.GlassTint)
            .padding(top = top),
    ) {
        Row(
            Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) PlainIconButton(Ic.BACK, "Back", onBack, tint = BB.TextPrimary)
            else Box(Modifier.padding(start = 12.dp))
            Txt(title, style = BBType.Title, maxLines = 1, modifier = Modifier.weight(1f).semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** Floating glass tab bar. */
@Composable
fun GlassTabBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = bottom + 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .glassSurface(32.dp, GlassLevel.Bar, BB.GlassTint)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tab in Tab.entries) {
                val selected = tab == current
                val icon = when (tab) {
                    Tab.ForYou -> Ic.SPARK
                    Tab.Browse -> Ic.LIST
                    Tab.Briefing -> Ic.SUN
                    Tab.Saved -> if (selected) Ic.BOOKMARK_FILLED else Ic.BOOKMARK
                    Tab.More -> Ic.SLIDERS
                }
                Column(
                    Modifier
                        .weight(1f)
                        .tappable({ onSelect(tab) }, description = tab.label, role = Role.Tab, minSize = 56.dp)
                        .semantics { this.selected = selected; stateDescription = if (selected) "Selected" else "Not selected" }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(icon, if (selected) BB.Accent else BB.TextSecondary, size = 24.dp)
                    Txt(tab.label, style = BBType.Caption, color = if (selected) BB.TextPrimary else BB.TextSecondary, maxLines = 1)
                }
            }
        }
    }
}
