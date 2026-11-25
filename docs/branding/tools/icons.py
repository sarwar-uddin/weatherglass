"""Weatherglass launcher icon: Android vector drawables (bg / fg / mono) + SVG previews.
Usage (from docs/branding): python tools/icons.py .
  writes icon-weatherglass{-bg,-fg,-mono}.xml, icon-weatherglass.svg (the launcher icon, masked)
  and icon-weatherglass-dial.svg (large version with the engraved weather words).
  PREVIEW_DIR=previews also writes unmasked full-bleed / mono SVGs for the contact sheets.
The previous concepts (aneroid, isobar, barograph) are in git history and as SVGs in archive/.
"""
import math, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from vd import *
C = 54.0
CRIMSON, CRIMSON_HI, CRIMSON_DEEP, OXBLOOD = '#E0203F', '#FF4A63', '#8A1028', '#4A0610'
INK, GRAPH = '#070708', '#1C1C21'
MARC = '/usr/share/fonts/marcellus/Marcellus-Regular.ttf'

def chrome_ring(ic, r_out, r_in, cy=C):
    # outer chrome: bright top, horizon break, dark bottom, rising again (classic Oxygen chrome)
    ic.path(circ(C, cy, r_out) + circ(C, cy, r_in), evenodd=True, fill=lin(C, cy-r_out, C, cy+r_out,
        (0, '#FFFFFF', 1), (0.30, '#C9C9D1', 1), (0.49, '#7A7A84', 1), (0.51, '#4A4A52', 1), (0.80, '#9A9AA4', 1), (1, '#E6E6EE', 1)))

def bg_black():
    b = Icon()
    b.path('M0,0h108v108h-108z', fill=rad(54, 30, 86, (0, '#2C2C33', 1), (0.55, '#111114', 1), (1, '#030304', 1)))
    # faint crimson floor glow, like the desktop
    b.path('M0,0h108v108h-108z', fill=rad(54, 112, 60, (0, CRIMSON, 0.30), (1, CRIMSON, 0)))
    return b

# ---------------------------------------------------------------- geometry
SWEEP0, SWEEP1 = 215, -35          # scale arc, math degrees (open at the bottom, 250 deg)
NEEDLE = 64                        # crimson hand: past CHANGE, towards FAIR
SET = 122                          # silver set-hand (large dial only): the earlier, wetter reading
CLOUD_AT = (C-13.8, C-0.5)         # pictograms: rain cloud on the low side, sun on the high side
SUN_AT = (C+13.6, C-1.0)
PK = 1.22                          # pictogram scale on the launcher icon
# dial words (large version only): (word, math angle of its centre)
WORDS = [('STORMY', 196), ('RAIN', 151), ('CHANGE', 90), ('FAIR', 29), ('VERY DRY', -16)]

def needle_shape(na, L, tail, w):
    perp = na + 90
    tip = polar(C, C, L, na)
    b1 = polar(C, C, w, perp); b2 = polar(C, C, w, perp+180)
    tb = polar(C, C, tail, na+180)
    t1 = polar(*tb, w*0.7, perp); t2 = polar(*tb, w*0.7, perp+180)
    return tip, b1, b2, t1, t2, tb

def cloud_path(cx, cy, k=1.0):
    # one path, several overlapping circles (non-zero fill = their union) on a flat base
    lobes = [(-4.6, 1.0, 3.1), (-1.2, -1.6, 4.0), (3.2, -0.4, 3.4), (5.6, 1.6, 2.3)]
    d = ''.join(circ(cx + dx*k, cy + dy*k, r*k) for dx, dy, r in lobes)
    d += poly([(cx-4.6*k, cy+1.2*k), (cx+5.6*k, cy+1.2*k), (cx+5.6*k, cy+3.9*k), (cx-4.6*k, cy+4.1*k)][::-1])  # same winding as the arcs
    return d

