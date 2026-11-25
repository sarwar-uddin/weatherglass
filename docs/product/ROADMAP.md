# Barometer — Product Roadmap (post v0.1)

*Status: strategy draft, 2026-11-24. Covers v0.2–v0.4 and a longer backlog. Nothing here is committed code.*

---

## 0. Where v0.1 stands (grounding)

v0.1 is already a strong expert-grade weather view. It has a live-sky hero, a 3-hour nowcast, an hourly strip, a scrubbable 48-hour meteogram, a 16-day outlook, a five-model consensus with a confidence score, AQI against WHO and Bangladesh limits, a details grid (wet-bulb, CAPE, cloud layers, pressure tendency), sun and moon, and an ERA5 "on this date" card going back to 1940. The stack is very clean for distribution:

- **Dependencies:** Compose, AndroidX, kotlinx-coroutines and kotlinx-serialization only. There is no Google Play Services, Firebase or analytics.
- **Location:** `ACCESS_COARSE_LOCATION` plus the platform `LocationManager`, with no fused-location provider. There is no background location.
- **Network:** Five Open-Meteo endpoints (forecast ×2, air quality, archive, geocoding), all keyless.
- **Attribution:** The footer already credits Open-Meteo (CC BY 4.0), the model centres, CAMS and ERA5.

The gap is that v0.1 is **descriptive**: it shows what the atmosphere is doing very well. It does not yet answer **"what should I do, and when?"**, and it does not cover the hazards that actually kill people in Bangladesh: heat, lightning, nor'westers, floods, cyclones and fog. Those questions are the opportunity.

---

## 1. Positioning

**One sentence:**
> *Barometer is the free, private, open-source weather instrument for South Asia: it shows the atmosphere with the honesty of an expert (models, uncertainty, climate context) and turns it into plain-language decisions for the hazards Bangladesh actually faces, in Bangla, without ads or tracking.*

Three things make it distinct, and no competitor does all three:
1. **Expert transparency.** It shows model spread, confidence, ERA5 context and its own verification, where others show one number.
2. **South-Asian hazard literacy.** It covers wet-bulb and WBGT work-rest advice, kalbaishakhi and lightning risk, GloFAS river flow, fog for ferries, winter smog, and the BD cyclone signal system explained.
3. **Ethics as a feature.** It is FOSS, has no account, no ads and no trackers, works offline-first and is Bangla-first. Every competitor that is popular in BD is ad-funded or paywalled.

### Personas and jobs-to-be-done

| Persona | Context | Jobs-to-be-done |
|---|---|---|
| **Rumana, city commuter (Dhaka/Chattogram)** | Rides buses or rickshaws, has a cheap Android phone and patchy data, and has a parent with asthma. | *When I leave home,* I want to know if rain or waterlogging will hit my commute, if the air is safe for my parent today, and if it's dangerously hot, *so I can* plan the trip and protect my family. *In winter,* I want to know which days the smog is worst and when the fog will clear. |
| **Arif, weather/sky nerd (anywhere)** | Reads model output, follows cyclones on Zoom Earth, stargazes, and wants data and not fluff. | *When the forecast is uncertain,* I want to see the models disagree and know which one has been right lately here, *so I can* make my own call. *I want* sky-quality nights, and beautiful, citable charts I can share. |
| **Karim, farmer / fisher / outdoor worker (rural, delta, coast)** | Shared or low-end phone, reads Bangla, may have limited literacy. Rice farmer in Rangpur, hilsa fisher in Bhola, or a rickshaw puller. | *Before working outdoors,* I want to know the hours when heat or lightning is dangerous, *so I can* rest or shelter in time. *Before spraying,* I want a no-rain, low-wind window. *Before going to sea,* I want to know wave height and whether a signal is up, *so I don't* risk the boat or my life. |

Persona 3 is the most underserved and the most important. Design for it first: icons, colour, Bangla, a large type size and few words. Personas 1 and 2 get the benefit of that clarity for free.

---

## 2. Competitor teardown

