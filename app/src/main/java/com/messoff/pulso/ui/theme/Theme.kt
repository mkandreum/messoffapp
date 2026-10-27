package com.messoff.pulso.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.messoff.pulso.R
import com.messoff.pulso.data.Category

/** Tokens calcados de `styles.css` (:root y [data-theme='night']). */
@Immutable
data class PulsoColors(
    val night: Boolean,
    val paper: Color,
    val paper2: Color,
    val paper3: Color,
    val paper4: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val onInk: Color,
    val note: Color,
    val noteInk: Color,
    val green: Color,
    val trabajo: Color,
    val trabajoWash: Color,
    val personal: Color,
    val personalWash: Color,
    val salud: Color,
    val saludWash: Color,
    val social: Color,
    val socialWash: Color,
) {
    fun cat(c: Category): Color = when (c) {
        Category.Trabajo -> trabajo
        Category.Personal -> personal
        Category.Salud -> salud
        Category.Social -> social
    }

    fun wash(c: Category): Color = when (c) {
        Category.Trabajo -> trabajoWash
        Category.Personal -> personalWash
        Category.Salud -> saludWash
        Category.Social -> socialWash
    }
}

val PaperColors = PulsoColors(
    night = false,
    paper = Color(0xFFF0EBE0),
    paper2 = Color(0xFFFBF8F0),
    paper3 = Color(0xFFE8E1D2),
    paper4 = Color(0xFFDCD3C0),
    ink = Color(0xFF1B1914),
    ink2 = Color(0xFF57524A),
    ink3 = Color(0xFF726B5E),
    line = Color(0xFFE1D9C8),
    lineStrong = Color(0xFFCFC5B0),
    accent = Color(0xFFC9431F),
    accentSoft = Color(0xFFF7E2D8),
    accentInk = Color(0xFFFFFFFF),
    onInk = Color(0xFFFBF8F0),
    note = Color(0xFFF2E3A9),
    noteInk = Color(0xFF3A3320),
    green = Color(0xFF3F6B52),
    trabajo = Color(0xFFC8491F),
    trabajoWash = Color(0xFFFBEEE7),
    personal = Color(0xFF3F6B52),
    personalWash = Color(0xFFEDF2E9),
    salud = Color(0xFF2F6480),
    saludWash = Color(0xFFE9F1F4),
    social = Color(0xFF996A12),
    socialWash = Color(0xFFF8F0DA),
)

val NightColors = PulsoColors(
    night = true,
    paper = Color(0xFF14130F),
    paper2 = Color(0xFF1D1B15),
    paper3 = Color(0xFF28251D),
    paper4 = Color(0xFF353126),
    ink = Color(0xFFF2ECDE),
    ink2 = Color(0xFFB7AE9C),
    ink3 = Color(0xFF9D9484),
    line = Color(0xFF2E2A21),
    lineStrong = Color(0xFF443E31),
    accent = Color(0xFFFF6F42),
    accentSoft = Color(0xFF3A241B),
    accentInk = Color(0xFF1A0F0A),
    onInk = Color(0xFF14130F),
    note = Color(0xFFE9D99C),
    noteInk = Color(0xFF332D1C),
    green = Color(0xFF82B090),
    trabajo = Color(0xFFFF7A4D),
    trabajoWash = Color(0xFF2E2019),
    personal = Color(0xFF8DBA99),
    personalWash = Color(0xFF1F2821),
    salud = Color(0xFF85B8D2),
    saludWash = Color(0xFF1B262D),
    social = Color(0xFFDCB060),
    socialWash = Color(0xFF2B2417),
)

val LocalPulso = staticCompositionLocalOf { PaperColors }

object PulsoFonts {
    val Sans = FontFamily(
        Font(R.font.instrument_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.instrument_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.instrument_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.instrument_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    val Serif = FontFamily(
        Font(R.font.instrument_serif, FontWeight.Normal),
        Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
    )
}

/** Estilos tipográficos usados en toda la app. */
object Type {
    fun sans(size: TextUnit, weight: FontWeight = FontWeight.Normal, spacing: TextUnit = 0.sp, lineHeight: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = PulsoFonts.Sans, fontSize = size, fontWeight = weight, letterSpacing = spacing, lineHeight = lineHeight)

    fun serif(size: TextUnit, italic: Boolean = false, spacing: TextUnit = (-0.01).em, lineHeight: TextUnit = TextUnit.Unspecified) =
        TextStyle(
            fontFamily = PulsoFonts.Serif,
            fontSize = size,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = spacing,
            lineHeight = lineHeight,
        )

    /** Versalitas con tracking: "SEMANA 44 · OCTUBRE". */
    val kicker = sans(11.5.sp, FontWeight.SemiBold, 0.12.em)
    val label = sans(11.sp, FontWeight.SemiBold, 0.1.em)
    val body = sans(15.sp, lineHeight = 21.sp)
    val bodySmall = sans(13.sp, lineHeight = 18.sp)
    val button = sans(15.sp, FontWeight.SemiBold)
    val pageTitle = serif(44.sp, lineHeight = 46.sp)
}

/** Colores de equipo: [papel, noche], calcados de la web. */
fun teamColor(key: String?, night: Boolean): Color {
    val pair = when (key) {
        "rojo" -> 0xFFC9431F to 0xFFFF7A4D
        "verde" -> 0xFF3F6B52 to 0xFF8DBA99
        "ocre" -> 0xFF996A12 to 0xFFDCB060
        "violeta" -> 0xFF6B4F96 to 0xFFB79AE0
        "grafito" -> 0xFF4A463D to 0xFFC4BCA8
        else -> 0xFF2F6480 to 0xFF85B8D2
    }
    return Color(if (night) pair.second else pair.first)
}

val TEAM_COLOR_KEYS = listOf("rojo", "verde", "azul", "ocre", "violeta", "grafito")

@Composable
fun PulsoTheme(night: Boolean, content: @Composable () -> Unit) {
    val colors = if (night) NightColors else PaperColors
    val scheme = if (night) {
        darkColorScheme(primary = colors.accent, background = colors.paper, surface = colors.paper2, onSurface = colors.ink)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.paper, surface = colors.paper2, onSurface = colors.ink)
    }
    // El diseño está medido para letra normal: con la letra del sistema muy grande se descuadra.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val capped = androidx.compose.ui.unit.Density(density.density, minOf(density.fontScale, 1.1f))
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPulso provides colors,
        androidx.compose.ui.platform.LocalDensity provides capped,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
