# Weatherglass: brand identity

Version 0.3 of the identity. The app was called Barometer in v0.1 and Aneroid in v0.2-dev; it is now **Weatherglass**. The rename came with a new icon, because the Aneroid mark (chrome bezel, black dial, crimson needle, silver set-hand) read as a pressure gauge or a speedometer rather than as weather. Files in this folder:

| File | What it is |
|---|---|
| `icon-weatherglass-{bg,fg,mono}.xml` | Launcher icon layers, installed as `res/drawable/ic_bg`, `ic_fg`, `ic_mono` |
| `icon-weatherglass.svg` | Launcher icon preview, clipped to the visible 72 dp circle |
| `icon-weatherglass-dial.svg` | Large dial with the engraved weather words (store art, About screen, promo) |
| `wordmark.svg`, `wordmark.png` | Wordmark lockup with tagline, outlined to paths; 2× PNG render for the README |
| `previews/` | PNG renders at 432, 192 and 48 px, themed-icon (mono) previews, and the large dial |
| `archive/` | SVGs of the retired v0.2 concepts (Aneroid, Isobar, Barograph) and the old wordmark, for reference |
| `fonts/OFL.txt` | SIL Open Font License 1.1 for the bundled IBM Plex fonts |
| `tools/` | Generators: `icons.py`, `wordmark.py`, `vd.py` (shared geometry), `previews.sh` |
| `STORE.md` | Play Store / F-Droid listing copy |

---

## 0. The name

**Weatherglass.** "Weatherglass" (also written *weather-glass*) is the old household word for a barometer: the round dial that hung in the hall, read Stormy, Rain, Change, Fair and Very Dry across its face, and got a tap on the glass on the way out of the door to see which way the hand moved. The same word was also used for other glass forecasting curiosities, such as the storm glass.

A bit of history the brand can lean on:
- In the 1860s **Admiral Robert FitzRoy**, then head of Britain's new Meteorological Department (the forerunner of the Met Office), pushed for public barometers in harbours and fishing villages so that crews could check the glass before putting to sea. These are usually called *fishery barometers* (or FitzRoy barometers), and some still stand in British ports. Keep any claim about them this general unless you check a source.
- The aneroid mechanism still lives inside. Most household weatherglasses from the later 1800s on are aneroids: a sealed metal capsule, partly emptied of air, flexes as the pressure changes, and levers turn that into a sweep of the hand. The word *aneroid* is Greek for "without liquid", as against the mercury column. The French maker **Lucien Vidi** (also spelled Vidie) is credited with the first practical aneroid, patented in the mid-1840s. In the app, the pressure card is still an **aneroid dial**, which is correct: that is the mechanism, and Weatherglass is what people called the thing on the wall.

Why it fits:
- It says *weather*. Barometer and Aneroid both name a pressure instrument. Weatherglass names an instrument whose job was to tell you the weather, which is what the app does.
- The dial words (Rain, Change, Fair) give the icon a vocabulary that reads as weather at a glance, while keeping the glass-and-chrome instrument look.
- It follows the house naming rule: an old instrument's name, not a coined word and not a pun.
- It is distinct in app stores where "Barometer" and "Weather" are generic words hundreds of apps share. (Check the Play listing for clashes before release.)

Say it *WEH-ther-glass*. Write it "Weatherglass" in running text (one word, one capital), "WEATHERGLASS" only in the wordmark. The wordmark puts the dial from the icon in front of the word.

## 1. Positioning

**One-liner.** A pocket weather instrument: honest forecasts, the air you breathe, and how today compares with the record, on a black glass dial.

**Who it's for.**
- People in Bangladesh and South Asia who need more than a sun icon: wet-bulb heat stress, monsoon rain timing, PM2.5 against WHO and national limits, thunderstorm energy before kalbaishakhi season, a pressure trend before a cyclone.
- Weather nerds anywhere who want to see the models disagree, read dew point instead of "feels like", and know where a number comes from.
- People on modest phones. The reference device is a realme C30 (Unisoc T612, 720p). If it runs well there, it runs well anywhere.

