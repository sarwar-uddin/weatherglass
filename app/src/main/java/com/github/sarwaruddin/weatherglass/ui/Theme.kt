package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.sarwaruddin.weatherglass.R
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Weatherglass palette: ink, chrome and one crimson focal point (docs/branding/BRAND.md, section 4).
 * Contrast figures are WCAG ratios on [Card].
 */
object Pal {
    val Bg = Color(0xFF070708)
    val Card = Color(0xFF131316)
    val CardTop = Color(0xFF1C1C21)
    val Edge = Color(0xFF26262C)
    val Grid = Color(0xFF2A2A2E)
    val GridStrong = Color(0xFF4A4A50)
    val Text = Color(0xFFF2F2F5)
    val Sub = Color(0xFF9A9AA2)
    val Dim = Color(0xFF8A8A93) // 5.4:1 on Card, 5.0:1 on CardTop: fine for small text
    val Crimson = Color(0xFFE0203F) // brand: needle, "now", selection. 3.9:1, so not for small text
    val CrimsonDeep = Color(0xFF8A1028)
    val Silver = Color(0xFFD8D8E0)
    val Rain = Color(0xFF4F8FD9)
    val RainDeep = Color(0xFF2A5A9A)
    val Humid = Color(0xFF788CAA)
    val Good = Color(0xFF3DB89A)
    val Warn = Color(0xFFF2DC5D)

    // --- brand roles added in v0.2 (new names only; nothing above changed) ---
    /** Crimson for text and thin glyphs: 5.9:1 on Card. Use where Crimson would be small text. */
    val CrimsonText = Color(0xFFFF5468)
    /** Specular top of crimson glass. Gradients only, never a flat fill. */
    val CrimsonHi = Color(0xFFFF4A63)
    /** Pressed/active states and the selected-chip fill. */
    val CrimsonPressed = Color(0xFFC8102E)
    /** Deepest crimson: icon backgrounds, vignettes. */
    val Oxblood = Color(0xFF4A0610)
    /** Severity step 5 (very unhealthy / extreme) as a fill: 4.6:1. */
    val Purple = Color(0xFFA55DE0)
    /** Step-5 purple for text: 6.3:1 on Card. */
    val PurpleText = Color(0xFFB77CEB)
    /** Chrome bezel stops with the hard "horizon" break at 50%. One chrome element per screen. */
    val Chrome = listOf(
        Color(0xFFFFFFFF), Color(0xFFC9C9D1), Color(0xFF7A7A84),
        Color(0xFF4A4A52), Color(0xFF9A9AA4), Color(0xFFE6E6EE),
    )
}

/** IBM Plex Sans (SIL OFL 1.1), static weights subset to Latin + symbols. */
val Plex = FontFamily(
    Font(R.font.plex_sans_thin, FontWeight.Thin),
    Font(R.font.plex_sans_light, FontWeight.Light),
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
)

/** IBM Plex Mono Regular (SIL OFL 1.1). */
val PlexMono = FontFamily(Font(R.font.plex_mono_regular, FontWeight.Normal))

/** Tabular figures, so columns of numbers don't jitter. Plex digits are already fixed-width; this keeps it explicit. */
private const val TNUM = "tnum"

private fun TextStyle.plex() = copy(fontFamily = Plex, fontFeatureSettings = TNUM)

private val typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.plex(), displayMedium = t.displayMedium.plex(), displaySmall = t.displaySmall.plex(),
        headlineLarge = t.headlineLarge.plex(), headlineMedium = t.headlineMedium.plex(), headlineSmall = t.headlineSmall.plex(),
        titleLarge = t.titleLarge.plex(), titleMedium = t.titleMedium.plex(), titleSmall = t.titleSmall.plex(),
        bodyLarge = t.bodyLarge.plex(), bodyMedium = t.bodyMedium.plex(), bodySmall = t.bodySmall.plex(),
        labelLarge = t.labelLarge.plex(), labelMedium = t.labelMedium.plex(), labelSmall = t.labelSmall.plex(),
    )
}

