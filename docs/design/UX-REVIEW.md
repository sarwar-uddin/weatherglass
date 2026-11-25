# Barometer v0.1 — UX review and v0.2 design proposal

Reviewed 2026-11-24 against `docs/screens/v0.1/01–10.png` (realme C30, 360 × 800 dp) and
`app/src/main/java/com/github/sarwaruddin/barometer/ui/*`. Paths below are relative to that `ui/`
folder unless they say otherwise.

**Short version.** The data is better than Weather Underground's: model consensus, ERA5
climatology, wet-bulb, CAPE, PM2.5 against WHO and BD limits. The meteogram and the history
chart already look like an expert's instrument. The weak point is the structure around them. It
is one ~5,000 dp scroll (about 6.5 phone screens) with no way to jump around. The hero takes 52%
of the first screen to show stars. Small text fails contrast. Not a single chart says anything to
TalkBack. v0.2 should mostly re-house what exists rather than add more data.

---

## 1. Heuristic review

### 1.1 Information hierarchy

**First 2 seconds:** what it's like now, whether it will rain soon, whether it's safe to go out
(heat or air), and today's high/low. **First 10 seconds:** the next 12–24 h shape, tonight, and
tomorrow. **Deep dive:** meteogram scrubbing, model spread, pollutants, CAPE, climatology, sky.

What v0.1 shows above the fold (01.png): place (twice), a clock that copies the status bar
(22:18 appears twice in 40 dp), the moon, 20°, "Clear", feels/H/L, a summary chip, then a whole
card saying "No rain for the next 3 hours", and the top half of Hourly.

Problems:
- **The AQI is the most urgent fact on this screen** ("unhealthy for sensitive groups", PM2.5
  6.4× WHO), yet it is a clause in a 14 sp chip and the number 135 sits four screens down
  (04.png). In Dhaka in November, AQI is a first-2-seconds fact, just like temperature.
- **Hero is 420 dp** (`SkyHero.kt:108`), and roughly 200 dp of it is empty sky between the place
  and the temperature. It's beautiful the first time. By the twentieth launch it is a scroll tax.
- **The nowcast card when dry** (`Nowcast.kt:71–83`) uses ~90 dp of glass on a negative. "No rain
  for 3 h" belongs in one line of the hero.
- **Order after Daily is arbitrary.** Model consensus (a nerd card) comes *before* Air and the
  everyday details (wind, humidity, UV). The deep stuff is between the user and the useful stuff.
- **Daily is 16 rows, all expanded** (~700 dp, 02–03.png). Days 8–16 are low-skill forecasts
  shown at the same weight as tomorrow. The model card already says confidence drops by Saturday,
  and the daily list should show that too.

### 1.2 Scroll length and structure

Current order (`WeatherScreen.kt:69–88`): Hero → Nowcast → Hourly → 48 h → 16 days → Models →
Air → 8 detail tiles → Sun & Moon → On this date → Footer. That is about 6.5 screens on a C30.

Options considered:

| Option | Verdict |
|---|---|
| Keep one scroll, add collapsible cards | Cheapest, but collapse state becomes a chore, and users still scroll past what they've collapsed. Not enough alone. |
| User-reorderable cards | Good for v0.3. It's a power feature, and drag-reorder in a LazyColumn is fiddly to get right. Ship "hide card" first. |
| Bottom navigation, 5 destinations | Too heavy, and it eats 56–80 dp on a 800 dp screen. Wrong for an app with one main view. |
| **Pager with a segmented tab strip: Now · Week · Air · Sky · Past** | **Recommended.** Each page fits in 1.5–2 screens. The swipe matches the live-sky metaphor, and pages are lazily composed (cheap). The tab strip doubles as a compact header when the hero collapses. |

The **Now** page stays a scroll and contains everything a normal person needs. The other four
pages are where the experts go, one swipe away and never in the way.

### 1.3 Readability at 360 dp and contrast (measured)

WCAG 2.x contrast of `Pal` (`Theme.kt:33–51`) on the card body `Card #131316` / card top
`CardTop #1C1C21`. AA needs 4.5:1 for small text and 3:1 for large text (≥ 18.66 sp bold or
24 sp) and for meaningful graphics.