**What it is NOT.**
- Not a lifestyle app. No mascots, no "Perfect day for a picnic!", no cartoon clouds. (The cloud and sun on the icon are engraved dial pictograms in chrome and glass, the way a weatherglass marks its scale, not characters.)
- Not an ad or data business. No accounts, no trackers, no analytics SDKs, no "premium" tier holding back the useful parts.
- Not a warning service. It shows what forecast models say. For official cyclone and flood warnings people should follow the national met service (BMD, IMD, and so on), and the app should say so wherever it shows severe weather.
- Not hacker-styled. Black and crimson is the palette of a precision instrument at night, not a terminal. No skulls, masks, glitch effects, green-on-black or "fsociety" references, ever.
- Not generic flat Material. Material 3 supplies the structure (components, accessibility, motion APIs). The look comes from instrument making and early-2000s glass.

## 2. Personality and voice

The brand is a **well-made instrument with a quiet expert behind it.** It is precise without showing off, plain where plain helps, and only says how sure it is when it actually knows.

Voice rules:
1. **Lead with the number, then what it means.** "17.7° wet-bulb. Low heat stress."
2. **Use physical units and real terms.** Dew point, wet-bulb, CAPE, hPa, µg/m³. Explain a term once, briefly, next to the value. Don't hide the term.
3. **Advise only when the data supports it.** Short imperatives are fine: "Cut long outdoor exertion."
4. **Give the source and the uncertainty.** "ERA5 cell average, not a station." "Models agree through Friday."
5. **Use as few words as possible, without dropping articles to sound like a telegram.**
6. **Write for everyone.** "Hard labour outdoors gets risky" respects the rickshaw puller and the office worker equally.
7. **No exclamation marks and no emoji** in the product.

| Do | Don't |
|---|---|
| No rain for the next 3 hours. | Looks like a dry one! ☀️ |
| Models agree through Friday; spread widens to 3° by Saturday. | Forecast confidence: Medium |
| 135 US AQI. Unhealthy for sensitive groups. PM2.5 6.4× WHO 24-h. | Air quality is not great today, take care! |
| Wet-bulb 29°. Very high: limit exertion outdoors, watch for heat exhaustion. | Feels like 41°! Stay cool! |
| Pressure falling 3.2 hPa / 3 h. | Weather is changing |
| 41st coolest of 87 years. | Cooler than usual today |
| Can't reach Open-Meteo. Showing data from 14:20. | Oops! Something went wrong. |
| Location off. Pick a place, or allow approximate location. | We need your location to give you the best experience. |

Bangla copy follows the same rules. Use plain standard Bangla with the technical term in parentheses on first use, e.g. আর্দ্র-বাল্ব তাপমাত্রা (wet-bulb).

## 3. Tagline

Options considered:
1. **Read the air.**
2. Pressure, honestly.
3. Weather, to instrument grade.
4. Know what the sky is doing.
5. The weather, with its working shown.

**Chosen: "Read the air."** It's three words. "Read" is what you do with an instrument, and "the air" covers pressure, heat, humidity and pollution in one phrase, which fits the South Asia AQI and heat focus. It doesn't promise accuracy that no forecast has. Lockup: under the wordmark, set in spaced caps (see `wordmark.svg`).

## 4. Colour system

### 4.1 Core palette

The current `Pal` in `Theme.kt` is already right. This section names the colours and fixes their roles. Two additions: **Crimson Pressed** (the desktop crimson) and **Chrome** gradient stops.

