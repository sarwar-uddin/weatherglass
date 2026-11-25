package com.github.sarwaruddin.weatherglass.ui

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.sarwaruddin.weatherglass.data.Bundle
import com.github.sarwaruddin.weatherglass.data.Forecast
import com.github.sarwaruddin.weatherglass.data.Meteo
import com.github.sarwaruddin.weatherglass.data.SearchHit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TABS = listOf("Now", "Week", "Air", "Sky", "Past")
private const val NOW = 0
private const val WEEK = 1
private const val AIR = 2
private const val SKY = 3
private const val PAST = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    s: UiState,
    onRefresh: () -> Unit,
    onSelect: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onAdd: (SearchHit) -> Unit,
    onRemove: (Int) -> Unit,
    onLocate: () -> Unit,
) {
    var placesOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { TABS.size }
    // Hoisted so each page keeps its scroll position while the pager disposes it offscreen.
    val nowList = rememberLazyListState()
    val weekList = rememberLazyListState()
    val airList = rememberLazyListState()
    val skyList = rememberLazyListState()
    val pastList = rememberLazyListState()
    val lists = remember(nowList, weekList, airList, skyList, pastList) { listOf(nowList, weekList, airList, skyList, pastList) }

    // ---- geometry: the header floats over the pages; the Now hero sits under it ----
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var headerPx by remember { mutableIntStateOf(0) }
    var heroPx by remember { mutableIntStateOf(0) }
    val headerH = if (headerPx > 0) with(density) { headerPx.toDp() } else statusTop + 108.dp
    val slackPx = with(density) { 24.dp.toPx() }

    /** The hero's sky has slid fully under the header. */
    val collapsed by remember {
        derivedStateOf {
            nowList.firstVisibleItemIndex > 0 ||
                (heroPx > 0 && nowList.firstVisibleItemScrollOffset >= heroPx - headerPx - slackPx)
        }
    }
    /** The big temperature has scrolled away, so the header shows the compact one. */
    val heroTempGone by remember {
        derivedStateOf {
            nowList.firstVisibleItemIndex > 0 ||
                (heroPx > 0 && nowList.firstVisibleItemScrollOffset > (heroPx - headerPx) * 0.55f)
        }
    }
    val onNow = pager.currentPage == NOW
    val glass = animateFloatAsState(if (!onNow || collapsed) 1f else 0f, tween(220), label = "glass")

    // Battery: the sky only animates while it can be seen, and never under "remove animations".
    val motionOn = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f) != 0f
    }
    val heroActive = motionOn && pager.settledPage == NOW && !collapsed

    // Pull-to-refresh shows its indicator only for a refresh the user asked for; background
    // refreshes (launch, resume) use the small spinner in the header.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(s.refreshing) { if (!s.refreshing) pulled = false }
    val pullRefresh = { pulled = true; onRefresh() }

    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000L); value = System.currentTimeMillis() }
    }

    // Cold start with no cache: the place is known, so the sky and skeletons can render at once.
    val b: Bundle? = s.bundle ?: s.places.getOrNull(s.selected)?.let { Bundle(it) }
    val f = b?.forecast
    val loading = s.refreshing || (s.error == null && f == null)

    fun goTo(page: Int) {
        scope.launch {
            if (pager.currentPage == page) lists[page].animateScrollToItem(0) else pager.animateScrollToPage(page)
        }
    }

    val ctx = PageCtx(
        s = s, b = b, f = f, loading = loading, now = now,
        retry = pullRefresh, locate = onLocate, pick = { placesOpen = true },
    )

    Box(Modifier.fillMaxSize().background(Pal.Bg)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            key = { it },
        ) { page ->
            PageFrame(
                list = lists[page],
                refreshing = pulled,
                onRefresh = pullRefresh,
                indicatorTop = headerH,
                contentTop = if (page == NOW) 0.dp else headerH + 12.dp,
            ) {
                when (page) {
                    NOW -> nowPage(
                        ctx,
                        hero = {
                            if (b != null) Hero(
                                b,
                                Modifier.onSizeChanged { heroPx = it.height },
                                active = heroActive,
                                topPad = headerH,
                                offline = s.errKind == ErrKind.OFFLINE,
                                onAqi = { goTo(AIR) },
                                scrollPx = {
                                    if (nowList.firstVisibleItemIndex == 0) nowList.firstVisibleItemScrollOffset.toFloat() else 0f
                                },
                            )
                        },
                    )
                    WEEK -> weekPage(ctx)
                    AIR -> airPage(ctx)
                    SKY -> skyPage(ctx)
                    else -> pastPage(ctx)
                }
            }
        }

        TopArea(
            b = b,
            refreshing = s.refreshing && !pulled,
            now = now,
            offline = s.errKind == ErrKind.OFFLINE,
            glass = glass,
            showCompact = !onNow || heroTempGone,
            pager = pager,
            onPlaces = { placesOpen = true },
            onShare = { if (b?.forecast != null) ShareCard.share(context, b) },
            onAbout = { aboutOpen = true },
            onCompact = {
                scope.launch {
                    pager.animateScrollToPage(NOW)
                    nowList.animateScrollToItem(0)
                }
            },
            onTab = ::goTo,
            modifier = Modifier.onSizeChanged { headerPx = it.height },
        )

        if (placesOpen) PlacesSheet(
            s = s,
            onDismiss = { placesOpen = false },
            onSelect = { onSelect(it); placesOpen = false },
            onSearch = onSearch,
            onAdd = { onAdd(it); placesOpen = false },
            onRemove = onRemove,
            onLocate = { onLocate(); placesOpen = false },
        )
        if (aboutOpen) AboutSheet(onDismiss = { aboutOpen = false })
    }
}

