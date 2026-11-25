package com.github.sarwaruddin.weatherglass.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.github.sarwaruddin.weatherglass.MainActivity
import com.github.sarwaruddin.weatherglass.R

// ---- shared pieces --------------------------------------------------------------

private fun cp(argb: Int) = ColorProvider(Color(argb))

private fun style(size: TextUnit, color: Int = Ink.TEXT, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = cp(color), fontSize = size, fontWeight = weight)

/** Icon bitmaps are rendered at a capped density to stay well inside RemoteViews' bitmap budget. */
private fun iconBitmap(context: Context, m: WidgetModel, dp: Int): Bitmap {
    val d = context.resources.displayMetrics.density.coerceAtMost(2.5f)
    return Glyph.bitmap(m.code, m.isDay, (dp * d).toInt().coerceAtLeast(16))
}

@Composable
private fun Glass(content: @Composable () -> Unit) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(22.dp)
            .background(ImageProvider(R.drawable.widget_glass))
            .clickable(actionStartActivity<MainActivity>()),
    ) { content() }
}

@Composable
private fun AqiDot(m: WidgetModel, size: TextUnit, compact: Boolean = false) {
    val aqi = m.aqi ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            ImageProvider(R.drawable.widget_dot), contentDescription = null,
            modifier = GlanceModifier.size(8.dp),
            colorFilter = ColorFilter.tint(cp(m.aqiColor)),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(if (compact) "$aqi" else "AQI $aqi", style = style(size, Ink.SUB), maxLines = 1)
    }
}

@Composable
private fun Empty() {
    Glass {
        Column(GlanceModifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("WEATHERGLASS", style = style(11.sp, Ink.CRIMSON, FontWeight.Bold))
            Text("Open the app to load a forecast.", style = style(12.sp, Ink.SUB))
        }
    }
}

// ---- small: 2x1 / 2x2 ------------------------------------------------------------

class SmallWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 40.dp), DpSize(110.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val m = WidgetData.load(context)
        val icon = m?.let { iconBitmap(context, it, 44) }
        provideContent { if (m == null || icon == null) Empty() else Small(m, icon) }
    }
}

@Composable
private fun Small(m: WidgetModel, icon: Bitmap) {
    val tall = LocalSize.current.height >= 100.dp
    Glass {
        if (tall) {
            Column(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.place, style = style(12.sp, Ink.SUB, FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    AqiDot(m, 11.sp)
                }
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.temp, style = style(38.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Image(ImageProvider(icon), contentDescription = m.condition, modifier = GlanceModifier.size(44.dp))
                }
                Text(m.condition, style = style(13.sp), maxLines = 1)
                Row(GlanceModifier.fillMaxWidth()) {
                    Text(m.hiLo, style = style(11.sp, Ink.SUB), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Text(m.updated, style = style(10.sp, Ink.DIM), maxLines = 1)
                }
            }
        } else {
            Row(
                GlanceModifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(ImageProvider(icon), contentDescription = m.condition, modifier = GlanceModifier.size(32.dp))
                Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(m.temp, style = style(22.sp), maxLines = 1)
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.place, style = style(10.sp, Ink.SUB), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        AqiDot(m, 10.sp, compact = true)
                    }
                }
            }
        }
    }
}

// ---- wide: 4x2 -------------------------------------------------------------------

class WideWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val m = WidgetData.load(context)
        if (m == null) {
            provideContent { Empty() }
            return
        }
        val icon = iconBitmap(context, m, 44)
        val dayIcons = m.days.map { Glyph.bitmap(it.code, true, (20 * context.resources.displayMetrics.density.coerceAtMost(2.5f)).toInt()) }
        provideContent {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val scale = ctx.resources.displayMetrics.density.coerceAtMost(2.5f)
            val plotH = if (size.height >= 150.dp) 64.dp else 48.dp
            val plotW = (size.width - 28.dp).coerceAtLeast(80.dp)
            val plot = m.next12?.let { Plot.bitmap(it, (plotW.value * scale).toInt(), (plotH.value * scale).toInt(), scale) }
            Wide(m, icon, dayIcons, plot, plotH)
        }
    }
}

@Composable
private fun Wide(m: WidgetModel, icon: Bitmap, dayIcons: List<Bitmap>, plot: Bitmap?, plotH: androidx.compose.ui.unit.Dp) {
    Glass {
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.place, style = style(12.sp, Ink.SUB, FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                AqiDot(m, 11.sp)
                Spacer(GlanceModifier.width(8.dp))
                Text(m.updated, style = style(10.sp, Ink.DIM), maxLines = 1)
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(icon), contentDescription = m.condition, modifier = GlanceModifier.size(44.dp))
                Spacer(GlanceModifier.width(6.dp))
                Column {
                    Text(m.temp, style = style(32.sp), maxLines = 1)
                    Text(m.condition, style = style(11.sp, Ink.SUB), maxLines = 1)
                }
                Spacer(GlanceModifier.defaultWeight())
                for ((k, d) in m.days.withIndex()) {
                    Column(GlanceModifier.padding(start = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.name, style = style(10.sp, Ink.SUB), maxLines = 1)
                        dayIcons.getOrNull(k)?.let {
                            Image(ImageProvider(it), contentDescription = null, modifier = GlanceModifier.size(20.dp))
                        }
                        Text(d.hi, style = style(11.sp, Ink.TEXT, FontWeight.Medium), maxLines = 1)
                        Text(d.lo, style = style(10.sp, Ink.DIM), maxLines = 1)
                    }
                }
            }
            if (plot != null) {
                Image(
                    ImageProvider(plot), contentDescription = "Next 12 hours: temperature and rain",
                    modifier = GlanceModifier.fillMaxWidth().height(plotH),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }
}

// ---- receivers ---------------------------------------------------------------------

class SmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.onWidgetsAdded(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.schedule(context) // cancels the periodic work once no widget is left
    }
}

class WideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WideWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.onWidgetsAdded(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.schedule(context)
    }
}