| Token | Hex | Role |
|---|---|---|
| Ink | `#070708` | App background. Not pure black, so OLED smear stays down and glass edges still read. |
| Card | `#131316` | Card body. |
| Card Top | `#1C1C21` | Top of the card gradient (glass catching light). |
| Edge | `#26262C` | Hairline card border, dividers. |
| Grid | `#2A2A2E` / Grid Strong `#4A4A50` | Chart gridlines, minor / major. |
| Text | `#F2F2F5` | Primary text (16.6:1 on Card). |
| Sub | `#9A9AA2` | Secondary text, card titles (6.6:1). |
| Dim | `#8A8A93` | Tertiary text, footnotes (5.4:1 on Card, 5.0:1 on Card Top). Lifted from `#6A6A72` (3.5:1) in v0.2 so it passes AA at small sizes. |
| **Crimson** | `#E0203F` | The brand colour: the needle, the "now" marker, selection, primary action. Use it sparingly. One crimson focal point per card is the rule. |
| Crimson Text | `#FF5468` | Text-only crimson (5.9:1 on Card). Use it wherever crimson would be small text: a warning word, a record value. Code: `Pal.CrimsonText`. |
| Crimson Hi | `#FF4A63` | Specular top of crimson glass. Gradients only, never as a flat colour. |
| Crimson Pressed | `#C8102E` | Pressed and active states, the selected-chip fill. |
| Crimson Deep | `#8A1028` | Needle shadow side, area-chart fills under the crimson line. Not for text. |
| Oxblood | `#4A0610` | Deepest crimson, for icon backgrounds and vignettes. |
| Silver | `#D8D8E0` | Chrome, secondary data line (pressure), moon. |
| Chrome stops | `#FFFFFF → #C9C9D1 → #7A7A84 \| #4A4A52 → #9A9AA4 → #E6E6EE` | The bezel gradient: top light, a hard "horizon" break at 50%, then a bounce light. This break is what makes metal look like metal. Use it on icon bezels, the wordmark and at most one UI element per screen. |

Code names (`Theme.kt`): `Pal.Bg`, `Card`, `CardTop`, `Edge`, `Grid`, `GridStrong`, `Text`, `Sub`, `Dim`, `Crimson`, `CrimsonText`, `CrimsonHi`, `CrimsonPressed`, `CrimsonDeep`, `Oxblood`, `Silver`, `Chrome` (the six stops), `Purple` / `PurpleText` (severity step 5 as fill / as text, 4.6:1 / 6.3:1).

Light theme: not planned. The identity is built for night and outdoor glare on black. If one is ever added, start from a silvered dial face, as on period weatherglasses (see §7).

### 4.2 Data colours

I checked every set against the card colour (WCAG contrast) and under simulated deuteranopia, protanopia and tritanopia (Machado 2009 matrices, CIELAB ΔE76). The target was ΔE ≥ 20 between adjacent steps under every simulation. In practice ΔE below about 12 means two colours look the same.

**Severity scale.** One scale for AQI, UV, heat stress, humidity bands and CAPE. The app already reuses a single 5-step scale everywhere, which is good, so keep that.

| Step | Current | Proposed | Contrast on Card | Why |
|---|---|---|---|---|
| 1 Good / Low | `#4FAF7C` | `#3DB89A` | 7.5 | A bluer teal-green. Under red-green colour blindness it turns grey-blue instead of olive, so it no longer collides with orange and red. |
| 2 Moderate | `#D4B13A` | `#F2DC5D` | 13.4 | Lighter, so it separates from step 3 by lightness and not only by hue. |
| 3 USG / High | `#E07B2A` | `#EA7A1E` | 6.4 | |
| 4 Unhealthy / Very high | `#E0203F` | `#E0203F` | 3.9 | Same as the brand crimson, on purpose: crimson means "act". Always paired with a label. |
| 5 Very unhealthy / Extreme | `#A04AB8` | `#A55DE0` | 4.6 | For a step-5 *word* in small text use `#B77CEB` (6.3:1). |
| 6 Hazardous (AQI > 300, new) | none | `#A8326E` | 3.0 | The EPA maroon, lifted so it is visible on black. Give it a 1 dp `#E6A0BE` outline. |

The worst adjacent pair in the current scale is Moderate/USG, which comes out at ΔE **10** under deuteranopia, so those two read as one colour. With the proposed scale the worst adjacent pair is ΔE **22** (also Moderate/USG under deuteranopia). Colour is never the only signal: every band also has its word ("Unhealthy"), a position on its arc or bar, and a number.

**Model colours** (Model Consensus card):