// =================================================================================
// Pages
// =================================================================================

/** Everything the page builders need, so the LazyListScope extensions stay small. */
private class PageCtx(
    val s: UiState,
    val b: Bundle?,
    val f: Forecast?,
    val loading: Boolean,
    val now: Long,
    val retry: () -> Unit,
    val locate: () -> Unit,
    val pick: () -> Unit,
)

private val pad = Modifier.padding(horizontal = 12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageFrame(
    list: LazyListState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    indicatorTop: Dp,
    contentTop: Dp,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = indicatorTop),
                containerColor = Pal.CardTop,
                color = Pal.Crimson,
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(top = contentTop, bottom = navBottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private fun LazyListScope.banner(c: PageCtx) {
    val err = c.s.error ?: return
    item(key = "err", contentType = "err") {
        when (c.s.errKind) {
            ErrKind.LOCATION -> ErrorBanner(err, pad, "Try again", c.locate, "Pick a city", c.pick)
            ErrKind.OFFLINE -> {
                val f = c.f
                val msg = if (f != null) {
                    "Can't reach Open-Meteo. Showing data from ${Fmt.time(f.fetched, phoneOffsetSec(f.fetched))}."
                } else err
                ErrorBanner(msg, pad, "Retry", c.retry)
            }
            else -> ErrorBanner(err, pad, "Retry", c.retry)
        }
    }
}

private fun LazyListScope.footer() {
    item(key = "foot", contentType = "foot") { Attribution() }
}

private const val PULL = "Pull down to try again."

private fun LazyListScope.nowPage(c: PageCtx, hero: @Composable () -> Unit) {
    item(key = "hero", contentType = "hero") { hero() }
    banner(c)
    val f = c.f
    if (f == null) {
        if (c.loading) {
            item(key = "sk-hourly") { SkeletonCard("Hourly", 104.dp, modifier = pad) }
            item(key = "sk-meteo") { SkeletonCard("48 hours", 210.dp, modifier = pad, rows = 4) }
            item(key = "sk-details") { SkeletonCard("Details", 160.dp, modifier = pad) }
        } else {
            item(key = "empty") { EmptyCard("Forecast", "No forecast saved for this place yet. $PULL", pad) }
        }
    } else {
        if (nowcastBrief(f, c.now).wet) item(key = "nowcast") { NowcastCard(f, pad) }
        item(key = "hourly") { HourlyCard(f, pad) }
        item(key = "meteo") { MeteogramCard(f, pad) }
        item(key = "details") { DetailsGrid(f, pad) }
        item(key = "heat") { HeatPlanCard(f, pad) }
    }
    footer()
}

private fun LazyListScope.weekPage(c: PageCtx) {
    banner(c)
    val f = c.f
    when {
        f != null -> item(key = "daily") { DailyCard(f, pad) }
        c.loading -> item(key = "sk-daily") { SkeletonCard("16 days", 380.dp, modifier = pad, rows = 7) }
        else -> item(key = "e-daily") { EmptyCard("16 days", "No forecast for this place yet. $PULL", pad) }
    }
    val m = c.b?.models
    when {
        m != null -> item(key = "models") { ModelsCard(m, f?.offsetSec ?: 0, pad) }
        c.loading -> item(key = "sk-models") { SkeletonCard("Model consensus", 220.dp, modifier = pad, rows = 5) }
        else -> item(key = "e-models") {
            EmptyCard("Model consensus", "The model comparison didn't load for this place.", pad, "Retry", c.retry)
        }
    }
    footer()
}

private fun LazyListScope.airPage(c: PageCtx) {
    banner(c)
    val a = c.b?.air
    when {
        a != null -> item(key = "air") { AirCard(a, c.f?.offsetSec ?: 0, pad) }
        c.loading -> {
            item(key = "sk-air") { SkeletonCard("Air quality", 200.dp, modifier = pad, rows = 3) }
            item(key = "sk-pm") { SkeletonCard("PM2.5", 140.dp, modifier = pad, rows = 2) }
        }
        else -> item(key = "e-air") {
            EmptyCard("Air quality", "No air-quality data for this place right now.", pad, "Retry", c.retry)
        }
    }
    footer()
}

private fun LazyListScope.skyPage(c: PageCtx) {
    banner(c)
    val f = c.f
    val b = c.b
    when {
        f != null && b != null -> item(key = "sun") { SunMoonCard(f, b.place, pad) }
        c.loading -> item(key = "sk-sun") { SkeletonCard("Sun & Moon", 260.dp, modifier = pad, rows = 4) }
        else -> item(key = "e-sun") { EmptyCard("Sun & Moon", "Sun and moon times need the forecast. $PULL", pad) }
    }
    footer()
}

private fun LazyListScope.pastPage(c: PageCtx) {
    banner(c)
    val f = c.f
    val a = c.b?.archive
    when {
        f != null && a != null -> item(key = "hist") { HistoryCard(a, f, pad) }
        c.loading -> item(key = "wait-hist") {
            WaitingCard(
                "On this date",
                "Reading every year since 1940 from the ERA5 archive for this place. The first load takes a moment.",
                modifier = pad,
            )
        }
        else -> item(key = "e-hist") {
            EmptyCard("On this date", "The climate archive didn't load.", pad, "Retry", c.retry)
        }
    }
    footer()
}

// =================================================================================
// Sticky top area: place pill, compact "now", share, about; then the tabs
// =================================================================================

private val topScrim = Brush.verticalGradient(listOf(Color(0x8C000000), Color(0x33000000), Color.Transparent))
private val glassBrush = Brush.verticalGradient(listOf(Color(0xFA1C1C21), Color(0xF5101013), Color(0xF0070708)))
private val hairline = Color(0x22FFFFFF)

@Composable
private fun TopArea(
    b: Bundle?,
    refreshing: Boolean,
    now: Long,
    offline: Boolean,
    glass: State<Float>,
    showCompact: Boolean,
    pager: PagerState,
    onPlaces: () -> Unit,
    onShare: () -> Unit,
    onAbout: () -> Unit,
    onCompact: () -> Unit,
    onTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val g = glass.value
                drawRect(topScrim)
                if (g > 0.01f) {
                    drawRect(glassBrush, alpha = g)
                    drawLine(hairline, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f, alpha = g)
                }
            }
            .statusBarsPadding()
            .padding(start = 8.dp, end = 4.dp, bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            PlacePill(b, onPlaces, Modifier.widthIn(max = 150.dp))
            CompactNow(
                f = b?.forecast,
                aqi = b?.air?.now?.aqi,
                now = now,
                offline = offline,
                visible = showCompact,
                onClick = onCompact,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            if (refreshing) {
                CircularProgressIndicator(
                    Modifier.size(16.dp).semantics { contentDescription = "Refreshing" },
                    color = Pal.Crimson, strokeWidth = 2.dp,
                )
            }
            GlyphButton(Glyph.SHARE, "Share this forecast", enabled = b?.forecast != null, onClick = onShare)
            GlyphButton(Glyph.MORE, "About Weatherglass", onClick = onAbout)
        }
        GlassTabs(TABS, pager, onTab, Modifier.padding(start = 4.dp, end = 8.dp))
    }
}

@Composable
private fun PlacePill(b: Bundle?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val name = b?.place?.name ?: "Weatherglass"
    val gps = b?.place?.gps == true
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(Color(0x66000000))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))), shape)
            .clickable(onClickLabel = "Change place", role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = (if (gps) "Current location, " else "") + "$name. Change place" }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (gps) Text("◉ ", style = Type.Small.copy(color = Pal.Crimson))
        Text(
            name, style = Type.Body.copy(fontWeight = FontWeight.Medium),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        Text(" ▾", style = Type.Small)
    }
}

