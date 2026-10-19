package app.bytebrief.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bytebrief.ui.glass.GlassLevel
import app.bytebrief.ui.glass.frostedCard
import app.bytebrief.ui.glass.glassSurface
import app.bytebrief.ui.theme.BB
import app.bytebrief.ui.theme.BBType
import app.bytebrief.ui.theme.LocalGlassConfig

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = BBType.Body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (color == Color.Unspecified) style else style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** Click handling with a subtle press response (skipped when reduced motion is on) and a >= 48dp touch target. */
@Composable
fun Modifier.tappable(
    onClick: () -> Unit,
    description: String? = null,
    role: Role = Role.Button,
    enabled: Boolean = true,
    minSize: Dp = 48.dp,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalGlassConfig.current.reducedMotion
    val scale by animateFloatAsState(if (pressed && !reduced) 0.97f else 1f, tween(90), label = "press")
    // Dimming on press is the one cue every tappable shares, so no control ever feels dead under the finger.
    val alpha = if (!enabled) 0.4f else if (pressed && !reduced) 0.72f else 1f
    return this
        .defaultMinSize(minHeight = minSize)
        .scale(scale)
        .alpha(alpha)
        .semantics(mergeDescendants = true) {
            this.role = role
            if (description != null) contentDescription = description
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/**
 * Glass button. [done] shows a finished state (check mark, green tint, not tappable) for actions that
 * leave something lasting, such as following a forum. [confirmLabel] instead flashes that label with a
 * check for a moment after a one-shot action whose result is not otherwise visible.
 */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Ic? = null,
    prominent: Boolean = false,
    enabled: Boolean = true,
    done: Boolean = false,
    confirmLabel: String? = null,
) {
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(flashing) {
        if (flashing) {
            delay(1800)
            flashing = false
        }
    }
    val showDone = done || flashing
    val tint = when {
        showDone -> Color(0xB3226B4A)
        prominent -> Color(0xB32A5E8F)
        else -> BB.GlassTint
    }
    Row(
        modifier
            .glassSurface(24.dp, GlassLevel.Control, tint)
            .tappable({ if (!done) { onClick(); if (confirmLabel != null) flashing = true } }, enabled = enabled)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shownIcon = if (showDone) Ic.CHECK else icon
        if (shownIcon != null) Icon(shownIcon, if (showDone) BB.Success else if (prominent) Color.White else BB.Accent, size = 18.dp)
        Txt(if (flashing && confirmLabel != null) confirmLabel else label, style = BBType.Label, color = Color.White, maxLines = 1)
    }
}

/** Round glass icon button with an accessible label. */
@Composable
fun GlassIconButton(icon: Ic, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = BB.TextPrimary, enabled: Boolean = true) {
    Box(
        modifier
            .size(48.dp)
            .glassSurface(24.dp, GlassLevel.Control)
            .tappable(onClick, description, enabled = enabled),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, size = 22.dp) }
}

/** Plain icon button for dense rows (no glass); still a 48dp target. */
@Composable
fun PlainIconButton(icon: Ic, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = BB.TextSecondary, enabled: Boolean = true) {
    Box(modifier.size(48.dp).tappable(onClick, description, enabled = enabled), contentAlignment = Alignment.Center) {
        Icon(icon, tint, size = 22.dp)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Ic? = null, accent: Color = BB.Accent) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.22f) else Color(0x14FFFFFF))
            .border(BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.7f) else Color(0x26FFFFFF)), shape)
            .tappable(onClick, role = Role.Switch, minSize = 40.dp)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, if (selected) accent else BB.TextSecondary, size = 16.dp)
        Txt(label, style = BBType.Label, color = if (selected) BB.TextPrimary else BB.TextSecondary, maxLines = 1)
    }
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier, icon: Ic? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, color, size = 12.dp, stroke = 2.2f)
        Txt(text, style = BBType.Caption, color = color, maxLines = 1)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Txt(
        text,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp).semantics { heading() },
        style = BBType.Caption.copy(color = BB.TextTertiary),
    )
}

/** Grouped settings container. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().frostedCard(20.dp).padding(horizontal = 16.dp, vertical = 6.dp)) { content() }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier
            .fillMaxWidth()
            .tappable({ onChange(!checked) }, role = Role.Switch, enabled = enabled, minSize = 56.dp)
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, style = BBType.Body)
            if (subtitle != null) Txt(subtitle, style = BBType.Caption, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Toggle(checked)
    }
}

@Composable
fun Toggle(checked: Boolean) {
    val reduced = LocalGlassConfig.current.reducedMotion
    val pos by animateFloatAsState(if (checked) 1f else 0f, tween(if (reduced) 0 else 160), label = "toggle")
    Box(
        Modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(CircleShape)
            .background(if (checked) BB.Accent.copy(alpha = 0.85f) else Color(0x33FFFFFF))
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .padding(start = (20.dp * pos))
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFF05243D) else Color(0xFFE9ECF2)),
        )
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = BB.Accent) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0x22FFFFFF))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(color))
    }
}

/** Empty, error and offline states share one layout so they feel consistent. */
@Composable
fun StateMessage(icon: Ic, title: String, body: String, modifier: Modifier = Modifier, tint: Color = BB.TextSecondary, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0x14FFFFFF)), contentAlignment = Alignment.Center) { Icon(icon, tint, size = 30.dp) }
        Txt(title, style = BBType.Title, modifier = Modifier.semantics { heading() })
        Txt(body, style = BBType.BodySecondary)
        if (action != null) Box(Modifier.padding(top = 8.dp)) { action() }
    }
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().frostedCard(20.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth(0.35f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x1FFFFFFF)))
        Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x26FFFFFF)))
        Box(Modifier.fillMaxWidth(0.8f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x26FFFFFF)))
        Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x14FFFFFF)))
    }
}

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    leading: Ic? = null,
    singleLine: Boolean = true,
    imeAction: ImeAction = if (onSearch != null) ImeAction.Search else ImeAction.Done,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leading != null) Icon(leading, BB.TextSecondary, size = 20.dp)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Txt(hint, style = BBType.Body, color = BB.TextTertiary, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = BBType.Body,
                cursorBrush = SolidColor(BB.Accent),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }, onDone = { onSearch?.invoke() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            )
        }
    }
}