| Model | Current | Proposed |
|---|---|---|
| ECMWF | `#E0203F` | `#E0203F` (flagship gets the brand colour) |
| GFS | `#4F8FD9` | `#3F8FE8` |
| ICON | `#D4B13A` | `#F2DC5D` |
| GEM | `#4FAF7C` | `#3DB89A` |
| JMA | `#A04AB8` | `#CDB8FF` (light lavender) |

Currently GFS and JMA are ΔE 11 apart under deuteranopia: blue and purple merge. With the proposal the worst pair is GFS/GEM at ΔE 16 under tritanopia, the rarest type. Also add the model initial next to each dot when a row is tapped.

**Temperature ramp** (range bars, map-style fills). It's diverging around a neutral "comfortable" band and tuned for a tropical climate, so the hot end has room:

| °C | ≤ 5 | 12 | 20 | 26 | 32 | 38 | ≥ 42 |
|---|---|---|---|---|---|---|---|
| Hex | `#6E7FE0` | `#5FA8E0` | `#A8C8D0` | `#E8E4D8` | `#F0A040` | `#E0203F` | `#B0308A` |

Lightness peaks at the neutral middle, and the two ends differ in hue (blue against crimson or magenta), which survives all three colour-blindness simulations (minimum adjacent ΔE 16). For ranges, interpolate in OKLab, not sRGB.

**Rain** (sequential, brighter = more, because the background is black):

| mm/h | trace < 0.5 | light < 2.5 | moderate < 10 | heavy ≥ 10 |
|---|---|---|---|---|
| Hex | `#24466E` | `#2F6CB0` | `#4F98E8` | `#9AD0FF` |

The trace step is only 1.9:1 on purpose, as a whisper. Probability-of-rain text keeps the current `#4F8FD9`.

**Meteogram lines.** Temperature crimson `#E0203F`. Dew point: the current dashed crimson-deep (`#8A1028`, 1.9:1) is too faint and too close to the temperature line. Use **teal `#3DB89A`, dashed**, which separates by hue and by dash. Pressure silver `#D8D8E0`. RH `#788CAA` area. Rain `#4F8FD9` bars.

## 5. Typography

All the fonts below are free under the **SIL Open Font License 1.1**, which allows bundling in an app (including commercial apps) as long as the font itself isn't sold alone. The licence text is in `docs/branding/fonts/OFL.txt`; list the fonts and the licence in the About screen.

| Use | Font | Why |
|---|---|---|
| UI and data | **IBM Plex Sans**: Thin (100) for the hero temperature, Light, Regular, Medium, SemiBold | Designed for engineering contexts, with an honest grotesque feel that sits between Roboto and an instrument label. It has true **tabular figures** (`fontFeatureSettings = "tnum"`) so columns of temperatures don't jitter, a proper `−` sign and the µ, ², ³ glyphs this app needs. |
| Dense tables (Model Consensus, pollutant rows) | **IBM Plex Sans Condensed** Regular/Medium | Fits more on 720 px without shrinking the type. |
| Code-like values (coordinates, debug) | **IBM Plex Mono** Regular | Same family, same rhythm. Replaces `FontFamily.Monospace`. |
| Bangla | **Noto Sans Bengali** (OFL) | Good conjunct shaping. Its x-height is close to Plex Sans, so mixed lines look even. |
| Wordmark and display only | **Marcellus** (OFL, Astigmatic) | A flared Roman, like letters engraved on a brass dial plate. **Never use it for UI text or numbers.** |

**Shipped in v0.2** (`app/src/main/res/font/`, from the official IBM/plex GitHub releases `@ibm/plex-sans@1.3.6` and `@ibm/plex-mono@2.5.0`): `plex_sans_thin`, `plex_sans_light`, `plex_sans_regular`, `plex_sans_medium`, `plex_sans_semibold`, `plex_mono_regular`, plus `plex_sans.xml` for View/XML use. That is one file per weight the type scale uses, nothing more. Each is subset with `pyftsubset` to Basic Latin, Latin-1, Latin Extended-A, Greek µ/Δ/Ω, punctuation, super/subscripts, arrows, math (− ≈ ≥) and geometric shapes, all layout features kept, hinting dropped: about 61 KB per Sans weight, 43 KB Mono, 352 KB in all (the full TTFs are about 210 KB each). Glyphs outside the subset (✕, ◉, ▾, ⇑) fall back to the system font per character. Condensed, Bengali and Marcellus are not bundled yet.

