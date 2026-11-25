"""Tiny shape model -> Android VectorDrawable XML and SVG (same geometry, same gradients)."""
import math
def f(x): return ('%.2f' % x).rstrip('0').rstrip('.')
def circ(cx, cy, r):
    return f"M{f(cx-r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2*r)},0a{f(r)},{f(r)} 0 1,0 {f(-2*r)},0Z"
def ellipse(cx, cy, rx, ry):
    return f"M{f(cx-rx)},{f(cy)}a{f(rx)},{f(ry)} 0 1,0 {f(2*rx)},0a{f(rx)},{f(ry)} 0 1,0 {f(-2*rx)},0Z"
def polar(cx, cy, r, deg):  # deg: math angle, 0 = east, counter-clockwise, y up
    a = math.radians(deg); return cx + r*math.cos(a), cy - r*math.sin(a)
def line(p, q): return f"M{f(p[0])},{f(p[1])}L{f(q[0])},{f(q[1])}"
def poly(pts, close=True):
    s = "M" + "L".join(f"{f(x)},{f(y)}" for x, y in pts); return s + ("Z" if close else "")
def arc(cx, cy, r, a0, a1):  # math angles, drawn from a0 to a1 clockwise-on-screen if a0>a1
    p, q = polar(cx, cy, r, a0), polar(cx, cy, r, a1)
    large = 1 if abs(a0-a1) > 180 else 0; sweep = 1 if a0 > a1 else 0
    return f"M{f(p[0])},{f(p[1])}A{f(r)},{f(r)} 0 {large},{sweep} {f(q[0])},{f(q[1])}"

def lin(x1, y1, x2, y2, *stops): return ('linear', (x1, y1, x2, y2), stops)
def rad(cx, cy, r, *stops): return ('radial', (cx, cy, r), stops)
# stop: (offset, '#RRGGBB', alpha)

def argb(c, a=1.0): return '#%02X%s' % (round(a*255), c.lstrip('#').upper())

class Icon:
    def __init__(s): s.items = []  # ('path', d, attrs) or ('clip', d, [items])
    def path(s, d, fill=None, stroke=None, width=None, cap='round', evenodd=False, alpha=1.0, join='round'):
        s.items.append(('path', d, dict(fill=fill, stroke=stroke, width=width, cap=cap, evenodd=evenodd, alpha=alpha, join=join)))
    def clip(s, d):
        sub = Icon(); s.items.append(('clip', d, sub)); return sub

def _vd_paint(attr, p, ind):
    if p is None: return '', ''
    if isinstance(p, str): return f' android:{attr}="{p if p.startswith("#") and len(p)==9 else argb(p)}"', ''
    if isinstance(p, tuple) and len(p) == 2 and isinstance(p[1], float):
        return f' android:{attr}="{argb(p[0], p[1])}"', ''
    kind, g, stops = p
    if kind == 'linear':
        head = f'android:type="linear" android:startX="{f(g[0])}" android:startY="{f(g[1])}" android:endX="{f(g[2])}" android:endY="{f(g[3])}"'
    else:
        head = f'android:type="radial" android:centerX="{f(g[0])}" android:centerY="{f(g[1])}" android:gradientRadius="{f(g[2])}"'
    items = ''.join(f'\n{ind}        <item android:offset="{f(o)}" android:color="{argb(c, a)}" />' for o, c, a in stops)
    return '', (f'\n{ind}    <aapt:attr name="android:{attr}">\n{ind}        <gradient {head}>{items}\n{ind}        </gradient>\n{ind}    </aapt:attr>')

def _vd_items(items, ind):
    out = []
    for kind, d, a in items:
        if kind == 'clip':
            out.append(f'{ind}<group>\n{ind}    <clip-path android:pathData="{d}" />\n' + _vd_items(a.items, ind+'    ') + f'\n{ind}</group>')
            continue
        fa, fg = _vd_paint('fillColor', a['fill'], ind)
        sa, sg = _vd_paint('strokeColor', a['stroke'], ind)
        extra = ''
        if a['stroke'] is not None:
            extra += f' android:strokeWidth="{f(a["width"])}" android:strokeLineCap="{a["cap"]}" android:strokeLineJoin="{a["join"]}"'
        if a['evenodd']: extra += ' android:fillType="evenOdd"'
        if a['alpha'] != 1.0:
            if a['fill'] is not None: extra += f' android:fillAlpha="{f(a["alpha"])}"'
            if a['stroke'] is not None: extra += f' android:strokeAlpha="{f(a["alpha"])}"'
        body = fg + sg
        if body: out.append(f'{ind}<path android:pathData="{d}"{fa}{sa}{extra}>{body}\n{ind}</path>')
        else: out.append(f'{ind}<path android:pathData="{d}"{fa}{sa}{extra} />')
    return '\n'.join(out)

def to_vd(icon, comment=''):
    return ('<?xml version="1.0" encoding="utf-8"?>\n' + (f'<!-- {comment} -->\n' if comment else '') +
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n' + _vd_items(icon.items, '    ') + '\n</vector>\n')

class _SvgCtx:
    def __init__(s, prefix): s.defs = []; s.n = 0; s.prefix = prefix
    def gid(s): s.n += 1; return f'{s.prefix}{s.n}'

def _svg_paint(ctx, p):
    if p is None: return 'none', 1.0
    if isinstance(p, str): return p, 1.0
    if isinstance(p, tuple) and len(p) == 2 and isinstance(p[1], float): return p[0], p[1]
    kind, g, stops = p; i = ctx.gid()
    st = ''.join(f'<stop offset="{f(o)}" stop-color="{c}" stop-opacity="{f(a)}"/>' for o, c, a in stops)
    if kind == 'linear':
        ctx.defs.append(f'<linearGradient id="{i}" gradientUnits="userSpaceOnUse" x1="{f(g[0])}" y1="{f(g[1])}" x2="{f(g[2])}" y2="{f(g[3])}">{st}</linearGradient>')
    else:
        ctx.defs.append(f'<radialGradient id="{i}" gradientUnits="userSpaceOnUse" cx="{f(g[0])}" cy="{f(g[1])}" r="{f(g[2])}">{st}</radialGradient>')
    return f'url(#{i})', 1.0

def _svg_items(ctx, items):
    out = []
    for kind, d, a in items:
        if kind == 'clip':
            i = ctx.gid(); ctx.defs.append(f'<clipPath id="{i}"><path d="{d}"/></clipPath>')
            out.append(f'<g clip-path="url(#{i})">' + _svg_items(ctx, a.items) + '</g>'); continue
        fill, fo = _svg_paint(ctx, a['fill']); stroke, so = _svg_paint(ctx, a['stroke'])
        attrs = f'd="{d}" fill="{fill}"'
        if fo * a['alpha'] != 1: attrs += f' fill-opacity="{f(fo*a["alpha"])}"'
        if a['stroke'] is not None:
            attrs += f' stroke="{stroke}" stroke-width="{f(a["width"])}" stroke-linecap="{a["cap"]}" stroke-linejoin="{a["join"]}"'
            if so * a['alpha'] != 1: attrs += f' stroke-opacity="{f(so*a["alpha"])}"'
        if a['evenodd']: attrs += ' fill-rule="evenodd"'
        out.append(f'<path {attrs}/>')
    return ''.join(out)

def svg_group(icon, prefix):
    ctx = _SvgCtx(prefix); body = _svg_items(ctx, icon.items); return ''.join(ctx.defs), body