def rain_path(cx, cy, k=1.0):
    # three short slanted streaks under the cloud
    out = ''
    for dx, l in [(-3.0, 3.0), (0.4, 3.8), (3.8, 3.0)]:
        x0, y0 = cx + dx*k, cy + 5.6*k
        out += line((x0, y0), (x0 - 1.3*k, y0 + l*k))
    return out

def sun_rays(cx, cy, r0, r1, n=8, rot=22.5):
    return ''.join(line(polar(cx, cy, r0, rot + i*360/n), polar(cx, cy, r1, rot + i*360/n)) for i in range(n))

def arc_text(text, r, a_mid, size, font=MARC, track=0.0):
    """Outline `text` along a circle round (C, C): baseline at radius r, letters upright, centred on a_mid."""
    from fontTools.ttLib import TTFont
    from fontTools.pens.svgPathPen import SVGPathPen
    from fontTools.pens.transformPen import TransformPen
    ft = TTFont(font); gs = ft.getGlyphSet(); cmap = ft.getBestCmap(); s = size/ft['head'].unitsPerEm
    adv = [(ft['hmtx']['space'][0] if ch == ' ' else gs[cmap[ord(ch)]].width)*s + track for ch in text]
    total = sum(adv) - track
    theta = math.radians(a_mid) + total/(2*r)
    d = ''
    for ch, w in zip(text, adv):
        gw = w - track; th = theta - (gw/2)/r
        if ch != ' ':
            sn, cs = math.sin(th), math.cos(th)
            pen = SVGPathPen(gs)
            m = (s*sn, s*cs, s*cs, -s*sn, C + r*cs - (gw/2)*sn, C - r*sn - (gw/2)*cs)
            gs[cmap[ord(ch)]].draw(TransformPen(pen, m))
            d += pen.getCommands()
        theta -= w/r
    return d