In Compose: `Plex` and `PlexMono` (`FontFamily`) in `Theme.kt`; every `Type.*` style and the Material typography use them, with `fontFeatureSettings = "tnum"`. Plex's default figures are already tabular (all digits 600 units), so `tnum` changes nothing today but keeps columns steady if the family changes. Plex has no `case` feature. Smallest default style is `Type.Tiny`, 11 sp; nothing in the scale goes below 10 sp.

Regenerating the subset (from the unzipped release):
```
pyftsubset IBMPlexSans-Regular.ttf --unicodes='U+0020-007E,U+00A0-017F,U+0394,U+03A9,U+03BC,U+2000-206F,U+2070-209F,U+20AC,U+2100-215F,U+2190-21FF,U+2212,U+2248,U+2260-2265,U+25A0-25FF' \
  --layout-features='*' --name-IDs='*' --drop-tables+=DSIG --no-hinting --output-file=plex_sans_regular.ttf
```
Don't ship the variable font. Static instances render faster on the T612. Card titles stay as now: 13 sp SemiBold, +1.6 sp tracking, uppercase. That's the "engraved scale label" voice of the UI, and it matches the wordmark's spacing.

## 6. Iconography

There are two tiers, and they don't mix.

**Tier 1: illustrative** (launcher icon, weather-condition icons, moon, hero sky). These are glossy, in the Crystal/Oxygen style:
- One light source, top-left at about 45°. Every highlight and shadow agrees with it.
- Each object gets at most one body gradient, one specular highlight (a soft-edged "smile" or ellipse at the top), and an optional contact shadow underneath.
- No hard outlines. Edges come from the gradient and the background. A chrome rim is the only exception.
- Weather icons in the app keep their colours (sun amber, cloud silver, rain blue). Crimson is reserved for needles, warnings and "now".
- They must read at 24 dp. Check that by rendering at 24 px and squinting.

**Tier 2: functional** (navigation, chevrons, close, add, info, location). These are flat:
- 1.75 dp stroke, round caps, 24 dp grid, Sub colour (`#9A9AA2`). Text colour when active, crimson only when selected.
- Material Symbols Rounded (Apache 2.0) at weight 300 is an acceptable source. Don't invent glossy versions of utility glyphs.

**Instrument motifs** that may appear in UI: the weatherglass dial (the brand mark: About, splash, empty states), the aneroid dial (gauges, the pressure card), tick-mark scales on arcs and bars, the crimson needle with its loop counterweight, and the silver set-hand. The dial words (Stormy, Rain, Change, Fair, Very Dry) belong to the brand dial only; never print them on a live pressure gauge as if they were a forecast. Don't add gears or brass steampunk, and don't put dials on things that aren't measurements.

## 7. The app icon

An adaptive-icon vector drawable: a 108 dp viewport, foreground art kept inside the 66 dp safe circle (checked by rasterising: no foreground pixels beyond r = 32.7 dp), gradients via `aapt:attr` (fine at `minSdk 26`), and a monochrome layer for Android 13+ themed icons. The SVG preview is clipped to the visible 72 dp circle.

### Weatherglass (shipping)
A household-barometer face behind a domed glass, in a chrome bezel with the hard-horizon chrome break, on the black background layer with its faint crimson floor glow. An engraved scale runs 250° round the rim (open at the bottom). On the face sit two pictograms: a **chrome rain cloud** with three silver-blue streaks on the low (left) side and a **warm sun**, an ember-to-crimson glass disc with eight rays, on the high (right) side. The **crimson hand** with its loop counterweight points up and right, past Change towards Fair.

