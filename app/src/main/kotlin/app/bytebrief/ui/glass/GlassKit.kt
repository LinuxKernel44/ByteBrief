package app.bytebrief.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bytebrief.ui.LocalNav
import app.bytebrief.ui.components.GlassTabBar
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.LocalGlassConfig
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/** The backdrop that glass surfaces of the current screen refract. */
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/** True inside [BackdropSource]: glass there would sample its own ancestor layer, so it renders frosted instead. */
val LocalInBackdropSource = compositionLocalOf { false }

/** Strength presets. Larger surfaces blur more; small controls refract less so their content stays crisp. */
enum class GlassLevel(val blurDp: Float, val refractionHeightDp: Float, val refractionAmountDp: Float) {
    Bar(blurDp = 6f, refractionHeightDp = 14f, refractionAmountDp = 26f),
    Control(blurDp = 3f, refractionHeightDp = 9f, refractionAmountDp = 18f),
    Sheet(blurDp = 12f, refractionHeightDp = 16f, refractionAmountDp = 30f),
}

/**
 * A Liquid Glass surface: the backdrop behind it is blurred, refracted through a lens and given a
 * specular edge by the verified Backdrop library, then tinted dark so foreground text keeps its
 * contrast. When glass is disabled (user setting, battery saver, reduced motion) or no backdrop is
 * available, an opaque surface with a hairline border is used instead.
 */
@Composable
fun Modifier.glassSurface(radius: Dp, level: GlassLevel = GlassLevel.Control, tint: Color = BB.GlassTint): Modifier {
    val config = LocalGlassConfig.current
    val backdrop = LocalBackdrop.current
    if (!config.enabled || backdrop == null) return this.opaqueSurface(radius, tint = BB.Opaque)
    // A surface inside the recorded content cannot refract that same content (it would recurse); use the cheap look.
    if (LocalInBackdropSource.current) return this.frostedSurface(radius, tint)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedRectangle(radius) },
        effects = {
            vibrancy()
            blur(level.blurDp.dp.toPx())
            lens(level.refractionHeightDp.dp.toPx(), level.refractionAmountDp.dp.toPx())
        },
        onDrawSurface = { drawRect(tint) },
    )
}

/** Translucent tinted surface with a specular hairline; used for controls inside scrolling content. */
fun Modifier.frostedSurface(radius: Dp, tint: Color): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(tint)
        .background(Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x0DFFFFFF))))
        .border(1.dp, Brush.verticalGradient(listOf(BB.CardBorderTop, BB.CardBorderBottom)), shape)
}

fun Modifier.opaqueSurface(radius: Dp, tint: Color = BB.Opaque): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(tint).border(1.dp, BB.OpaqueBorder, shape)
}

/** Cheap frosted card for long lists: no per-card shader, just a translucent fill and a specular hairline. */
fun Modifier.frostedCard(radius: Dp = 20.dp, highlight: Boolean = false): Modifier {
    val shape = RoundedCornerShape(radius)
    val fill = if (highlight) Brush.verticalGradient(listOf(Color(0x2E7CC4FF), Color(0x127CC4FF)))
    else Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x10FFFFFF)))
    return this
        .clip(shape)
        .background(fill)
        .border(1.dp, Brush.verticalGradient(listOf(BB.CardBorderTop, BB.CardBorderBottom)), shape)
}

/**
 * A screen with glass chrome. The backdrop that glass bars sample is recorded from [BackdropSource] only;
 * bars and the tab bar are siblings drawn above it. A glass surface must never be a descendant of the
 * layer it samples, or the layer would record itself.
 */
@Composable
fun GlassScreen(modifier: Modifier = Modifier.fillMaxSize(), tabs: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(modifier) {
            content()
            if (tabs) {
                val nav = LocalNav.current
                GlassTabBar(nav.currentTab, { nav.selectTab(it) }, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
            }
        }
    }
}

/** The part of a screen that glass refracts: the atmospheric background plus the scrolling content. */
@Composable
fun BoxScope.BackdropSource(modifier: Modifier = Modifier.fillMaxSize(), content: @Composable BoxScope.() -> Unit) {
    val backdrop = LocalBackdrop.current as? com.kyant.backdrop.backdrops.LayerBackdrop
    Box(modifier.then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
        AtmosphericBackground()
        CompositionLocalProvider(LocalInBackdropSource provides true) { content() }
    }
}

/** Dark gradient with soft colored light pools. Static, so it costs nothing per frame and honors reduced motion. */
@Composable
fun AtmosphericBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(BB.BackgroundBrush)
            .background(Brush.radialGradient(listOf(Color(0x3A5B7CFF), Color.Transparent), center = Offset(150f, 250f), radius = 900f))
            .background(Brush.radialGradient(listOf(Color(0x2E00D1B2), Color.Transparent), center = Offset(1100f, 1500f), radius = 1000f))
            .background(Brush.radialGradient(listOf(Color(0x24B08CFF), Color.Transparent), center = Offset(900f, 700f), radius = 800f))
    )
}