| Token | on Card | on CardTop | Used for | Verdict |
|---|---|---|---|---|
| Text `#F2F2F5` | 16.6 | 15.2 | values | pass |
| Silver `#D8D8E0` | 13.1 | 12.0 | secondary values | pass |
| Sub `#9A9AA2` | 6.6 | 6.1 | Small 12 sp | pass |
| **Dim `#6A6A72`** | **3.5** | **3.2** | **`Type.Tiny` default, 10 sp**: units, dates in Daily, card trailing text, footnotes, footer | **FAIL**, at the smallest size, which is the worst combination |
| **Crimson `#E0203F`** | **3.9** | **3.6** | spread ≥ 3° (12 sp, `ModelsCard.kt:203`), record high, pressure-falling text | **FAIL** for small text |
| Purple `#A04AB8` | 3.7 | 3.4 | "Very unhealthy"/"Extreme" text, JMA | FAIL for small text |
| Rain `#4F8FD9` | 5.5 | 5.1 | PoP % | pass |
| Good / Warn / Orange | 6.9 / 9.0 / 6.2 | 6.3 / 8.2 / 5.7 | bands | pass |
| GridStrong `#4A4A50` | 2.1 | 1.9 | range-bar track, dashed nowcast line | FAIL as a meaningful graphic (3:1) |

Type sizes: `Type.Tiny` is 10 sp. Chart labels are dropped further to **9 sp** (`AirCard.kt:254`,
`HistoryCard.kt:129`, `SunMoonCard.kt:108`), and wind-compass cardinals to **8 sp**
(`DetailsGrid.kt:90`). On a C30 panel (~270 ppi, LCD, often dimmed outdoors), 8–9 sp Dim text
is decoration, not information.

**Fixes (token-level, one file):**
- `Dim` → `#8A8A93` (≈ 5.0:1 on Card). Keep the old value as `Hairline` for non-text use only.
- `Type.Tiny` → 11 sp. Set a floor of 10 sp for anything inside a Canvas, and never use 8–9 sp.
- Add `CrimsonText = #FF4D63` (≈ 5.3:1) for crimson *text*. Keep `Crimson #E0203F` for strokes
  and fills, where 3:1 is enough.
- Add `PurpleText = #C77DDB` the same way.
- `GridStrong` → `#5C5C64` where it carries meaning (range-bar track).

### 1.4 Touch targets

- Place pill (`WeatherScreen.kt:98–106`): about 34 dp tall. Use `minimumInteractiveComponentSize()`
  or vertical padding of 12 dp to reach 48 dp.
- Remove-place "×" in `PlacesSheet.kt:113`: 10 dp padding around a glyph is about 36 dp. Make it 48 dp.
- Daily rows: ~42 dp and tappable (`DailyCard.kt:78`) but **nothing shows they expand**. Add a
  chevron or a "tap for detail" first-run hint.
- Hourly cells (56 × ~110 dp) look tappable and aren't. Make them tappable (see §4).
- Meteogram: the whole 230 dp area is a scrub surface. Good. But a press starts the scrub at once
  (`Meteogram.kt:97`), so a slow vertical scroll flashes the readout. Show the scrub only after
  the touch-slop decision.

### 1.5 Consistency between cards

- **Header right slot means different things:** "drag to inspect" (a hint), "14° … 29°" (a
  range), "avg 71/100" (a score), "US AQI · CAMS" (a source), "sun −70°" (a value),
  "24 Nov · 1940–" (a span). Pick one rule. **Trailing slot = the card's key number or nothing.**
  Move sources and hints into the info sheet (§4).
- **Units are attached three ways:** "1018 hPa" (inline Tiny), "5 / km/h" (stacked under), and
  "17.7° wet-bulb" (inline label). Use one `ValueWithUnit` composable everywhere.
- **Band pills:** Humidity, Heat, Thunder and UV all use Pill + advice, but Visibility uses
  coloured *text* ("Slight haze"), and Air uses a large pill and a separate paragraph. Make
  Visibility a Pill too.
- **Footnotes:** Models, Air, History and Sun each have a grey paragraph of caveats at the bottom
  (03–07.png), ~60 dp each. Move them all behind an ⓘ in the header, into the explanation sheet.
- **Midnight in Hourly shows "Wed" in crimson** (01.png). Elsewhere crimson means *alert* or
  *now*. Use Silver bold for the day break and keep crimson for "Now".
- **Tile heights in the grid** follow the taller tile (Humidity is left with 80 dp of nothing,
  05.png). Either give each tile a fixed body, or make Humidity carry a 24 h RH sparkline like
  Pressure has.