| App | Does well | Does badly / user complaints | BD/South-Asia relevance | Lesson for Barometer |
|---|---|---|---|---|
| **Weather Underground** (TWC/IBM) | Personal weather station (PWS) network, hyperlocal station observations, good history pages. | Long-time users report ads creeping in "all over the place" and paywall creep for Premium. Navigation got worse after the IBM takeover. | Few PWS in BD, so its core advantage is absent. | PWS integration is valuable, but **local-first** (your own station), not a cloud network. |
| **Windy** | Best-in-class model maps (ECMWF/GFS/ICON), layers, a sounding view, a cyclone view. Loved by nerds and sailors. | Subscription friction: accidental subscriptions, double charges, Play/web subscription conflicts, ~25% price rise complaints, cross-promotion of other apps. Overwhelming for laypeople. Premium gates hourly ECMWF updates. | Widely used by BD weather enthusiasts during cyclones. | Don't try to out-map Windy. Win on **point-based decisions** and on **free**. Deep-link to Windy for maps. |
| **CARROT Weather** | Personality, design, deep customisation, a great widget editor, multiple data sources. | Reviewers say about 90% of features are paywalled. Notifications and widgets need a subscription. Accuracy doubts because it relies on a single provider (Foreca default). | Low; it's an iOS-first product with a pricey subscription. | Personality in *copy* is good (cf. "Clear night, AQI unhealthy for some"). Keep widgets and alerts free. |
| **Apple Weather** (iOS) | Dark Sky heritage, clean design, next-hour precipitation where available, averages and "how it compares", maps. | iOS only. Next-hour rain only in selected countries. Little uncertainty shown. | None on Android. A design benchmark only. | Its "averages / unusual" framing is a good bar. Barometer's ERA5 card already exceeds it. |
| **Google Weather** (Pixel / Search) | Zero install, fast, large reach, AI-generated summaries on Pixels. | A single opaque number with no models, uncertainty or sourcing. Minimal AQI detail. Limited hazards outside the US/EU. | Default for many BD users via Search. | Show the *why*: source, model and confidence. That earns trust a black box can't. |
| **Zoom Earth** | Near-real-time satellite (Himawari for South Asia), excellent cyclone tracking from JTWC/NHC/NRL/IBTrACS, fire hotspots, beautiful maps. | Map-only, so there is no "what do I do". Some features are behind a subscription. No point forecasts with uncertainty. | High during Bay of Bengal cyclones. | Cyclone tracks plus "distance and ETA to *my* place" is the missing piece. |
| **Meteoblue** | Strong meteograms, multi-model "MultiModel" views, an offline mode, Swiss rigour, astronomy "seeing" forecast. | Ads unless you pay. A recent 3-tab redesign was called a downgrade by long-time users. Some views need a subscription. | Its global models are fine for BD. No BD hazards. | Closest in spirit. Barometer's edge is free, FOSS and BD hazards. Borrow the seeing forecast idea. |
| **Breezy Weather** (FOSS) | LGPL-3.0 and on F-Droid. 50+ sources (Open-Meteo by default). Rich cards, widgets, live wallpaper, alternate calendars, alerts from national services where available. | A generalist built mostly for EU/NA sources. Configuration-heavy. No BD-specific hazards and no BMD alerts. | Usable in BD, but generic. | **The closest FOSS competitor.** Don't clone it. Differentiate on depth (verification, climate, WBGT) and BD hazards. Its "freenet" flavour shows how to handle non-free sources on F-Droid. |
| **BMD apps** (official; the older BMD Weather app and the newer "আবহাওয়া – Beta") | Authoritative: official bulletins, cyclone signals, warnings, Bangla. Station-based (dozens of AWS). | Store listings show very low install counts. Reviews and coverage describe dated UI and reliability issues. No uncertainty, no meteogram, no AQI detail. | The *official* voice. Its signals carry legal and operational meaning. | Never compete with or impersonate BMD. **Explain and link** its signals. Be the readable companion. |
| *(AccuWeather / TWC: incumbents)* | Brand, reach, "MinuteCast". | Heavy ads. Reports of unclosable video ads blocking radar (TWC, 2026). Privacy concerns. | Very common on BD phones. | "No ads, no tracking" is a real differentiator in BD, not just ideology. |

---

## 3. Feature ideas (40)

**Legend.**
- **Effort:** S is under a week of evenings, M is 1–3 weeks, L is a month or more.
- **Offline/battery:**
  - ○ means nothing extra.
  - ◐ means one extra cached fetch per refresh.
  - ● means periodic background work or heavy rendering.
- **Licence shorthand:**
  - **OM** is Open-Meteo data, CC BY 4.0. It is free for non-commercial use, which means no ads and no subscriptions. Underlying model licences also apply: ECMWF and DWD are CC BY 4.0; NOAA is public domain; JMA and ECCC each have their own open licence; CAMS/C3S data fall under the Copernicus licence.
  - **PD** is public domain.

### 3.1 Alerts

