# Weatherglass: store listing

For Google Play and F-Droid (`fastlane/metadata/android/en-US/` layout: `title.txt`, `short_description.txt`, `full_description.txt`). Character counts were checked when this was written.

## Title

```
Weatherglass
```

If Play reports the name as taken, use `Weatherglass: Read the air` (26 chars). Never `Weatherglass Barometer`: it invites confusion with apps that read the phone's pressure sensor, which this app doesn't.

Package: `com.github.sarwaruddin.weatherglass`. The app was called Barometer up to v0.1 and Aneroid in v0.2-dev.

## Short description (≤ 80 chars)

```
Forecasts, air quality, heat stress and model agreement. No ads, no tracking.
```
(77 chars)

Alternative for F-Droid's summary line, which is shorter in practice:
```
Honest weather: models, AQI, heat stress, climate record. No tracking.
```
(70 chars)

## Full description (≤ 4000 chars)

```
Weatherglass is a weather instrument for your pocket, named after the barometer that used to hang in the hall, the one with Rain, Change and Fair on its dial. It shows what the forecast models say, how much they agree, what is in the air, and how today compares with the last 86 years, on a black glass dial built to be read at a glance.

It is made first for Bangladesh and South Asia, where heat, humidity, monsoon rain and polluted air matter more than a sun icon, and it works anywhere in the world.

WHAT YOU GET

• Now: temperature, feels-like, today's high and low, and a one-line summary over a live sky that shows the real moon phase and day or night.
• Next 3 hours: will it rain, and when.
• Hourly and 48-hour meteogram: temperature, dew point, humidity, rain and pressure on one chart. Drag to read any hour.
• 16-day outlook with range bars and rain chance.
• Model consensus: ECMWF, GFS, ICON, GEM and JMA side by side for each day, with spread, wet/dry agreement and a confidence score. You see when the forecast is solid and when it is a guess.
• Air quality: US AQI with PM2.5, PM10, NO₂, O₃, SO₂, CO and dust, each measured against WHO guidelines and Bangladesh's national standard, plus an hourly PM2.5 history and forecast.
• Heat stress: wet-bulb temperature and heat index, with plain advice for people who work outdoors.
• Thunder: storm energy (CAPE) for the next 24 hours, useful in the pre-monsoon kalbaishakhi season.
• Pressure with a 3-hour tendency, wind with gusts and Beaufort force, humidity with dew-point comfort bands, UV index, visibility and cloud layers.
• Sun and moon: sunrise, sunset, day-length change, solar noon, golden hours, moon phase and age.
• On this date: today's forecast high against every year since 1940 from ERA5 reanalysis, with record high, record low, the 1991–2020 normal and the long-term trend per decade.
• Heat-work planner: the safest hours today for hard outdoor work, from the wet-bulb forecast.
• Home-screen widgets in the same black-glass style.
• Shareable cards: send today's reading as an image, with the source on it.
• Several saved places, plus your current location if you allow it.

HOW IT TALKS

Numbers first, then what they mean. Real units (hPa, µg/m³, wet-bulb °C). Short advice only where the science supports it. Sources and limits are stated on the card that uses them.

PRIVACY

No account. No ads. No analytics or tracking libraries. Your saved places stay on your phone. If you allow location, the app rounds your coordinates to about 1 km before sending them to Open-Meteo for the forecast. Weatherglass asks only for approximate location and internet access.

DATA

Weather data by Open-Meteo.com, licensed CC BY 4.0, with model data from ECMWF, DWD, NOAA, ECCC and JMA. Air quality from the Copernicus Atmosphere Monitoring Service (CAMS). Historical climate from ERA5 reanalysis (Copernicus Climate Change Service). Weatherglass shows forecasts. It does not issue warnings: for cyclones, floods and heatwaves, follow your national meteorological service.

DESIGN

Glossy instrument styling in black, chrome and crimson. It's built to stay smooth on budget phones, with no blur effects or heavy animation.
```