### 1.6 Empty, loading, error and offline states

| State | v0.1 behaviour | Problem | Fix |
|---|---|---|---|
| Cold start, no cache | `Spacer(420.dp)` (`WeatherScreen.kt:69`), black void, no shimmer (HeroShimmer only runs when `b != null && f == null`) | Looks broken on a slow 4G start | Show the sky plus HeroShimmer whenever `b == null`. The place name is known from `UiState.places`. |
| Forecast loading | Cards absent; they pop in one at a time, and the list jumps | Layout shift | Skeleton cards with fixed heights matching the real ones |
| Air/models/archive missing | The card is silently omitted (`WeatherScreen.kt:81–87`), except Models and Daily, which have "unavailable" text | Users can't tell "no data" from "not supported here" | Always render the card shell with a one-line reason and a retry |
| Error | Warn-coloured 12 sp text under the hero, no action | Easy to miss, can't retry | Inline banner card with "Retry" (48 dp) |
| Offline with cache | "Offline: showing saved data." | Doesn't say how old the data is | **"Updated 3 h ago · offline"** in the compact header, always visible, turning amber after 6 h. `Forecast.fetched` already exists. |
| Location fails | "Couldn't get a location fix." | Dead end | Banner with "Try again" and "Pick a city" |

### 1.7 Accessibility

- **TalkBack:** there are zero `semantics` or `contentDescription` calls in `ui/`. Every Canvas
  (meteogram, PM2.5 chart, AQI gauge, compass, sun arc, history scatter, model dots, range bars,
  weather icons) is silent. Minimum per card: `Modifier.semantics(mergeDescendants = true)` with
  a sentence such as *"48 hours: high 27° Wednesday 2 pm, low 19° Thursday 5 am, no rain,
  pressure 1015 to 1019."* For the meteogram, add `customActions` for "next hour / previous hour"
  so the scrub is usable without sight. Weather icons: `contentDescription = wmo.text`.
- **Font scale 1.3×:** fixed widths break. Daily day column `width(52.dp)`, temps 32/34 dp
  (`DailyCard.kt:82,97,105`), Air rows 46/52 dp (`AirCard.kt:127–139`), Models columns 42/48/52 dp
  (`ModelsCard.kt:149–151`), Hourly cells 56 dp. "Today" at 15 × 1.3 sp will not fit in 52 dp.
  The 92 sp hero turns into 120 sp and pushes "Clear" off the 420 dp box.
  - Rule: **body text scales; chart text and the hero numeral are capped at 1.15×** (convert with
    `LocalDensity` to a dp-based size). Replace fixed widths with `widthIn(min=)` plus `weight`.
