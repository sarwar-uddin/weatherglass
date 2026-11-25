#!/bin/sh
# Renders the launcher-icon previews into previews/ (run from docs/branding). Needs rsvg-convert.
set -e
mkdir -p previews
PREVIEW_DIR=previews python tools/icons.py .
for s in 432 192 48; do rsvg-convert -w $s -h $s icon-weatherglass.svg -o previews/icon-$s.png; done
rsvg-convert -w 864 icon-weatherglass-dial.svg -o previews/dial-864.png
# themed (Android 13+) preview: system tint applied to the alpha-only mono layer, on a tonal disc
python - <<'PY'
import re
m = open('previews/raw-weatherglass-mono.svg').read()
body = re.search(r'</defs>\n?(.*)</svg>', m, re.S).group(1)
body = body.replace('#FFFFFF', '#E8DEF8')
for name, size in [('themed-192', 192), ('themed-48', 48)]:
    open(f'previews/{name}.svg', 'w').write(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="18 18 72 72">'
        f'<clipPath id="m"><circle cx="54" cy="54" r="36"/></clipPath><g clip-path="url(#m)">'
        f'<rect width="108" height="108" fill="#3B3346"/>{body}</g></svg>')
PY
rsvg-convert previews/themed-192.svg -o previews/themed-mono-192.png
rsvg-convert previews/themed-48.svg -o previews/themed-mono-48.png
rm previews/themed-192.svg previews/themed-48.svg
