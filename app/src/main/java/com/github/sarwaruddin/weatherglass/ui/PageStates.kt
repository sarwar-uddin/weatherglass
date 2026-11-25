package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- loading -----------------------------------------------------------------------------

/** One pulse shared by every skeleton on a page; read in the draw phase so it never recomposes. */
@Composable
fun rememberSkeletonPulse(): State<Float> {
    val tr = rememberInfiniteTransition(label = "skeleton")
    return tr.animateFloat(0.05f, 0.13f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
}

/**
 * A card-shaped placeholder with the real card's title, so the page doesn't jump when data
 * lands. [height] is the body height of the card it stands in for (no text inside, so a fixed
 * height can't clip anything).
 */
@Composable
fun SkeletonCard(
    title: String,
    height: Dp,
    modifier: Modifier = Modifier,
    rows: Int = 3,
    pulse: State<Float> = rememberSkeletonPulse(),
) {
    GlassCard(title, modifier.semantics(mergeDescendants = true) { contentDescription = "$title, loading" }) {
        Spacer(
            Modifier.fillMaxWidth().height(height).drawWithCache {
                val r = CornerRadius(6.dp.toPx())
                val gap = 10.dp.toPx()
                val bar = ((size.height - gap * (rows - 1)) / rows).coerceAtLeast(4f)
                onDrawBehind {
                    val c = Color.White.copy(alpha = pulse.value)
                    for (k in 0 until rows) {
                        val w = if (k == rows - 1) size.width * 0.6f else size.width
                        drawRoundRect(c, Offset(0f, k * (bar + gap)), Size(w, bar), r)
                    }
                }
            },
        )
    }
}

// ---- empty / missing data ----------------------------------------------------------------

/** The card shell with a one-line reason and, if useful, a way to try again. */
@Composable
fun EmptyCard(title: String, message: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    GlassCard(title, modifier) {
        Text(message, style = Type.Body.copy(color = Pal.Silver, fontSize = 14.sp))
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            GlassButton(action, onAction)
        }
    }
}

/** A friendly "still working on it" card, with a slow needle sweep instead of a spinner. */
@Composable
fun WaitingCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    pulse: State<Float> = rememberSkeletonPulse(),
) {
    GlassCard(title, modifier.semantics(mergeDescendants = true) { contentDescription = "$title. $message" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier.size(28.dp).drawWithCache {
                    val sw = 2.dp.toPx()
                    val r = size.minDimension / 2 - sw
                    val c = Offset(size.width / 2, size.height / 2)
                    onDrawBehind {
                        drawCircle(Pal.Edge, r, c, style = Stroke(sw))
                        // The needle swings with the pulse: a barometer settling, not a spinner.
                        val a = Math.toRadians((-140.0 + (pulse.value - 0.05) / 0.08 * 100.0)).toFloat()
                        val tip = Offset(c.x + r * 0.8f * kotlin.math.cos(a), c.y + r * 0.8f * kotlin.math.sin(a))
                        drawLine(Pal.Crimson, c, tip, sw, StrokeCap.Round)
                        drawCircle(Pal.Silver, sw, c)
                    }
                },
            )
            Spacer(Modifier.width(12.dp))
            Text(message, style = Type.Body.copy(color = Pal.Silver, fontSize = 14.sp), modifier = Modifier.weight(1f))
        }
    }
}

// ---- errors ------------------------------------------------------------------------------

/** Inline banner with up to two actions (each a 48 dp target). */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    action2: String? = null,
    onAction2: () -> Unit = {},
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind { drawRect(Color(0x1FF0A040)) }
            .border(1.dp, StaleAmber.copy(alpha = 0.55f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(8.dp).drawBehind { drawCircle(StaleAmber) })
            Spacer(Modifier.width(10.dp))
            Text(message, style = Type.Body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
        }
        if (action != null || action2 != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                if (action2 != null) GlassButton(action2, onAction2, quiet = true)
                if (action != null) GlassButton(action, onAction)
            }
        }
    }
}

@Composable
fun GlassButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, quiet: Boolean = false) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Type.Body.copy(
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (quiet) Pal.Silver else Color(0xFFFF4D63),
            ),
        )
    }
}

// ---- footer ------------------------------------------------------------------------------

/** CC BY attribution: it must be legible, so 11 sp Sub, not Dim. */
@Composable
fun Attribution(modifier: Modifier = Modifier) {
    Text(
        "Weather data by Open-Meteo.com (CC BY 4.0): ECMWF, DWD, NOAA, ECCC, JMA models · " +
            "CAMS air quality · ERA5 reanalysis (Copernicus)",
        style = Type.Small.copy(fontSize = 11.sp), textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