- **Colour-blind safety (simulated, Machado 2009, ΔE76 under 20 = confusable):**
  - Bands: **Warn `#D4B13A` vs Orange `#E07B2A`: ΔE 10 under deuteranopia**, 19 under
    protanopia. That is "Moderate" against "Unhealthy for sensitive groups" (AQI), and "Moderate"
    against "High" (UV, heat). Green/crimson stays separable because of lightness.
  - Model dots: **GFS blue vs JMA purple: ΔE 11 (deut)**.
  - Fix: every band already has a text label, so make sure the label is *always* shown next to
    the colour (the UV strip and the PM2.5 bars aren't labelled). Shift Orange towards
    `#F08A3C`/darker and Warn lighter/yellower (`#E6CC5A`) to split them by lightness. Give model
    dots **shapes** (● ■ ▲ ◆ ✚), which cost nothing on Canvas.
- **Reduce motion:** respect `Settings.Global.ANIMATOR_DURATION_SCALE == 0`. Freeze the sky
  (also saves battery).

### 1.8 First-run experience

Today the app opens on Dhaka (`Repo.kt:253 DEFAULT_PLACES`) with no prompt. GPS lives in the
places sheet behind a 34 dp pill, and `Fmt` (`Theme.kt:121–138`) hard-codes °C, km/h, hPa,
24 h and English.

Proposed first run, one glass sheet over the live sky, three rows, skippable:

```
┌──────────────────────────────────────┐
│  Welcome to Barometer                │
│                                      │
│  [◉ Use my location ]  [ Pick city ] │   ← coarse only; explain "city-level, never leaves phone"
│                                      │
│  Temperature   (°C) | °F             │   ← default from locale (US → °F)
│  Wind          (km/h)| m/s | kn | mph│
│  Pressure      (hPa)| inHg | mmHg    │
│  Clock         (24 h)| 12 h          │   ← default from DateFormat.is24HourFormat
│                                      │
│              [ Start ]               │
└──────────────────────────────────────┘
```

Defaults come from the locale, so most people just tap Start. Store them in DataStore and expose
`LocalUnits`. `Fmt.temp/num` becomes unit-aware. That is a one-file change for formatting, but
every hard-coded `"km/h"` and `"hPa"` string has to be replaced.

---

## 2. Per-card critique

Severity: **P0** = broken or misleading, **P1** = hurts most users, **P2** = polish with real
payoff, **P3** = nice to have.

### Hero (`SkyHero.kt`, 01.png)
- **P0:** Place drawn twice (pill and `SkyHero.kt:118–128` overlap). **Fix:** delete the hero's
  place label. The pill owns the place, and the region goes under it in Small.
- **P1:** The clock copies the status bar (`:130–135`). **Fix:** replace it with
  **"Updated 22:05"** (data age, which is actually useful), or the local time only when the place
  is in a different time zone from the phone.
- **P1:** 420 dp tall, half of it empty. **Fix:** 340 dp, temp block raised. Collapses on scroll
  (§3.3).
- **P1:** Summary chip `maxLines = 1` (`:182`). Longer summaries end in "…" at 360 dp. **Fix:**
  allow 2 lines, or split into two chips: weather and air.
- **P1:** AQI not visible. **Fix:** an AQI chip next to the summary — `● AQI 135 · USG` in the
  band colour with its text label — that taps through to the Air page.
- **P2:** H/L line in Sub over stars can drop below 4.5:1 on bright daytime skies. The scrim
  (`SkyScrim`) should darken the lower 40% a bit more in day scenes.
- **P3:** The 92 sp Thin numeral is gorgeous. Keep it, but cap its font scale.

### Next 3 hours (`Nowcast.kt`)
- **P1:** A full card for "no rain". **Fix:** when dry, fold it into the hero as a line
  ("Dry next 3 h") and hide the card. When wet, show the card *above* Hourly with the bar chart,
  and mark the start time in big text ("Rain from 14:30, 2.1 mm").
- **P2:** The dashed line (GridStrong, 2.1:1) reads as a loading placeholder.

### Hourly (`HourlyStrip.kt`)
- **P1:** Only 5 hours visible, and the content isn't tappable. **Fix:** 52 dp cells (6 visible)
  and tap → scroll the meteogram scrub to that hour.
- **P2:** Crimson "Wed" at midnight (see 1.5). Wind arrow 11 dp plus 10 sp Dim speed. Make the
  arrow 14 dp and use Sub.
- **P2:** PoP only shows at ≥10%. That's fine, but the row height jumps between days. Reserve
  the 16 dp row (it already does), and show "—" in Hairline for consistency.
- **P3:** Mini temperature curve behind the temps (one Path, cheap). It connects visually to the
  meteogram the owner loves.

### 48 hours meteogram (`Meteogram.kt`, 02.png)
- **P1:** Five series on one plot, and the pressure line (light grey, its own axis) visually
  competes with temperature. It is easy to read 1019 hPa as a temperature. **Fix:** split into
  stacked lanes sharing the x-axis, still one Canvas: temp + dew (140 dp), precipitation bars +
  PoP (40 dp), wind barbs/arrows (24 dp), pressure (36 dp). That is the classic meteogram
  (yr.no / Meteoblue style), so it's familiar to experts. Drop RH (it's derivable from temp and
  dew) or make it a toggle.
- **P1:** Dew point is a dashed dark crimson on a crimson fill. It's barely visible. **Fix:** dew
  in `Humid #788CAA`, solid 1.5 dp.
- **P1:** Scrub readout is 11 sp and pops instantly on touch-down. **Fix:** show it after the
  slop decision, add a light haptic tick per hour crossed (§4), and snap to the hour.
- **P2:** "drag to inspect" is a Tiny Dim hint. Show it on first run only, then make the trailing
  slot show the 48 h range.
- **P2:** Day boundary: shade alternate days, or mark night with the faint `isDay` band (cheap:
  one rect per night).
- **P2:** No TalkBack summary or actions (see 1.7).

### 16 days (`DailyCard.kt`, 02–03.png)
- **P1:** 16 equal-weight rows. **Fix:** show 7 rows, then a "Days 8–16 · low confidence" row
  that expands. Fade min/max text alpha by model confidence from `Models` (you already have it,
  so use it).