# ---------------------------------------------------------------- WEATHERGLASS
def weatherglass(words=False):
    ic = Icon()
    ic.path(circ(C, 55, 32), fill=rad(C, 55, 32, (0.80, '#000000', 0.6), (1, '#000000', 0)))   # contact shadow
    chrome_ring(ic, 31.5, 26.4)
    ic.path(circ(C, C, 27.6) + circ(C, C, 26.4), evenodd=True,
            fill=lin(C, 26, C, 82, (0, '#18181C', 1), (1, '#F0F0F6', 1)))
    ic.path(circ(C, C, 26.4), fill=rad(C, 46, 30, (0, '#26262D', 1), (0.7, '#111115', 1), (1, '#060607', 1)))
    ink, ink2 = '#E6E6EE', '#8A8A94'
    # engraved scale band at the rim: one line, minor + major ticks
    R0, R1 = 21.4, 24.2
    ic.path(arc(C, C, R1, SWEEP0, SWEEP1), stroke=ink2, width=0.6, cap='butt')
    n = 50; minor, major = [], []
    for i in range(n+1):
        a = SWEEP0 - i*(SWEEP0-SWEEP1)/n
        big = i % 10 == 0
        (major if big else minor).append(line(polar(C, C, R0 if big else R1-1.6, a), polar(C, C, R1, a)))
    ic.path(''.join(minor), stroke=ink2, width=0.55, cap='butt')
    ic.path(''.join(major), stroke=ink, width=1.3, cap='butt')
    if words:
        ic.path(''.join(arc_text(w, 17.6, a, 2.55, track=0.32) for w, a in WORDS), fill='#C9C9D1')
    # ---- pictograms: chrome rain cloud (low / left), warm sun (high / right)
    k = PK if not words else 0.8
    cx, cy = CLOUD_AT if not words else (C-10.2, C-0.6)
    cy -= 1.6*k
    ic.path(cloud_path(cx+0.5, cy+0.8, k), fill='#000000', alpha=0.35)
    ic.path(cloud_path(cx, cy, k), fill=lin(cx, cy-5.6*k, cx, cy+4.1*k,
            (0, '#FFFFFF', 1), (0.45, '#C9C9D1', 1), (0.62, '#8A8A94', 1), (1, '#5A5A64', 1)))
    ic.path(rain_path(cx, cy, k), stroke=lin(cx, cy+5.6*k, cx, cy+9.6*k, (0, '#E6E6EE', 1), (1, '#7A8AA4', 1)), width=1.05*k)
    sx, sy = SUN_AT if not words else (C+10.6, C-2.6)
    rs = 3.5*k
    ic.path(sun_rays(sx, sy, rs+1.3*k, rs+3.2*k), stroke=lin(sx, sy-rs-3, sx, sy+rs+3, (0, '#FFA070', 1), (1, CRIMSON, 1)), width=1.1*k)
    ic.path(circ(sx, sy, rs), fill=rad(sx-rs*0.35, sy-rs*0.4, rs*1.5, (0, '#FFD3A0', 1), (0.35, '#FF7A4A', 1), (0.75, CRIMSON, 1), (1, CRIMSON_DEEP, 1)))
    # set hand (large dial only; on the launcher it only adds clutter): thin silver pointer back towards RAIN
    hand = '#C9C9D1'
    if words:
        ic.path(line(polar(C, C, 3, SET), polar(C, C, 19.5, SET)), stroke=hand, width=0.9)
        ic.path(poly([polar(C, C, 22.6, SET), polar(C, C, 18.4, SET+4.5), polar(C, C, 19.2, SET), polar(C, C, 18.4, SET-4.5)]), fill=hand)
    # crimson indicator with the loop counterweight (the household-barometer hand)
    na = NEEDLE
    tip, b1, b2, t1, t2, tb = needle_shape(na, 23.6, 8.0, 2.2)
    sh = lambda p: (p[0]+0.9, p[1]+1.3)
    ic.path(poly([sh(b1), sh(tip), sh(b2), sh(t2), sh(t1)]), fill='#000000', alpha=0.32)
    ic.path(circ(*sh(polar(C, C, 9.6, na+180)), 3.0), fill='#000000', alpha=0.22)
    ic.path(poly([b1, tip, b2]), fill=lin(*b1, *b2, (0, CRIMSON_HI, 1), (0.5, CRIMSON, 1), (0.51, '#A8142F', 1), (1, CRIMSON_DEEP, 1)))
    ic.path(poly([b2, t2, t1, b1]), fill=CRIMSON_DEEP)
    lc = polar(C, C, 9.8, na+180)
    ic.path(circ(*lc, 3.2) + circ(*lc, 1.8), evenodd=True, fill=lin(lc[0], lc[1]-3.2, lc[0], lc[1]+3.2, (0, CRIMSON_HI, 1), (1, CRIMSON_DEEP, 1)))
    # hub
    ic.path(circ(C, C, 3.6), fill=lin(C, 50.4, C, 57.6, (0, '#FFFFFF', 1), (0.5, '#9A9AA4', 1), (1, '#2E2E36', 1)))
    ic.path(circ(C, C, 1.2), fill='#141418')
    # glass dome
    ic.path(f"M{f(C-24.6)},{f(C-1)}A24.6,24.6 0 0,1 {f(C+24.6)},{f(C-1)}C{f(C+16)},{f(C-8.5)} {f(C-16)},{f(C-8.5)} {f(C-24.6)},{f(C-1)}Z",
            fill=lin(C, 29, C, 50, (0, '#FFFFFF', 0.30), (1, '#FFFFFF', 0.0)))
    ic.path(arc(C, C, 25, 232, 308), stroke='#FFFFFF', width=1.2, alpha=0.35)
    if not words:   # on the large dial the dome gloss is enough, and the spot would sit on the set-hand
        ic.path(ellipse(C-10.5, C-18.4, 3.6, 1.5), fill=rad(C-10.5, C-18.4, 3.6, (0, '#FFFFFF', 0.9), (1, '#FFFFFF', 0)))
    return ic

