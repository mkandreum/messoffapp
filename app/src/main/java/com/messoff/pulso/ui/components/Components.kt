package com.messoff.pulso.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messoff.pulso.data.Category
import com.messoff.pulso.haptics.Haptic
import com.messoff.pulso.haptics.Haptics
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.PulsoFonts
import com.messoff.pulso.ui.theme.Type

/** Pulsable con muelle: cede al apretar y rebota al soltar. */
@Composable
fun Modifier.press(
    enabled: Boolean = true,
    scaleTo: Float = 0.95f,
    haptics: Haptics? = null,
    haptic: Haptic = Haptic.Selection,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) scaleTo else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .scale(scale)
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            haptics?.play(haptic)
            onClick()
        }
}

@Composable
fun Eyebrow(text: String, color: Color = LocalPulso.current.ink2, modifier: Modifier = Modifier) {
    val c = LocalPulso.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = Type.kicker, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Tu *semana*": serif con la segunda parte en cursiva bermellón. */
@Composable
fun PageTitle(plain: String, accent: String, modifier: Modifier = Modifier, size: Int = 46) {
    val c = LocalPulso.current
    Text(
        buildAnnotatedString {
            append(plain)
            append(" ")
            withStyle(SpanStyle(color = c.accent, fontStyle = FontStyle.Italic, fontFamily = PulsoFonts.Serif)) {
                append(accent)
            }
        },
        style = Type.serif(size.sp, lineHeight = (size + 2).sp),
        color = c.ink,
        modifier = modifier,
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    radius: Dp = 22.dp,
    content: @Composable () -> Unit,
) {
    val c = LocalPulso.current
    Box(
        modifier
            .shadow(14.dp, RoundedCornerShape(radius), ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
            .clip(RoundedCornerShape(radius))
            .background(c.paper2)
            .border(1.dp, c.line, RoundedCornerShape(radius))
            .padding(padding),
    ) { content() }
}

@Composable
fun PanelHead(title: String, modifier: Modifier = Modifier, aside: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalPulso.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Type.sans(17.sp, FontWeight.SemiBold), color = c.ink, modifier = Modifier.weight(1f))
        if (aside != null) Text(aside, style = Type.sans(13.sp), color = c.ink3)
        if (action != null && onAction != null) {
            Row(
                Modifier.press(onClick = onAction).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = Type.sans(14.sp, FontWeight.Medium), color = c.ink2)
                Spacer(Modifier.width(4.dp))
                LucideIcon(LucideData.ArrowRight, c.ink2, 16.dp)
            }
        }
    }
}

enum class BtnKind { Primary, Ghost, Accent, Danger }

@Composable
fun PulsoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Primary,
    icon: List<String>? = null,
    trailing: List<String>? = null,
    enabled: Boolean = true,
    haptics: Haptics? = null,
    height: Dp = 48.dp,
) {
    val c = LocalPulso.current
    val (bg, fg, border) = when (kind) {
        BtnKind.Primary -> Triple(c.ink, c.onInk, c.ink)
        BtnKind.Ghost -> Triple(Color.Transparent, c.ink, c.lineStrong)
        BtnKind.Accent -> Triple(c.accent, c.accentInk, c.accent)
        BtnKind.Danger -> Triple(Color.Transparent, c.accent, Color.Transparent)
    }
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .border(1.dp, if (enabled) border else border.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .press(enabled = enabled, haptics = haptics, haptic = Haptic.Light, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            LucideIcon(icon, fg, 18.dp, 2f)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.button, color = fg)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            LucideIcon(trailing, fg, 18.dp, 2f)
        }
    }
}

@Composable
fun IconCircle(
    icon: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    bg: Color = LocalPulso.current.paper2,
    tint: Color = LocalPulso.current.ink,
    border: Color = LocalPulso.current.line,
    shape: Shape = RoundedCornerShape(16.dp),
    haptics: Haptics? = null,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .press(haptics = haptics, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, tint, size * 0.42f, contentDescription = contentDescription) }
}

/** Chip de selección: relleno de tinta cuando está activo, discontinuo si `dashed`. */
@Composable
fun PulsoChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dashed: Boolean = false,
    dot: Color? = null,
    haptics: Haptics? = null,
    enabled: Boolean = true,
) {
    val c = LocalPulso.current
    val bg by animateColorAsState(if (selected) c.ink else Color.Transparent, label = "chip")
    val fg by animateColorAsState(if (selected) c.onInk else c.ink2, label = "chipFg")
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (dashed && !selected) Modifier.dashedBorder(c.lineStrong, 999.dp)
                else Modifier.border(1.dp, if (selected) c.ink else c.lineStrong, shape),
            )
            .press(enabled = enabled, haptics = haptics, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = Type.sans(14.sp, FontWeight.Medium), color = if (enabled) fg else fg.copy(alpha = 0.4f))
    }
}

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.dp): Modifier = drawBehind {
    val stroke = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
    val r = minOf(radius.toPx(), size.minDimension / 2)
    drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(r, r))
}