- **P1:** Tap-to-expand has no affordance. Add a 12 dp chevron, rotated when open.
- **P2:** The rain column is empty on 15 of 16 rows but reserves 58 dp, which squeezes the range
  bar. **Fix:** put PoP *under the icon* in 11 sp Rain and give the range bar the 58 dp.
- **P2:** Dates in Tiny Dim (fails contrast). Use 11 sp Sub.
- **P3:** Tap → open the Week page with that day's meteogram (§4).

### Model consensus (`ModelsCard.kt`, 03–04.png)
This is the differentiator against WU. It deserves to be *understood*, not just shown.
- **P1:** It's on the main scroll above Air. **Fix:** move it to the Week page under the daily list.
  On Now, keep only the headline sentence as a one-liner ("Models agree through Friday").
- **P1:** Blue/purple dots collide for colour-blind users. **Fix:** shapes per model (1.7).
- **P1:** Spread ≥ 3° in Crimson 12 sp is 3.6:1. Use CrimsonText.
- **P2:** The "0/5 wet" column is identical on every row in the dry season. **Fix:** hide it when
  all rows are 0/n. Otherwise show a 5-segment pip (●●○○○), which fits better than text.
- **P2:** CONF bar plus number repeats the spread information. Keep the number, and drop the bar
  or turn it into row background tint.
- **P3:** Legend dots are 7 dp. Use 9 dp with shapes.

### Air (`AirCard.kt`, 04–05.png)
- **P0:** Headline mismatch. The pollutant table says PM2.5 **6.4×** (now ÷ WHO 24 h), while the
  orange line under the gauge says **3.4× WHO** (24 h mean). Both are right, but side by side they
  look contradictory. **Fix:** label both explicitly. The table column becomes "now ÷ WHO 24 h"
  and the gauge line "24 h mean = 3.4× WHO". Better still, use the 24 h mean in both places and
  move "now" to the chart.
- **P1:** Header row "× WHO 24-h ┃ WHO ┃ BD" (`:130`) is cryptic. **Fix:** a legend under the bars
  — "│ WHO limit   │ Bangladesh limit" — with the tick shapes drawn in it.
- **P1:** Belongs on its own page in Dhaka. Give it the Air tab. On Now, show the AQI chip in the
  hero plus a compact Air row (§3).
- **P2:** PM2.5 bars are coloured by category with amber/orange adjacent (colour-blind). The
  BD 65 and WHO 15 lines carry the meaning, so colour by observed/forecast only (solid/faded) and
  let the threshold lines do the work. Or keep the colours and add band labels on the y-axis.
- **P2:** CO and Dust rows switch to a text layout mid-table. Use the same bar row with the
  "no guideline" bar greyed.
- **P3:** Add the "best window today" hint: the lowest-PM2.5 3 h window in the next 24 h
  ("Cleanest air: 14:00–17:00"). It's cheap to compute and very useful for runners and parents.

### Details grid (`DetailsGrid.kt`, 05–06.png)
- **P1:** Night shows dead tiles: UV 0.0, Thunder 0 J/kg / Stable, Clouds 0/0/0. **Fix:**
  context-aware tiles. At night UV shows "Tomorrow peak 5.6 at 11:45". When stable, Thunder
  becomes a single line in a "Quiet" tile. Sort tiles by *salience*, so anything with a band ≥
  Moderate rises to the top.
- **P1:** Compass labels 8 sp (`:90`). Use 10 sp minimum, or drop the N/E/S/W letters and keep
  only N.
- **P2:** Humidity tile has 80 dp of empty space. Add a 24 h RH/dew sparkline that matches
  Pressure.
- **P2:** Wind "from N 360°". Use "from N (0°)". Also, 360° reads as a bug to nerds.
- **P2:** Visibility caveat "Model value; often capped near 24 km" goes in the info sheet.
- **P3:** Pressure tile is the app's namesake. Give it a 48 h trend (past 24 h plus the next
  24 h dashed). That's the "barometer" moment.

### Sun & Moon (`SunMoonCard.kt`, 06–07.png)
- **P1:** At night the chart shows the sun at −70° under a flat line. That's a dead chart.
  **Fix:** after sunset, switch to the moon's altitude arc for tonight (moonrise/moonset, which
  are missing anyway and are the most-asked night fact).
