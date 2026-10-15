package app.bytebrief.ui.glass

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/** Kotlin/Java bridge: the only Kotlin entry point the Java activity needs. */
object GlassSpikeBridge {
    @JvmStatic
    fun setContent(activity: ComponentActivity) {
        activity.setContent { GlassSpike() }
    }
}

@Composable
private fun GlassSpike() {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF0A0A12), Color(0xFF3B2A8C), Color(0xFF0E7C86)))
                )
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(280.dp, 140.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(32.dp) },
                    effects = {
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(16f.dp.toPx(), 32f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.08f)) }
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText("ByteBrief glass spike", style = androidx.compose.ui.text.TextStyle(color = Color.White))
        }
    }
}