(≈ 3,200 characters. Recount after edits; Play's limit is 4,000.)

## Feature bullets (for the website, README and promo graphic)

- **Five models, one verdict:** see where ECMWF, GFS, ICON, GEM and JMA agree.
- **The air, measured honestly:** pollutants against WHO and national limits, not just one AQI number.
- **Heat that's dangerous, not just hot:** wet-bulb heat stress with outdoor-work advice.
- **86 years of context:** every forecast high placed against the ERA5 record since 1940.
- **A meteogram you can read:** temperature, dew point, rain and pressure on one drag-to-inspect chart.
- **Private by construction:** no accounts, no trackers, coordinates rounded before they leave the phone.
- **Plan the hot day:** a heat-work planner that finds the safest hours for outdoor labour.
- **On your home screen:** widgets and shareable reading cards in the same glass style.
- **Runs on modest phones:** tuned on a budget Unisoc handset.

## Screenshot plan

Eight phone screenshots at 1080 × 2400 or the device's native 720 × 1600. Use a *neutral demo city*: a large South Asian city for the regional listing, plus one set for a city elsewhere (e.g. Lisbon) for global listings. Never a personal location. Put each caption above the screenshot in Plex Sans SemiBold, Text colour on Ink, with a 2 px crimson rule under it, matching the wordmark lockup.

For v0.2 the README uses `docs/screens/v0.2/`: `now.png`, `week.png`, `air.png`, `sky.png`, `past.png`, `widget.png`, `share.png`. The original v0.1 store plan:

| # | Screen (from `docs/screens/v0.1`) | Caption |
|---|---|---|
| 1 | Hero: night sky, temperature, summary pill (`01`) | **Read the air.** |
| 2 | 48-hour meteogram with a point inspected (`02`) | **48 hours on one chart.** Temperature, dew point, rain, pressure. |
| 3 | Model consensus card (`03`/`04`) | **Five models. One verdict.** See when the forecast is solid. |
| 4 | Air card with AQI dial and pollutant bars (`04`) | **Pollutants measured against WHO limits.** Not just one number. |
| 5 | Heat stress + humidity + thunder tiles (`05`) | **Heat that's dangerous, not just hot.** Wet-bulb, dew point, storm energy. |
| 6 | 16-day outlook (`02`/`03`) | **16 days, with range bars.** |
| 7 | Sun & moon card (`06`) | **Golden hours, day length, moon.** |
| 8 | On this date, ERA5 record (`07`) | **86 years of context.** Today against every year since 1940. |

Feature graphic (1024 × 500): `wordmark.svg` centred on Ink, with the weatherglass icon on the left at 50% height and no screenshots. For a larger hero, use `icon-weatherglass-dial.svg` (the dial with its engraved weather words).

Before capturing: turn on demo mode (`adb shell settings put global sysui_demo_allowed 1`, then fix the clock, battery and signal), so status bars are clean and identical, with no personal notification icons.

## Privacy statement (plain words; also the Play "Data safety" basis)

```
Weatherglass has no accounts, no ads and no tracking.

What leaves your phone
• To get a forecast, the app sends a place's latitude and longitude to Open-Meteo (open-meteo.com). If you use your current location, the coordinates are rounded to two decimal places, about 1 km, first.
• If you search for a place by name, the text you type is sent to Open-Meteo's geocoding service.
• To show the name of your current location, the app asks your phone's built-in geocoder, which is part of the phone's system software and may contact its own provider.

What stays on your phone
• Your saved places and settings. They are not backed up to us; we have no server.

What we never do
• Collect analytics, crash reports, advertising IDs or contact details.
• Sell or share anything, because we don't have anything.

Permissions
• Approximate location (optional): only to find the weather where you are.
• Internet: to fetch weather data.

Data sources: Open-Meteo.com (CC BY 4.0) with model data from ECMWF, DWD, NOAA, ECCC and JMA; Copernicus Atmosphere Monitoring Service (CAMS) for air quality; ERA5 reanalysis (Copernicus Climate Change Service) for historical climate.
```

**Note for the developer, not for the listing.** `Repo.locate()` rounds the coordinates for Open-Meteo, but `placeName()` passes the *unrounded* fix to `android.location.Geocoder`. On Google-services phones that request goes to Google. Either pass the rounded values (the town name will still resolve), or keep the third bullet above, which is accurate as written. Rounding is the better option because it makes "rounded before it leaves the phone" true everywhere.

### Play Data safety form

- Data collected: **Approximate location**, sent off-device for app functionality only, not stored by the developer, not shared for advertising. It is processed ephemerally by Open-Meteo.
- Data shared with third parties: none, beyond the forecast request above. Disclose Open-Meteo as a service provider.
- Encrypted in transit: yes (HTTPS).
- Users can request deletion: not applicable, since nothing is retained.

## F-Droid specifics

- Anti-features: none expected. All network services are free and open data, and there are no proprietary libraries. Confirm there is no Google Play Services dependency.
- Categories: `Science & Education`, `Internet`.
- Licence: state the app's licence in the repo root before submitting.
- `changelogs/1.txt`: "First release: forecasts, model consensus, air quality, heat stress, ERA5 climate record."