fun band(argb: Long) = Color(argb)

private val scheme = darkColorScheme(
    primary = Pal.Crimson, onPrimary = Pal.Text, background = Pal.Bg, onBackground = Pal.Text,
    surface = Pal.Card, onSurface = Pal.Text, surfaceVariant = Pal.CardTop, onSurfaceVariant = Pal.Sub,
    outline = Pal.Edge,
)

@Composable
fun WeatherglassTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)

/** House type scale, all IBM Plex with tabular figures. Nothing below 11 sp. */
object Type {
    val Huge = TextStyle(fontSize = 92.sp, fontWeight = FontWeight.Thin, color = Pal.Text, letterSpacing = (-4).sp, fontFamily = Plex, fontFeatureSettings = TNUM)
    val Big = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Light, color = Pal.Text, fontFamily = Plex, fontFeatureSettings = TNUM)
    val Title = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pal.Sub, letterSpacing = 1.6.sp, fontFamily = Plex, fontFeatureSettings = TNUM)
    val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = Pal.Text, fontFamily = Plex, fontFeatureSettings = TNUM)
    val Small = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Pal.Sub, fontFamily = Plex, fontFeatureSettings = TNUM)
    val Tiny = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = Pal.Dim, fontFamily = Plex, fontFeatureSettings = TNUM, letterSpacing = 0.2.sp)
    val Mono = TextStyle(fontSize = 13.sp, fontFamily = PlexMono, color = Pal.Text, fontFeatureSettings = TNUM)
}

/**
 * The house card: dark glass with a glossy top highlight and a hairline edge.
 * `title` is drawn small-caps style; `trailing` sits at the right of the title row.
 */
@Composable
fun GlassCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Pal.CardTop, Pal.Card, Pal.Card)))
            // glass sheen: a soft white wash over the top third (static gradient, no blur)
            .background(Brush.verticalGradient(0f to Color(0x0DFFFFFF), 0.35f to Color(0x00FFFFFF)))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x40FFFFFF), Pal.Edge, Color(0x14FFFFFF))), shape)
            .padding(16.dp),
    ) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(Locale.ROOT), style = Type.Title, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
        }
        content()
    }
}

/** Small coloured pill, e.g. an AQI category. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = Type.Small.copy(color = Pal.Text, fontWeight = FontWeight.Medium),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))

/** Formatting in the PLACE's time zone (its UTC offset), not the phone's. */
object Fmt {
    private fun f(p: String) = DateTimeFormatter.ofPattern(p, Locale.ENGLISH)
    private val hm = f("HH:mm")
    private val h = f("HH")
    private val dow = f("EEE")
    private val dm = f("d MMM")
    private val full = f("EEEE d MMMM")
    private fun z(ms: Long, off: Int) = Instant.ofEpochMilli(ms).atOffset(ZoneOffset.ofTotalSeconds(off))
    fun time(ms: Long, off: Int): String = hm.format(z(ms, off))
    fun hour(ms: Long, off: Int): String = h.format(z(ms, off))
    fun weekday(ms: Long, off: Int): String = dow.format(z(ms, off))
    fun dayMonth(ms: Long, off: Int): String = dm.format(z(ms, off))
    fun fullDate(ms: Long, off: Int): String = full.format(z(ms, off))
    fun localHour(ms: Long, off: Int): Int = z(ms, off).hour
    fun temp(v: Double?): String = if (v == null || v.isNaN()) "—" else "%.0f°".format(v).replace("-", "−")
    fun num(v: Double?, digits: Int = 0): String = if (v == null || v.isNaN()) "—" else "%.${digits}f".format(v)
    fun duration(hours: Double): String { val m = (hours * 60).toInt(); return "${m / 60}h ${"%02d".format(m % 60)}m" }
}