@Composable
fun CategoryDot(cat: Category, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(LocalPulso.current.cat(cat)))
}

@Composable
fun CategoryTag(cat: Category) {
    val c = LocalPulso.current
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.wash(cat))
            .border(1.dp, c.cat(cat).copy(alpha = 0.3f), RoundedCornerShape(999.dp))
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryDot(cat, 7.dp)
        Spacer(Modifier.width(8.dp))
        Text(cat.label.uppercase(), style = Type.sans(13.sp, FontWeight.SemiBold, 0.08.sp), color = c.ink)
    }
}

fun initials(name: String) = name.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
    .joinToString("") { it.first().uppercase() }

@Composable
fun Avatar(name: String, size: Dp = 26.dp, bg: Color = LocalPulso.current.paper3) {
    val c = LocalPulso.current
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).border(1.5.dp, c.paper2, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), style = Type.sans((size.value * 0.4f).sp, FontWeight.SemiBold), color = c.ink2)
    }
}

@Composable
fun AvatarStack(names: List<String>, max: Int = 3, size: Dp = 24.dp) {
    if (names.isEmpty()) return
    Row {
        names.take(max).forEachIndexed { i, n ->
            Box(Modifier.offset(x = (-6 * i).dp)) { Avatar(n, size) }
        }
        if (names.size > max) Box(Modifier.offset(x = (-6 * max).dp)) { Avatar("+${names.size - max}", size) }
    }
}

@Composable
fun PulsoSwitch(checked: Boolean, onChange: (Boolean) -> Unit, haptics: Haptics? = null) {
    val c = LocalPulso.current
    val track by animateColorAsState(if (checked) c.accent else c.paper4, label = "track")
    val knob by animateDpAsState(
        if (checked) 24.dp else 3.dp,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "knob",
    )
    Box(
        Modifier
            .width(54.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(track)
            .press(haptics = haptics) { onChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 3.dp)
                .size(26.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** El logo de Pulso: dos círculos de tinta, núcleo bermellón y marcas de pulso. */
@Composable
fun LogoMark(size: Dp = 32.dp, ink: Color = LocalPulso.current.ink, accent: Color = LocalPulso.current.accent) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 32f
        val stroke = Stroke(1.5f * s, cap = StrokeCap.Round)
        val center = Offset(16f * s, 16f * s)
        drawCircle(ink, 12.5f * s, center, style = stroke)
        drawCircle(ink, 5.5f * s, center, style = stroke)
        drawCircle(accent, 2.4f * s, center)
        drawLine(ink, Offset(16f * s, 0.8f * s), Offset(16f * s, 5f * s), 1.5f * s, StrokeCap.Round)
        drawLine(ink, Offset(16f * s, 27f * s), Offset(16f * s, 31.2f * s), 1.5f * s, StrokeCap.Round)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, icon: List<String>? = null, aside: String? = null) {
    val c = LocalPulso.current
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            LucideIcon(icon, c.ink3, 15.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), style = Type.label, color = c.ink3, modifier = Modifier.weight(1f))
        if (aside != null) Text(aside, style = Type.sans(13.sp), color = c.ink3)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier, dashed: Boolean = false) {
    val c = LocalPulso.current
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
        )
    }
}

@Composable
fun EmptyState(mark: String, title: String, copy: String) {
    val c = LocalPulso.current
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(mark, style = Type.serif(34.sp), color = c.accent)
        Spacer(Modifier.height(6.dp))
        Text(title, style = Type.serif(24.sp), color = c.ink)
        Spacer(Modifier.height(4.dp))
        Text(copy, style = Type.bodySmall, color = c.ink3)
    }
}

/** Píldora "En curso" con punto latiendo. */
@Composable
fun LiveBadge(text: String = "En curso", color: Color = LocalPulso.current.accent) {
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "live").animateFloat(
        0.35f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "liveDot",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color.copy(alpha = pulse)))
        Spacer(Modifier.width(6.dp))
        Text(text.uppercase(), style = Type.sans(12.sp, FontWeight.SemiBold, 0.1.sp), color = color)
    }
}

@Composable
fun BorderBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalPulso.current
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.paper)
            .border(BorderStroke(1.dp, c.line), RoundedCornerShape(16.dp)),
    ) { content() }
}