- **Why.** The v0.2 Aneroid mark was a well-made instrument that read as a gauge or speedometer. The cloud and sun carry the meaning at launcher size, where dial words would be mush. The chrome, ink face, glass dome and single crimson hand keep it a premium instrument rather than a cartoon weather icon. At 48 px the read is "silver ring, black face, cloud left, sun right, red hand between". The sun is warm orange-to-crimson so it reads as a sun first; the hand stays the one pure crimson stroke.
- **What was left out of the launcher version.** The dial words and the silver set-hand. Both are on the large dial (`icon-weatherglass-dial.svg`, `weatherglass(words=True)` in the generator), where they read. On the launcher they turned into noise and the set-hand competed with the glass highlight.
- **Large dial.** Words engraved in Marcellus round the scale: STORMY, RAIN, CHANGE, FAIR, VERY DRY. Smaller pictograms, and the silver set-hand back towards Rain, showing "it was wet, now it is clearing". Use it for store art, the About screen and promo graphics.
- **Mono.** Bezel ring, six scale ticks, the cloud with rain, the sun with rays, the hand with its loop, and the hub, all solid white (alpha only), so the system tint draws it. It still reads as cloud, sun and hand at 48 px.

Wiring: `mipmap-anydpi/ic_launcher.xml` points `<background>`, `<foreground>` and `<monochrome>` at `@drawable/ic_bg`, `ic_fg` and `ic_mono`. To change the icon, edit `tools/icons.py`, run `python tools/icons.py .` (or `sh tools/previews.sh`, which also renders `previews/`) from this folder, and copy `icon-weatherglass-{bg,fg,mono}.xml` over `res/drawable/ic_{bg,fg,mono}.xml`. Edit the script, not the XML, so the vector drawables and SVG previews stay in sync. The retired v0.2 concepts (Aneroid dial, Isobar orb, Barograph drum) are in git history; their SVGs are in `archive/`.

## 8. Motion

The target is a realme C30: Unisoc T612 with a Mali-G57 MP1 GPU, a 60 Hz 720 × 1600 screen and 3–4 GB RAM. Motion should **feel like a mechanism**: it settles, has a little mass, and stops.

1. **Animate only transform and alpha.** Use `graphicsLayer { rotationZ / translationY / alpha }`. Never animate layout size, blur, shadows or gradient stops per frame.
2. **No blur, anywhere.** `RenderEffect` blur needs API 31+ and is expensive on a single-core Mali. Glass is faked with gradients and highlights, as in the icons.
3. **Needles overshoot once and settle.** `spring(dampingRatio = 0.6f, stiffness = 200f)`, about 450 ms. That covers the AQI dial, wind arrow and pressure dial: one swing, no wobbling.
4. **Data enters once.** Charts draw their line left to right in 300 ms with `FastOutSlowIn`, on first show only. Not on every scroll back, and not on refresh.
5. **One ambient animation per screen**, at most. The hero sky (star twinkle, moon glow) runs at 30 fps or slower, stops when the hero scrolls off screen or the app goes to the background, and uses `drawWithCache` for gradients.
6. **Refresh is a needle sweep.** The pull-to-refresh indicator is the crimson hand rotating, not a Material spinner.
7. **Respect the system setting.** If animator duration scale is 0 ("Remove animations"), jump straight to the end state.
8. **Budget:** every frame under 16 ms on the C30 with GPU profiling bars on. If a new effect costs a dropped frame on scroll, cut it.

## 9. Brand inside the app

- **Splash:** Android 12+ `SplashScreen` with the weatherglass foreground on Ink, no text, and the needle doing one 300 ms settle as the exit animation.
- **Pressure card:** draw the reading as a small aneroid dial. The silver set-hand marks the value 3 hours ago and the crimson hand shows now, so the gap between them is the tendency. That's exactly what a set-hand was invented for, and it turns the brand mark into a working gauge.
- **Footer** (attribution line): add "Weatherglass · Read the air." in Dim above the data credits.
- **Crimson discipline:** if a card already has a crimson severity pill, its "now" marker turns Silver.