def weatherglass_mono():
    ic = Icon(); W = '#FFFFFF'
    ic.path(circ(C, C, 31.5) + circ(C, C, 27.6), evenodd=True, fill=W)
    ic.path(''.join(line(polar(C, C, 21.6, SWEEP0 - i*(SWEEP0-SWEEP1)/5), polar(C, C, 24.4, SWEEP0 - i*(SWEEP0-SWEEP1)/5)) for i in range(6)),
            stroke=W, width=1.8, cap='butt')
    cx, cy = CLOUD_AT; cy -= 1.6*PK
    ic.path(cloud_path(cx, cy, PK), fill=W)
    ic.path(rain_path(cx, cy, PK), stroke=W, width=1.5)
    sx, sy = SUN_AT; rs = 3.5*PK
    ic.path(circ(sx, sy, rs), fill=W)
    ic.path(sun_rays(sx, sy, rs+1.3*PK, rs+3.2*PK), stroke=W, width=1.5)
    tip, b1, b2, t1, t2, tb = needle_shape(NEEDLE, 23.6, 8.0, 2.4)
    ic.path(poly([b1, tip, b2, t2, t1]), fill=W)
    lc = polar(C, C, 9.8, NEEDLE+180)
    ic.path(circ(*lc, 3.5) + circ(*lc, 1.8), evenodd=True, fill=W)
    ic.path(circ(C, C, 3.8), fill=W)
    return ic

CONCEPTS = {
    'weatherglass': (bg_black, weatherglass, weatherglass_mono,
                     'Weatherglass: household-barometer dial, chrome rain cloud and warm sun, crimson hand towards Fair, silver set-hand, glass dome'),
}

def write_svg(path, title, layers, size=432, mask=True):
    defs = body = ''
    for i, ic in enumerate(layers):
        d, b = svg_group(ic, 'l%d_' % i); defs += d; body += b
    vb = '18 18 72 72' if mask else '0 0 108 108'
    clip = '<clipPath id="mask"><circle cx="54" cy="54" r="36"/></clipPath>' if mask else ''
    g = f'<g clip-path="url(#mask)">{body}</g>' if mask else body
    open(path, 'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{vb}">\n'
                          f'<title>{title}</title>\n<defs>{clip}{defs}</defs>\n{g}\n</svg>\n')

if __name__ == '__main__':
    out = sys.argv[1]
    for name, (bgf, fgf, monof, desc) in CONCEPTS.items():
        bg, fg, mono = bgf(), fgf(), monof()
        open(f'{out}/icon-{name}-bg.xml', 'w').write(to_vd(bg, f'Weatherglass launcher icon, concept "{name}" - background layer'))
        open(f'{out}/icon-{name}-fg.xml', 'w').write(to_vd(fg, f'{desc} - foreground layer (art kept inside the 66dp safe zone)'))
        open(f'{out}/icon-{name}-mono.xml', 'w').write(to_vd(mono, f'Weatherglass launcher icon, concept "{name}" - monochrome layer for Android 13+ themed icons (alpha only)'))
        write_svg(f'{out}/icon-{name}.svg', f'Weatherglass launcher icon - {name}', [bg, fg])
        write_svg(f'{out}/icon-{name}-dial.svg', 'Weatherglass dial, large version with the weather words', [bg, fgf(words=True)], size=864)
        if os.environ.get('PREVIEW_DIR'):
            pd = os.environ['PREVIEW_DIR']
            write_svg(f'{pd}/raw-{name}-full.svg', f'{name} full bleed', [bg, fg], mask=False)
            write_svg(f'{pd}/raw-{name}-fg.svg', f'{name} foreground only', [fg], mask=False)
            write_svg(f'{pd}/raw-{name}-mono.svg', f'{name} mono', [mono], mask=False)