- **P2:** 9 sp chart labels. Use 10 sp.
- **P2:** "Mean synodic month; phases good to about ±14 h" goes in the info sheet.
- **P3:** Blue hour next to golden hour (photographers).

### On this date (`HistoryCard.kt`, 07.png)
The best card. Keep it nearly as is.
- **P1:** "27°" is today's *forecast* high, but it reads as an observation. Label it
  "Today's forecast high 27°".
- **P2:** Record dots are red/blue only. Add ▲/▼ shapes for colour-blind users.
- **P2:** The ERA5 caveat paragraph goes in the info sheet.
- **P3:** Toggle hi/lo series, plus "this month" vs "this date".

### Top bar and places (`WeatherScreen.kt:94–109`, `PlacesSheet.kt`)
- **P1:** 34 dp pill. Make it 48 dp.
- **P1:** The refresh spinner is the only staleness signal. Add the "Updated …" text (1.6).
- **P2:** Swipe between saved places. With the pager proposal, horizontal swipe is taken, so use
  the pill and the sheet. Optionally swipe the *hero* only.

### Footer
- **P2:** 10 sp Dim (3.4:1). It's legally the attribution (CC BY), so it must be legible. Use
  11 sp Sub.

---

## 3. Proposed information architecture

### 3.1 Structure

```
App
├── Compact header (sticky): place ▾ · icon · temp · AQI dot · "upd 22:05"
├── Tab strip (sticky): Now | Week | Air | Sky | Past
└── HorizontalPager (beyondViewportPageCount = 0; pages composed lazily)
    ├── Now   — hero, alerts, nowcast(if wet), hourly, 48 h meteogram, today+2 days, salient tiles
    ├── Week  — 16-day list (7 + expander), selected-day meteogram, model consensus
    ├── Air   — gauge, pollutants, PM2.5 chart, cleanest window, guidance
    ├── Sky   — sun/moon arc, golden/blue hour, moon phase, clouds by layer, visibility, UV curve
    └── Past  — on this date, this month's anomaly, records, trend
```

Each page is LazyColumn-backed and 1.5–2.5 screens long. Settings (units, card visibility,
reduce motion) open from a ⋮ in the compact header.

### 3.2 Wireframes (360 dp wide; 1 char ≈ 9 dp)

**Now — at rest**
```
┌────────────────────────────────────────┐ ← status bar
│ (◉ Dhaka ▾)                 upd 22:05 ⋮│  48 dp pill, data age, overflow
│                                        │
│        ✦       ◯ moon        ✦         │  live sky, 340 dp total
│   ✦                     ✦              │
│                                        │
│  20°                                   │  92 sp thin
│  Clear · Feels 22°                     │
│  H 27°  L 17° tomorrow                 │
│  (● Dry next 3 h) (● AQI 135 USG ›)    │  two chips; AQI chip in band colour + label
├────────────────────────────────────────┤
│ [Now]  Week   Air   Sky   Past         │  tab strip, 40 dp, crimson underline
├────────────────────────────────────────┤
│ ┌ HOURLY ─────────────────────── 17–20°┐│
│ │ Now 23  Wed 01  02  03 …             ││  6 visible, mini temp curve behind
│ │  ☾   ☾   ☾   ☾   ☾   ☾               ││
│ │ 20° 20° 20° 19° 19° 18°              ││
│ └──────────────────────────────────────┘│
│ ┌ 48 HOURS ──────────────── 17°…27° ⓘ ┐│
│ │ ╭─╮      ╭─╮   temp/dew lane        ││
│ │ ┘ ╰──────┘ ╰── ───────────          ││
│ │ ▁▁ ▂▅▂ ▁   precip lane              ││
│ │ ↓ ↙ ← ↙ ↓  wind lane                ││
│ │ ~~~~~~~~   pressure lane 1012–1019  ││
│ └──────────────────────────────────────┘│
│ ┌ NEXT DAYS ─────────────────── more ›┐│  3 rows only → Week tab
│ │ Today ☀ 20° ━━━●━━━ 27°             ││
│ │ Wed   ☀ 17° ━━━━━━  26°             ││
│ │ Thu   ☀ 17° ━━━━━━  26°             ││
│ └──────────────────────────────────────┘│
│ ┌ WIND ──────┐ ┌ HEAT ──────┐          │  tiles sorted by salience;
│ │ 5 km/h N   │ │ 17.7° wb   │          │  quiet ones collapse into
│ └────────────┘ └────────────┘          │  one "Quiet: UV, storms" row
│ Quiet now: UV 0 (peak 5.6 tomorrow) ·  │
│ storms none · clouds clear             │
└────────────────────────────────────────┘
```

