package app.bytebrief.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Design tokens. ByteBrief is a dark-only app: glass needs a dark field to refract against. */
object BB {
    // Surfaces
    val Background = Color(0xFF07080C)
    val BackgroundTop = Color(0xFF0C0F18)
    val Elevated = Color(0xFF12151F)
    val Opaque = Color(0xFF151927)          // fallback for glass surfaces
    val OpaqueBorder = Color(0x33FFFFFF)

    // Text (all measured against Background / Opaque: >= 7:1 for primary and secondary)
    val TextPrimary = Color(0xFFF2F4F8)
    val TextSecondary = Color(0xFFB4BBC9)
    val TextTertiary = Color(0xFF8B93A5)    // >= 4.5:1 on Opaque; used only for non-essential metadata

    // Accent and semantics
    val Accent = Color(0xFF7CC4FF)
    val AccentSoft = Color(0x337CC4FF)
    val Warm = Color(0xFFFFB86B)
    val Success = Color(0xFF6EE7A8)
    val Danger = Color(0xFFFF8080)

    // Source identity colors
    val HackerNews = Color(0xFFFF8A3D)
    val Trending = Color(0xFFB08CFF)
    val Rss = Color(0xFF4FD1E8)
    val Release = Color(0xFF6EE7A8)

    // Glass
    val GlassTint = Color(0x990A0D16)       // dark tint keeps text legible over any backdrop
    val GlassTintStrong = Color(0xCC0A0D16)
    val CardFill = Color(0x1AFFFFFF)
    val CardBorderTop = Color(0x40FFFFFF)
    val CardBorderBottom = Color(0x0DFFFFFF)

    val BackgroundBrush = Brush.verticalGradient(listOf(BackgroundTop, Background))

    val ScreenPadding = 16.dp
}

object BBType {
    private val sans = FontFamily.SansSerif
    val Display = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, color = BB.TextPrimary)
    val Title = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, color = BB.TextPrimary)
    val Heading = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, color = BB.TextPrimary)
    val Body = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = BB.TextPrimary)
    val BodySecondary = Body.copy(color = BB.TextSecondary)
    val Caption = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = BB.TextSecondary)
    val Label = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, color = BB.TextPrimary)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = BB.TextPrimary)
}

/** How glass is rendered. Computed once from user setting, battery saver and accessibility settings. */
data class GlassConfig(
    /** Real Liquid Glass (backdrop refraction); false means opaque surfaces. */
    val enabled: Boolean = true,
    /** Skip non-essential motion. */
    val reducedMotion: Boolean = false,
)

val LocalGlassConfig = staticCompositionLocalOf { GlassConfig() }

@Composable
fun ByteBriefTheme(glass: GlassConfig, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassConfig provides glass, content = content)
}