| # | Feature | User value | Uniqueness | Data + licence | Effort | Offline/battery |
|---|---|---|---|---|---|---|
| A1 | **Morning brief notification.** One local notification at a user-chosen time: "Hot 36°, wet-bulb peaks 29° at 14:00 — rest 13–16h; storms likely after 17h; AQI unhealthy." | One glance replaces opening the app. Works for persona 3 with icons and Bangla. | Most apps send generic "today's forecast" notifications or gate them behind a subscription (Carrot). | OM (already fetched) | S | ● 1 WorkManager job a day |
| A2 | **On-device threshold alerts.** The user sets thresholds such as rain within 1h, gust over 50 km/h, wet-bulb over 28°, PM2.5 over 75, or visibility under 1 km. Evaluated locally against the cached forecast. | Personal, relevant alerts with no server and no account. | Breezy has some. No one else offers wet-bulb or PM2.5 thresholds keyed to WHO and BD standards. | OM | M | ● periodic job every 1–3h |
| A3 | **Kalbaishakhi (nor'wester) risk.** A March–May index combining CAPE, CIN, lifted index, deep-layer shear (from pressure-level winds), the gust forecast and ensemble agreement. Shown as a 0–4 scale with "most likely window 15–20h". | Nor'westers kill via wind, hail, lightning and capsized boats, and the main risk window is predictable hours ahead. | None. Generic "thunderstorm" icons ignore the BD squall climatology. | OM forecast + pressure levels + ensemble API | M | ◐ |
| A4 | **Lightning safety (বজ্রপাত) mode.** A derived thunder probability from CAPE, convective precipitation and ensemble spread. Plus a "30/30 rule" coaching card, field-worker guidance (crouch, avoid lone trees and water) and an optional strike-proximity layer only if a licence is granted (see §3.2 and Won't do). | BD loses roughly 250–350 people a year to lightning, mostly farmers and fishers in open fields. Even a 1–3h heads-up plus education saves lives. | None in consumer apps for South Asia. | OM (derived). Blitzortung only with written permission. | M | ◐ |
| A5 | **Official warning mirror (link-out, not re-authored).** Headline plus a link to the latest BMD special bulletin, IMD RSMC tropical advisory, JTWC warning and GDACS alert for the area, timestamped and attributed. | One place to see that an official warning exists, without trusting Barometer's own interpretation. | Breezy does this for EU/US national services, but not for BD. | GDACS (EU CC BY 4.0, credit "GDACS"). JTWC (US gov, PD). IMD/BMD pages: link only, check terms. | M | ◐ |

### 3.2 Hazards

| # | Feature | User value | Uniqueness | Data + licence | Effort | Offline/battery |
|---|---|---|---|---|---|---|
| H1 | **Heat-work planner (WBGT).** Estimates shaded and sun WBGT hourly using the Liljegren/Bernard approximations from temperature, dew point, wind and shortwave radiation. Maps the result to ISO 7243 / ACGIH work-rest ratios for light, moderate and heavy work, then answers "Safest hours to work today", "Drink 1 cup every 20 min" and "Rest 45 of every 60 min, 13–15h". | The 2025 wearable study of Dhaka rickshaw pullers found about a third exposed to high-risk WBGT (over 31.1°C). This applies to rickshaw, construction, garment and farm work. | Unique. Apps show "feels like" (heat index), which under-reads risk in sun and humid heat. | OM: temperature, dew point, wind, `shortwave_radiation`, `direct_radiation` | M | ○ (computed) |
| H2 | **River flood panel.** GloFAS v4 discharge for the largest river within about 5 km (Padma, Jamuna, Meghna, Teesta, Surma…). Shows a 30-day ensemble fan against the location's own return-period thresholds, computed from the 1984–2022 reanalysis, with the label "2-yr / 5-yr / 20-yr flow". | Monsoon flood awareness days ahead, at river scale. | Unique among consumer apps. FFWC bulletins are PDF and station-based. | OM flood API (GloFAS, Copernicus EWDS licence: attribution required). Note 5 km resolution: river selection can be wrong, so allow nudging the point. | M | ◐ cache daily |
| H3 | **FFWC station levels.** Nearest FFWC gauge: water level against its danger level, with a trend arrow. | Official BD river truth, more trusted than a model. | Unique. | FFWC API exists (`api.ffwc.gov.bd`, BWDB). Access terms are unpublished, so **ask BWDB in writing first**. Fallback: link to the bulletin. | M (+ permission wait) | ◐ |
| H4 | **Cyclone tracker.** Active Bay of Bengal and Arabian Sea systems: track, cone, intensity, then distance, closest approach and ETA to each saved place. For coastal places, adds sea-level and wave context from the Open-Meteo marine API, labelled as model guidance and not surge warnings. Plus a **BD signal explainer** covering maritime port signals 1–11 (distant cautionary, local warning, danger 5–7, great danger 8–10, 11 communication failure) and river port signals 1–4, with the note that 5–7 and 8–10 differ in storm *position*, not intensity. | Cyclone season (Apr–May, Oct–Nov) is when people install weather apps. The signal numbers are widely misunderstood. | Zoom Earth has tracks but not "to my place" and not BD signals. BMD has signals but no tracks UI. | GDACS GeoJSON API (CC BY 4.0, credit GDACS). JTWC RSS/ATCF text (US gov, PD). IMD RSMC as official link. Signal text: paraphrase BMD's published definitions with a link. | L | ◐ (● during an active storm) |
| H5 | **Fog for ferries and flights.** A night/morning fog probability from visibility, dew-point depression under 1°C, wind under 3 m/s, low cloud, clear sky after sunset and the ensemble spread. Presets for major river crossings (Sadarghat–Barishal/Chandpur, Paturia–Daulatdia, Mawa–Janjira) and airports (DAC/CGP/ZYL). Answers "Fog likely 01–09h, clearing ~10h." | BIWTA suspends launch services nationwide on dense-fog nights, and ferry collisions in fog still kill people. Travellers need to know whether they'll be stuck. | Unique. | OM: `visibility`, `dew_point_2m`, cloud layers, ensemble | M | ○/◐ |
| H6 | **Earthquakes nearby.** USGS feed for felt-range quakes (M4+ within ~500 km). Shows distance, depth and a "Did you feel it?" link to USGS. | BD sits near active faults (Dauki, Arakan). Shakes cause rumours; data calms them. | Few weather apps include quakes. | USGS GeoJSON feeds: US gov, PD, credit "USGS" requested | S | ◐ (only when the app is open) |
| H7 | **Fires and smoke upwind.** FIRMS VIIRS hotspots within ~300 km, weighted by today's wind direction. Answers "Crop-burning fires upwind (NW) — smoke may worsen PM2.5 tomorrow." | Explains winter smog spikes (Indo-Gangetic burning, brick kilns). | Unique as a *causal* explanation. Zoom Earth shows fires but doesn't link them to your air. | NASA FIRMS: free MAP_KEY, 5,000 transactions/10 min. NASA data is generally open with acknowledgement. **A per-user key or a proxy is needed** (a key embedded in a public app is fragile). | M | ◐ |

### 3.3 Sky & astronomy

| # | Feature | User value | Uniqueness | Data + licence | Effort | Offline/battery |
|---|---|---|---|---|---|---|
| S1 | **Stargazing / sky-quality forecast.** Combines low, mid and high cloud, humidity, aerosol optical depth (CAMS) and the moon's altitude and phase into an hourly "observing" score, plus astronomical darkness windows. | Turns the existing sun/moon card into planning. Aerosol is the real limiter in BD. | Meteoblue has seeing. None of them factor in AOD. | OM forecast + OM air quality `aerosol_optical_depth` | M | ○ |
| S2 | **Events calendar with cloud odds.** Meteor showers, eclipses, conjunctions and supermoons, each with cloud-cover forecast or climatological clear-sky odds for the date. | "Will I see it?", answered. | Unique combination. | Own ephemeris (already in-house) + OM / ERA5 cloud climatology | S/M | ○ |
| S3 | **Photographer's sky.** Golden and blue hour (exists), plus rainbow chance (low sun, showers on the opposite side), sunset colour odds (high cloud over a clear western horizon) and haze. | Shareable delight. | Some niche apps do this. Rare in weather apps. | OM | S | ○ |
| S4 | **Bengali calendar sky notes.** Tithi and lunar month next to the Bangla date, plus Eid crescent visibility (Yallop/Odeh criteria) per evening. | High cultural relevance: moon sighting disputes happen every year. | Unique in a weather app. | Own ephemeris. Cite the published criteria. | M | ○ |

### 3.4 Air

| # | Feature | User value | Uniqueness | Data + licence | Effort | Offline/battery |
|---|---|---|---|---|---|---|
| Q1 | **Winter smog outlook.** A 5-day PM2.5 forecast by part of the day, plus a **ventilation index** (boundary-layer height × wind) explaining *why* evenings and nights are worst. Answers "Best window for a walk: 13–15h" and gives indoor/outdoor advice ("open windows 13–16h; keep closed after 18h"). | Dhaka is among the world's most polluted cities in Dec–Feb. Knowing the clean hours matters. | Unique explanation. Others show a number only. | OM air quality (CAMS) + OM `boundary_layer_height` | S/M | ○ |
| Q2 | **Ground-truth stations.** Nearest monitor readings (US Embassy, DoE CAMS stations, low-cost sensors) beside the CAMS model value, with a "model vs station" bias note. | CAMS at ~40 km under- or over-reads city hotspots. | Partial (IQAir does stations). Model-vs-station honesty is unique. | OpenAQ v3: **API key required, keys are non-transferable**, free for non-commercial, per-source licences via `/v3/licenses`. Needs a per-user key option or a self-hosted proxy. | M | ◐ |
| Q3 | **Exposure diary.** Cumulative PM2.5 this week and month at saved places, with a "cigarette-equivalent" (Berkeley Earth's ~22 µg/m³/day ≈ 1 cigarette) clearly labelled as an analogy. | Makes chronic exposure tangible and shareable. | Rare. | OM AQ (computed) | S | ○ |

### 3.5 Climate & history

| # | Feature | User value | Uniqueness | Data + licence | Effort | Offline/battery |
|---|---|---|---|---|---|---|
| C1 | **Forecast verification ("Was it right?").** Compares past model forecasts with ERA5 or observed outcomes per location. Shows "Over the last 30 days here, ICON was closest on highs; GFS over-forecast rain by 40%", then optionally weights the consensus by recent skill. | Real expert value. Builds trust. | **Unique.** No consumer app shows per-location model skill. | OM Historical Forecast API + Previous Runs API + archive (CC BY 4.0) | M | ◐ cache weekly |
| C2 | **Place climate stripes.** Warming stripes from ERA5 annual means (1940–now) for any place, plus rainy-season totals and a past-year anomaly calendar heatmap. | Instantly shareable climate literacy. | Rare in-app. | OM archive (ERA5, Copernicus licence: attribution) | S | ◐ once per place |
| C3 | **2050 heat outlook.** CMIP6 downscaled: "Days with wet-bulb over 28°C per year: 1990s ~X, 2050 ~Y" for your place, with model range. | Makes climate projections local and actionable. | Unique. | OM Climate API (CMIP6 HighResMIP; licences vary per model, so check each before use; attribution) | M | ◐ once |
| C4 | **Monsoon season tracker.** Season-to-date rain against the 1991–2020 normal, onset/withdrawal status (link to BMD/IMD onset declarations), dry spells and active/break phases. | Farmers and planners live by the monsoon. | Unique for BD. | OM forecast + archive. BMD/IMD links. | M | ◐ |

### 3.6 Outdoor activity planner ("best hours to…")

| # | Feature | User value | Uniqueness | Data + licence | Effort | Offline/battery |
|---|---|---|---|---|---|---|
| P1 | **Activity windows engine.** One scoring engine with presets, each giving the top 3 windows in the next 48h with reasons:<ul><li>Run/cycle: WBGT, PM2.5, rain</li><li>**Dry laundry**: vapour-pressure deficit, wind, sun, no rain 4h</li><li>**Fly a kite**: steady 10–25 km/h, no gusts over 40, no thunder</li><li>Photography</li><li>Stargazing</li></ul> | Turns data into decisions. Laundry drying is a genuine monsoon pain point. | Some apps have a few "indices", but none are explained or scored by hour like this. | OM | M | ○ |
| P2 | **Spraying window (farmers).** Wind 3–15 km/h, no rain for 6h after, Delta-T 2–8°C, no inversion (low wind and clear night). | Avoids wasted pesticide and drift, and saves money. | Common in AU/EU agri apps, absent in BD consumer apps. | OM (`et0`, VPD, wind, precipitation) | S/M | ○ |
| P3 | **Rice crop advisor (Boro/Aman/Aus).** The user picks crop and sowing date. The app shows stage by calendar and growing-degree days, plus stage-specific risks: cold injury for Boro seedbeds (<15°C), heat sterility at flowering (>35°C), flash-flood risk in the haor in April. Includes an ET0 water balance (FAO-56). | Concrete value to the largest occupational group. | Unique in a FOSS consumer app. BAMIS (gov) publishes bulletins but no app logic. | OM `et0_fao_evapotranspiration`, soil moisture, temperature. Thresholds cited from BRRI/IRRI literature. | L | ○ |
| P4 | **Fisher's sea card.** For coastal places: significant wave height, swell, wind and gusts for 72h, plus active maritime signal (from A5) and a plain "conditions rough / very rough" label, **never "safe to go"**. | Hilsa fishers face deadly seas. | Unique for BD small boats. | OM marine API (not for navigation, so say so) | M | ◐ |
| P5 | **Commute rain & waterlogging hint.** Rain intensity in user-set commute windows. Flags "intense rain >20 mm/h: waterlogging likely in low-lying areas" for Dhaka and Chattogram. | Daily relevance for persona 1. | Unique framing. | OM (minutely_15 where available, else hourly) | S | ○ |

### 3.7 Widgets, lock screen, Wear

| # | Feature | User value | Uniqueness | Data + licence | Effort | Offline/battery |
|---|---|---|---|---|---|---|
| W1 | **Home-screen widgets (Jetpack Glance).** Now-plus-next-6h, a mini meteogram, AQI dial, "next safe-work window", sun/moon. Refreshed from the cache, never by waking GPS. Optional ongoing-notification glance for the lock screen, off by default. | Widgets are the #1 retention driver for weather apps, and Carrot paywalls them. | Free, with expert content. | Cache only | M | ● periodic (30–60 min, inexact) |
| W2 | **Live-sky DreamService and live wallpaper** (planned). The hero sky as a screensaver or wallpaper: real sun and moon position, star field, current cloud cover and rain, an optional minimal clock. | Beautiful and on-brand. Turns a charging phone into a weather window. | Breezy has a live wallpaper. A real-ephemeris sky is unique. | Own ephemeris + cache | L | ● GPU while visible. Cap at 30 fps, pause when off-screen. |
| W3 | **Wear OS tile and complication.** | Watch users. | Common. | Cache via Data Layer (**needs GMS on phone and watch**, which complicates F-Droid) | L | ● |

### 3.8 Sharing (beautiful cards)

| # | Feature | User value | Uniqueness | Data + licence | Effort | Offline/battery |
|---|---|---|---|---|---|---|
| X1 | **Shareable cards.** Render a card to PNG: "Today vs 1940–2026", AQI vs WHO ("6.4× WHO"), 48h meteogram poster, climate stripes, cyclone distance card. Each card carries an attribution footer automatically and comes with an optional auto-generated Bangla caption to paste alongside. | Free word-of-mouth on WhatsApp and Facebook (how things spread in BD). | Most apps share a link, not a designed image. | Whatever the card shows; attribution baked in. | S/M | ○ |
| X2 | **"Data receipt" export for journalists and researchers.** CSV/JSON of what's on screen with model run times, coordinates (rounded), source list, licence text and a suggested citation. | Makes Barometer citable in reporting. Fits open-data values. | **Unique.** | All (licence text per source) | S | ○ |

### 3.9 Accessibility & localisation

| # | Feature | User value | Uniqueness | Data + licence | Effort | Offline/battery |
|---|---|---|---|---|---|---|
| L1 | **Full Bangla UI.** Bangla numerals optional. Bangla date (Bangabda) and the six seasons (ছয় ঋতু). Bangla place names from geocoding (`language=bn`). Later: Hindi, Urdu, Nepali and Assamese via Weblate (free hosting for libre projects). | Most of the target users read Bangla first. | Few polished Bangla weather apps besides BMD's. | OM geocoding supports a language parameter | M | ○ |
| L2 | **Offline-first.** Last good forecast persisted. A clear "updated 3h ago" staleness badge. Hazard cards degrade gracefully. Optional "low-data mode" that fetches fewer variables and compresses responses (gzip). | Patchy mobile data, load-shedding, data costs. | Breezy caches. Explicit staleness honesty is rarer. | — | M | ○ (saves battery) |
| L3 | **Accessibility pass.** TalkBack labels on every chart (a spoken summary of the meteogram), large-text layouts, a colour-blind-safe AQI and model palette, reduced-motion mode for the animated sky. | Inclusive. Also required for a quality Play listing. | Charts are usually invisible to screen readers. | — | M | ○ |
| L4 | **Icon-first simple mode.** A big-type single screen for low-literacy or elderly users: today, danger hours, rain yes/no. | Persona 3. | Unique. | — | S/M | ○ |

### 3.10 Community

| # | Feature | User value | Uniqueness | Data + licence | Effort | Offline/battery |
|---|---|---|---|---|---|---|
| K1 | **Phone barometer logger.** Many Android phones have a pressure sensor. Log pressure every 15–30 min *locally* to show a real 3h tendency next to the model's. | Fits the app's name. Gives true local pressure falls before nor'westers. | Rare and charming. | Device sensor (no network) | S | ● low (sensor batch reads) |
| K2 | **Personal weather station (local).** Read the user's own Ecowitt/Ambient gateway over LAN (Ecowitt local HTTP API) or import CSV, and show it beside the forecast. Also guide owners to share upstream with open networks (WMO WOW, CWOP) instead of Barometer building its own network. | Weather-nerd persona. Hyperlocal truth. | WU does this via the cloud. Local-only is unique and private. | User's own device | M | ◐ |
| K3 | **Crowd reports** (fog, waterlogging, hail, flooding street). **Deferred.** It needs a server, moderation and anti-abuse, and raises location-privacy risks (reports reveal where people live and move). If ever built: no accounts, coarse grid cells (≥2 km), 6h expiry, rate limits, a small set of fixed report types and no free text, a self-hosted FOSS backend, and the data published openly as CC0 aggregates. | Ground truth for flooding and fog. | — | Own server (cost and ops) | L | ◐ |

---

## 4. Ranked top 10 for v0.2–v0.4

The ranking criteria were: (a) lives or money at stake in BD, (b) uniqueness, (c) reuse of data already fetched, (d) seasonality, since the app is being built in November and winter smog and fog are next, (e) effort.

| Rank | Release | Feature(s) | Why |
|---|---|---|---|
| 1 | v0.2 | **L1 + L2: Bangla UI + offline-first cache with staleness** | Foundation for every hazard feature. Without Bangla and offline, the people most at risk can't use the app. It's cheap to do early and expensive to retrofit. |
| 2 | v0.2 | **H1: Heat-work planner (WBGT, work-rest hours)** | The highest life-impact feature that needs *zero new data*. Directly addresses rickshaw pullers and outdoor workers. Unique. |
| 3 | v0.2 | **Q1 + H5: Winter smog outlook + fog for ferries and flights** | Seasonal: December–February is coming. Both reuse existing endpoints plus a couple of variables, and they're shareable ("best walking window", "launch fog till 10am"). |
| 4 | v0.2 | **A1 + A2: Morning brief + on-device threshold alerts** | Turns the app from "open to check" into "it tells me". All logic is on-device, so there's no server and privacy stays intact. It's prerequisite plumbing (WorkManager, notification channels) for the hazard alerts. |
| 5 | v0.3 | **W1: Glance widgets** | Retention. A free expert widget is a direct jab at Carrot and Windy paywalls. It reuses the cache. |
| 6 | v0.3 | **A3 + A4: Kalbaishakhi and lightning risk (derived, no strike feed)** | Ship before March for pre-monsoon. It targets BD's single largest weather killer by count. It's honest because it's probabilistic, from model fields, plus safety education. |
| 7 | v0.3 | **P1 + P2: Activity windows (laundry, run, kite) + spraying window** | Makes the app useful every day, not only on hazard days. Farmers get value without the full crop advisor. |
| 8 | v0.3 | **X1 + X2: Shareable cards + data receipt** | Growth with no ads: WhatsApp-native distribution, plus journalism and open-data credibility. It bakes attribution in. |
| 9 | v0.4 | **H2: River flood panel (GloFAS)** | Ready before the June monsoon. Unique at consumer level. Add H3 (FFWC) if BWDB grants access. |
| 10 | v0.4 | **H4 + A5: Cyclone tracker + BD signal explainer + official warning links** | Ships before the April–May cyclone season. Highest-stakes, highest-effort, and the most liability-sensitive, which is why it comes after the alert plumbing and disclaimers are mature. |

**Next after v0.4:**
- C1 forecast verification, the nerd crown jewel
- W2 live-sky DreamService
- S1 stargazing score
- P4 fisher card
- K1 phone barometer logger
- P3 crop advisor

### Won't do (and why)

- **Own radar map via RainViewer.** The free tier is now zoom ≤7, one colour scheme, no nowcast and personal/educational use only. BD radar coverage is thin anyway, and it would risk the NonFreeNet anti-feature. Deep-link to Windy, Zoom Earth or RainViewer instead.
- **Real-time lightning strike map from Blitzortung**, unless written permission comes. Its terms are informal: forum statements say non-commercial only, no direct access to its servers, apps must use their own server, and "for entertainment". A safety feature can't rest on that. Derived risk (A4) only.
- **Ads, analytics SDKs, crash reporters, Firebase, Google Play Services** (including fused location). They break the privacy stance and F-Droid.
- **User accounts or a cloud backend in v0.x.** That includes crowd reports (K3), which wait until there's a moderation and privacy design and a host.
- **Authoring our own official-looking warnings** (colour codes, signal numbers, "evacuate"). Barometer *explains and links* official warnings and never issues them.
- **"Safe to go to sea / safe to work" verdicts.** Barometer gives conditions and risk, never permission.
- **Precise or background location.** Coarse only. Widgets use the last-known saved place.
- **Pollen.** CAMS pollen is Europe-only and has no BD relevance.
- **AI chat / LLM summaries.** Rule-based plain-language text is deterministic, translatable, offline and auditable.
- **Wear OS (for now).** The Data Layer needs GMS and the audience in BD is small. Revisit later.
- **Hyperlocal "minute-by-minute" claims.** Model resolution doesn't support them in BD, so don't promise them.

---

## 5. Distribution notes

### 5.1 F-Droid

- **Dependencies are fine.** Compose, AndroidX, kotlinx (Apache-2.0), JUnit (test) are all FOSS. There is no GMS, Firebase, Play Core, or proprietary SDK. **Keep it that way.** Watch for transitive pulls when adding libraries (e.g. some Glance or Wear artifacts, `play-services-location`, ML Kit). Run `./gradlew :app:dependencies` before each release.
- **Disable the AGP dependency-metadata block.** F-Droid cannot verify the encrypted blob, and reproducible-build checks fail. Use `android { dependenciesInfo { includeInApk = false; includeInBundle = false } }`.
- **Licence.** Pick an OSI licence and add `LICENSE`. GPL-3.0-or-later fits the values; Apache-2.0 if you want permissive. Note that Open-Meteo's *server* is AGPL, which doesn't affect API clients.
- **Builds.** Builds must come from source with no prebuilt binaries in the repo. Use tagged releases and a `versionCode` bump per release. Provide Fastlane-style metadata (`fastlane/metadata/android/en-US/…`, plus `bn/`) for descriptions and screenshots. Aim for reproducible builds so F-Droid can ship *your* signature.
- **Anti-features.**
  - **NonFreeNet** applies when an app "promotes or depends entirely on a proprietary network service". Open-Meteo is open-source (AGPL), so it should not trigger it.
  - Optional proprietary sources (RainViewer, a FIRMS or OpenAQ key) should be off by default and replaceable. Breezy's `freenet` flavour pattern is the precedent.
  - **Tracking** is avoided: no telemetry; any update check or crash report must be opt-in.
  - Ask `team@f-droid.org` if unsure.
- **IzzyOnDroid** repo is a faster first step than main F-Droid.

### 5.2 Google Play

- **Location.**
  - Keep `ACCESS_COARSE_LOCATION`, foreground-only, requested in context (the GPS button). Play's own guidance cites regional weather as a coarse-location use case.
  - **Do not request `ACCESS_BACKGROUND_LOCATION`.** It needs a Permissions Declaration Form, a video and a prominent disclosure, and it's often rejected when the feature can work in the foreground. Widgets and alerts use the last saved place.
- **Background work.**
  - Use WorkManager periodic work (minimum 15 min; prefer 1–3h with network and battery-not-low constraints).
  - Avoid `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, which Play restricts to alarm-clock and calendar apps; the morning brief can tolerate a few minutes of drift.
  - No foreground service is needed. If a cyclone "watch mode" ever needs one, it must declare a valid type.
  - Request `POST_NOTIFICATIONS` (Android 13+) only when the user enables an alert.
- **Data safety form.**
  - Coordinates sent to Open-Meteo count as approximate location data leaving the device. Declare it honestly: approximate location, transmitted for app functionality, not used for tracking, not sold, encrypted in transit.
  - **Round coordinates to 2 decimals (~1 km)** before any request. It reduces sensitivity and doesn't change model output.
- **Privacy policy URL** is required even for no-collection apps. Host it in the repo or GitHub Pages.
- **Target API** yearly requirement. `targetSdk` 37 is current.
- **Health and safety copy.** Heat and AQI advice is general wellness information. Avoid medical claims and cite WHO, ISO 7243 and ACGIH sources.
- **Monetisation constraint.** If ever adding paid features or ads, Open-Meteo's free tier no longer applies (see §6). Donations (Liberapay or GitHub Sponsors) are explicitly allowed.

### 5.3 Privacy stance (publish verbatim-ish)

> Barometer has no accounts, no ads, no analytics and no trackers. Your places are stored only on your phone. To get forecasts, the app sends a rounded location (~1 km) directly to Open-Meteo and the other data services listed in Settings → Sources; those services see your IP address as with any web request. Nothing is sent to the Barometer developer. GPS is used only when you tap it, and never in the background.

### 5.4 Attribution obligations (build an in-app **Sources & licences** screen)

| Source | Licence / terms | Commercial use | Required attribution |
|---|---|---|---|
| Open-Meteo (all APIs) | Data CC BY 4.0. Free tier is non-commercial: no ads or subscriptions; donations OK. | Only with a paid API plan (key). | "Weather data by Open-Meteo.com" with a link, **next to where data is displayed** (footer per screen, widget long-press/about, share cards). |
| ECMWF IFS / AIFS, DWD ICON | CC BY 4.0 | Yes | Name the provider. |
| NOAA GFS / GFS-Wave | US gov, public domain | Yes | Credit appreciated. |
| JMA | JMA website terms (reuse allowed with credit) | Generally yes, check | "Source: JMA". |
| ECCC GEM | ECCC Open Data Licence | Yes | "Contains information licensed under the Data Server End-use Licence of Environment and Climate Change Canada". |
| Copernicus CAMS / C3S (ERA5, AQ) | Copernicus licence (free, open, attribution) | Yes | "Generated using Copernicus Climate Change Service / Atmosphere Monitoring Service information [year]"; "neither the EC nor ECMWF is responsible…". |
| GloFAS (flood API) | Copernicus Emergency Management Service, EWDS terms | Yes, with attribution | Credit "Copernicus Emergency Management Service – GloFAS". |
| Météo-France (marine MFWAM, SMOC) | Etalab Open Licence 2.0 | Yes | "Météo-France". |
| CMIP6 (Climate API) | Mostly CC BY 4.0, **some models non-commercial**; check per model. | Varies | Model and institution names. |
| GDACS | EU reuse policy (Decision 2011/833/EU), CC BY 4.0 in feeds | Yes | "Global Disaster Alert and Coordination System, GDACS". Link to the disclaimer. |
| JTWC | US DoD, public domain | Yes | Credit "JTWC"; state it is not the official RSMC for BD. |
| IMD RSMC New Delhi / BMD | Government sites, no clear open licence | Unclear | **Link out, don't republish.** Ask BMD in writing before parsing bulletins. |
| FFWC / BWDB API | Unpublished terms | Unclear | Ask BWDB (`api.support@bwdb.gov.bd`) first. |
| USGS earthquakes | Public domain | Yes | "USGS" credit requested. |
| NASA FIRMS | NASA open data, free MAP_KEY (5,000 tx/10 min) | Generally yes | NASA FIRMS acknowledgement statement. |
| OpenAQ | Free for non-commercial; per-source licences; **keys non-transferable** | Contact OpenAQ | OpenAQ plus the original source per `/v3/licenses`. |
| RainViewer (if ever) | Personal/educational only; zoom ≤7 since 2026-01 | No | "Weather data by RainViewer" link (mandatory per FAQ). |
| Blitzortung (if ever) | Informal: CC BY-SA 4.0 claimed, non-commercial only, no direct server access | No | Only with written permission. |
| CelesTrak (TLEs) | Free public | Yes | "CelesTrak". |

---

## 6. Risks

### 6.1 Data reliability
- **Models in BD.** All five models are global (9–25 km). Convective storms, nor'westers and sea-breeze rain are sub-grid. Communicate this with confidence and ensemble spread, and never use point-precise language. CAPE-based thunder risk has many false alarms; tune and verify against BMD reports and the C1 verification framework.
- **CAMS AQ** (~40 km) smooths Dhaka hotspots and can be biased several-fold. Label it a "model estimate" and show stations when possible (Q2).
- **ERA5** is a grid-cell average (already disclosed on the card, which is good). The same caveat applies to the GloFAS river selection (5 km): allow nudging the point and show which river was picked.
- **Marine and sea-level** data are explicitly "not for navigation" per Open-Meteo, with limited coastal accuracy.
- **Official sources change format without notice** (government HTML/PDF). Any parser needs graceful failure with a "see official site" fallback, never a stale warning shown as current. **Every hazard card shows issue time and source.**
- **Single upstream.** Open-Meteo is one point of failure. Mitigations:
  - Offline cache (L2).
  - Clear error states.
  - Optional user-configurable base URL, since Open-Meteo is self-hostable under AGPL.

### 6.2 API rate limits and commercial terms
- **Open-Meteo free tier:**
  - 600 calls/min, 5,000/hour, 10,000/day and ~300,000/month, **per IP and per application**.
  - CC BY 4.0, non-commercial only: no ads, no subscriptions, and no "commercial products". Donations are OK.
  - Abuse can lead to blocking without notice.
- **Per-device requests are fine:** each phone has its own IP. v0.1 makes about 5 calls per refresh. Keep the following rules:
  - Cache the archive (ERA5) per place per day and geocoding indefinitely.
  - Batch variables into one forecast call.
  - Widgets and background jobs read the cache and refresh at most hourly.
  - Add jitter so the whole user base doesn't hit at :00.
- **Never route all users through one proxy** on the free tier. Shared IP limits would trip. If a proxy becomes necessary (for FIRMS or OpenAQ keys), that's the point to buy a commercial Open-Meteo key or self-host.
- **Going commercial** in any form (ads, a paid tier, a sponsor-branded build) requires an Open-Meteo API subscription and re-checking OpenAQ, RainViewer, Blitzortung and the CMIP6 per-model licences.
- **Keyed services** (FIRMS, OpenAQ) can't safely ship a shared key in an open-source APK: keys are extractable, and OpenAQ forbids transfer. Options are "bring your own key" in settings, or keep the features off.

### 6.3 Liability wording for hazard features
Put this in the Sources screen, the first run of any alert, and as a footer on every hazard card:

> **Barometer is not an official warning service.** Forecasts and risk scores are computer-model estimates and can be wrong, late, or miss local events. Always follow instructions from the Bangladesh Meteorological Department (BMD), the Flood Forecasting and Warning Centre (FFWC), the Department of Disaster Management, port and BIWTA authorities, and local officials. Do not rely on this app to decide whether it is safe to go to sea, travel, work outdoors, or stay in an area at risk. In an emergency call **999**.

Design rules that follow from it:
- Use risk language ("high risk", "likely", "conditions rough"), never permission language ("safe").
- Show the official signal or warning *verbatim, with source and time*, separately from Barometer's own model-based scores. Use different visual treatment so they're never confused.
- Never send a notification that looks like an official warning: no government emblem and no "WARNING" without a source.
- If an official feed fails, say "official status unavailable — check BMD", not "no warnings".
- Heat and AQI advice should cite WHO, ISO 7243 and ACGIH guidance and recommend medical help for symptoms. No medical claims.
- Include the disclaimer and the "as is" clause of the chosen licence in the About screen.

---

## Sources (consulted 2026-11-24)

- Open-Meteo terms & limits: https://open-meteo.com/en/terms · licence & attribution: https://open-meteo.com/en/licence · flood API: https://open-meteo.com/en/docs/flood-api · marine API: https://open-meteo.com/en/docs/marine-weather-api
- RainViewer API: https://www.rainviewer.com/api.html · 2026 transition FAQ: https://rainviewer.com/api/transition-faq.html · LibreWXR note: https://github.com/JoshuaKimsey/LibreWXR
- Blitzortung forum on data access/commercial use: https://forum.blitzortung.org/showthread.php?pid=12557 · https://forum.blitzortung.org/showthread.php?tid=4247 · https://www.lightningmaps.org/about
- GDACS API quickstart & terms: https://data.gdacs.org/Documents/2025/GDACS_API_quickstart_v1.pdf · https://gdacs.org/About/termofuse.aspx
- NASA FIRMS MAP_KEY: https://firms.modaps.eosdis.nasa.gov/api/map_key · https://www.earthdata.nasa.gov/data/tools/firms
- OpenAQ licences/terms/keys: https://docs.openaq.org/resources/licenses · https://docs.openaq.org/about/terms · https://docs.openaq.org/using-the-api/api-key
- FFWC API: https://api.ffwc.gov.bd/ · https://api3.ffwc.gov.bd
- F-Droid anti-features: https://f-droid.org/docs/Anti-Features/ · Breezy Weather on F-Droid: https://f-droid.org/packages/org.breezyweather
- Play background location: https://support.google.com/googleplay/android-developer/answer/9799150
- BD lightning mortality: https://news.mongabay.com/2026/06/bangladeshs-lightning-death-toll-persists-as-years-of-govt-safeguards-fail/ · https://bdnews24.com/bangladesh/oldpsszerl · https://www.dhakatribune.com/bangladesh/408854/lightning-deaths-rise-as-rural-bangladesh-lacks
- Rickshaw-puller heat study: https://arxiv.org/html/2511.00081 · https://en.prothomalo.com/bangladesh/tg1xs3cn30
- BD maritime/river port signals: https://www.thedailystar.net/environment/weather/news/what-do-signals-1-11-mean-ports-during-storms-3317891 · https://www.tbsnews.net/bangladesh/environment/what-signals-1-11-mean-maritime-ports-during-storms-860361 · https://en.banglatribune.com/national/news/44157/Signal-codes-for-storm
- Fog and launch suspensions: https://www.tbsnews.net/bangladesh/dense-fog-halts-launch-services-nationwide-again-today-1321946 · https://www.jagonews24.com/en/national/news/88474
- BMD apps: https://chrome-stats.com/d/bd.gov.bmd.abohawa · https://www.thedailystar.net/country/news/pm-opens-met-offices-weather-app-1336303
- Competitor complaints: https://community.windy.com/topic/40171/subscription-increase · https://justuseapp.com/en/app/997079492/windy-app-wetter-radar/reviews · https://justuseapp.com/en/app/486154808/weather-underground-local-map/reviews · https://www.stuff.tv/app-reviews/carrot-weather/review · https://apps.apple.com/us/app/-/id994459137 · https://apps.apple.com/sg/app/id1531561063 · https://unstar.app/blog/weather-apps-ranked-by-user-complaints-2026

## Owner request (2026-11-24): radar & map, a later version

The owner loves Weather Underground's radar and map, so it's wanted for a future version (not v0.2). Licensing notes to settle first:
- **RainViewer**: since January 2026 the free tier is personal/educational use only, zoom ≤7, no nowcast. It doesn't fit a public free app without permission.
- Options to look into:
  - Open-Meteo model precipitation and cloud fields, drawn on a grid as our own "model radar" with animated frames (no licence issue).
  - NASA GIBS satellite imagery (IMERG precipitation, GOES/Himawari true colour; public domain, needs attribution).
  - EUMETSAT/Himawari geostationary loops.
  - BMD radar images (terms unpublished, so ask first).
- Basemap: OpenStreetMap-derived vector tiles (e.g. self-styled dark, crimson accents) with OSM attribution; respect the tile usage policy.

### Owner's refinement (2026-11-24): a LOCAL map, not a world radar
Show only the selected place: a self-styled city map (OSM data for the city bounding box, fetched once via Overpass and cached on-device; black ground, steel rivers, thin roads, landmarks, crimson accents; ODbL attribution) inside a ~150–200 km regional view. Overlays come from an Open-Meteo multi-point grid (~15×15, one request): model rain with a −2 h…+48 h scrub timeline, drifting cloud cover, animated wind streamlines, AQI and heat haze. Label it honestly as "model rain" (9–13 km grid), not radar. Optionally add BMD Doppler radar images later if BMD permits. Working name: "Chart" tab.
