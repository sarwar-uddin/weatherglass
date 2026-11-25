"""Wordmark generator. Needs fontTools + Marcellus and IBM Plex Sans Condensed installed (paths below).
Usage (from docs/branding): python tools/wordmark.py wordmark.svg
       rsvg-convert -w 1600 wordmark.svg -o wordmark.png   (2x PNG for the README)
Lockup: the weatherglass dial from the launcher icon (tools/icons.py, same geometry), then WEATHERGLASS
in chrome Marcellus with a crimson rule and the tagline under it.
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from vd import f, svg_group
import icons

def glyphs(fontfile, text, size, x, base, track):
    font = TTFont(fontfile); gs = font.getGlyphSet(); cmap = font.getBestCmap()
    upm = font['head'].unitsPerEm; s = size/upm
    out = []
    for ch in text:
        if ch == ' ': x += font['hmtx']['space'][0]*s + track; continue
        g = cmap[ord(ch)]; pen = SVGPathPen(gs)
        gs[g].draw(TransformPen(pen, (s, 0, 0, -s, x, base)))
        out.append((ch, x, pen.getCommands())); x += gs[g].width*s + track
    return out, x, font['OS/2'].sCapHeight*s

W, H = 800, 220
MARC = icons.MARC
PLEX = '/usr/share/fonts/ibm-plex-sans-condensed/IBMPlexSansCondensed-Medium.ttf'
DIAL = 128                   # emblem diameter (the 63 dp bezel)
GAP = 26
size, track = 60, 9.5
base = 117
word, x1, capH = glyphs(MARC, 'WEATHERGLASS', size, 0, base, track)
total = x1 - track
group_w = DIAL + GAP + total
x0 = (W - group_w)/2
wx = x0 + DIAL + GAP                       # word start
cy = base - capH/2 + 14                    # dial centre: a little below the cap middle, so word + tagline sit beside it
k = DIAL/63.0                              # icon units (108 viewport, bezel r 31.5) -> px
dial_defs, dial_body = svg_group(icons.weatherglass(), 'd')
dial_tf = f'translate({f(x0 + DIAL/2 - 54*k)},{f(cy - 54*k)}) scale({f(k)})'

paths = ''.join(f'<path transform="translate({f(wx)},0)" d="{c}"/>' for _, _, c in word)
tag, tx, _ = glyphs(PLEX, 'READ THE AIR', 15, 0, 0, 7.5)
tagp = ''.join(f'<path transform="translate({f(wx)},{f(base+44)})" d="{c}"/>' for _, _, c in tag)

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<title>Weatherglass wordmark</title>
<desc>Wordmark: the weatherglass dial from the app icon (rain cloud, sun, crimson hand towards Fair), then WEATHERGLASS set in Marcellus (SIL OFL 1.1), tracked, chrome fill, over a crimson rule. Tagline in IBM Plex Sans Condensed Medium (SIL OFL 1.1). Outlined to paths, no font needed.</desc>
<defs>
  <linearGradient id="chrome" gradientUnits="userSpaceOnUse" x1="0" y1="{f(base-capH-2)}" x2="0" y2="{f(base+2)}">
    <stop offset="0" stop-color="#FFFFFF"/><stop offset="0.42" stop-color="#D2D2DA"/>
    <stop offset="0.52" stop-color="#7E7E88"/><stop offset="0.56" stop-color="#5A5A62"/>
    <stop offset="1" stop-color="#C9C9D1"/>
  </linearGradient>
  <radialGradient id="panel" cx="{W/2}" cy="40" r="{W*0.6}" gradientUnits="userSpaceOnUse">
    <stop offset="0" stop-color="#1C1C21"/><stop offset="1" stop-color="#070708"/>
  </radialGradient>
  {dial_defs}
</defs>
<rect width="{W}" height="{H}" rx="18" fill="url(#panel)"/>
<g transform="{dial_tf}">{dial_body}</g>
<g fill="#000000" fill-opacity="0.6" transform="translate(0,2)">{paths}</g>
<g fill="url(#chrome)">{paths}</g>
<path d="M{f(wx)},{f(base+20)}H{f(wx+total)}" stroke="#E0203F" stroke-width="1.5"/>
<g fill="#9A9AA2">{tagp}</g>
</svg>
'''
open(sys.argv[1], 'w').write(svg)