**Compact header — after the hero scrolls away (any page)**
```
┌────────────────────────────────────────┐
│ (◉ Dhaka ▾)  ☾ 20°  ● 135   upd 22:05 ⋮│  56 dp, glass: CardTop→Bg gradient + 1 dp top highlight
│ [Now]  Week   Air   Sky   Past         │  40 dp
└────────────────────────────────────────┘
```

**Week**
```
┌────────────────────────────────────────┐
│ ┌ 16 DAYS ───────────────── 14°…29° ⓘ┐│
│ │ Today ☀ 24%  20° ━━━●━━━━ 27°  ›   ││
│ │▌Wed   ☀      17° ━━━━━━━  26°  ⌄   ││ ← selected (crimson edge)
│ │  ┌ Wed meteogram, 24 h, 120 dp ──┐  ││   inline expand: that day's meteogram
│ │  └───────────────────────────────┘  ││   + rain mm, wind max, UV max, sunrise
│ │ Thu   ☀      17° ━━━━━━━  26°  ›   ││
│ │ … to Mon (7 rows)                   ││
│ │ Days 8–16 · lower confidence   ⌄   ││ ← text alpha follows model conf
│ └──────────────────────────────────────┘│
│ ┌ MODEL CONSENSUS ────────────── 71 ⓘ┐│
│ │ Models agree through Fri; spread    ││
│ │ widens to 3° by Sat.                ││
│ │ ● ECMWF ■ GFS ▲ ICON ◆ GEM ✚ JMA    ││ ← shapes
│ │ Today  ●▲■◆✚│      2.0°   75        ││
│ │ …                                   ││
│ └──────────────────────────────────────┘│
└────────────────────────────────────────┘
```

**Air**
```
┌────────────────────────────────────────┐
│ ┌ AIR NOW ──────────────────── 135 ⓘ┐│
│ │   ╭──gauge──╮   Unhealthy for      ││
│ │   │   135   │   sensitive groups   ││
│ │   ╰─────────╯   24 h PM2.5 52 µg   ││
│ │                 = 3.4× WHO         ││
│ │ Cleanest window today 14:00–17:00  ││ ← new
│ └──────────────────────────────────────┘│
│ ┌ POLLUTANTS (now ÷ WHO 24 h) ──────┐│
│ │ PM2.5  96  ███████████│░░░│  6.4× ││
│ │ PM10  101  ████│░░░│░░░░░░  2.2×  ││
│ │ …        │ WHO    ┃ Bangladesh     ││ ← real legend
│ └──────────────────────────────────────┘│
│ ┌ PM2.5, 5 DAYS ─────────────────────┐│
│ │ bars (observed solid, forecast 40%) ││
│ │ ─ ─ BD 65   ─ ─ WHO 15              ││
│ └──────────────────────────────────────┘│
│ ┌ WHAT TO DO ────────────────────────┐│  band advice, 1–3 lines
└────────────────────────────────────────┘
```

**Sky** is Sun & Moon (moon arc at night), clouds by layer as a 24 h stacked strip,
visibility, and the UV daily curve. **Past** is HistoryCard as it is, plus a month-anomaly strip
later.

### 3.3 Live-sky hero on scroll

Use a nested-scroll connection (the `TopAppBarScrollBehavior.exitUntilCollapsed` pattern), with
no `Modifier.blur` (too expensive on Helio G35):

1. **0–60% of the collapse:** sky `graphicsLayer { translationY = -offset * 0.5f }` (parallax),
   temperature numeral scales 92 → 28 sp *visually* via `graphicsLayer.scale` (no relayout),
   moving towards the header slot.
2. **60–100%:** sky alpha → 0 and the compact header's glass fades in. The tab strip pins.
3. **Collapsed:** **stop the sky animation clock** (`scene.animated && !collapsed`). That saves
   the 15–30 fps loop (`SkyHero.kt:96–106`) the whole time the user reads cards, which is most of
   the session. It's the single biggest battery and jank win on the C30.
4. Only the Now page has the full hero. Other pages start already collapsed, with the same compact
   header, so switching tabs never jumps.
5. A tap on the compact temp snaps back to Now and expands the hero.