/** Icon, live temperature, AQI dot and condition with data age; fades in as the hero leaves. */
@Composable
private fun CompactNow(
    f: Forecast?,
    aqi: Double?,
    now: Long,
    offline: Boolean,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = animateFloatAsState(if (visible && f != null) 1f else 0f, tween(200), label = "compact")
    Box(modifier.graphicsLayer { this.alpha = alpha.value }) {
        if (f == null) return@Box
        val c = f.current
        val band = aqiBand(aqi)
        val cond = Meteo.wmo(c.code).text
        val stale = now - f.fetched > STALE_MS
        val sem = Modifier.semantics(mergeDescendants = true) {
            contentDescription = buildString {
                append("Now ${Fmt.temp(c.temp)}, $cond")
                if (band != null && aqi != null) append(", air quality ${aqi.roundToInt()} ${band.name}")
                append(". Updated ${ageText(now - f.fetched)}")
                if (offline) append(", offline")
                append(". Show the full forecast")
            }
        }
        Row(
            (if (visible) Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).then(sem)
            else Modifier.clearAndSetSemantics { })
                .heightIn(min = 48.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherIcon(c.code, c.isDay, 26.dp)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.temp(c.temp), style = Type.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                    if (band != null && aqi != null) {
                        Spacer(Modifier.width(7.dp))
                        val bc = band(band.color)
                        Spacer(Modifier.size(7.dp).drawBehind { drawCircle(bc) })
                        Text(" ${aqi.roundToInt()}", style = Type.Small.copy(color = Pal.Silver), maxLines = 1)
                    }
                }
                Text(
                    "$cond · " + Fmt.time(f.fetched, phoneOffsetSec(f.fetched)) + if (offline) " · offline" else "",
                    style = Type.Tiny.copy(color = if (stale) StaleAmber else Pal.Sub),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private enum class Glyph { SHARE, MORE }

/** 48 dp icon button with a hand-drawn glyph (no icon font in the app). */
@Composable
private fun GlyphButton(glyph: Glyph, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) Pal.Silver else Pal.GridStrong
        Spacer(
            Modifier.size(22.dp).drawWithCache {
                val w = size.width
                val h = size.height
                val sw = 1.7f.dp.toPx()
                val dot = 2.2f.dp.toPx()
                val node = 3.1f.dp.toPx()
                val a = Offset(w * 0.76f, h * 0.2f)
                val m = Offset(w * 0.26f, h * 0.5f)
                val z = Offset(w * 0.76f, h * 0.8f)
                onDrawBehind {
                    when (glyph) {
                        Glyph.SHARE -> {
                            drawLine(tint, a, m, sw, StrokeCap.Round)
                            drawLine(tint, m, z, sw, StrokeCap.Round)
                            drawCircle(tint, node, a)
                            drawCircle(tint, node, m)
                            drawCircle(tint, node, z)
                        }
                        Glyph.MORE -> {
                            drawCircle(tint, dot, Offset(w / 2, h * 0.18f))
                            drawCircle(tint, dot, Offset(w / 2, h * 0.5f))
                            drawCircle(tint, dot, Offset(w / 2, h * 0.82f))
                        }
                    }
                }
            },
        )
    }
}