---

## 4. Micro-interactions (all cheap)

| Interaction | Behaviour | Cost |
|---|---|---|
| **Scrub haptics** | `HapticFeedbackType.TextHandleMove` each time the meteogram scrub crosses an hour, and a stronger `LongPress` tick at midnight and at "now". It feels like a detented dial and fits the Oxygen-hardware vibe. | S. `LocalHapticFeedback` in `Meteogram.kt` pointer loop |
| **Tap hour → scrub** | Tap an Hourly cell: the meteogram scrolls into view and pins the scrub at that hour for 3 s | S |
| **Tap day → day meteogram** | Daily row expands an inline 24 h meteogram (reuse `buildGram` with a window) | M |
| **Long-press any tile or ⓘ → explanation sheet** | "What is wet-bulb?", with a 2-line definition, *your* thresholds (the `Band` tables in `Meteo.kt:112–159` are the content), the source, and the caveat paragraphs moved out of the cards | M. One `ExplainSheet(topic)` plus a string map |
| **Pull-to-refresh glint** | The crimson indicator gets a glossy highlight sweep. Show "Updated just now" for 2 s afterwards. | S |
| **AQI chip pulse** | One slow 1.5 s glow pulse on first show when AQI ≥ 151. Never loops. | S |
| **Tab change** | The crimson underline slides with the pager offset (`pagerState.currentPageOffsetFraction`), no extra animation | S |
| **Pressure falling alert** | When dp3 < −1.5 hPa, the pressure tile header gets a crimson edge, and the tile rises to the top of the grid | S |
| **Copy readout** | Long-press the meteogram readout to copy "Wed 14:00 · 26.8° · dew 17° · 1016 hPa" for sharing | S |
| **Hide card** | ⋮ on a card header → "Hide on Now page" (restore in settings). It's the gateway to reordering in v0.3. | M |

---

## 5. v0.2 priorities (≤ 12)

| # | Item | Why | Effort |
|---|---|---|---|
| 1 | **Contrast and type tokens**: Dim → `#8A8A93`, Tiny 11 sp, CrimsonText/PurpleText, no labels under 10 sp | Fails WCAG at 3.2–3.9:1 in ~40 places. One-file fix in `Theme.kt` plus a grep for 8/9 sp. | S |
| 2 | **Hero fixes**: remove duplicate place, replace the clock with "Updated hh:mm", 340 dp, AQI chip, 2-line summary | P0 bug and first-2-seconds hierarchy | S |
| 3 | **Fold the dry nowcast into the hero**; nowcast card only when wet | Saves ~100 dp of a negative | S |
| 4 | **Collapsing hero plus sticky compact header, and pause the sky clock when collapsed** | Structure, battery and jank on the C30 | M |
| 5 | **Tabs and pager: Now / Week / Air / Sky / Past**; move cards to pages | Cuts the 6.5-screen scroll to ~2 per page | M |
| 6 | **Meteogram lanes** (temp+dew / precip / wind / pressure), dew in Humid colour, scrub after slop, hour haptics | Makes the loved chart readable and more expert | M |
| 7 | **TalkBack**: semantics summaries on every card/Canvas, icon descriptions, meteogram custom actions | Zero accessibility today | M |
| 8 | **Font-scale safety**: drop fixed widths in Daily/Air/Models/Hourly, cap chart and hero text at 1.15× | 1.3× currently breaks layouts | S |
| 9 | **Colour-blind**: split Warn/Orange by lightness, model-dot shapes, record ▲/▼ | Moderate vs USG is indistinguishable for ~5% of men | S |
| 10 | **First-run sheet and units** (°C/°F, km/h·m/s·kn·mph, hPa·inHg·mmHg, 12/24 h; locale defaults; DataStore; `Fmt` unit-aware) | Table stakes against WU | M |
| 11 | **States**: shimmer on cold start, skeleton cards, retry banner, "Updated N h ago · offline" | Looks broken on slow networks today | S |
| 12 | **Explanation sheet** (long-press/ⓘ) and move all footnote paragraphs into it; context-aware night tiles (UV → tomorrow's peak, Sun → moon arc) | Cleaner cards and better teaching; it plays to the nerd strength | M |

Deferred to v0.3: reorderable/hideable cards, the Daily inline day-meteogram if #6 runs long, the
cleanest-air window, blue hour, the month-anomaly strip, and swipe-between-places on the hero.
